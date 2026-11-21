package app.drivelink.receiver.decoder

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Hardware MediaCodec video decoder rendering directly onto BYD DiLink SurfaceView,
 * paired with AAC-LC MediaCodec + AudioTrack playback and safe same-clock latency guard.
 */
class VideoAudioDecoder {

    private var videoCodec: MediaCodec? = null
    private data class VideoTarget(val surface: Surface, val width: Int, val height: Int, val mime: String)

    private var videoTarget: VideoTarget? = null
    private var audioCodec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private var currentMime: String = MediaFormat.MIMETYPE_VIDEO_AVC
    private var firstVideoPtsUs: Long = -1L
    private var videoPlaybackStartNs: Long = -1L
    @Volatile
    var isMuted: Boolean = false

    fun supportsHevcDecoder(): Boolean {
        return runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos.any { !it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) } }
        }.getOrDefault(false)
    }

    @Synchronized
    fun configureVideo(surface: Surface, width: Int, height: Int, mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC) {
        if (!surface.isValid) {
            releaseVideo()
            return
        }
        val target = VideoTarget(surface, width, height, mimeType)
        if (videoCodec != null && videoTarget == target) return
        releaseVideo()

        // The decoder must match the incoming bitstream. A local MIME substitution
        // cannot turn an HEVC stream into AVC.
        runCatching {
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
            }
            val codec = MediaCodec.createDecoderByType(mimeType)
            videoCodec = codec
            codec.configure(format, surface, null, 0)
            codec.start()
            currentMime = mimeType
            videoTarget = target
        }.onFailure {
            Log.w("DriveLink", "Video decoder setup failed: $mimeType ${width}x$height", it)
            releaseVideo()
        }
    }

    @Synchronized
    fun configureAudio(sampleRateHz: Int = 48_000, channelCount: Int = 2) {
        releaseAudio()

        // AudioSpecificConfig (CSD-0) for AAC-LC 48kHz Stereo: 0x11, 0x90
        val aacCsd0 = ByteBuffer.wrap(byteArrayOf(0x11, 0x90.toByte()))
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateHz, channelCount).apply {
            setInteger(MediaFormat.KEY_IS_ADTS, 0)
            setByteBuffer("csd-0", aacCsd0)
        }
        runCatching {
            audioCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioCodec!!.apply {
                configure(format, null, null, 0)
                start()
            }

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
                .build()
            audioTrack!!.play()
        }.onFailure { releaseAudio() }
    }

    @Synchronized
    fun decodeVideoFrame(annexBNalu: ByteArray, timestampUs: Long) {
        val codec = videoCodec ?: return
        val unsignedPtsUs = timestampUs and 0xFFFF_FFFFL
        runCatching {
            val inIndex = codec.dequeueInputBuffer(5_000L)
            if (inIndex >= 0) {
                val buffer = codec.getInputBuffer(inIndex) ?: return
                buffer.clear()
                if (annexBNalu.size > buffer.remaining()) {
                    codec.queueInputBuffer(inIndex, 0, 0, unsignedPtsUs, 0)
                    return
                }
                buffer.put(annexBNalu)
                val flags = if (isCodecConfigOnly(annexBNalu, currentMime)) {
                    MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                } else {
                    0
                }
                codec.queueInputBuffer(inIndex, 0, annexBNalu.size, unsignedPtsUs, flags)
            }

            val info = MediaCodec.BufferInfo()
            var outIndex = codec.dequeueOutputBuffer(info, 0)
            while (outIndex >= 0) {
                val shouldRender = !isVideoFrameStale(info.presentationTimeUs)
                codec.releaseOutputBuffer(outIndex, shouldRender)
                outIndex = codec.dequeueOutputBuffer(info, 0)
            }
        }
    }

    @Synchronized
    fun decodeAudioFrame(aacFrame: ByteArray, timestampUs: Long) {
        val codec = audioCodec ?: return
        val unsignedPtsUs = timestampUs and 0xFFFF_FFFFL
        runCatching {
            val inIndex = codec.dequeueInputBuffer(5_000L)
            if (inIndex >= 0) {
                val buffer = codec.getInputBuffer(inIndex) ?: return
                buffer.clear()
                if (aacFrame.size > buffer.remaining()) {
                    codec.queueInputBuffer(inIndex, 0, 0, unsignedPtsUs, 0)
                    return
                }
                buffer.put(aacFrame)
                codec.queueInputBuffer(inIndex, 0, aacFrame.size, unsignedPtsUs, 0)
            }
            val info = MediaCodec.BufferInfo()
            var outIndex = codec.dequeueOutputBuffer(info, 0)
            while (outIndex >= 0) {
                val pcmBuf = codec.getOutputBuffer(outIndex)
                if (pcmBuf != null && info.size > 0 && !isMuted) {
                    val pcm = ByteArray(info.size)
                    pcmBuf.position(info.offset)
                    pcmBuf.get(pcm)
                    audioTrack?.write(pcm, 0, pcm.size)
                }
                codec.releaseOutputBuffer(outIndex, false)
                outIndex = codec.dequeueOutputBuffer(info, 0)
            }
        }
    }

    private fun isVideoFrameStale(videoPtsUs: Long): Boolean {
        if (firstVideoPtsUs < 0L || kotlin.math.abs(videoPtsUs - firstVideoPtsUs) > 10_000_000L) {
            firstVideoPtsUs = videoPtsUs
            videoPlaybackStartNs = System.nanoTime()
            return false
        }
        val elapsedUs = (System.nanoTime() - videoPlaybackStartNs) / 1_000L
        val expectedVideoPtsUs = firstVideoPtsUs + elapsedUs
        return (expectedVideoPtsUs - videoPtsUs) > MAX_VIDEO_LAG_US
    }

    @Synchronized
    fun releaseVideo() {
        videoTarget = null
        firstVideoPtsUs = -1L
        videoPlaybackStartNs = -1L
        runCatching { videoCodec?.stop() }
        runCatching { videoCodec?.release() }
        videoCodec = null
    }

    @Synchronized
    fun releaseAudio() {
        runCatching { audioCodec?.stop() }
        runCatching { audioCodec?.release() }
        audioCodec = null
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
    }

    @Synchronized
    fun releaseAll() {
        releaseVideo()
        releaseAudio()
    }

    companion object {
        private const val MAX_VIDEO_LAG_US = 250_000L
    }
}
