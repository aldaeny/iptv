package app.drivelink.cast.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches Wi-Fi network transitions to trigger Auto-Connect as soon as the phone
 * enters the BYD vehicle Wi-Fi coverage range.
 */
class AutoConnectMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _inBydWifiRange = MutableStateFlow(false)
    val inBydWifiRange: StateFlow<Boolean> = _inBydWifiRange.asStateFlow()

    private val _detectedSsid = MutableStateFlow<String?>(null)
    val detectedSsid: StateFlow<String?> = _detectedSsid.asStateFlow()

    private val networkCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val ssid = extractSsid(capabilities)
                _detectedSsid.value = ssid
                _inBydWifiRange.value = ssid != null && isBydSsid(ssid)
            }

            override fun onLost(network: Network) {
                _detectedSsid.value = null
                _inBydWifiRange.value = false
            }
        }
    } else {
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val ssid = extractSsid(capabilities)
                _detectedSsid.value = ssid
                _inBydWifiRange.value = ssid != null && isBydSsid(ssid)
            }

            override fun onLost(network: Network) {
                _detectedSsid.value = null
                _inBydWifiRange.value = false
            }
        }
    }

    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun stopMonitoring() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    private fun extractSsid(capabilities: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = capabilities.transportInfo as? WifiInfo ?: return null
            return wifiInfo.ssid?.trim('"')
        }
        return null
    }

    private fun isBydSsid(ssid: String): Boolean {
        return BYD_SSID_PREFIXES.any { prefix -> ssid.startsWith(prefix, ignoreCase = true) }
    }

    companion object {
        private val BYD_SSID_PREFIXES = listOf("BYD-DiLink-", "BYD-Auto-", "BYD-Link-", "DIRECT-BYD-")
    }
}
