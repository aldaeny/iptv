package com.byd.mirror.core.streaming

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.view.Surface
import com.byd.mirror.core.model.DisplayOrientation
import com.byd.mirror.core.model.DisplayProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Hardware-accelerated low-latency screen encoder using MediaProjection -> Surface -> MediaCodec.
 */
class ScreenCaptureEncoder(
    private val mediaProjection: MediaProjection,
    private val scope: CoroutineScope,
    private val onEncodedNalu: suspend (ByteArray, Int) -> Unit
) {
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var drainJob: Job? = null

    fun start(profile: DisplayProfile, orientation: DisplayOrientation, densityDpi: Int) {
        stop()
        val (width, height) = profile.dimensionsFor(orientation)
        val format = MediaFormat.createVideoFormat(profile.codec.mimeType, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, profile.bitrateMbps * 1_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, profile.targetFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_PRIORITY, 0) // Real-time encoding priority
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
        }

        val mediaCodec = MediaCodec.createEncoderByType(profile.codec.mimeType).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        val surface = mediaCodec.createInputSurface()
        mediaCodec.start()

        val display = mediaProjection.createVirtualDisplay(
            "BYD-Wireless-Display",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )

        codec = mediaCodec
        inputSurface = surface
        virtualDisplay = display

        drainJob = scope.launch(Dispatchers.Default) {
            val bufferInfo = MediaCodec.BufferInfo()
            while (isActive) {
                val index = mediaCodec.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (index >= 0) {
                    val outBuffer = mediaCodec.getOutputBuffer(index)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        val nalu = ByteArray(bufferInfo.size)
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.get(nalu)
                        onEncodedNalu(nalu, bufferInfo.presentationTimeUs.toInt())
                    }
                    mediaCodec.releaseOutputBuffer(index, false)
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
        drainJob?.cancel()
        drainJob = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { inputSurface?.release() }
        inputSurface = null
    }
}
