package com.byd.mirror.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.byd.mirror.core.model.BydDevice
import com.byd.mirror.core.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Discovers BYD Wireless Mirroring sinks on the local car hotspot / Wi-Fi LAN
 * via Android Network Service Discovery (`_byd-mirror._tcp.`).
 */
class NsdDiscoveryManager(context: Context) {

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _services = MutableStateFlow<List<BydDevice>>(emptyList())
    val services: StateFlow<List<BydDevice>> = _services.asStateFlow()

    private var activeDiscoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }

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
        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun resolveService(info: NsdServiceInfo) {
        @Suppress("DEPRECATION")
        nsdManager?.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val host = resolved.host?.hostAddress ?: return
                val diLinkVersion = resolved.attributes["dilink"]?.decodeToString() ?: "4.0"
                val model = resolved.attributes["model"]?.decodeToString() ?: "BYD DiLink"
                val device = BydDevice(
                    id = "${resolved.serviceName}@$host",
                    displayName = resolved.serviceName,
                    modelName = model,
                    diLinkVersion = diLinkVersion,
                    hostAddress = host,
                    controlPort = resolved.port,
                    transportType = TransportType.LAN_NSD
                )
                val updated = _services.value.filterNot { it.id == device.id } + device
                _services.value = updated
            }
        })
    }

    fun stopDiscovery() {
        activeDiscoveryListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        activeDiscoveryListener = null
    }

    companion object {
        const val SERVICE_TYPE = "_byd-mirror._tcp."
    }
}
