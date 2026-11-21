package app.drivelink.cast

import android.Manifest
import android.graphics.Color
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.ConnectionState
import app.drivelink.cast.core.model.DisplayOrientation
import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.session.MirrorSessionCoordinator
import app.drivelink.receiver.protocol.BydReceiverServer
import app.drivelink.receiver.protocol.ReceiverState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Real Activity consent -> production foreground service -> MediaProjection -> H.264 ->
 * production encrypted UDP transport -> production receiver decoder -> sampled screen pixels.
 * No fixture video, synthetic encoder input, shell-granted projection token, or mocked service.
 */
class ScreenProjectionDeviceTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.NEARBY_WIFI_DEVICES
    )

    @Test
    fun screenPixelsReachTheReceiverAfterSystemConsent() = runBlocking<Unit> {
        Fixture().use { f ->
            f.startSession(Color.RED)
            f.expectPixels(320, 240, RED)
            f.paint(Color.GREEN)
            f.expectPixels(320, 240, GREEN)
        }
    }

    @Test
    fun rotationReconfiguresTheLiveProjectionWithoutLosingScreenFrames() = runBlocking<Unit> {
        Fixture().use { f ->
            f.startSession(Color.RED)
            f.expectPixels(320, 240, RED)
            // Exercise both Android Activity recreation and the product's stream orientation control.
            f.device.setOrientationLeft()
            f.device.waitForIdle()
            f.paint(Color.BLUE)
            withContext(Dispatchers.Main) { MirrorSessionCoordinator.toggleOrientation() }
            withTimeout(10_000) {
                f.receiver.state.first { it is ReceiverState.MirroringActive && !it.isLandscape }
            }
            f.replaceOutput(240, 320)
            f.expectPixels(240, 320, BLUE)
            f.device.setOrientationNatural()
            f.device.waitForIdle()
            f.paint(Color.GREEN)
            withContext(Dispatchers.Main) { MirrorSessionCoordinator.toggleOrientation() }
            withTimeout(10_000) {
                f.receiver.state.first { it is ReceiverState.MirroringActive && it.isLandscape }
            }
            f.replaceOutput(320, 240)
            f.expectPixels(320, 240, GREEN)
        }
    }

    @Test
    fun stoppingThenStartingRequestsNewConsentAndRendersANewScreen() = runBlocking<Unit> {
        Fixture().use { f ->
            f.startSession(Color.RED)
            f.expectPixels(320, 240, RED)
            f.stopSession()
            f.replaceOutput(320, 240)
            f.startSession(Color.BLUE)
            f.expectPixels(320, 240, BLUE)
            assertEquals("Each session must pass through a fresh system consent dialog", 2, f.consents)
        }
    }

    private class Fixture : AutoCloseable {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val context = instrumentation.targetContext
        val device: UiDevice = UiDevice.getInstance(instrumentation)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val callbacks = HandlerThread("projection-pixels").apply { start() }
        val receiver = BydReceiverServer()
        private val sample = AtomicReference<Sample?>(null)
        private var output: ImageReader? = null
        private val scenario: ActivityScenario<MainActivity>
        var consents = 0
            private set

        init {
            check(Build.VERSION.SDK_INT >= 34) { "This regression must run on Android 14+" }
            device.setOrientationNatural()
            runBlocking {
                withContext(Dispatchers.Main) {
                    MirrorSessionCoordinator.disconnect()
                    if (MirrorSessionCoordinator.orientation.value != DisplayOrientation.LANDSCAPE) {
                        MirrorSessionCoordinator.toggleOrientation()
                    }
                    MirrorSessionCoordinator.selectProfileOverride(
                        DisplayProfile.DILINK_12_8_FHD.copy(
                            baseWidth = 320, baseHeight = 240, targetFps = 30, bitrateMbps = 2
                        )
                    )
                }
            }
            receiver.start(scope)
            replaceOutput(320, 240)
            scenario = ActivityScenario.launch(MainActivity::class.java)
        }

        fun paint(color: Int) {
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                root.findViewWithTag<View>("projection-pattern")?.let { root.removeView(it) }
                root.addView(View(activity).apply {
                    tag = "projection-pattern"
                    setBackgroundColor(color)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            instrumentation.waitForIdleSync()
        }

        suspend fun startSession(color: Int) {
            paint(color)
            // Real ECDH pairing; only loopback device discovery is supplied by the fixture.
            withTimeout(10_000) {
                var paired = false
                while (!paired) {
                    withContext(Dispatchers.Main) {
                        MirrorSessionCoordinator.connect(context,
                            BydDevice("projection-loopback", "Receiver", "Emulator", "5.0", "127.0.0.1"))
                    }
                    paired = MirrorSessionCoordinator.pendingPairing.value != null
                    if (!paired) delay(200)
                }
            }
            val prompt = MirrorSessionCoordinator.pendingPairing.value
            assertNotNull("Production phone pairing must complete", prompt)
            val receiverPairing = receiver.state.value as? ReceiverState.PairingVerification
            assertNotNull("Receiver must verify the same fresh pairing", receiverPairing)
            assertEquals(prompt?.pin, receiverPairing?.pin)
            withContext(Dispatchers.Main) { MirrorSessionCoordinator.confirmTrustedVehicle(context) }
            // MainActivity's actual ActivityResult callback starts MirrorForegroundService.
            val consent = device.wait(Until.findObject(By.res("com.android.systemui", "start_now")), 15_000)
                ?: device.wait(Until.findObject(By.text("Start now")), 5_000)
                ?: throw AssertionError("No Android MediaProjection consent dialog; no screen capture was verified")
            consent.click()
            consents++
            withTimeout(15_000) {
                while (MirrorSessionCoordinator.onReconfigureEncoder == null) delay(50)
            }
            assertTrue(MirrorSessionCoordinator.connectionState.value is ConnectionState.Streaming)
            paint(color)
        }

        suspend fun stopSession() {
            withContext(Dispatchers.Main) { MirrorSessionCoordinator.disconnect() }
            withTimeout(10_000) {
                receiver.state.first { it == ReceiverState.WaitingForPhone }
                // onDestroy clears these hooks; wait before issuing another projection permission.
                while (MirrorSessionCoordinator.onReconfigureEncoder != null) delay(50)
            }
            instrumentation.waitForIdleSync()
        }

        fun replaceOutput(width: Int, height: Int) {
            receiver.attachSurface(null)
            output?.close()
            sample.set(null)
            val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 4)
            reader.setOnImageAvailableListener({ source ->
                source.acquireLatestImage()?.use { image ->
                    sample.set(Sample(image.width, image.height, SystemClock.elapsedRealtime(),
                        pixel(image, 0), pixel(image, 1), pixel(image, 2)))
                }
            }, Handler(callbacks.looper))
            output = reader
            receiver.attachSurface(reader.surface)
            MirrorSessionCoordinator.onKeyframeRequested?.invoke()
        }

        suspend fun expectPixels(width: Int, height: Int, expected: IntArray) {
            val after = SystemClock.elapsedRealtime()
            var last: Sample? = null
            val deadline = after + 20_000
            while (SystemClock.elapsedRealtime() < deadline) {
                last = sample.get()
                if (last != null && last.at >= after && last.width == width && last.height == height &&
                    kotlin.math.abs(last.y - expected[0]) < 40 &&
                    kotlin.math.abs(last.u - expected[1]) < 40 &&
                    kotlin.math.abs(last.v - expected[2]) < 40) {
                    Log.i("DriveLinkProjectionTest", "PASS captured screen ${width}x${height}: YUV=${last.y},${last.u},${last.v}; consent=$consents")
                    return
                }
                delay(50)
            }
            throw AssertionError("No decoded screen pixels matching ${expected.toList()} at ${width}x${height}; last=$last; receiver=${receiver.state.value::class.simpleName}")
        }

        override fun close() {
            try {
                runBlocking { stopSession() }
            } finally {
                scenario.close()
                receiver.stop()
                scope.cancel()
                output?.close()
                callbacks.quitSafely()
                callbacks.join()
                device.setOrientationNatural()
                device.unfreezeRotation()
                runBlocking { withContext(Dispatchers.Main) { MirrorSessionCoordinator.selectProfileOverride(null) } }
            }
        }

        private fun pixel(image: Image, index: Int): Int {
            val plane = image.planes[index]
            val divisor = if (index == 0) 1 else 2
            val x = image.cropRect.centerX() / divisor
            val y = image.cropRect.centerY() / divisor
            return plane.buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 255
        }
    }

    private data class Sample(val width: Int, val height: Int, val at: Long, val y: Int, val u: Int, val v: Int)

    companion object {
        private val RED = intArrayOf(76, 85, 255)
        private val GREEN = intArrayOf(150, 44, 21)
        private val BLUE = intArrayOf(29, 255, 107)
    }
}
