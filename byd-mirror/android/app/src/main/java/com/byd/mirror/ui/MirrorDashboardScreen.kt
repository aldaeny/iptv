package com.byd.mirror.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.byd.mirror.core.model.BydDevice
import com.byd.mirror.core.model.ConnectionState
import com.byd.mirror.core.model.DisplayOrientation
import com.byd.mirror.core.model.TransportType
import com.byd.mirror.ui.theme.BydColors

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
    val telemetry by viewModel.telemetry.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()
    val trustedVehicles by viewModel.trustedVehicles.collectAsState()
    val pendingPairing by viewModel.pendingPairing.collectAsState()
    var captureRequestedForSession by remember { mutableStateOf(false) }

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
                Text("تأكيد الاقتران الآمن", color = BydColors.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "تحقق من تطابق الرمز الظاهر على شاشة ${prompt.device.displayName}:",
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
                    Text("مطابق وحفظ السيارة", color = Color(0xFF041016), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rejectPairingPin() }) {
                    Text("إلغاء", color = BydColors.DangerRed)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BydColors.ObsidianBg)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BYD WIRELESS MIRROR",
                    color = BydColors.EvCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "ربط شاشة السيارة لاسلكياً",
                    color = BydColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            StatusPill(state)
        }

        // One-Tap Connect Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = BydColors.SurfaceCard),
            border = BorderStroke(1.dp, BydColors.BorderSubtle),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
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
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = statusHeadline(state),
                    color = BydColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                val (w, h) = profile.dimensionsFor(orientation)
                Text(
                    text = "${w}x${h} • ${profile.targetFps} FPS • ${profile.codec.name} • RTT ${telemetry.rttMs}ms",
                    color = BydColors.TextSecondary,
                    fontSize = 13.sp
                )

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
                        .height(54.dp)
                ) {
                    Text(
                        text = if (state is ConnectionState.Streaming) "إنهاء الاتصال" else "اتصال بضغطة واحدة",
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
                            text = if (orientation == DisplayOrientation.LANDSCAPE) "أفقي" else "عمودي",
                            color = BydColors.TextPrimary
                        )
                    }

                    OutlinedButton(
                        onClick = onLaunchOverlay,
                        border = BorderStroke(1.dp, BydColors.BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "لوحة التحكم العائمة",
                            color = BydColors.TextPrimary
                        )
                    }
                }
            }
        }

        // Auto-Connect & Security Card
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null, tint = BydColors.EvCyan)
                        Column {
                            Text("الربط التلقائي", color = BydColors.TextPrimary, fontWeight = FontWeight.Medium)
                            Text(
                                "اتصال مباشر عند دخول نطاق واي فاي BYD",
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
                        text = "قناة مشفرة ECDH P-256 + AES-256-GCM",
                        color = BydColors.EvEmerald,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Discovered BYD Head Units
        Text(
            text = "الشاشات المكتشفة (${devices.size})",
            color = BydColors.TextSecondary,
            style = MaterialTheme.typography.labelLarge
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.id }) { device ->
                DeviceRow(
                    device = device,
                    isTrusted = viewModel.isTrustedDevice(device.id),
                    onClick = { viewModel.connectToDevice(device) }
                )
            }
            if (trustedVehicles.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "السيارات الموثوقة (${trustedVehicles.size})",
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
                                    contentDescription = "إزالة السيارة",
                                    tint = BydColors.DangerRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: ConnectionState) {
    val (label, color) = when (state) {
        is ConnectionState.Idle -> "جاهز" to BydColors.TextSecondary
        is ConnectionState.Discovering -> "جاري البحث..." to BydColors.EvCyan
        is ConnectionState.Handshaking,
        is ConnectionState.KeyExchange,
        is ConnectionState.NegotiatingQuality -> "مصافحة آمنة" to BydColors.WarningAmber
        is ConnectionState.Streaming -> "متصل" to BydColors.EvEmerald
        is ConnectionState.Reconnecting -> "إعادة الربط" to BydColors.WarningAmber
        is ConnectionState.Error -> "خطأ" to BydColors.DangerRed
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
private fun DeviceRow(device: BydDevice, isTrusted: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = BydColors.SurfaceElevated),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(device.displayName, color = BydColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    if (isTrusted) {
                        Text("موثوقة", color = BydColors.EvEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "${device.modelName} • DiLink ${device.diLinkVersion} • ${device.hostAddress}",
                    color = BydColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Text("ربط", color = BydColors.EvCyan, fontWeight = FontWeight.Bold)
        }
    }
}

private fun statusHeadline(state: ConnectionState): String = when (state) {
    is ConnectionState.Idle -> "اضغط للبحث والربط مع شاشة BYD"
    is ConnectionState.Discovering -> "جاري اكتشاف شاشة BYD عبر Wi-Fi P2P..."
    is ConnectionState.Handshaking -> "مصافحة بروتوكول ${state.device.displayName}"
    is ConnectionState.KeyExchange -> "تبادل مفاتيح التشفير ECDH P-256..."
    is ConnectionState.NegotiatingQuality -> "ضبط الدقة المناسبة للشاشة..."
    is ConnectionState.Streaming -> "بث مباشر إلى ${state.device.displayName}"
    is ConnectionState.Reconnecting -> "إعادة الاتصال (محاولة ${state.attempt})"
    is ConnectionState.Error -> state.message
}
