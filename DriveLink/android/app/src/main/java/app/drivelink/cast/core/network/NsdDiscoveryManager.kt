package app.drivelink.cast.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Discovers BYD & Chinese EV Wireless Mirroring sinks on the local car hotspot / Wi-Fi LAN
 * via BOTH Android NSD (`_byd-mirror._tcp.`) AND zero-config UDP Discovery Beacon (port 47813).
 */
class NsdDiscoveryManager(context: Context) {

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _services = MutableStateFlow<List<BydDevice>>(emptyList())
    val services: StateFlow<List<BydDevice>> = _services.asStateFlow()

    private var activeDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var beaconJob: Job? = null
    private var pruneJob: Job? = null
    private var beaconSocket: DatagramSocket? = null
    private val beaconLastSeenMs = ConcurrentHashMap<String, Long>()

    fun startDiscovery() {
        stopDiscovery()
        startNsdDiscovery()
        startUdpBeaconListener()
        startBeaconPruner()
    }

    private fun startBeaconPruner() {
        pruneJob = scope.launch {
            while (isActive) {
                delay(2_000L)
                val now = System.currentTimeMillis()
                val staleKeys = beaconLastSeenMs.entries
                    .filter { now - it.value > BEACON_TTL_MS }
                    .map { it.key }
                if (staleKeys.isNotEmpty()) {
                    staleKeys.forEach { beaconLastSeenMs.remove(it) }
                    _services.value = _services.value.filterNot { it.id in staleKeys }
                }
            }
        }
    }

    private fun startNsdDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("_byd-mirror")) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _services.value = _services.value.filterNot { it.displayName == serviceInfo.serviceName }
            }
        }
        activeDiscoveryListener = listener
        runCatching {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun startUdpBeaconListener() {
        beaconJob = scope.launch {
            runCatching {
                val sock = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(BEACON_UDP_PORT))
                }
                beaconSocket = sock
                val buf = ByteArray(1024)
                val pkt = DatagramPacket(buf, buf.size)
                while (isActive) {
                    pkt.length = buf.size
                    sock.receive(pkt)
                    val host = pkt.address?.hostAddress ?: continue
                    val raw = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    val json = runCatching { JSONObject(raw) }.getOrNull() ?: continue
                    if (json.optString("proto") != "byd-mirror-v1") continue
                    val name = json.optString("name", "BYD DiLink")
                    val model = json.optString("model", "BYD / Chinese EV")
                    val ctrlPort = json.optInt("port", 47810)
                    val mediaPort = json.optInt("mediaPort", 47811)
                    if (ctrlPort !in 1..65535 || mediaPort !in 1..65535) continue
                    val supportsHevc = json.optBoolean("hevc", false)
                    upsertDevice(
                        BydDevice(
                            id = "beacon-$host",
                            displayName = name,
                            modelName = model,
                            diLinkVersion = "5.0",
                            hostAddress = host,
                            controlPort = ctrlPort,
                            mediaPort = mediaPort,
                            transportType = TransportType.LAN_NSD,
                            supportsRotatingScreen = true,
                            supportsHevc = supportsHevc
                        )
                    )
                }
            }
        }
    }

    private fun resolveService(info: NsdServiceInfo) {
        @Suppress("DEPRECATION")
        nsdManager?.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val host = resolved.host?.hostAddress ?: return
                val diLinkVersion = resolved.attributes["dilink"]?.decodeToString() ?: "4.0"
                val model = resolved.attributes["model"]?.decodeToString() ?: "BYD DiLink"
                upsertDevice(
                    BydDevice(
                        id = "${resolved.serviceName}@$host",
                        displayName = resolved.serviceName,
                        modelName = model,
                        diLinkVersion = diLinkVersion,
                        hostAddress = host,
                        controlPort = resolved.port,
                        transportType = TransportType.LAN_NSD
                    )
                )
            }
        })
    }

    private fun upsertDevice(device: BydDevice) {
        if (device.id.startsWith("beacon-")) {
            beaconLastSeenMs[device.id] = System.currentTimeMillis()
        }
        val current = _services.value
        if (current.any { it.hostAddress == device.hostAddress && it.displayName == device.displayName }) return
        _services.value = current.filterNot { it.hostAddress == device.hostAddress } + device
    }

    fun stopDiscovery() {
        activeDiscoveryListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        activeDiscoveryListener = null
        pruneJob?.cancel()
        pruneJob = null
        beaconJob?.cancel()
        beaconJob = null
        beaconLastSeenMs.clear()
        runCatching { beaconSocket?.close() }
        beaconSocket = null
    }

    companion object {
        const val SERVICE_TYPE = "_byd-mirror._tcp."
        const val BEACON_UDP_PORT = 47813
        private const val BEACON_TTL_MS = 6_500L
    }
}
