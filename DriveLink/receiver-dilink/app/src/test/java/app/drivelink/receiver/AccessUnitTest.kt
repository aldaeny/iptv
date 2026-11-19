package app.drivelink.receiver

import app.drivelink.receiver.decoder.isCodecConfigOnly
import app.drivelink.receiver.protocol.AntiReplayWindow
import org.junit.Assert.*
import org.junit.Test

class AccessUnitTest {
    @Test fun pictureAfterParameterSetsIsNotConfigOnly() {
        for ((mime, headers) in listOf("video/avc" to (7 to 0x65), "video/hevc" to (64 to 38))) {
            val config = byteArrayOf(0, 0, 1, headers.first.toByte(), 1)
            val picture = byteArrayOf(0, 0, 0, 1, headers.second.toByte(), 1)
            assertTrue(isCodecConfigOnly(config, mime))
            assertFalse(isCodecConfigOnly(config + picture, mime))
            assertFalse(isCodecConfigOnly(byteArrayOf(0, 0, 1), mime))
        }
    }

    @Test fun replayWindowUsesUnsignedDistanceWithoutNarrowing() {
        val window = AntiReplayWindow()
        assertTrue(window.accept(1)); assertTrue(window.accept(-2))
        assertFalse(window.accept(2)); assertFalse(window.accept(-2))
        window.reset(); assertTrue(window.accept(2))
    }
}
