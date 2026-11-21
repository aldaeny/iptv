package app.drivelink.receiver

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import app.drivelink.receiver.network.NsdServiceRegistrar
import app.drivelink.receiver.protocol.BydReceiverServer
import app.drivelink.receiver.ui.ReceiverScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    private lateinit var nsdRegistrar: NsdServiceRegistrar
    private val server = BydReceiverServer()
    private data class PendingTouch(val x: Float, val y: Float, val action: Byte, val session: Any, val at: Long)
    private val touches = Channel<PendingTouch>(64, BufferOverflow.DROP_OLDEST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nsdRegistrar = NsdServiceRegistrar(this)
        nsdRegistrar.register(port = BydReceiverServer.CONTROL_PORT)
        server.start(lifecycleScope)
        // One writer preserves Android MotionEvent order and never carries input into a new session.
        lifecycleScope.launch(Dispatchers.IO) {
            for (touch in touches) {
                if (SystemClock.uptimeMillis() - touch.at <= 500L) {
                    server.sendTouchEvent(touch.x, touch.y, touch.action, touch.session)
                } else {
                    server.sendTouchEvent(0f, 0f, 3, touch.session)
                }
            }
        }

        setContent {
            val state by server.state.collectAsState()
            val isMuted by server.isMuted.collectAsState()
            val localIps = remember(state) { resolveLocalIpv4Addresses() }
            ReceiverScreen(
                state = state,
                localIps = localIps,
                isMuted = isMuted,
                onToggleMute = { server.toggleMute() },
                onDisconnect = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        server.disconnectActiveSession()
                    }
                },
                onSurfaceReady = { surface -> server.attachSurface(surface) },
                onTouchNormalized = { x, y, action ->
                    server.touchSessionToken()?.let { token ->
                        touches.trySend(PendingTouch(x, y, action, token, SystemClock.uptimeMillis()))
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        touches.cancel()
        nsdRegistrar.unregister()
        server.stop()
        super.onDestroy()
    }

    private fun resolveLocalIpv4Addresses(): List<String> {
        return runCatching {
            val list = mutableListOf<String>()
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { list.add(it) }
                    }
                }
            }
            list.distinct()
        }.getOrDefault(emptyList())
    }
}
