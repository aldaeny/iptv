package app.drivelink.cast.core.streaming

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import app.drivelink.cast.core.model.DisplayOrientation
import app.drivelink.cast.core.model.DisplayProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Hardware-accelerated low-latency screen encoder using MediaProjection -> Surface -> MediaCodec.
 * Registers MediaProjection.Callback (mandatory on Android 14+ / API 34+) and caches SPS/PPS
 * codec config so every IDR keyframe is self-contained.
 */
class ScreenCaptureEncoder(
    private val mediaProjection: MediaProjection,
    private val scope: CoroutineScope,
    private val onEncodedNalu: suspend (ByteArray, Int) -> Unit,
    private val onCodecFallback: suspend (String) -> Unit,
    private val onProjectionStopped: () -> Unit = {}
) {
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var drainJob: Job? = null
    private var callbackRegistered = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stop()
            onProjectionStopped()
        }
    }

    fun start(profile: DisplayProfile, orientation: DisplayOrientation, densityDpi: Int) {
        // Android 14 allows only one VirtualDisplay per projection consent.
        virtualDisplay?.surface = null
        releaseCodec()
        if (!callbackRegistered) {
            mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            callbackRegistered = true
        }

        val (width, height) = profile.dimensionsFor(orientation)
        val requestedMime = profile.codec.mimeType
        var selectedMime = requestedMime
        var mediaCodec: MediaCodec? = null
        for (candidateMime in listOf(requestedMime, MediaFormat.MIMETYPE_VIDEO_AVC).distinct()) {
            val format = MediaFormat.createVideoFormat(candidateMime, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, profile.bitrateMbps * 1_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, profile.targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
            }
            val candidate = runCatching { MediaCodec.createEncoderByType(candidateMime) }.getOrNull() ?: continue
            if (runCatching { candidate.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }.isSuccess) {
                selectedMime = candidateMime
                mediaCodec = candidate
                break
            }
            runCatching { candidate.release() }
        }
        val activeCodec = requireNotNull(mediaCodec) { "No hardware video encoder available" }
        codec = activeCodec
        val surface = activeCodec.createInputSurface()
        inputSurface = surface
        activeCodec.start()
        val display = virtualDisplay
        if (display == null) {
            virtualDisplay = requireNotNull(mediaProjection.createVirtualDisplay(
                "DriveLink Cast", width, height, densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            ))
        } else {
            display.resize(width, height, densityDpi)
            display.surface = surface
        }

        drainJob = scope.launch(Dispatchers.Default) {
            if (selectedMime != requestedMime && runCatching { onCodecFallback(selectedMime) }.isFailure) {
                if (isActive) onProjectionStopped()
                return@launch
            }
            var codecConfigNalu: ByteArray? = null
            val bufferInfo = MediaCodec.BufferInfo()
            while (isActive) {
                val index = runCatching { activeCodec.dequeueOutputBuffer(bufferInfo, 10_000L) }.getOrElse {
                    if (isActive) onProjectionStopped()
                    return@launch
                }
                if (index >= 0) {
                    val outBuffer = runCatching { activeCodec.getOutputBuffer(index) }.getOrNull()
                    if (outBuffer != null && bufferInfo.size > 0) {
                        val nalu = ByteArray(bufferInfo.size)
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.get(nalu)
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isKey = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        if (isConfig) {
                            codecConfigNalu = nalu
                            runCatching { onEncodedNalu(nalu, bufferInfo.presentationTimeUs.toInt()) }
                        } else if (isKey && codecConfigNalu != null) {
                            val combined = codecConfigNalu + nalu
                            runCatching { onEncodedNalu(combined, bufferInfo.presentationTimeUs.toInt()) }
                        } else {
                            runCatching { onEncodedNalu(nalu, bufferInfo.presentationTimeUs.toInt()) }
                        }
                    }
                    runCatching { activeCodec.releaseOutputBuffer(index, false) }
                }
            }
        }
    }

    fun reconfigure(profile: DisplayProfile, orientation: DisplayOrientation, densityDpi: Int) {
        start(profile, orientation, densityDpi)
    }

    fun updateBitrateMbps(bitrateMbps: Int) {
        val params = Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrateMbps * 1_000_000)
        }
        runCatching { codec?.setParameters(params) }
    }

    fun requestKeyFrame() {
        val params = Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        }
        runCatching { codec?.setParameters(params) }
    }

    fun stop() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        releaseCodec()
    }

    private fun releaseCodec() {
        drainJob?.cancel()
        drainJob = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { inputSurface?.release() }
        inputSurface = null
    }

    fun releaseProjection() {
        stop()
        if (callbackRegistered) {
            runCatching { mediaProjection.unregisterCallback(projectionCallback) }
            callbackRegistered = false
        }
    }
}
