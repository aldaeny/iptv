package com.byd.receiver.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/**
 * Advertises the BYD DiLink head unit on local Wi-Fi / hotspot via NSD (`_byd-mirror._tcp.`).
 */
class NsdServiceRegistrar(context: Context) {

    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun register(
        serviceName: String = "BYD-DiLink-Display",
        port: Int = 47810,
        diLinkVersion: String = "5.0",
        model: String = "BYD DiLink 15.6\""
    ) {
        unregister()
        val mgr = nsdManager ?: return
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = "_byd-mirror._tcp."
            this.port = port
            setAttribute("dilink", diLinkVersion)
            setAttribute("model", model)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(arg0: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        runCatching { mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun unregister() {
        val mgr = nsdManager ?: return
        registrationListener?.let {
            runCatching { mgr.unregisterService(it) }
        }
        registrationListener = null
    }
}
