package app.drivelink.receiver.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 64-packet sliding window anti-replay filter for encrypted UDP media packets.
 */
class AntiReplayWindow {
    private var highestSeq: Long = 0L
    private var bitmap: Long = 0L

    @Synchronized
    fun accept(sequence: Int): Boolean {
        val seq = sequence.toLong() and 0xFFFF_FFFFL
        if (seq == 0L) return false

        if (highestSeq == 0L) {
            highestSeq = seq
            bitmap = 1L
            return true
        }

        if (seq > highestSeq) {
            val shift = seq - highestSeq
            bitmap = if (shift >= 64) 1L else ((bitmap shl shift.toInt()) or 1L)
            highestSeq = seq
            return true
        }

        val delta = highestSeq - seq
        if (delta >= 64) return false // Too old / outside replay window

        val mask = 1L shl delta.toInt()
        if ((bitmap and mask) != 0L) return false // Duplicate / replayed packet
        bitmap = bitmap or mask
        return true
    }

    @Synchronized
    fun reset() {
        highestSeq = 0L
        bitmap = 0L
    }
}

/**
 * Reassembles MTU-fragmented VIDEO_NALU_FRAG packets:
 * Payload format: [4B frameId][2B fragIdx][2B totalFrags][chunkBytes]
 */
class UdpFrameReassembler(private val maxPendingFrames: Int = 12) {

    private data class PendingFrame(
        val totalFrags: Int,
        val chunks: Array<ByteArray?>,
        var receivedCount: Int = 0
    )

    private val pending = LinkedHashMap<Int, PendingFrame>()

    @Synchronized
    fun pushFragment(payload: ByteArray): ByteArray? {
        if (payload.size <= FRAG_HEADER_BYTES) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val frameId = buf.int
        val fragIdx = buf.short.toInt() and 0xFFFF
        val totalFrags = buf.short.toInt() and 0xFFFF
        if (totalFrags <= 0 || fragIdx >= totalFrags || totalFrags > 256) return null

        val chunk = payload.copyOfRange(FRAG_HEADER_BYTES, payload.size)
        val entry = pending.getOrPut(frameId) {
            while (pending.size >= maxPendingFrames) {
                val oldestKey = pending.keys.firstOrNull() ?: break
                pending.remove(oldestKey)
            }
            PendingFrame(totalFrags, arrayOfNulls(totalFrags))
        }

        if (entry.totalFrags != totalFrags) return null
        if (entry.chunks[fragIdx] == null) {
            entry.chunks[fragIdx] = chunk
            entry.receivedCount++
        }

        if (entry.receivedCount == totalFrags) {
            pending.remove(frameId)
            val totalBytes = entry.chunks.sumOf { it?.size ?: 0 }
            val assembled = ByteBuffer.allocate(totalBytes)
            entry.chunks.forEach { piece -> if (piece != null) assembled.put(piece) }
            return assembled.array()
        }
        return null
    }

    @Synchronized
    fun clear() {
        pending.clear()
    }

    companion object {
        const val FRAG_HEADER_BYTES = 8
    }
}
