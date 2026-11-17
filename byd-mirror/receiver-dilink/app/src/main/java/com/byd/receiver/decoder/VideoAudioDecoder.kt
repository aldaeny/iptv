package com.byd.receiver.decoder

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Hardware MediaCodec video decoder rendering zero-copy directly onto BYD DiLink SurfaceView,
 * paired with AAC-LC MediaCodec + AudioTrack audio playback and A/V PTS synchronization.
 */
class VideoAudioDecoder {

    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private var currentMime: String = MediaFormat.MIMETYPE_VIDEO_AVC
    private var firstAudioPtsUs: Long = -1L
    private var audioPlaybackStartNs: Long = -1L

    fun configureVideo(surface: Surface, width: Int, height: Int, mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC) {
        releaseVideo()
        currentMime = mimeType
        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
        }
        val codec = MediaCodec.createDecoderByType(mimeType).apply {
            configure(format, surface, null, 0)
            start()
        }
        videoCodec = codec
    }

    fun configureAudio(sampleRateHz: Int = 48_000, channelCount: Int = 2) {
        releaseAudio()
        firstAudioPtsUs = -1L
        audioPlaybackStartNs = -1L

        // AudioSpecificConfig (CSD-0) for AAC-LC 48kHz Stereo: objectType=2 (5b), freqIdx=3 (4b), ch=2 (4b) -> 0x11, 0x90
        val aacCsd0 = ByteBuffer.wrap(byteArrayOf(0x11, 0x90.toByte()))
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateHz, channelCount).apply {
            setInteger(MediaFormat.KEY_IS_ADTS, 0)
            setByteBuffer("csd-0", aacCsd0)
        }
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, 0)
            start()
        }
        audioCodec = codec

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().apply { play() }
    }

    fun decodeVideoFrame(annexBNalu: ByteArray, timestampUs: Long) {
        val codec = videoCodec ?: return
        val inIndex = codec.dequeueInputBuffer(5_000L)
        if (inIndex >= 0) {
            val buffer = codec.getInputBuffer(inIndex) ?: return
            buffer.clear()
            buffer.put(annexBNalu)
            val flags = if (isCodecConfigOnly(annexBNalu, currentMime)) {
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG
            } else {
                0
            }
            codec.queueInputBuffer(inIndex, 0, annexBNalu.size, timestampUs, flags)
        }

        val info = MediaCodec.BufferInfo()
        var outIndex = codec.dequeueOutputBuffer(info, 0)
        while (outIndex >= 0) {
            val shouldRender = !isFrameTooLateForSync(info.presentationTimeUs)
            codec.releaseOutputBuffer(outIndex, shouldRender)
            outIndex = codec.dequeueOutputBuffer(info, 0)
        }
    }

    fun decodeAudioFrame(aacFrame: ByteArray, timestampUs: Long) {
        val codec = audioCodec ?: return
        if (firstAudioPtsUs < 0L) {
            firstAudioPtsUs = timestampUs
            audioPlaybackStartNs = System.nanoTime()
        }
        val inIndex = codec.dequeueInputBuffer(5_000L)
        if (inIndex >= 0) {
            val buffer = codec.getInputBuffer(inIndex) ?: return
            buffer.clear()
            buffer.put(aacFrame)
            codec.queueInputBuffer(inIndex, 0, aacFrame.size, timestampUs, 0)
        }
        val info = MediaCodec.BufferInfo()
        var outIndex = codec.dequeueOutputBuffer(info, 0)
        while (outIndex >= 0) {
            val pcmBuf = codec.getOutputBuffer(outIndex)
            if (pcmBuf != null && info.size > 0) {
                val pcm = ByteArray(info.size)
                pcmBuf.position(info.offset)
                pcmBuf.get(pcm)
                audioTrack?.write(pcm, 0, pcm.size)
            }
            codec.releaseOutputBuffer(outIndex, false)
            outIndex = codec.dequeueOutputBuffer(info, 0)
        }
    }

    private fun isFrameTooLateForSync(videoPtsUs: Long): Boolean {
        if (firstAudioPtsUs < 0L || audioPlaybackStartNs < 0L) return false
        val elapsedUs = (System.nanoTime() - audioPlaybackStartNs) / 1_000L
        val expectedAudioPtsUs = firstAudioPtsUs + elapsedUs
        return (expectedAudioPtsUs - videoPtsUs) > MAX_AV_LAG_US
    }

    private fun isCodecConfigOnly(data: ByteArray, mimeType: String): Boolean {
        if (data.size < 5) return false
        val naluByte = if (data[0].toInt() == 0 && data[1].toInt() == 0 && data[2].toInt() == 0 && data[3].toInt() == 1) {
            data[4].toInt() and 0xFF
        } else {
            return false
        }
        return if (mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC) {
            val type = (naluByte ushr 1) and 0x3F
            type in 32..34 // VPS=32, SPS=33, PPS=34
        } else {
            val type = naluByte and 0x1F
            type == 7 || type == 8 // SPS=7, PPS=8
        }
    }

    fun releaseVideo() {
        runCatching { videoCodec?.stop() }
        runCatching { videoCodec?.release() }
        videoCodec = null
    }

    fun releaseAudio() {
        firstAudioPtsUs = -1L
        audioPlaybackStartNs = -1L
        runCatching { audioCodec?.stop() }
        runCatching { audioCodec?.release() }
        audioCodec = null
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
    }

    fun releaseAll() {
        releaseVideo()
        releaseAudio()
    }

    companion object {
        private const val MAX_AV_LAG_US = 120_000L // Drop stale video frame if >120ms behind audio clock
    }
}
