package app.drivelink.receiver

import app.drivelink.receiver.protocol.AntiReplayWindow
import app.drivelink.receiver.protocol.UdpFrameReassembler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BydReceiverServerTest {

    private fun makeFrag(frameId: Int, idx: Int, total: Int, body: ByteArray): ByteArray {
        return ByteBuffer.allocate(8 + body.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(frameId)
            .putShort(idx.toShort())
            .putShort(total.toShort())
            .put(body)
            .array()
    }

    @Test
    fun `anti replay window accepts in-order and recent out-of-order packets but rejects duplicates`() {
        val window = AntiReplayWindow()
        assertTrue(window.accept(1))
        assertTrue(window.accept(3))
        assertTrue(window.accept(2)) // Out-of-order within window
        assertFalse(window.accept(2)) // Replay rejected
        assertFalse(window.accept(3)) // Replay rejected
        assertTrue(window.accept(100))
        assertFalse(window.accept(10)) // Outside 64-packet window
    }

    @Test
    fun `udp frame reassembler reconstructs out-of-order fragments`() {
        val reassembler = UdpFrameReassembler()
        val part0 = "HELLO-".toByteArray()
        val part1 = "BYD-".toByteArray()
        val part2 = "WORLD".toByteArray()

        assertNull(reassembler.pushFragment(makeFrag(7, 2, 3, part2)))
        assertNull(reassembler.pushFragment(makeFrag(7, 0, 3, part0)))
        val full = reassembler.pushFragment(makeFrag(7, 1, 3, part1))
        assertArrayEquals("HELLO-BYD-WORLD".toByteArray(), full)
    }

    // ---- AntiReplayWindow failure paths (Phase 3: invalid data, session reset) ----

    @Test
    fun `anti replay window rejects sequence zero`() {
        val window = AntiReplayWindow()
        assertFalse(window.accept(0))
        assertFalse(window.accept(0))
    }

    @Test
    fun `anti replay window rejects duplicate of the newest packet`() {
        val window = AntiReplayWindow()
        assertTrue(window.accept(5))
        assertFalse(window.accept(5))
        assertTrue(window.accept(6))
        assertFalse(window.accept(6))
    }

    @Test
    fun `anti replay window reset allows a new session to reuse old sequences`() {
        val window = AntiReplayWindow()
        assertTrue(window.accept(1))
        assertTrue(window.accept(2))
        assertFalse(window.accept(1)) // Same session: replay
        window.reset()
        assertTrue(window.accept(1)) // New session: handleClient resets per connection
        assertTrue(window.accept(2))
    }

    @Test
    fun `anti replay window slides out packets beyond the 64 packet window`() {
        val window = AntiReplayWindow()
        assertTrue(window.accept(1000))
        repeat(64) { assertTrue(window.accept(1001 + it)) }
        assertFalse(window.accept(1000)) // Now outside the window
        assertFalse(window.accept(1064)) // Newest packet itself is a duplicate (bitmap reset by the 64-shift)
        assertTrue(window.accept(1065)) // Fresh packet still accepted
    }

    // ---- UdpFrameReassembler failure paths (Phase 3: malformed, truncated, bounded memory) ----

    @Test
    fun `reassembler rejects payloads shorter than the fragment header`() {
        val reassembler = UdpFrameReassembler()
        assertNull(reassembler.pushFragment(ByteArray(0)))
        assertNull(reassembler.pushFragment(ByteArray(8))) // Header only, no chunk bytes
    }

    @Test
    fun `reassembler rejects malformed fragment metadata`() {
        val reassembler = UdpFrameReassembler()
        assertNull(reassembler.pushFragment(makeFrag(1, 0, 0, "x".toByteArray()))) // totalFrags = 0
        assertNull(reassembler.pushFragment(makeFrag(1, 2, 2, "x".toByteArray()))) // fragIdx >= total
        assertNull(reassembler.pushFragment(makeFrag(1, 0, 257, "x".toByteArray()))) // total > 256
    }

    @Test
    fun `reassembler rejects fragments whose total disagrees with the pending frame`() {
        val reassembler = UdpFrameReassembler()
        assertNull(reassembler.pushFragment(makeFrag(9, 0, 3, "A".toByteArray())))
        assertNull(reassembler.pushFragment(makeFrag(9, 1, 2, "B".toByteArray()))) // Conflicting total
        // The conflicting fragment must not have advanced the frame:
        assertNull(reassembler.pushFragment(makeFrag(9, 1, 3, "B".toByteArray())))
        val full = reassembler.pushFragment(makeFrag(9, 2, 3, "C".toByteArray()))
        assertArrayEquals("ABC".toByteArray(), full)
    }

    @Test
    fun `reassembler bounds pending memory by evicting the oldest frame`() {
        val reassembler = UdpFrameReassembler(maxPendingFrames = 2)
        assertNull(reassembler.pushFragment(makeFrag(1, 0, 2, "A".toByteArray())))
        assertNull(reassembler.pushFragment(makeFrag(2, 0, 2, "B".toByteArray())))
        // Frame 1 is evicted when frame 3 arrives; frame 3 must still be pending.
        assertNull(reassembler.pushFragment(makeFrag(3, 0, 2, "C".toByteArray())))
        // Frame 2 is now the oldest and completes normally.
        val two = reassembler.pushFragment(makeFrag(2, 1, 2, "D".toByteArray()))
        assertArrayEquals("BD".toByteArray(), two)
        // Reassembling the evicted frame 1 requires resending every fragment (fresh entry).
        assertNull(reassembler.pushFragment(makeFrag(1, 0, 2, "A".toByteArray())))
        val one = reassembler.pushFragment(makeFrag(1, 1, 2, "E".toByteArray()))
        assertArrayEquals("AE".toByteArray(), one)
        // A late duplicate after completion starts a fresh, incomplete entry — no corruption:
        assertNull(reassembler.pushFragment(makeFrag(1, 1, 2, "E".toByteArray())))
        // ...and it is discarded wholesale by clear() like any pending frame.
        reassembler.clear()
        assertNull(reassembler.pushFragment(makeFrag(1, 0, 2, "A".toByteArray())))
    }

    @Test
    fun `reassembler clear drops pending frames exactly like a session reset`() {
        val reassembler = UdpFrameReassembler()
        assertNull(reassembler.pushFragment(makeFrag(4, 0, 2, "A".toByteArray())))
        reassembler.clear() // handleClient calls this on every new connection
        // After clear the stale fragment must not assemble into the new session.
        assertNull(reassembler.pushFragment(makeFrag(4, 1, 2, "B".toByteArray())))
        val fresh = reassembler.pushFragment(makeFrag(5, 0, 1, "NEW".toByteArray()))
        assertArrayEquals("NEW".toByteArray(), fresh)
    }
}
