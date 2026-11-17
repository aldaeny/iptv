package com.byd.mirror.core.streaming

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures internal device audio via AudioPlaybackCaptureConfiguration and encodes
 * low-latency AAC-LC 48kHz stereo frames for synchronized BYD infotainment playback.
 */
class AudioCaptureEncoder(
    private val mediaProjection: MediaProjection,
    private val scope: CoroutineScope,
    private val onEncodedAac: suspend (ByteArray, Int) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var captureJob: Job? = null

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        val sampleRate = 48_000
        val channelCount = 2
        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val record = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(maxOf(minBufSize, 8192))
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        } else {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(maxOf(minBufSize, 8192))
                .build()
        }

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }

        val aacCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        record.startRecording()
        audioRecord = record
        codec = aacCodec

        captureJob = scope.launch(Dispatchers.Default) {
            val pcmBuf = ByteArray(4096)
            val bufferInfo = MediaCodec.BufferInfo()
            while (isActive) {
                val read = record.read(pcmBuf, 0, pcmBuf.size)
                if (read > 0) {
                    val inIdx = aacCodec.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        val inBuffer = aacCodec.getInputBuffer(inIdx)
                        inBuffer?.clear()
                        inBuffer?.put(pcmBuf, 0, read)
                        val ptsUs = System.nanoTime() / 1_000L
                        aacCodec.queueInputBuffer(inIdx, 0, read, ptsUs, 0)
                    }
                }

                var outIdx = aacCodec.dequeueOutputBuffer(bufferInfo, 0)
                while (outIdx >= 0) {
                    val outBuffer = aacCodec.getOutputBuffer(outIdx)
                    if (outBuffer != null && bufferInfo.size > 0 &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        val aac = ByteArray(bufferInfo.size)
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.get(aac)
                        onEncodedAac(aac, bufferInfo.presentationTimeUs.toInt())
                    }
                    aacCodec.releaseOutputBuffer(outIdx, false)
                    outIdx = aacCodec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }
}
