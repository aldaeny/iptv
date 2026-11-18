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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var activeInfoDialog by remember { mutableStateOf<String?>(null) }

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

    // Secure 6-digit PIN Pairing Dialog
    pendingPairing?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.rejectPairingPin() },
            containerColor = BydColors.SurfaceCard,
            title = {
                Text(
                    text = if (isArabic) "تأكيد الاقتران" else "Verify Pairing",
                    color = BydColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic) {
                            "تأكد من تطابق الرمز على شاشة ${prompt.device.displayName}:"
                        } else {
                            "Confirm this PIN matches ${prompt.device.displayName}:"
                        },
                        color = BydColors.TextSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        prompt.pin,
                        color = BydColors.EvCyan,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 6.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPairingPin() },
                    colors = ButtonDefaults.buttonColors(containerColor = BydColors.EvCyan)
                ) {
                    Text(
                        text = if (isArabic) "تأكيد وحفظ" else "Trust Vehicle",
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

    // Quick Manual IP Dialog
    if (showManualIpDialog) {
        AlertDialog(
            onDismissRequest = { showManualIpDialog = false },
            containerColor = BydColors.SurfaceCard,
            title = {
                Text(
                    text = if (isArabic) "الاتصال برقم IP" else "Connect by IP",
                    color = BydColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isArabic) {
                            "أدخل رقم IP الظاهر على شاشة تطبيق BYD Receiver في السيارة:"
                        } else {
                            "Enter the IP address shown on the BYD Receiver screen:"
                        },
                        color = BydColors.TextSecondary,
                        fontSize = 13.sp
                    )
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.connectManualIp(manualIpInput)
                        showManualIpDialog = false
                    },
                    enabled = manualIpInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BydColors.EvCyan)
                ) {
                    Text(
                        text = if (isArabic) "ربط الآن" else "Connect",
                        color = Color(0xFF041016),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualIpDialog = false }) {
                    Text(if (isArabic) "إلغاء" else "Cancel", color = BydColors.TextSecondary)
                }
            }
        )
    }

    // Advanced Settings Bottom Sheet (tucked cleanly out of main screen)
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BydColors.SurfaceCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isArabic) "الإعدادات المتقدمة" else "Advanced Settings",
                    color = BydColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                // Orientation & Optional Floating Overlay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                            color = BydColors.TextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) {
                        OutlinedButton(
                            onClick = {
                                showSettingsSheet = false
                                onLaunchOverlay()
                            },
                            border = BorderStroke(1.dp, BydColors.BorderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isArabic) "تحكم عائم" else "Overlay",
                                color = BydColors.TextPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                HorizontalDivider(color = BydColors.BorderSubtle)

                // Video Codec
                Text(
                    text = if (isArabic) "جودة الترميز" else "Video Codec",
                    color = BydColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(VideoCodec.H265 to "H.265", VideoCodec.H264 to "H.264").forEach { (codec, label) ->
                        FilterChip(
                            selected = preferredCodec == codec,
                            onClick = { viewModel.setPreferredCodec(codec) },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BydColors.EvCyan.copy(alpha = 0.2f),
                                selectedLabelColor = BydColors.EvCyan
                            )
                        )
                    }
                }

                // Screen Size
                Text(
                    text = if (isArabic) "حجم شاشة السيارة" else "Car Screen Size",
                    color = BydColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
                            label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BydColors.EvEmerald.copy(alpha = 0.2f),
                                selectedLabelColor = BydColors.EvEmerald
                            )
                        )
                    }
                }

                HorizontalDivider(color = BydColors.BorderSubtle)

                // Auto-Connect
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
                                text = if (isArabic) "اتصال تلقائي عند الاقتراب من السيارة" else "Auto-link on BYD Wi-Fi",
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

                // Trusted Vehicles
                if (trustedVehicles.isNotEmpty()) {
                    HorizontalDivider(color = BydColors.BorderSubtle)
                    Text(
                        text = if (isArabic) "السيارات المحفوظة (${trustedVehicles.size})" else "Saved Vehicles (${trustedVehicles.size})",
                        color = BydColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    trustedVehicles.forEach { vehicle ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BydColors.SurfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(vehicle.displayName, color = BydColors.TextPrimary, fontWeight = FontWeight.Medium)
                                Text("ID: ${vehicle.fingerprint}", color = BydColors.EvEmerald, fontSize = 11.sp)
                            }
                            IconButton(onClick = { viewModel.removeTrustedVehicle(vehicle.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = BydColors.DangerRed)
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = BydColors.EvEmerald, modifier = Modifier.size(14.dp))
                    Text(
                        text = if (isArabic) "اتصال مشفر بالكامل ECDH P-256 + AES-256-GCM" else "End-to-end encrypted ECDH P-256",
                        color = BydColors.EvEmerald,
                        fontSize = 12.sp
                    )
                }

                HorizontalDivider(color = BydColors.BorderSubtle)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { activeInfoDialog = "compat" },
                        border = BorderStroke(1.dp, BydColors.BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isArabic) "توافق السيارات" else "Vehicles", color = BydColors.TextPrimary, fontSize = 11.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { activeInfoDialog = "help" },
                        border = BorderStroke(1.dp, BydColors.BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isArabic) "حل المشاكل" else "Help", color = BydColors.TextPrimary, fontSize = 11.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { activeInfoDialog = "privacy" },
                        border = BorderStroke(1.dp, BydColors.BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isArabic) "الخصوصية" else "Privacy", color = BydColors.TextPrimary, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    activeInfoDialog?.let { kind ->
        AlertDialog(
            onDismissRequest = { activeInfoDialog = null },
            containerColor = BydColors.SurfaceCard,
            title = {
                Text(
                    text = when (kind) {
                        "compat" -> if (isArabic) "توافق السيارات الصينية" else "Vehicle Compatibility"
                        "help" -> if (isArabic) "دليل حل المشاكل" else "Troubleshooting"
                        else -> if (isArabic) "الخصوصية والأمان" else "Privacy & Security"
                    },
                    color = BydColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val body = when (kind) {
                    "compat" -> if (isArabic) {
                        "• BYD DiLink 3.0 / 4.0 / 5.0 (شاشات أندرويد المفتوحة): يتطلب تثبيت BYD Receiver.\n• MG / Chery / Geely / Changan: يعمل إذا سمحت الشاشة بتثبيت APK أو عبر صندوق HDMI/Carlinkit أندرويد خارجي.\n• الشاشات المغلقة بدون تثبيت: تحتاج مستقبل خارجي متصل بالشاشة."
                    } else {
                        "• BYD DiLink 3/4/5 (open Android): requires BYD Receiver APK.\n• MG / Chery / Geely / Changan: supported when APK sideloading is allowed or via external Android/HDMI box."
                    }
                    "help" -> if (isArabic) {
                        "1. تأكد أن الهاتف والشاشة على نفس شبكة Wi-Fi أو نقطة الاتصال.\n2. إذا لم تظهر السيارة تلقائياً، استخدم «اتصال بـ IP يدوياً» وأدخل الـ IP الظاهر على شاشة السيارة.\n3. عند ظهور نافذة «بدء التسجيل أو الإرسال»، اضغط «البدء الآن».\n4. في أندرويد 14+: إذا حُظر التثبيت، فعّل التثبيت من مصادر غير معروفة."
                    } else {
                        "1. Ensure phone and car are on the same Wi-Fi/hotspot.\n2. Use Connect by IP if auto-discovery is blocked.\n3. Tap Start Now on the screen capture prompt."
                    }
                    else -> if (isArabic) {
                        "• لا يجمع التطبيق أي بيانات ولا يرسل أي شيء للإنترنت أو السحابة.\n• جميع إطارات الشاشة والصوت مشفرة محلياً بين الهاتف والسيارة فقط عبر ECDH P-256 + AES-256-GCM.\n• يمكنك مسح جميع السيارات المحفوظة في أي وقت."
                    } else {
                        "• Zero cloud telemetry or analytics.\n• Screen & audio frames stay strictly on local Wi-Fi, encrypted with ECDH P-256 + AES-256-GCM."
                    }
                }
                Text(body, color = BydColors.TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            },
            confirmButton = {
                if (kind == "privacy" && trustedVehicles.isNotEmpty()) {
                    TextButton(onClick = {
                        viewModel.clearAllTrustedVehicles()
                        activeInfoDialog = null
                    }) {
                        Text(if (isArabic) "مسح السيارات المحفوظة" else "Clear Saved Vehicles", color = BydColors.DangerRed)
                    }
                }
                Button(
                    onClick = { activeInfoDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BydColors.EvCyan)
                ) {
                    Text(if (isArabic) "حسناً" else "OK", color = Color(0xFF041016), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Ultra-Clean Main Screen
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BydColors.ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Minimal Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "شاشة BYD" else "BYD Mirror",
                    color = BydColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(state, isArabic)
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BydColors.SurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = if (isArabic) "الإعدادات" else "Settings",
                            tint = BydColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Clean One-Tap Hero Card
        item {
            val isStreaming = state is ConnectionState.Streaming
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
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        (if (isStreaming) BydColors.EvEmerald else BydColors.EvCyan).copy(alpha = 0.28f),
                                        BydColors.SurfaceElevated
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cast,
                            contentDescription = null,
                            tint = if (isStreaming) BydColors.EvEmerald else BydColors.EvCyan,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Text(
                        text = statusHeadline(state, isArabic),
                        color = BydColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    if (isStreaming) {
                        DiagnosticsHudRow(telemetry = telemetry, isArabic = isArabic)
                    }

                    Button(
                        onClick = {
                            if (isStreaming) viewModel.disconnect() else viewModel.onOneTapConnect()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStreaming) BydColors.DangerRed else BydColors.EvCyan
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = if (isStreaming) {
                                if (isArabic) "إيقاف البث" else "Stop Mirroring"
                            } else {
                                if (isArabic) "اتصال بضغطة واحدة" else "One-Tap Connect"
                            },
                            color = if (isStreaming) Color.White else Color(0xFF041016),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (isStreaming) {
                        OutlinedButton(
                            onClick = { viewModel.toggleOrientation() },
                            border = BorderStroke(1.dp, BydColors.BorderSubtle),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ScreenRotation,
                                contentDescription = null,
                                tint = BydColors.EvCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = if (orientation == DisplayOrientation.LANDSCAPE) {
                                    if (isArabic) "الوضع الحالي: أفقي (اضغط للتدوير)" else "Landscape (Tap to Rotate)"
                                } else {
                                    if (isArabic) "الوضع الحالي: عمودي (اضغط للتدوير)" else "Portrait (Tap to Rotate)"
                                },
                                color = BydColors.TextPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Discovered BYD Screens (or calm 1-step guidance + manual IP shortcut)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "شاشات BYD المتاحة (${devices.size})" else "Available Screens (${devices.size})",
                    color = BydColors.TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
                TextButton(onClick = { showManualIpDialog = true }) {
                    Text(
                        text = if (isArabic) "اتصال بـ IP يدوياً" else "Connect by IP",
                        color = BydColors.EvCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (devices.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BydColors.SurfaceCard),
                    border = BorderStroke(1.dp, BydColors.BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isArabic) {
                            "1. افتح تطبيق BYD Receiver على شاشة السيارة.\n2. تأكد أن الهاتف والسيارة على نفس الشبكة أو نقطة الاتصال، ثم اضغط «اتصال بضغطة واحدة»."
                        } else {
                            "1. Open BYD Receiver on your car screen.\n2. Connect phone & car to the same Wi-Fi/Hotspot, then tap One-Tap Connect."
                        },
                        color = BydColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(devices, key = { it.id }) { device ->
                DeviceRow(
                    device = device,
                    isTrusted = viewModel.isTrustedDevice(device.id),
                    isArabic = isArabic,
                    onClick = { viewModel.connectToDevice(device) }
                )
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
        is ConnectionState.Discovering -> (if (isArabic) "جاري البحث" else "Scanning") to BydColors.EvCyan
        is ConnectionState.Handshaking,
        is ConnectionState.KeyExchange,
        is ConnectionState.NegotiatingQuality -> (if (isArabic) "جاري الربط" else "Securing") to BydColors.WarningAmber
        is ConnectionState.Streaming -> (if (isArabic) "متصل" else "Live") to BydColors.EvEmerald
        is ConnectionState.Reconnecting -> (if (isArabic) "إعادة الربط" else "Reconnecting") to BydColors.WarningAmber
        is ConnectionState.Error -> (if (isArabic) "خطأ" else "Error") to BydColors.DangerRed
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun DeviceRow(device: BydDevice, isTrusted: Boolean, isArabic: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = BydColors.SurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                            text = if (isArabic) "محفوظة" else "Trusted",
                            color = BydColors.EvEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    "${device.modelName} • ${device.hostAddress}",
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
    is ConnectionState.Idle -> if (isArabic) "جاهز للربط مع شاشة BYD" else "Ready to mirror to BYD screen"
    is ConnectionState.Discovering -> if (isArabic) "جاري البحث عن شاشة السيارة..." else "Searching for your BYD screen..."
    is ConnectionState.Handshaking -> if (isArabic) "جاري تأمين الاتصال مع ${state.device.displayName}..." else "Securing connection to ${state.device.displayName}..."
    is ConnectionState.KeyExchange -> if (isArabic) "جاري تأمين الاتصال مع ${state.device.displayName}..." else "Securing connection to ${state.device.displayName}..."
    is ConnectionState.NegotiatingQuality -> if (isArabic) "جاري تأمين الاتصال مع ${state.device.displayName}..." else "Securing connection to ${state.device.displayName}..."
    is ConnectionState.Streaming -> if (isArabic) "الشاشة تبث الآن إلى ${state.device.displayName}" else "Mirroring live to ${state.device.displayName}"
    is ConnectionState.Reconnecting -> if (isArabic) "إعادة الاتصال بالسيارة (محاولة ${state.attempt})..." else "Reconnecting (Attempt ${state.attempt})..."
    is ConnectionState.Error -> state.message
}
