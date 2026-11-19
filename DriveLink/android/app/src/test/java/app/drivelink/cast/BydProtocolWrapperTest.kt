package app.drivelink.cast

import app.drivelink.cast.core.protocol.BydProtocolWrapper
import app.drivelink.cast.core.protocol.ProtocolOpcode
import org.junit.Assert.assertEquals
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
}
