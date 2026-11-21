package app.drivelink.cast.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.drivelink.cast.core.model.ConnectionState
import app.drivelink.cast.core.model.TransportType
import app.drivelink.cast.ui.components.BydBottomNavBar
import app.drivelink.cast.ui.components.BydManualIpDialog
import app.drivelink.cast.ui.components.BydPinVerificationDialog
import app.drivelink.cast.ui.components.BydTopBar
import app.drivelink.cast.ui.components.MirrorTab
import app.drivelink.cast.ui.screens.AboutScreen
import app.drivelink.cast.ui.screens.ActiveMirroringScreen
import app.drivelink.cast.ui.screens.ConnectingScreen
import app.drivelink.cast.ui.screens.DevicesScreen
import app.drivelink.cast.ui.screens.HomeScreen
import app.drivelink.cast.ui.screens.SettingsScreen
import app.drivelink.cast.ui.theme.BydColors

@Composable
fun MirrorDashboardScreen(
    viewModel: MirrorViewModel,
    onRequestMediaProjection: () -> Unit,
    onLaunchOverlay: () -> Unit
) {
    val state by viewModel.connectionState.collectAsState()
    val autoConnect by viewModel.autoConnectEnabled.collectAsState()
    val orientation by viewModel.orientation.collectAsState()
    val preferredCodec by viewModel.preferredCodec.collectAsState()
    val profileOverride by viewModel.profileOverride.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()
    val trustedVehicles by viewModel.trustedVehicles.collectAsState()
    val pendingPairing by viewModel.pendingPairing.collectAsState()
    val isArabic by viewModel.isArabic.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var currentTab by remember { mutableStateOf(MirrorTab.HOME) }
    var showActiveMirrorScreen by remember { mutableStateOf(false) }
    var captureRequestedForSession by rememberSaveable { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }

    // Trigger MediaProjection cleanly when streaming begins (non-Miracast)
    LaunchedEffect(state) {
        val current = state
        if (current is ConnectionState.Streaming) {
            if (current.device.transportType != TransportType.MIRACAST_SINK && !captureRequestedForSession) {
                captureRequestedForSession = true
                onRequestMediaProjection()
            }
            showActiveMirrorScreen = true
        } else {
            captureRequestedForSession = false
            showActiveMirrorScreen = false
        }
    }

    // Back button behavior across sub-views and tabs
    BackHandler(enabled = showActiveMirrorScreen || currentTab != MirrorTab.HOME) {
        if (showActiveMirrorScreen) {
            showActiveMirrorScreen = false
        } else {
            currentTab = MirrorTab.HOME
        }
    }

    // 6-digit ECDH PIN confirmation dialog
    pendingPairing?.let { prompt ->
        BydPinVerificationDialog(
            prompt = prompt,
            isArabic = isArabic,
            onConfirm = { viewModel.confirmPairingPin() },
            onReject = { viewModel.rejectPairingPin() }
        )
    }

    // Manual IP connection dialog
    if (showManualIpDialog) {
        BydManualIpDialog(
            initialIp = "",
            isArabic = isArabic,
            onDismiss = { showManualIpDialog = false },
            onConnect = { ip ->
                showManualIpDialog = false
                viewModel.connectManualIp(ip)
            }
        )
    }

    // Dedicated Connecting Experience during active connection/handshake stages
    val isConnectingStage = state is ConnectionState.Discovering ||
        state is ConnectionState.Handshaking ||
        state is ConnectionState.KeyExchange ||
        state is ConnectionState.NegotiatingQuality ||
        state is ConnectionState.Reconnecting

    if (isConnectingStage) {
        ConnectingScreen(
            state = state,
            isArabic = isArabic,
            onCancel = { viewModel.disconnect() }
        )
        return
    }

    // Dedicated Active Mirroring Screen when live & open
    val streamingState = state as? ConnectionState.Streaming
    if (showActiveMirrorScreen && streamingState != null) {
        ActiveMirroringScreen(
            state = streamingState,
            telemetry = telemetry,
            isArabic = isArabic,
            onToggleOrientation = { viewModel.toggleOrientation() },
            onLaunchOverlay = onLaunchOverlay,
            onStopMirroring = { viewModel.disconnect() },
            onReturnHome = {
                showActiveMirrorScreen = false
                currentTab = MirrorTab.HOME
            }
        )
        return
    }

    Scaffold(
        containerColor = BydColors.Background,
        topBar = {
            BydTopBar(
                state = state,
                isArabic = isArabic,
                onToggleLanguage = { viewModel.toggleLanguage() },
                onOpenDevices = { currentTab = MirrorTab.DEVICES }
            )
        },
        bottomBar = {
            BydBottomNavBar(
                currentTab = currentTab,
                isArabic = isArabic,
                onSelectTab = { currentTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BydColors.Background)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MirrorTab.HOME -> HomeScreen(
                    state = state,
                    discoveredDevices = devices,
                    telemetry = telemetry,
                    isArabic = isArabic,
                    onStartMirroring = { viewModel.onOneTapConnect() },
                    onOpenDevices = {
                        viewModel.refreshDiscovery()
                        currentTab = MirrorTab.DEVICES
                    },
                    onOpenActiveMirror = { showActiveMirrorScreen = true },
                    onOpenManualIp = { showManualIpDialog = true }
                )
                MirrorTab.DEVICES -> DevicesScreen(
                    state = state,
                    devices = devices,
                    trustedVehicles = trustedVehicles,
                    isScanning = isScanning,
                    isArabic = isArabic,
                    isTrusted = { viewModel.isTrustedDevice(it) },
                    onRefreshScan = { viewModel.refreshDiscovery() },
                    onConnectDevice = { viewModel.connectToDevice(it) },
                    onDisconnect = { viewModel.disconnect() },
                    onRemoveTrusted = { viewModel.removeTrustedVehicle(it) },
                    onOpenManualIp = { showManualIpDialog = true }
                )
                MirrorTab.SETTINGS -> SettingsScreen(
                    orientation = orientation,
                    preferredCodec = preferredCodec,
                    profileOverride = profileOverride,
                    autoConnect = autoConnect,
                    trustedVehicles = trustedVehicles,
                    isArabic = isArabic,
                    onToggleOrientation = { viewModel.toggleOrientation() },
                    onSelectCodec = { viewModel.setPreferredCodec(it) },
                    onSelectProfileOverride = { viewModel.selectProfileOverride(it) },
                    onSetAutoConnect = { viewModel.setAutoConnect(it) },
                    onLaunchOverlay = onLaunchOverlay,
                    onRemoveTrusted = { viewModel.removeTrustedVehicle(it) },
                    onClearTrusted = { viewModel.clearAllTrustedVehicles() },
                    onOpenManualIp = { showManualIpDialog = true },
                    onOpenAbout = { currentTab = MirrorTab.ABOUT }
                )
                MirrorTab.ABOUT -> AboutScreen(isArabic = isArabic)
            }
        }
    }
}
