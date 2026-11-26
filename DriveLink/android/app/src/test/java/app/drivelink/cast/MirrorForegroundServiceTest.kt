package app.drivelink.cast

import android.app.Service
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.drivelink.cast.core.session.MirrorSessionCoordinator
import app.drivelink.cast.service.MirrorForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Executable JVM tests for MirrorForegroundService lifecycle and cleanup,
 * using Robolectric's real Android framework implementation.
 *
 * Covers the cleanup contract: onDestroy must always reset the session
 * coordinator callbacks, even when the service never started capture.
 * ACTION_START capture paths (real MediaProjection / MediaCodec) stay
 * device-tested by design.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MirrorForegroundServiceTest {

    @Before
    fun setUp() {
        resetCoordinatorCallbacks()
    }

    private fun resetCoordinatorCallbacks() {
        MirrorSessionCoordinator.onKeyframeRequested = null
        MirrorSessionCoordinator.onReconfigureEncoder = null
        MirrorSessionCoordinator.onStopServices = null
    }

    private fun startIntent(action: String): Intent =
        Intent(ApplicationProvider.getApplicationContext(), MirrorForegroundService::class.java)
            .apply { this.action = action }

    @Test
    fun `onStartCommand with ACTION_STOP returns START_NOT_STICKY and destroy cleans callbacks`() {
        val controller = Robolectric.buildService(MirrorForegroundService::class.java)
        controller.create()
        // Register callbacks first to prove onDestroy resets them.
        MirrorSessionCoordinator.onKeyframeRequested = {}
        MirrorSessionCoordinator.onReconfigureEncoder = { _, _ -> }
        MirrorSessionCoordinator.onStopServices = {}

        val result = controller.get().onStartCommand(
            startIntent(MirrorForegroundService.ACTION_STOP), 0, 1
        )
        assertEquals(Service.START_NOT_STICKY, result)

        controller.destroy()
        assertNull(MirrorSessionCoordinator.onKeyframeRequested)
        assertNull(MirrorSessionCoordinator.onReconfigureEncoder)
        assertNull(MirrorSessionCoordinator.onStopServices)
    }

    @Test
    fun `onStartCommand with an unknown action is a harmless no-op`() {
        val controller = Robolectric.buildService(MirrorForegroundService::class.java)
        controller.create()

        val result = controller.get().onStartCommand(
            startIntent("app.drivelink.cast.action.UNKNOWN"), 0, 2
        )
        assertEquals(Service.START_NOT_STICKY, result)

        controller.destroy()
        assertNull(MirrorSessionCoordinator.onKeyframeRequested)
        assertNull(MirrorSessionCoordinator.onReconfigureEncoder)
        assertNull(MirrorSessionCoordinator.onStopServices)
    }

    @Test
    fun `onStartCommand with ACTION_START and no projection result stops self without crashing`() {
        val controller = Robolectric.buildService(MirrorForegroundService::class.java)
        controller.create()
        // No EXTRA_RESULT_CODE / EXTRA_RESULT_DATA: the service must tear itself
        // down instead of starting capture, and must not throw.
        val result = controller.get().onStartCommand(
            startIntent(MirrorForegroundService.ACTION_START), 0, 3
        )
        assertEquals(Service.START_NOT_STICKY, result)

        controller.destroy()
        assertNull(MirrorSessionCoordinator.onKeyframeRequested)
        assertNull(MirrorSessionCoordinator.onReconfigureEncoder)
        assertNull(MirrorSessionCoordinator.onStopServices)
    }

    @Test
    fun `destroy without ever starting still cleans coordinator callbacks`() {
        val controller = Robolectric.buildService(MirrorForegroundService::class.java)
        controller.create()
        controller.destroy()
        assertNull(MirrorSessionCoordinator.onKeyframeRequested)
        assertNull(MirrorSessionCoordinator.onReconfigureEncoder)
        assertNull(MirrorSessionCoordinator.onStopServices)
    }
}
