package com.byd.mirror.core.session

import android.content.Context
import com.byd.mirror.core.model.BydDevice
import com.byd.mirror.core.model.ConnectionState
import com.byd.mirror.core.model.DisplayOrientation
import com.byd.mirror.core.model.DisplayProfile
import com.byd.mirror.core.model.TelemetryMetrics
import com.byd.mirror.core.model.ThermalLevel
import com.byd.mirror.core.protocol.BydProtocolWrapper
import com.byd.mirror.core.protocol.QualityNegotiator
import com.byd.mirror.core.security.TrustedVehicle
import com.byd.mirror.core.security.TrustedVehiclesStore
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

data class PairingPrompt(
    val device: BydDevice,
    val profile: DisplayProfile,
    val pin: String,
    val fingerprint: String
)

/**
 * Shared single source of truth for the active BYD wireless mirroring session.
 * Gates media streaming behind PIN confirmation for untrusted vehicles and stops services on disconnect.
 */
object MirrorSessionCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val protocolWrapper = BydProtocolWrapper()
    val qualityNegotiator = QualityNegotiator()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _orientation = MutableStateFlow(DisplayOrientation.LANDSCAPE)
    val orientation: StateFlow<DisplayOrientation> = _orientation.asStateFlow()

    private val _activeProfile = MutableStateFlow(DisplayProfile.DILINK_15_6_FHD)
    val activeProfile: StateFlow<DisplayProfile> = _activeProfile.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryMetrics())
    val telemetry: StateFlow<TelemetryMetrics> = _telemetry.asStateFlow()

    private val _pendingPairing = MutableStateFlow<PairingPrompt?>(null)
    val pendingPairing: StateFlow<PairingPrompt?> = _pendingPairing.asStateFlow()

    var onKeyframeRequested: (() -> Unit)? = null
    var onReconfigureEncoder: ((DisplayProfile, DisplayOrientation) -> Unit)? = null
    var onStopServices: (() -> Unit)? = null

    private var pingJob: Job? = null

    fun markDiscovering() {
        if (_connectionState.value is ConnectionState.Idle || _connectionState.value is ConnectionState.Error) {
            _connectionState.value = ConnectionState.Discovering
        }
    }

    fun markMiracastStreaming(device: BydDevice) {
        _connectionState.value = ConnectionState.Streaming(
            device = device,
            profile = _activeProfile.value,
            orientation = _orientation.value,
            encryptedChannelFingerprint = "WFD-MIRACAST"
        )
    }

    suspend fun connect(context: Context, device: BydDevice) {
        _connectionState.value = ConnectionState.Handshaking(device)
        val negotiated = qualityNegotiator.initialProfileFor(device)
        _activeProfile.value = negotiated

        _connectionState.value = ConnectionState.KeyExchange(device)
        val result = protocolWrapper.connectAndHandshake(device, negotiated)
        result.fold(
            onSuccess = { handshake ->
                val store = TrustedVehiclesStore(context)
                if (store.isTrusted(device.id)) {
                    activateStreaming(device, negotiated, handshake.fingerprint)
                } else {
                    _pendingPairing.value = PairingPrompt(
                        device = device,
                        profile = negotiated,
                        pin = handshake.pairingPin,
                        fingerprint = handshake.fingerprint
                    )
                }
            },
            onFailure = { error ->
                _connectionState.value = ConnectionState.Error(
                    message = error.localizedMessage ?: "Handshake failed"
                )
            }
        )
    }

    fun confirmTrustedVehicle(context: Context) {
        val prompt = _pendingPairing.value ?: return
        TrustedVehiclesStore(context).saveTrustedVehicle(
            TrustedVehicle(
                id = prompt.device.id,
                displayName = prompt.device.displayName,
                fingerprint = prompt.fingerprint,
                pairedAtEpochMs = System.currentTimeMillis()
            )
        )
        _pendingPairing.value = null
        scope.launch {
            activateStreaming(prompt.device, prompt.profile, prompt.fingerprint)
        }
    }

    private suspend fun activateStreaming(
        device: BydDevice,
        profile: DisplayProfile,
        fingerprint: String
    ) {
        protocolWrapper.startMediaSession(profile)
        _connectionState.value = ConnectionState.Streaming(
            device = device,
            profile = profile,
            orientation = _orientation.value,
            encryptedChannelFingerprint = fingerprint
        )
        startTelemetryLoops()
    }

    fun rejectPairing() {
        _pendingPairing.value = null
        scope.launch { disconnect() }
    }

    fun toggleOrientation() {
        val next = if (_orientation.value == DisplayOrientation.LANDSCAPE) {
            DisplayOrientation.PORTRAIT
        } else {
            DisplayOrientation.LANDSCAPE
        }
        _orientation.value = next
        scope.launch {
            protocolWrapper.sendOrientationChange(next)
            onReconfigureEncoder?.invoke(_activeProfile.value, next)
            val state = _connectionState.value
            if (state is ConnectionState.Streaming) {
                _connectionState.value = state.copy(orientation = next)
            }
        }
    }

    fun updateThermalLevel(level: ThermalLevel) {
        val updated = _telemetry.value.copy(thermalLevel = level)
        _telemetry.value = updated
        val adapted = qualityNegotiator.adaptProfile(_activeProfile.value, updated)
        if (adapted != _activeProfile.value) {
            _activeProfile.value = adapted
            onReconfigureEncoder?.invoke(adapted, _orientation.value)
            if (_connectionState.value is ConnectionState.Streaming) {
                scope.launch {
                    protocolWrapper.sendThermalThrottleNotice(adapted.targetFps, adapted.bitrateMbps)
                }
            }
        }
    }

    private fun startTelemetryLoops() {
        protocolWrapper.startControlReadLoop(
            scope = scope,
            onKeyframeRequested = { onKeyframeRequested?.invoke() },
            onQosReport = { fps, dropped ->
                val lossPct = if (fps + dropped > 0) (dropped * 100f) / (fps + dropped) else 0f
                val updated = _telemetry.value.copy(actualFps = fps, packetLossPercent = lossPct)
                _telemetry.value = updated
                val adapted = qualityNegotiator.adaptProfile(_activeProfile.value, updated)
                if (adapted != _activeProfile.value) {
                    _activeProfile.value = adapted
                    onReconfigureEncoder?.invoke(adapted, _orientation.value)
                }
            },
            onHeartbeatRtt = { rtt ->
                _telemetry.value = _telemetry.value.copy(rttMs = rtt)
            },
            onTouchEvent = { xNorm, yNorm, action ->
                com.byd.mirror.service.BydTouchAccessibilityService.dispatchNormalizedTouch(xNorm, yNorm, action)
            }
        )

        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1_500L)
                runCatching { protocolWrapper.sendHeartbeatPing() }
            }
        }
    }

    suspend fun disconnect() {
        pingJob?.cancel()
        pingJob = null
        _pendingPairing.value = null
        protocolWrapper.disconnect()
        onStopServices?.invoke()
        _connectionState.value = ConnectionState.Idle
    }
}
