package app.drivelink.cast.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.drivelink.cast.core.capability.Capability
import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.ConnectionState
import app.drivelink.cast.core.model.DisplayOrientation
import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.model.TelemetryMetrics
import app.drivelink.cast.core.model.TransportType
import app.drivelink.cast.core.model.VideoCodec
import app.drivelink.cast.core.network.AutoConnectMonitor
import app.drivelink.cast.core.network.MiracastDisplayAdapter
import app.drivelink.cast.core.network.NsdDiscoveryManager
import app.drivelink.cast.core.network.WifiP2pTransport
import app.drivelink.cast.core.security.TrustedVehicle
import app.drivelink.cast.core.security.TrustedVehiclesStore
import app.drivelink.cast.core.session.MirrorSessionCoordinator
import app.drivelink.cast.core.session.SessionPhase
import app.drivelink.cast.core.session.PairingPrompt
import app.drivelink.cast.core.streaming.ThermalPowerManager
import app.drivelink.cast.core.transport.TransportSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MirrorViewModel(application: Application) : AndroidViewModel(application) {

    private val wifiP2p = WifiP2pTransport(application)
    private val nsdDiscovery = NsdDiscoveryManager(application)
    private val miracastAdapter = MiracastDisplayAdapter(application)
    private val autoConnectMonitor = AutoConnectMonitor(application)
    private val thermalManager = ThermalPowerManager(application)
    private val trustedStore = TrustedVehiclesStore(application)

    val connectionState: StateFlow<ConnectionState> = MirrorSessionCoordinator.connectionState
    val orientation: StateFlow<DisplayOrientation> = MirrorSessionCoordinator.orientation
    val activeProfile: StateFlow<DisplayProfile> = MirrorSessionCoordinator.activeProfile
    val preferredCodec: StateFlow<VideoCodec> = MirrorSessionCoordinator.preferredCodec
    val profileOverride: StateFlow<DisplayProfile?> = MirrorSessionCoordinator.profileOverride
    val telemetry: StateFlow<TelemetryMetrics> = MirrorSessionCoordinator.telemetry
    val pendingPairing: StateFlow<PairingPrompt?> = MirrorSessionCoordinator.pendingPairing
    val sessionPhase: StateFlow<SessionPhase> = MirrorSessionCoordinator.session.snapshot
        .map { it.phase }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MirrorSessionCoordinator.session.phase)

    val capabilities: List<Capability> = MirrorSessionCoordinator.capabilities

    private val _autoConnectEnabled = MutableStateFlow(true)
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()

    private val _isArabic = MutableStateFlow(true)
    val isArabic: StateFlow<Boolean> = _isArabic.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _trustedVehicles = MutableStateFlow(trustedStore.listTrusted())
    val trustedVehicles: StateFlow<List<TrustedVehicle>> = _trustedVehicles.asStateFlow()

    val discoveredDevices: StateFlow<List<BydDevice>> = combine(
        wifiP2p.discoveredPeers,
        nsdDiscovery.services,
        miracastAdapter.miracastDevices
    ) { p2p, nsd, miracast ->
        (p2p + nsd + miracast).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        wifiP2p.initialize()
        nsdDiscovery.startDiscovery()
        autoConnectMonitor.startMonitoring()
        thermalManager.startMonitoring()

        viewModelScope.launch {
            thermalManager.thermalLevel.collect { level ->
                MirrorSessionCoordinator.updateThermalLevel(level)
            }
        }

        viewModelScope.launch {
            combine(
                autoConnectMonitor.inBydWifiRange,
                discoveredDevices,
                _autoConnectEnabled
            ) { inRange, devices, enabled ->
                Triple(inRange, devices.firstOrNull { trustedStore.isTrusted(it.id) }, enabled)
            }.collect { (inRange, candidate, enabled) ->
                if (enabled && inRange && candidate != null && connectionState.value is ConnectionState.Idle) {
                    connectToDevice(candidate)
                }
            }
        }
    }

    fun onOneTapConnect() {
        val current = connectionState.value
        if (current is ConnectionState.Streaming) {
            disconnect()
            return
        }

        MirrorSessionCoordinator.markDiscovering()
        wifiP2p.startDiscovery()
        nsdDiscovery.startDiscovery()
        miracastAdapter.startScanning()

        val firstKnown = discoveredDevices.value.firstOrNull()
        if (firstKnown != null) {
            connectToDevice(firstKnown)
        } else {
            viewModelScope.launch {
                val found = withTimeoutOrNull(4_000L) {
                    discoveredDevices.firstOrNull { it.isNotEmpty() }?.firstOrNull()
                }
                if (found != null && connectionState.value is ConnectionState.Discovering) {
                    connectToDevice(found)
                }
            }
        }
    }

    fun connectToDevice(device: BydDevice) {
        viewModelScope.launch {
            if (device.transportType == TransportType.MIRACAST_SINK) {
                if (miracastAdapter.selectMiracastSink(device.id)) {
                    MirrorSessionCoordinator.markMiracastStreaming(device)
                }
                return@launch
            }
            val resolvedDevice = if (device.transportType == TransportType.WIFI_DIRECT) {
                val goHost = wifiP2p.connectAndAwaitGroupOwner(device.id)
                if (goHost != null) device.copy(hostAddress = goHost) else device
            } else {
                device
            }
            val endpoint = TransportSelector.forDevice(resolvedDevice)
                .endpointFor(resolvedDevice)
                .getOrElse { return@launch }
            val target = resolvedDevice.copy(
                hostAddress = endpoint.host,
                controlPort = endpoint.controlPort,
                mediaPort = endpoint.mediaPort
            )
            MirrorSessionCoordinator.connect(getApplication(), target)
        }
    }

    fun confirmPairingPin() {
        MirrorSessionCoordinator.confirmTrustedVehicle(getApplication())
        _trustedVehicles.value = trustedStore.listTrusted()
    }

    fun removeTrustedVehicle(deviceId: String) {
        trustedStore.removeTrustedVehicle(deviceId)
        _trustedVehicles.value = trustedStore.listTrusted()
    }

    fun clearAllTrustedVehicles() {
        trustedStore.listTrusted().forEach { trustedStore.removeTrustedVehicle(it.id) }
        _trustedVehicles.value = emptyList()
    }

    fun rejectPairingPin() {
        MirrorSessionCoordinator.rejectPairing()
    }

    fun isTrustedDevice(deviceId: String): Boolean = trustedStore.isTrusted(deviceId)

    fun toggleOrientation() {
        MirrorSessionCoordinator.toggleOrientation()
    }

    fun setAutoConnect(enabled: Boolean) {
        _autoConnectEnabled.value = enabled
    }

    fun setPreferredCodec(codec: VideoCodec) {
        MirrorSessionCoordinator.setPreferredCodec(codec)
    }

    fun selectProfileOverride(profile: DisplayProfile?) {
        MirrorSessionCoordinator.selectProfileOverride(profile)
    }

    fun toggleLanguage() {
        _isArabic.value = !_isArabic.value
    }

    fun refreshDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true
        wifiP2p.startDiscovery()
        nsdDiscovery.startDiscovery()
        miracastAdapter.startScanning()
        viewModelScope.launch {
            kotlinx.coroutines.delay(3_000L)
            _isScanning.value = false
        }
    }

    fun connectManualIp(rawHost: String) {
        val host = rawHost.trim()
        if (host.isEmpty()) return
        val manualDevice = BydDevice(
            id = "manual-$host",
            displayName = "BYD DiLink ($host)",
            modelName = "BYD DiLink",
            diLinkVersion = "5.0",
            hostAddress = host,
            controlPort = 47810,
            mediaPort = 47811,
            transportType = TransportType.LAN_NSD,
            supportsRotatingScreen = true,
            supportsHevc = preferredCodec.value == VideoCodec.H265
        )
        connectToDevice(manualDevice)
    }

    fun disconnect() {
        miracastAdapter.clearSelection()
        viewModelScope.launch {
            MirrorSessionCoordinator.disconnect()
        }
    }

    override fun onCleared() {
        wifiP2p.release()
        nsdDiscovery.stopDiscovery()
        miracastAdapter.stopScanning()
        autoConnectMonitor.stopMonitoring()
        thermalManager.stopMonitoring()
        super.onCleared()
    }
}
