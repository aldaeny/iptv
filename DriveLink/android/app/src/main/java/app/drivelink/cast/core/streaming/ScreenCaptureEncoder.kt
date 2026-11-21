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
    private val onCodecFallback: (String) -> Unit = {},
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

    @Synchronized
    fun start(profile: DisplayProfile, orientation: DisplayOrientation, densityDpi: Int) {
        try {
            configureCapture(profile, orientation, densityDpi)
        } catch (error: Exception) {
            releaseProjection()
            throw error
        }
    }

    private fun configureCapture(profile: DisplayProfile, orientation: DisplayOrientation, densityDpi: Int) {
        // API 34 allows only one createVirtualDisplay call per projection token.
        // Detach the old encoder before releasing it, but retain the display.
        virtualDisplay?.setSurface(null)
        releaseEncoder()
        if (!callbackRegistered) {
            mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            callbackRegistered = true
        }

        val (width, height) = profile.dimensionsFor(orientation)
        val requestedMime = profile.codec.mimeType
        var selectedMime = requestedMime
        var activeCodec: MediaCodec? = null
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
            val created = runCatching {
                MediaCodec.createEncoderByType(candidateMime).apply {
                    // A candidate that refuses the format must be released here: otherwise a failed
                    // fallback attempt keeps a native encoder allocated for the rest of the session.
                    try {
                        configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    } catch (error: Throwable) {
                        runCatching { release() }
                        throw error
                    }
                }
            }.getOrNull()
            if (created != null) {
                activeCodec = created
                selectedMime = candidateMime
                break
            }
        }
        val encoder = requireNotNull(activeCodec) {
            onProjectionStopped()
            "No hardware video encoder available"
        }
        val surface = try {
            encoder.createInputSurface()
        } catch (error: Throwable) {
            runCatching { encoder.release() }
            throw error
        }
        try {
            encoder.start()
        } catch (error: Throwable) {
            runCatching { surface.release() }
            runCatching { encoder.release() }
            throw error
        }
        codec = encoder
        inputSurface = surface
        // Announced only once the encoder is actually running, so a codec that fails to start
        // can never move the session profile away from the codec that is really in use.
        if (selectedMime != requestedMime) {
            onCodecFallback(selectedMime)
        }

        val display = virtualDisplay
        if (display == null) {
            virtualDisplay = checkNotNull(mediaProjection.createVirtualDisplay(
                "BYD-Wireless-Display",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )) { "Screen capture display unavailable" }
        } else {
            display.resize(width, height, densityDpi)
            display.setSurface(surface)
        }

        drainJob = scope.launch(Dispatchers.Default) {
            var codecConfigNalu: ByteArray? = null
            val bufferInfo = MediaCodec.BufferInfo()
            while (isActive) {
                val nalu = runCatching {
                    // Copy the native buffer under the same lock used by stop/reconfigure;
                    // never retain a codec buffer across a suspending network send.
                    synchronized(this@ScreenCaptureEncoder) {
                        if (!isActive || codec !== encoder) return@launch
                        val index = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                        if (index < 0) null else try {
                            encoder.getOutputBuffer(index)?.takeIf { bufferInfo.size > 0 }?.let { buffer ->
                                ByteArray(bufferInfo.size).also {
                                    buffer.position(bufferInfo.offset)
                                    buffer.get(it)
                                }
                            }
                        } finally {
                            encoder.releaseOutputBuffer(index, false)
                        }
                    }
                }.getOrElse {
                    if (isActive) onProjectionStopped()
                    return@launch
                } ?: continue
                if (!isActive) return@launch
                val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                val isKey = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                val payload = when {
                    isConfig -> nalu.also { codecConfigNalu = it }
                    isKey -> codecConfigNalu?.plus(nalu) ?: nalu
                    else -> nalu
                }
                runCatching { onEncodedNalu(payload, bufferInfo.presentationTimeUs.toInt()) }
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

    @Synchronized
    fun stop() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        releaseEncoder()
    }

    private fun releaseEncoder() {
        drainJob?.cancel()
        drainJob = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { inputSurface?.release() }
        inputSurface = null
    }

    @Synchronized
    fun releaseProjection() {
        stop()
        if (callbackRegistered) {
            runCatching { mediaProjection.unregisterCallback(projectionCallback) }
            callbackRegistered = false
        }
    }
}
