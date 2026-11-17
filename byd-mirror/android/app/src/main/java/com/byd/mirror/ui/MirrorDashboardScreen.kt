package com.byd.mirror.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.mirror.BuildConfig
import com.byd.mirror.core.model.BydDevice
import com.byd.mirror.core.model.ConnectionState
import com.byd.mirror.core.model.DisplayOrientation
import com.byd.mirror.core.model.DisplayProfile
import com.byd.mirror.core.model.TelemetryMetrics
import com.byd.mirror.core.model.TransportType
import com.byd.mirror.core.model.VideoCodec
import com.byd.mirror.ui.theme.BydColors
import java.util.Locale

@Composable
fun MirrorDashboardScreen(
    viewModel: MirrorViewModel,
    onRequestMediaProjection: () -> Unit,
    onLaunchOverlay: () -> Unit
) {
    val state by viewModel.connectionState.collectAsState()
    val autoConnect by viewModel.autoConnectEnabled.collectAsState()
    val orientation by viewModel.orientation.collectAsState()
    val profile by viewModel.activeProfile.collectAsState()
    val preferredCodec by viewModel.preferredCodec.collectAsState()
    val profileOverride by viewModel.profileOverride.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()
    val trustedVehicles by viewModel.trustedVehicles.collectAsState()
    val pendingPairing by viewModel.pendingPairing.collectAsState()
    val isArabic by viewModel.isArabic.collectAsState()

    var captureRequestedForSession by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        val current = state
        if (current is ConnectionState.Streaming &&
            current.device.transportType != TransportType.MIRACAST_SINK &&
            !captureRequestedForSession
        ) {
            captureRequestedForSession = true
            onRequestMediaProjection()
        } else if (current !is ConnectionState.Streaming) {
            captureRequestedForSession = false
        }
    }

    pendingPairing?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.rejectPairingPin() },
            containerColor = BydColors.SurfaceCard,
            title = {
                Text(
                    text = if (isArabic) "تأكيد الاقتران الآمن" else "Verify Secure Pairing",
                    color = BydColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic) {
                            "تحقق من تطابق الرمز الظاهر على شاشة ${prompt.device.displayName}:"
                        } else {
                            "Confirm the 6-digit PIN matches ${prompt.device.displayName}:"
                        },
                        color = BydColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        prompt.pin,
                        color = BydColors.EvCyan,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 6.sp
                    )
                    Text(
                        "Fingerprint: ${prompt.fingerprint}",
                        color = BydColors.EvEmerald,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPairingPin() },
                    colors = ButtonDefaults.buttonColors(containerColor = BydColors.EvCyan)
                ) {
                    Text(
                        text = if (isArabic) "مطابق وحفظ السيارة" else "Match & Trust Vehicle",
                        color = Color(0xFF041016),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rejectPairingPin() }) {
                    Text(
                        text = if (isArabic) "إلغاء" else "Cancel",
                        color = BydColors.DangerRed
                    )
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BydColors.ObsidianBg),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BYD WIRELESS MIRROR",
                        color = BydColors.EvCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (isArabic) "ربط شاشة السيارة لاسلكياً" else "Wireless Car Display Link",
                        color = BydColors.TextPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(BydColors.SurfaceElevated)
                            .clickable { viewModel.toggleLanguage() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isArabic) "EN" else "AR",
                            color = BydColors.EvCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    StatusPill(state, isArabic)
                }
            }
        }

        // One-Tap Connect Hero Card + Live Telemetry HUD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BydColors.SurfaceCard),
                border = BorderStroke(1.dp, BydColors.BorderSubtle),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        BydColors.EvCyan.copy(alpha = 0.30f),
                                        BydColors.SurfaceElevated
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cast,
                            contentDescription = null,
                            tint = if (state is ConnectionState.Streaming) BydColors.EvEmerald else BydColors.EvCyan,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Text(
                        text = statusHeadline(state, isArabic),
                        color = BydColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    val (w, h) = profile.dimensionsFor(orientation)
                    Text(
                        text = "${w}x${h} • ${profile.targetFps} FPS • ${profile.codec.name}",
                        color = BydColors.TextSecondary,
                        fontSize = 13.sp
                    )

                    if (state is ConnectionState.Streaming) {
                        DiagnosticsHudRow(telemetry = telemetry, isArabic = isArabic)
                    }

                    Button(
                        onClick = {
                            if (state is ConnectionState.Streaming) {
                                viewModel.disconnect()
                            } else {
                                viewModel.onOneTapConnect()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state is ConnectionState.Streaming) {
                                BydColors.DangerRed
                            } else {
                                BydColors.EvCyan
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (state is ConnectionState.Streaming) {
                                if (isArabic) "إنهاء الاتصال" else "Disconnect"
                            } else {
                                if (isArabic) "اتصال بضغطة واحدة" else "One-Tap Connect"
                            },
                            color = if (state is ConnectionState.Streaming) Color.White else Color(0xFF041016),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleOrientation() },
                            border = BorderStroke(1.dp, BydColors.BorderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ScreenRotation,
                                contentDescription = null,
                                tint = BydColors.EvCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = if (orientation == DisplayOrientation.LANDSCAPE) {
                                    if (isArabic) "أفقي" else "Landscape"
                                } else {
                                    if (isArabic) "عمودي" else "Portrait"
                                },
                                color = BydColors.TextPrimary
                            )
                        }

                        if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) {
                            OutlinedButton(
                                onClick = onLaunchOverlay,
                                border = BorderStroke(1.dp, BydColors.BorderSubtle),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isArabic) "لوحة التحكم العائمة" else "Floating Panel",
                                    color = BydColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Stream Codec & BYD Screen Size Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BydColors.SurfaceCard),
                border = BorderStroke(1.dp, BydColors.BorderSubtle),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isArabic) "إعدادات البث والشاشة" else "Stream Codec & Display Size",
                        color = BydColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(VideoCodec.H265 to "H.265 (HEVC)", VideoCodec.H264 to "H.264 (AVC)").forEach { (codec, label) ->
                            FilterChip(
                                selected = preferredCodec == codec,
                                onClick = { viewModel.setPreferredCodec(codec) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BydColors.EvCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = BydColors.EvCyan
                                )
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val profiles = listOf(
                            null to (if (isArabic) "تلقائي" else "Auto"),
                            DisplayProfile.DILINK_15_6_FHD to "15.6\"",
                            DisplayProfile.DILINK_12_8_FHD to "12.8\"",
                            DisplayProfile.DILINK_10_1_HD to "10.1\""
                        )
                        profiles.forEach { (itemProfile, label) ->
                            val selected = profileOverride?.id == itemProfile?.id &&
                                ((itemProfile == null && profileOverride == null) || itemProfile != null)
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectProfileOverride(itemProfile) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BydColors.EvEmerald.copy(alpha = 0.2f),
                                    selectedLabelColor = BydColors.EvEmerald
                                )
                            )
                        }
                    }
                }
            }
        }

        // Auto-Connect & Security Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BydColors.SurfaceCard),
                border = BorderStroke(1.dp, BydColors.BorderSubtle),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = BydColors.EvCyan)
                            Column {
                                Text(
                                    text = if (isArabic) "الربط التلقائي" else "Auto-Connect",
                                    color = BydColors.TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (isArabic) "اتصال مباشر عند دخول نطاق واي فاي BYD" else "Connect automatically in BYD Wi-Fi range",
                                    color = BydColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Switch(
                            checked = autoConnect,
                            onCheckedChange = viewModel::setAutoConnect,
                            colors = SwitchDefaults.colors(checkedThumbColor = BydColors.EvCyan)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = BydColors.EvEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isArabic) "قناة مشفرة ECDH P-256 + AES-256-GCM" else "Encrypted ECDH P-256 + AES-256-GCM",
                            color = BydColors.EvEmerald,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Manual IP Direct Connect Fallback
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BydColors.SurfaceCard),
                border = BorderStroke(1.dp, BydColors.BorderSubtle),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isArabic) "الاتصال اليدوي عبر IP (عند حجب الاكتشاف)" else "Manual IP Connect (NSD Fallback)",
                        color = BydColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualIpInput,
                            onValueChange = { manualIpInput = it },
                            placeholder = { Text("192.168.43.1", color = BydColors.TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = BydColors.TextPrimary,
                                unfocusedTextColor = BydColors.TextPrimary,
                                focusedBorderColor = BydColors.EvCyan,
                                unfocusedBorderColor = BydColors.BorderSubtle
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { viewModel.connectManualIp(manualIpInput) },
                            enabled = manualIpInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = BydColors.EvCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isArabic) "ربط" else "Connect",
                                color = Color(0xFF041016),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Discovered BYD Head Units
        item {
            Text(
                text = if (isArabic) "الشاشات المكتشفة (${devices.size})" else "Discovered Screens (${devices.size})",
                color = BydColors.TextSecondary,
                style = MaterialTheme.typography.labelLarge
            )
        }

        items(devices, key = { it.id }) { device ->
            DeviceRow(
                device = device,
                isTrusted = viewModel.isTrustedDevice(device.id),
                isArabic = isArabic,
                onClick = { viewModel.connectToDevice(device) }
            )
        }

        if (trustedVehicles.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isArabic) "السيارات الموثوقة (${trustedVehicles.size})" else "Trusted Vehicles (${trustedVehicles.size})",
                    color = BydColors.TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            items(trustedVehicles, key = { "trusted-${it.id}" }) { vehicle ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = BydColors.SurfaceCard),
                    border = BorderStroke(1.dp, BydColors.BorderSubtle),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(vehicle.displayName, color = BydColors.TextPrimary, fontWeight = FontWeight.Medium)
                            Text(
                                "Fingerprint: ${vehicle.fingerprint}",
                                color = BydColors.EvEmerald,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { viewModel.removeTrustedVehicle(vehicle.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = if (isArabic) "إزالة السيارة" else "Remove Vehicle",
                                tint = BydColors.DangerRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsHudRow(telemetry: TelemetryMetrics, isArabic: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BydColors.SurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HudMetric(label = "FPS", value = "${telemetry.actualFps}", color = BydColors.EvEmerald)
        HudMetric(label = if (isArabic) "التأخير" else "RTT", value = "${telemetry.rttMs}ms", color = BydColors.EvCyan)
        HudMetric(
            label = if (isArabic) "السرعة" else "Bitrate",
            value = String.format(Locale.US, "%.0fM", telemetry.currentBitrateMbps),
            color = BydColors.TextPrimary
        )
        HudMetric(
            label = if (isArabic) "فقدان" else "Loss",
            value = String.format(Locale.US, "%.1f%%", telemetry.packetLossPercent),
            color = if (telemetry.packetLossPercent > 2f) BydColors.WarningAmber else BydColors.EvEmerald
        )
    }
}

@Composable
private fun HudMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = BydColors.TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun StatusPill(state: ConnectionState, isArabic: Boolean) {
    val (label, color) = when (state) {
        is ConnectionState.Idle -> (if (isArabic) "جاهز" else "Ready") to BydColors.TextSecondary
        is ConnectionState.Discovering -> (if (isArabic) "جاري البحث..." else "Scanning...") to BydColors.EvCyan
        is ConnectionState.Handshaking,
        is ConnectionState.KeyExchange,
        is ConnectionState.NegotiatingQuality -> (if (isArabic) "مصافحة آمنة" else "Securing") to BydColors.WarningAmber
        is ConnectionState.Streaming -> (if (isArabic) "متصل" else "Live") to BydColors.EvEmerald
        is ConnectionState.Reconnecting -> (if (isArabic) "إعادة الربط" else "Reconnecting") to BydColors.WarningAmber
        is ConnectionState.Error -> (if (isArabic) "خطأ" else "Error") to BydColors.DangerRed
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeviceRow(device: BydDevice, isTrusted: Boolean, isArabic: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = BydColors.SurfaceElevated),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(device.displayName, color = BydColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    if (isTrusted) {
                        Text(
                            text = if (isArabic) "موثوقة" else "Trusted",
                            color = BydColors.EvEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    "${device.modelName} • DiLink ${device.diLinkVersion} • ${device.hostAddress}",
                    color = BydColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Text(
                text = if (isArabic) "ربط" else "Connect",
                color = BydColors.EvCyan,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun statusHeadline(state: ConnectionState, isArabic: Boolean): String = when (state) {
    is ConnectionState.Idle -> if (isArabic) "اضغط للبحث والربط مع شاشة BYD" else "Tap to scan & mirror to BYD screen"
    is ConnectionState.Discovering -> if (isArabic) "جاري اكتشاف شاشة BYD عبر Wi-Fi P2P..." else "Discovering BYD screen via Wi-Fi P2P..."
    is ConnectionState.Handshaking -> if (isArabic) "مصافحة بروتوكول ${state.device.displayName}" else "Handshaking with ${state.device.displayName}"
    is ConnectionState.KeyExchange -> if (isArabic) "تبادل مفاتيح التشفير ECDH P-256..." else "Exchanging ECDH P-256 keys..."
    is ConnectionState.NegotiatingQuality -> if (isArabic) "ضبط الدقة المناسبة للشاشة..." else "Negotiating display quality..."
    is ConnectionState.Streaming -> if (isArabic) "بث مباشر إلى ${state.device.displayName}" else "Streaming live to ${state.device.displayName}"
    is ConnectionState.Reconnecting -> if (isArabic) "إعادة الاتصال (محاولة ${state.attempt})" else "Reconnecting (Attempt ${state.attempt})"
    is ConnectionState.Error -> state.message
}
