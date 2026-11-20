package app.drivelink.cast

import app.drivelink.cast.core.protocol.BydProtocolWrapper
import app.drivelink.cast.core.protocol.ProtocolOpcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BydProtocolWrapperTest {

    @Test
    fun `builds valid 16-byte big-endian BYD-WMP v1 header`() {
        val wrapper = BydProtocolWrapper()
        val header = wrapper.buildHeader(
            opcode = ProtocolOpcode.VIDEO_NALU_FRAG,
            sequence = 42,
            timestampUs = 123_456,
            payloadLength = 1_158
        )

        assertEquals(BydProtocolWrapper.HEADER_SIZE_BYTES, header.size)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        assertEquals(BydProtocolWrapper.MAGIC_BYTES, buf.short)
        assertEquals(BydProtocolWrapper.PROTOCOL_VERSION, buf.get())
        assertEquals(ProtocolOpcode.VIDEO_NALU_FRAG.code, buf.get())
        assertEquals(42, buf.int)
        assertEquals(123_456, buf.int)
        assertEquals(1_158, buf.int)
    }

    @Test
    fun `maps touch event opcode 0x14`() {
        assertEquals(ProtocolOpcode.TOUCH_EVENT, ProtocolOpcode.fromByte(0x14))
        assertEquals(ProtocolOpcode.VIDEO_NALU_FRAG, ProtocolOpcode.fromByte(0x13))
    }

    @Test
    fun `unknown opcode bytes resolve to null so receivers can reject them`() {
        assertNull(ProtocolOpcode.fromByte(0x00))
        assertNull(ProtocolOpcode.fromByte(0x7F))
        assertNull(ProtocolOpcode.fromByte(0x15.toByte())) // Gap between TOUCH_EVENT and HEARTBEAT_PING
    }

    @Test
    fun `every opcode has a unique code so wire bytes stay unambiguous`() {
        val codes = ProtocolOpcode.entries.map { it.code }
        assertEquals(ProtocolOpcode.entries.size, codes.toSet().size)
        ProtocolOpcode.entries.forEach { opcode ->
            assertEquals(opcode, ProtocolOpcode.fromByte(opcode.code))
        }
    }

    @Test
    fun `wire format constants match the receiver's hardcoded framing`() {
        // The receiver pins identical literals in BydReceiverServer.kt's companion
        // (MAGIC_BYTES 0x4259, PROTOCOL_VERSION 0x01, HEADER_SIZE_BYTES 16, MAX_PACKET_BYTES 65_536).
        // These literal assertions fail CI if either side changes the wire format alone.
        assertEquals(0x4259.toShort(), BydProtocolWrapper.MAGIC_BYTES)
        assertEquals(0x01.toByte(), BydProtocolWrapper.PROTOCOL_VERSION)
        assertEquals(16, BydProtocolWrapper.HEADER_SIZE_BYTES)
        assertEquals(65_536, BydProtocolWrapper.MAX_PAYLOAD_BYTES)
    }

    @Test
    fun `buildHeader survives boundary sequence and payload values without overflow`() {
        val wrapper = BydProtocolWrapper()
        val header = wrapper.buildHeader(
            opcode = ProtocolOpcode.VIDEO_NALU,
            sequence = Int.MAX_VALUE,
            timestampUs = Int.MAX_VALUE,
            payloadLength = BydProtocolWrapper.MAX_PAYLOAD_BYTES
        )
        assertEquals(16, header.size)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        assertEquals(0x4259.toShort(), buf.short)
        buf.get() // version
        buf.get() // opcode
        assertEquals(Int.MAX_VALUE, buf.int)
        assertEquals(Int.MAX_VALUE, buf.int)
        assertEquals(65_536, buf.int)
    }

    @Test
    fun `udp chunking keeps worst-case encrypted packets under the 1500 byte MTU`() {
        // Worst-case UDP packet: 8B fragment header + 1150B chunk + 16B packet header + 28B GCM overhead.
        val worstCase = BydProtocolWrapper.UDP_CHUNK_LIMIT_BYTES +
            UdpFragHeaderBytes +
            BydProtocolWrapper.HEADER_SIZE_BYTES +
            BydProtocolWrapper.ENCRYPTION_OVERHEAD_BYTES
        assertTrue("Worst-case UDP packet must stay under MTU", worstCase <= 1500)
        assertEquals(1150, BydProtocolWrapper.UDP_CHUNK_LIMIT_BYTES)
        assertEquals(28, BydProtocolWrapper.ENCRYPTION_OVERHEAD_BYTES)
    }

    private companion object {
        const val UdpFragHeaderBytes = 8
    }
}
