package app.drivelink.cast.core.session

import android.content.Context
import app.drivelink.cast.core.capability.Capability
import app.drivelink.cast.core.capability.CapabilityRegistry
import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.ConnectionState
import app.drivelink.cast.core.model.DisplayOrientation
import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.model.TelemetryMetrics
import app.drivelink.cast.core.model.ThermalLevel
import app.drivelink.cast.core.model.VideoCodec
import app.drivelink.cast.core.protocol.BydProtocolWrapper
import app.drivelink.cast.core.protocol.QualityNegotiator
import app.drivelink.cast.core.security.TrustedVehicle
import app.drivelink.cast.core.security.AndroidDeviceIdentity
import app.drivelink.cast.core.security.TrustedVehiclesStore
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

    /** Legal ordering / single-flight gate for the whole app. */
    val session = SessionStateMachine()

    /** Static capability truth for this build; UI renders exactly these states. */
    val capabilities: List<Capability> = CapabilityRegistry.forThisBuild()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _orientation = MutableStateFlow(DisplayOrientation.LANDSCAPE)
    val orientation: StateFlow<DisplayOrientation> = _orientation.asStateFlow()

    private val _activeProfile = MutableStateFlow(DisplayProfile.DILINK_15_6_FHD)
    val activeProfile: StateFlow<DisplayProfile> = _activeProfile.asStateFlow()

    private val _preferredCodec = MutableStateFlow(VideoCodec.H264)
    val preferredCodec: StateFlow<VideoCodec> = _preferredCodec.asStateFlow()

    private val _profileOverride = MutableStateFlow<DisplayProfile?>(null)
    val profileOverride: StateFlow<DisplayProfile?> = _profileOverride.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryMetrics())
    val telemetry: StateFlow<TelemetryMetrics> = _telemetry.asStateFlow()

    private val _pendingPairing = MutableStateFlow<PairingPrompt?>(null)
    val pendingPairing: StateFlow<PairingPrompt?> = _pendingPairing.asStateFlow()

    var onKeyframeRequested: (() -> Unit)? = null
    var onReconfigureEncoder: ((DisplayProfile, DisplayOrientation) -> Unit)? = null
    var onStopServices: (() -> Unit)? = null

    private var peerSupportsHevc = false

    private fun supportedCodec(codec: VideoCodec) = if (peerSupportsHevc) codec else VideoCodec.H264

    /**
     * Records the codec the hardware encoder actually opened after a fallback, and keeps the live
     * session in step with it. [activeProfile] stays the single source of truth for the codec, so
     * later reconfigures (orientation, thermal, quality) ask for the real codec instead of one this
     * device cannot open.
     *
     * Returns the updated profile, or null when the codec is unchanged — that null is what stops
     * the same transition from being announced twice. The encoder is deliberately not reconfigured
     * here: it is already running this codec, and reconfiguring from inside its own start callback
     * would restart it recursively.
     */
    fun applyActiveCodec(codec: VideoCodec): DisplayProfile? {
        val current = _activeProfile.value
        if (current.codec == codec) return null
        val updated = current.copy(codec = codec)
        _activeProfile.value = updated
        val state = _connectionState.value
        if (state is ConnectionState.Streaming) {
            _connectionState.value = state.copy(profile = updated)
        }
        return updated
    }

    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastConnectedDevice: BydDevice? = null
    private var appContext: Context? = null
    @Volatile
    private var userInitiatedDisconnect = false

    fun markDiscovering() {
        if (!session.phase.canAcceptNewConnection) return
        if (session.move(SessionPhase.Discovering, "discovery")) {
            _connectionState.value = ConnectionState.Discovering
        }
    }

    fun markMiracastStreaming(device: BydDevice) {
        session.enterExternalStreaming("miracast")
        app.drivelink.cast.service.BydTouchAccessibilityService.setInjectionAllowed(false)
        _connectionState.value = ConnectionState.Streaming(
            device = device,
            profile = _activeProfile.value,
            orientation = _orientation.value,
            encryptedChannelFingerprint = "WFD-MIRACAST"
        )
    }

    fun setPreferredCodec(codec: VideoCodec) {
        _preferredCodec.value = codec
        val updated = _activeProfile.value.copy(codec = supportedCodec(codec))
        _activeProfile.value = updated
        if (_connectionState.value is ConnectionState.Streaming) {
            scope.launch {
                val state = _connectionState.value as? ConnectionState.Streaming ?: return@launch
                if (runCatching { protocolWrapper.startMediaSession(updated, state.encryptedChannelFingerprint) }.isFailure) return@launch
                onReconfigureEncoder?.invoke(updated, _orientation.value)
            }
        }
    }

    fun selectProfileOverride(profile: DisplayProfile?) {
        _profileOverride.value = profile
        if (profile != null) {
            val withCodec = profile.copy(codec = supportedCodec(_preferredCodec.value))
            _activeProfile.value = withCodec
            if (_connectionState.value is ConnectionState.Streaming) {
                onReconfigureEncoder?.invoke(withCodec, _orientation.value)
            }
        }
    }

    suspend fun connect(context: Context, device: BydDevice) {
        val phase = session.phase
        if (!phase.canAcceptNewConnection && phase != SessionPhase.Discovering) return
        appContext = context.applicationContext
        protocolWrapper.identitySigner = AndroidDeviceIdentity(appContext!!)
        userInitiatedDisconnect = false
        reconnectJob?.cancel()
        lastConnectedDevice = device
        session.move(SessionPhase.Connecting, "connect")
        app.drivelink.cast.service.BydTouchAccessibilityService.setInjectionAllowed(false)

        _pendingPairing.value = null
        _connectionState.value = ConnectionState.Handshaking(device)
        val base = _profileOverride.value ?: qualityNegotiator.initialProfileFor(device)
        val negotiated = base.copy(codec = _preferredCodec.value)
        _activeProfile.value = negotiated
        session.move(SessionPhase.Handshaking, "handshake")
        val generation = session.snapshot.value.generation

        _connectionState.value = ConnectionState.KeyExchange(device)
        val result = protocolWrapper.connectAndHandshake(device, negotiated)
        if (!session.isCurrent(generation)) {
            protocolWrapper.disconnect()
            return
        }
        result.fold(
            onSuccess = { handshake ->
                session.move(SessionPhase.AwaitingApproval, "awaiting receiver approval")
                // A discovery ID is not a persistent signing key. Reverify every fresh ECDH session.
                peerSupportsHevc = handshake.supportsHevc
                val profile = negotiated.copy(codec = supportedCodec(negotiated.codec))
                _activeProfile.value = profile
                _pendingPairing.value = PairingPrompt(device, profile, handshake.pairingPin, handshake.fingerprint)
            },
            onFailure = { error ->
                session.move(SessionPhase.Failed, "handshake failed")
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
        // Honour a codec recorded by an encoder fallback: the receiver must be told the codec that
        // will really be used, not the one originally negotiated.
        val effectiveProfile = profile.copy(codec = _activeProfile.value.codec)
        runCatching { protocolWrapper.startMediaSession(effectiveProfile, fingerprint) }.onFailure {
            session.move(SessionPhase.Failed, "session start failed")
            _connectionState.value = ConnectionState.Error(it.localizedMessage ?: "Session start failed")
            return
        }
        _telemetry.value = _telemetry.value.copy(currentBitrateMbps = effectiveProfile.bitrateMbps.toFloat())
        _connectionState.value = ConnectionState.Streaming(
            device = device,
            profile = effectiveProfile,
            orientation = _orientation.value,
            encryptedChannelFingerprint = fingerprint
        )
        session.move(SessionPhase.Streaming, "approved")
        if (app.drivelink.cast.BuildConfig.ENABLE_OVERLAY_AND_TOUCH) {
            app.drivelink.cast.service.BydTouchAccessibilityService.setInjectionAllowed(true)
        }
        onReconfigureEncoder?.invoke(effectiveProfile, _orientation.value)
        startTelemetryLoops()
    }

    fun rejectPairing() {
        _pendingPairing.value = null
        session.move(SessionPhase.Stopping, "pairing rejected")
        app.drivelink.cast.service.BydTouchAccessibilityService.setInjectionAllowed(false)
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
            if (_connectionState.value !is ConnectionState.Streaming) return@launch
            if (runCatching { protocolWrapper.sendOrientationChange(next) }.isFailure) return@launch
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
                    runCatching { protocolWrapper.sendThermalThrottleNotice(adapted.targetFps, adapted.bitrateMbps) }
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
                if (app.drivelink.cast.BuildConfig.ENABLE_OVERLAY_AND_TOUCH &&
                    session.phase == SessionPhase.Streaming
                ) {
                    app.drivelink.cast.service.BydTouchAccessibilityService.dispatchNormalizedTouch(xNorm, yNorm, action)
                }
            },
            onUnexpectedDisconnect = {
                handleUnexpectedDrop()
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

    private fun handleUnexpectedDrop() {
        val device = lastConnectedDevice ?: return
        val ctx = appContext ?: return
        if (userInitiatedDisconnect || !TrustedVehiclesStore(ctx).isTrusted(device.id)) {
            session.move(SessionPhase.Failed, "unexpected drop")
            _connectionState.value = ConnectionState.Error("انقطع الاتصال بشاشة السيارة")
            return
        }
        if (reconnectJob?.isActive == true) return
        session.move(SessionPhase.Reconnecting, "reconnect")
        reconnectJob = scope.launch {
            val delaysMs = longArrayOf(1_500L, 3_000L, 5_000L)
            for ((idx, waitMs) in delaysMs.withIndex()) {
                if (userInitiatedDisconnect) return@launch
                _connectionState.value = ConnectionState.Reconnecting(device, idx + 1)
                delay(waitMs)
                protocolWrapper.disconnect()
                session.move(SessionPhase.Connecting, "reconnect attempt")
                session.move(SessionPhase.Handshaking, "reconnect handshake")
                val base = _profileOverride.value ?: qualityNegotiator.initialProfileFor(device)
                val profile = base.copy(codec = _preferredCodec.value)
                val result = protocolWrapper.connectAndHandshake(device, profile)
                if (result.isSuccess) {
                    val hs = result.getOrThrow()
                    _connectionState.value = ConnectionState.KeyExchange(device)
                    peerSupportsHevc = hs.supportsHevc
                    val selected = profile.copy(codec = supportedCodec(profile.codec))
                    _activeProfile.value = selected
                    _pendingPairing.value = PairingPrompt(device, selected, hs.pairingPin, hs.fingerprint)
                    session.move(SessionPhase.AwaitingApproval, "reconnected; awaiting approval")
                    return@launch
                }
                session.move(SessionPhase.Failed, "reconnect attempt failed")
            }
            session.move(SessionPhase.Failed, "reconnect exhausted")
            _connectionState.value = ConnectionState.Error("تعذر إعادة الاتصال بشاشة BYD")
        }
    }

    suspend fun disconnect() {
        userInitiatedDisconnect = true
        session.move(SessionPhase.Stopping, "user disconnect")
        app.drivelink.cast.service.BydTouchAccessibilityService.setInjectionAllowed(false)
        reconnectJob?.cancel()
        reconnectJob = null
        pingJob?.cancel()
        pingJob = null
        _pendingPairing.value = null
        protocolWrapper.disconnect()
        onStopServices?.invoke()
        session.reset("disconnected")
        _connectionState.value = ConnectionState.Idle
    }
}
