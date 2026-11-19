package app.drivelink.cast.core.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import androidx.core.content.ContextCompat
import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Direct Wi-Fi P2P (Wi-Fi Direct) transport for BYD infotainment head units.
 */
class WifiP2pTransport(private val context: Context) {

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val _discoveredPeers = MutableStateFlow<List<BydDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<BydDevice>> = _discoveredPeers.asStateFlow()

    private val _groupOwnerAddress = MutableStateFlow<String?>(null)
    val groupOwnerAddress: StateFlow<String?> = _groupOwnerAddress.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager?.requestPeers(channel) { peerList ->
                        val devices = peerList.deviceList
                            .filter { isBydPeer(it) }
                            .map { it.toBydDevice() }
                        _discoveredPeers.value = devices
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    manager?.requestConnectionInfo(channel) { info: WifiP2pInfo? ->
                        if (info?.groupFormed == true) {
                            _groupOwnerAddress.value = info.groupOwnerAddress?.hostAddress
                        } else {
                            _groupOwnerAddress.value = null
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun initialize() {
        channel = manager?.initialize(context, context.mainLooper, null)
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(onError: (Int) -> Unit = {}) {
        val mgr = manager ?: return
        val ch = channel ?: return
        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = Unit
            override fun onFailure(reason: Int) = onError(reason)
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        val mgr = manager ?: return
        val ch = channel ?: return
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = 0 // Prefer BYD head unit as Group Owner
        }
        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onSuccess()
            override fun onFailure(reason: Int) = onFailure(reason)
        })
    }

    suspend fun connectAndAwaitGroupOwner(
        deviceAddress: String,
        timeoutMs: Long = 10_000L
    ): String? {
        _groupOwnerAddress.value?.let { return it }
        val initiated = suspendCancellableCoroutine<Boolean> { cont ->
            connectToPeer(
                deviceAddress = deviceAddress,
                onSuccess = { if (cont.isActive) cont.resume(true) },
                onFailure = { if (cont.isActive) cont.resume(false) }
            )
        }
        if (!initiated) return null
        return withTimeoutOrNull(timeoutMs) {
            _groupOwnerAddress.filterNotNull().first()
        }
    }

    fun release() {
        runCatching { context.unregisterReceiver(receiver) }
        manager?.let { mgr ->
            channel?.let { ch -> mgr.removeGroup(ch, null) }
        }
    }

    private fun isBydPeer(peer: WifiP2pDevice): Boolean {
        val name = peer.deviceName.orEmpty()
        return name.contains("BYD", ignoreCase = true) ||
            name.startsWith("DIRECT-BYD-", ignoreCase = true) ||
            name.contains("DiLink", ignoreCase = true)
    }

    private fun WifiP2pDevice.toBydDevice(): BydDevice {
        return BydDevice(
            id = deviceAddress,
            displayName = deviceName.ifBlank { "BYD DiLink Display" },
            modelName = "BYD DiLink 15.6\"",
            diLinkVersion = "5.0",
            hostAddress = _groupOwnerAddress.value ?: "192.168.49.1",
            transportType = TransportType.WIFI_DIRECT
        )
    }
}
