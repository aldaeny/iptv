package app.drivelink.cast

import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.ConnectionState
import app.drivelink.cast.core.model.VideoCodec
import app.drivelink.cast.core.session.MirrorSessionCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * A fallback that succeeds must move the active profile to the codec the hardware actually
 * opened, keep the live session in step with it, and never announce the same transition twice.
 */
class CodecFallbackProfileTest {

    private val device = BydDevice(
        id = "byd-fallback",
        displayName = "BYD Seal",
        modelName = "BYD DiLink 15.6\"",
        diLinkVersion = "5.0",
        hostAddress = "192.168.49.1"
    )

    @Before
    fun setUpMainDispatcher() {
        // MirrorSessionCoordinator builds its scope on Dispatchers.Main.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `records the fallback codec in the active profile and the live session`() {
        MirrorSessionCoordinator.applyActiveCodec(VideoCodec.H265)
        MirrorSessionCoordinator.markMiracastStreaming(device)

        val updated = MirrorSessionCoordinator.applyActiveCodec(VideoCodec.H264)

        assertNotNull(updated)
        assertEquals(VideoCodec.H264, MirrorSessionCoordinator.activeProfile.value.codec)
        val state = MirrorSessionCoordinator.connectionState.value as ConnectionState.Streaming
        assertEquals(VideoCodec.H264, state.profile.codec)
    }

    @Test
    fun `does not announce the same transition twice`() {
        MirrorSessionCoordinator.applyActiveCodec(VideoCodec.H265)
        MirrorSessionCoordinator.markMiracastStreaming(device)

        assertNotNull(MirrorSessionCoordinator.applyActiveCodec(VideoCodec.H264))
        assertNull(MirrorSessionCoordinator.applyActiveCodec(VideoCodec.H264))
    }

    @Test
    fun `does not restart the encoder from inside the fallback callback`() {
        MirrorSessionCoordinator.applyActiveCodec(VideoCodec.H265)
        MirrorSessionCoordinator.markMiracastStreaming(device)
        var reconfigureCalls = 0
        MirrorSessionCoordinator.onReconfigureEncoder = { _, _ -> reconfigureCalls++ }

        try {
            MirrorSessionCoordinator.applyActiveCodec(VideoCodec.H264)
            assertEquals(0, reconfigureCalls)
        } finally {
            MirrorSessionCoordinator.onReconfigureEncoder = null
        }
    }
}
