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

        fun makeFrag(frameId: Int, idx: Int, total: Int, body: ByteArray): ByteArray {
            return ByteBuffer.allocate(8 + body.size)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(frameId)
                .putShort(idx.toShort())
                .putShort(total.toShort())
                .put(body)
                .array()
        }

        assertNull(reassembler.pushFragment(makeFrag(7, 2, 3, part2)))
        assertNull(reassembler.pushFragment(makeFrag(7, 0, 3, part0)))
        val full = reassembler.pushFragment(makeFrag(7, 1, 3, part1))
        assertArrayEquals("HELLO-BYD-WORLD".toByteArray(), full)
    }
}
