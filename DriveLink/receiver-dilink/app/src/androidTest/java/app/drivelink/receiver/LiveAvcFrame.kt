package app.drivelink.receiver

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat

/** Encodes a synthetic image with the emulator's real Android AVC encoder. */
internal fun encodeLiveAvcFrame(width: Int, height: Int): ByteArray {
    val mime = MediaFormat.MIMETYPE_VIDEO_AVC
    val codec = MediaCodec.createEncoderByType(mime)
    try {
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 1_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var config = ByteArray(0)
        var inputCount = 0
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            if (inputCount < 8) {
                val input = codec.dequeueInputBuffer(10_000)
                if (input >= 0) {
                    val image = checkNotNull(codec.getInputImage(input))
                    image.planes.forEachIndexed { index, plane ->
                        val stride = if (index == 0) 1 else 2
                        val value = if (index == 0) 96.toByte() else 128.toByte()
                        for (row in 0 until height / stride) {
                            for (col in 0 until width / stride) {
                                plane.buffer.put(row * plane.rowStride + col * plane.pixelStride, value)
                            }
                        }
                    }
                    codec.queueInputBuffer(input, 0, width * height * 3 / 2, inputCount * 33_333L, 0)
                    inputCount++
                }
            }
            val output = codec.dequeueOutputBuffer(info, 10_000)
            if (output < 0) continue
            try {
                val bytes = ByteArray(info.size)
                if (bytes.isEmpty()) continue
                checkNotNull(codec.getOutputBuffer(output)).apply {
                    position(info.offset)
                    get(bytes)
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    config = bytes
                } else if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
                    return config + bytes
                }
            } finally {
                codec.releaseOutputBuffer(output, false)
            }
        }
        error("Android AVC encoder did not produce a key frame")
    } finally {
        codec.release()
    }
}
