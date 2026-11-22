package app.drivelink.receiver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.drivelink.receiver.protocol.BydReceiverServer
import app.drivelink.receiver.ui.ReceiverScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    private val server get() = (application as DriveLinkReceiverApp).server

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.startForegroundService(
            this,
            Intent(this, DriveLinkReceiverService::class.java)
        )
        requestNotificationPermission()

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
                onApprove = { server.approvePendingSession() },
                onDeny = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        server.denyPendingSession()
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 47810)
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
