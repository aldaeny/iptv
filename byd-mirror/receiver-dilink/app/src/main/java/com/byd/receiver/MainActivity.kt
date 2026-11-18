package com.byd.receiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.byd.receiver.network.NsdServiceRegistrar
import com.byd.receiver.protocol.BydReceiverServer
import com.byd.receiver.ui.ReceiverScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    private lateinit var nsdRegistrar: NsdServiceRegistrar
    private val server = BydReceiverServer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nsdRegistrar = NsdServiceRegistrar(this)
        nsdRegistrar.register(port = BydReceiverServer.CONTROL_PORT)
        server.start(lifecycleScope)

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
                    lifecycleScope.launch(Dispatchers.IO) {
                        server.sendTouchEvent(x, y, action)
                    }
                }
            )
        }
    }

    override fun onDestroy() {
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
