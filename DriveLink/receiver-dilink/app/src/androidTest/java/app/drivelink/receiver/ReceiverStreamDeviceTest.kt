package app.drivelink.receiver

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.protocol.BydProtocolWrapper
import app.drivelink.receiver.protocol.BydReceiverServer
import app.drivelink.receiver.protocol.ReceiverState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException

class ReceiverStreamDeviceTest {
    @Test
    fun encryptedPhoneStreamRendersAfterDisconnectAndReconnect() = runBlocking<Unit> {
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets
            .open("blue-idr.h264").use { it.readBytes() }
        val callbacks = HandlerThread("stream-frame-callbacks").apply { start() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val receiver = BydReceiverServer()
        val phone = BydProtocolWrapper()
        val device = BydDevice("loopback", "Receiver", "Emulator", "5.0", "127.0.0.1")
        val profile = DisplayProfile.DILINK_12_8_FHD.copy(baseWidth = 320, baseHeight = 240, targetFps = 30)
        try {
            receiver.start(scope)
            repeat(2) { round ->
                val frame = if (round == 0) fixture else encodeLiveAvcFrame(320, 240)
                val texture = SurfaceTexture(false)
                val surface = Surface(texture)
                val rendered = CompletableDeferred<Unit>()
                val keyframeRequested = CompletableDeferred<Unit>()
                try {
                    texture.setDefaultBufferSize(320, 240)
                    texture.setOnFrameAvailableListener({ rendered.complete(Unit) }, Handler(callbacks.looper))
                    receiver.attachSurface(surface)
                    var handshake = phone.connectAndHandshake(device, profile)
                    repeat(20) {
                        if (handshake.exceptionOrNull() is ConnectException) {
                            delay(100)
                            handshake = phone.connectAndHandshake(device, profile)
                        }
                    }
                    val pairing = handshake.getOrThrow()
                    val receiverPairing = receiver.state.value as ReceiverState.PairingVerification
                    assertEquals(pairing.pairingPin, receiverPairing.pin)
                    phone.startMediaSession(profile, pairing.fingerprint)
                    phone.startControlReadLoop(scope, { keyframeRequested.complete(Unit) }, { _, _ -> }, {})
                    withTimeout(5_000L) { keyframeRequested.await() }
                    withTimeout(8_000L) {
                        var ptsUs = 0
                        while (!rendered.isCompleted) {
                            phone.sendVideoFrame(frame, ptsUs)
                            ptsUs += 33_333
                            delay(33)
                        }
                        rendered.await()
                    }
                    phone.disconnect()
                    withTimeout(5_000L) { receiver.state.first { it == ReceiverState.WaitingForPhone } }
                } finally {
                    receiver.attachSurface(null)
                    surface.release()
                    texture.release()
                }
            }
        } finally {
            try {
                phone.disconnect()
            } finally {
                scope.cancel()
                receiver.stop()
                callbacks.quitSafely()
                callbacks.join()
            }
        }
    }
}
