package com.byd.mirror.core.network

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaRouter
import android.view.Display
import com.byd.mirror.core.model.BydDevice
import com.byd.mirror.core.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fallback adapter for BYD DiLink head units that do not allow sideloading the BYD Receiver APK.
 * Uses Android's native MediaRouter (ROUTE_TYPE_LIVE_VIDEO / Wi-Fi Display / Miracast)
 * and DisplayManager presentation displays.
 */
class MiracastDisplayAdapter(context: Context) {

    private val mediaRouter = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

    private val _miracastDevices = MutableStateFlow<List<BydDevice>>(emptyList())
    val miracastDevices: StateFlow<List<BydDevice>> = _miracastDevices.asStateFlow()

    private val _activePresentationDisplay = MutableStateFlow<Display?>(null)
    val activePresentationDisplay: StateFlow<Display?> = _activePresentationDisplay.asStateFlow()

    private val routeCallback = object : MediaRouter.SimpleCallback() {
        override fun onRouteAdded(router: MediaRouter, info: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteRemoved(router: MediaRouter, info: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteChanged(router: MediaRouter, info: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteSelected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) {
            _activePresentationDisplay.value = info.presentationDisplay
        }
        override fun onRouteUnselected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) {
            _activePresentationDisplay.value = null
        }
    }

    fun startScanning() {
        mediaRouter?.addCallback(
            MediaRouter.ROUTE_TYPE_LIVE_VIDEO,
            routeCallback,
            MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
        )
        refreshRoutes()
    }

    fun selectMiracastSink(deviceId: String): Boolean {
        val router = mediaRouter ?: return false
        val count = router.routeCount
        for (i in 0 until count) {
            val route = router.getRouteAt(i)
            val id = "miracast-${route.name}"
            if (id == deviceId) {
                router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, route)
                _activePresentationDisplay.value = route.presentationDisplay
                return true
            }
        }
        return false
    }

    fun clearSelection() {
        val router = mediaRouter ?: return
        router.selectRoute(
            MediaRouter.ROUTE_TYPE_LIVE_VIDEO,
            router.defaultRoute
        )
        _activePresentationDisplay.value = null
    }

    fun stopScanning() {
        mediaRouter?.removeCallback(routeCallback)
    }

    private fun refreshRoutes() {
        val router = mediaRouter ?: return
        val found = mutableListOf<BydDevice>()
        val count = router.routeCount
        for (i in 0 until count) {
            val route = router.getRouteAt(i)
            if (route == router.defaultRoute) continue
            val name = route.name?.toString().orEmpty()
            if (route.supportedTypes and MediaRouter.ROUTE_TYPE_LIVE_VIDEO != 0) {
                found += BydDevice(
                    id = "miracast-$name",
                    displayName = name.ifBlank { "BYD Miracast Display" },
                    modelName = "WFD / Miracast Sink",
                    diLinkVersion = "WFD",
                    hostAddress = "wfd://local",
                    transportType = TransportType.MIRACAST_SINK
                )
            }
        }
        _miracastDevices.value = found
        if (_activePresentationDisplay.value == null) {
            _activePresentationDisplay.value = displayManager
                ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                ?.firstOrNull()
        }
    }
}
