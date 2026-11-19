package app.drivelink.cast.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drivelink.cast.BuildConfig
import app.drivelink.cast.core.model.DisplayOrientation
import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.model.VideoCodec
import app.drivelink.cast.core.security.TrustedVehicle
import app.drivelink.cast.ui.components.BydSurfaceCard
import app.drivelink.cast.ui.theme.BydColors
import app.drivelink.cast.ui.theme.BydGradients

@Composable
fun SettingsScreen(
    orientation: DisplayOrientation,
    preferredCodec: VideoCodec,
    profileOverride: DisplayProfile?,
    autoConnect: Boolean,
    trustedVehicles: List<TrustedVehicle>,
    isArabic: Boolean,
    onToggleOrientation: () -> Unit,
    onSelectCodec: (VideoCodec) -> Unit,
    onSelectProfileOverride: (DisplayProfile?) -> Unit,
    onSetAutoConnect: (Boolean) -> Unit,
    onLaunchOverlay: () -> Unit,
    onRemoveTrusted: (String) -> Unit,
    onClearTrusted: () -> Unit,
    onOpenManualIp: () -> Unit,
    onOpenAbout: () -> Unit
) {
    var activeDialog by remember { mutableStateOf<String?>(null) }

    activeDialog?.let { kind ->
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            containerColor = BydColors.Surface,
            title = {
                Text(
                    text = when (kind) {
                        "compat" -> if (isArabic) "توافق السيارات" else "Vehicle Compatibility"
                        "help" -> if (isArabic) "المساعدة وحل المشاكل" else "Help & Troubleshooting"
                        else -> if (isArabic) "الخصوصية والأمان" else "Privacy & Security"
                    },
                    color = BydColors.PrimaryText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val body = when (kind) {
                    "compat" -> if (isArabic) {
                        "• BYD DiLink 3.0 / 4.0 / 5.0 (أندرويد): يدعم تثبيت تطبيق BYD Receiver مباشرة.\n• MG / Chery / Geely / Changan: يعمل عند السماح بتثبيت APK أو عبر صندوق أندرويد خارجي.\n• الشاشات المغلقة: تتطلب مستقبل أندرويد خارجي."
                    } else {
                        "• BYD DiLink 3/4/5 (Android): installs BYD Receiver APK directly.\n• MG / Chery / Geely / Changan: supported when APK sideloading is permitted or via external Android/HDMI box."
                    }
                    "help" -> if (isArabic) {
                        "1. اربط الهاتف وشاشة BYD على نفس شبكة Wi-Fi أو نقطة الاتصال.\n2. إذا لم يظهر الجهاز تلقائياً، اختر «اتصال بـ IP يدوياً» من تبويب الأجهزة.\n3. عند ظهور نافذة «بدء التسجيل أو الإرسال»، اضغط «البدء الآن»."
                    } else {
                        "1. Connect phone and car to the same Wi-Fi/Hotspot.\n2. Use Manual IP if auto-discovery is filtered by the hotspot.\n3. Grant Screen Capture when prompted."
                    }
                    else -> if (isArabic) {
                        "• لا يجمع التطبيق أي بيانات أو إحصائيات سحابية.\n• جميع إطارات الشاشة والصوت مشفرة محلياً عبر ECDH P-256 + AES-256-GCM.\n• يمكنك مسح جميع السيارات الموثوقة المحفوظة في أي وقت."
                    } else {
                        "• Zero cloud telemetry or external analytics.\n• All video and audio packets stay on local Wi-Fi, encrypted with ECDH P-256 + AES-256-GCM."
                    }
                }
                Text(body, color = BydColors.SecondaryText, fontSize = 13.sp, lineHeight = 20.sp)
            },
            confirmButton = {
                if (kind == "privacy" && trustedVehicles.isNotEmpty()) {
                    TextButton(onClick = {
                        onClearTrusted()
                        activeDialog = null
                    }) {
                        Text(if (isArabic) "مسح السيارات" else "Clear Vehicles", color = BydColors.Error)
                    }
                }
                Button(
                    onClick = { activeDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BydColors.Primary)
                ) {
                    Text(if (isArabic) "حسناً" else "OK", color = Color(0xFF041016), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BydGradients.DeepNavyBackdrop),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = if (isArabic) "الإعدادات" else "Settings",
                    color = BydColors.PrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isArabic) "تخصيص العرض، الصوت، الاتصال والأمان" else "Display, audio, connection & security controls",
                    color = BydColors.SecondaryText,
                    fontSize = 12.sp
                )
            }
        }

        // Category 1: Display
        item {
            SectionHeader(icon = Icons.Rounded.Tune, title = if (isArabic) "العرض والشاشة" else "Display")
        }
        item {
            BydSurfaceCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (isArabic) "حجم شاشة السيارة (الدقة)" else "Car Display Resolution",
                        color = BydColors.PrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val profiles = listOf(
                            null to (if (isArabic) "تلقائي" else "Auto"),
                            DisplayProfile.DILINK_15_6_FHD to "15.6\" FHD",
                            DisplayProfile.DILINK_12_8_FHD to "12.8\"",
                            DisplayProfile.DILINK_10_1_HD to "10.1\" HD"
                        )
                        profiles.forEach { (itemProfile, label) ->
                            val selected = (itemProfile == null && profileOverride == null) ||
                                (itemProfile != null && profileOverride?.id == itemProfile.id)
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectProfileOverride(itemProfile) },
                                label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BydColors.Primary.copy(alpha = 0.2f),
                                    selectedLabelColor = BydColors.Primary,
                                    labelColor = BydColors.SecondaryText
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = BydColors.Border)

                    Text(
                        text = if (isArabic) "ترميز الفيديو" else "Video Codec",
                        color = BydColors.PrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(VideoCodec.H265 to "H.265 (HEVC)", VideoCodec.H264 to "H.264 (AVC)").forEach { (codec, label) ->
                            FilterChip(
                                selected = preferredCodec == codec,
                                onClick = { onSelectCodec(codec) },
                                label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BydColors.Success.copy(alpha = 0.2f),
                                    selectedLabelColor = BydColors.Success,
                                    labelColor = BydColors.SecondaryText
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = BydColors.Border)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleOrientation)
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.ScreenRotation, contentDescription = null, tint = BydColors.Primary)
                            Column {
                                Text(
                                    text = if (isArabic) "اتجاه العرض الافتراضي" else "Display Orientation",
                                    color = BydColors.PrimaryText,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (orientation == DisplayOrientation.LANDSCAPE) {
                                        if (isArabic) "أفقي (16:9)" else "Landscape (16:9)"
                                    } else {
                                        if (isArabic) "عمودي (9:16)" else "Portrait (9:16)"
                                    },
                                    color = BydColors.Primary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Text(
                            text = if (isArabic) "تبديل" else "Switch",
                            color = BydColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    HorizontalDivider(color = BydColors.Border)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = BuildConfig.ENABLE_OVERLAY_AND_TOUCH, onClick = onLaunchOverlay)
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Rounded.Layers,
                                contentDescription = null,
                                tint = if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) BydColors.Primary else BydColors.Disabled
                            )
                            Column {
                                Text(
                                    text = if (isArabic) "زر التحكم العائم واللمس" else "Floating Control & Reverse Touch",
                                    color = if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) BydColors.PrimaryText else BydColors.Disabled,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) {
                                        if (isArabic) "اضغط لفتح نافذة التحكم العائمة" else "Tap to launch floating overlay"
                                    } else {
                                        if (isArabic) "غير مفعّل في النسخة القياسية (Standard)" else "Unavailable in Standard build"
                                    },
                                    color = BydColors.SecondaryText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Category 2: Audio
        item {
            SectionHeader(icon = Icons.Rounded.VolumeUp, title = if (isArabic) "الصوت" else "Audio")
        }
        item {
            BydSurfaceCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = BydColors.Success)
                    Column {
                        Text(
                            text = if (isArabic) "بث صوت النظام عبر القناة المشفرة" else "System Audio Routing",
                            color = BydColors.PrimaryText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isArabic) {
                                "AAC-LC 48kHz Stereo مدمج تلقائياً مع البث؛ التحكم بالكتم متاح من شاشة المستقبل في السيارة."
                            } else {
                                "AAC-LC 48kHz Stereo streams automatically to the car receiver; mute control is available on the BYD Receiver HMI."
                            },
                            color = BydColors.SecondaryText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Category 3: Connection
        item {
            SectionHeader(icon = Icons.Rounded.Wifi, title = if (isArabic) "الاتصال والاقتران" else "Connection")
        }
        item {
            BydSurfaceCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) "الربط التلقائي" else "Auto Reconnect",
                                color = BydColors.PrimaryText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isArabic) "اتصال تلقائي عند الاقتراب من واي فاي السيارة" else "Auto-link in BYD Wi-Fi range",
                                color = BydColors.SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = autoConnect,
                            onCheckedChange = onSetAutoConnect,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BydColors.Primary,
                                checkedTrackColor = BydColors.Primary.copy(alpha = 0.35f)
                            )
                        )
                    }

                    HorizontalDivider(color = BydColors.Border)

                    OutlinedButton(
                        onClick = onOpenManualIp,
                        border = BorderStroke(1.dp, BydColors.Border),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Text(
                            text = if (isArabic) "إدخال عنوان IP يدوياً" else "Connect Manually by IP",
                            color = BydColors.Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (trustedVehicles.isNotEmpty()) {
                        HorizontalDivider(color = BydColors.Border)
                        Text(
                            text = if (isArabic) "السيارات المحفوظة (${trustedVehicles.size})" else "Saved Vehicles (${trustedVehicles.size})",
                            color = BydColors.SecondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        trustedVehicles.forEach { vehicle ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BydColors.ElevatedSurface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(vehicle.displayName, color = BydColors.PrimaryText, fontWeight = FontWeight.Medium)
                                    Text("Fingerprint: ${vehicle.fingerprint}", color = BydColors.Success, fontSize = 11.sp)
                                }
                                IconButton(onClick = { onRemoveTrusted(vehicle.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = BydColors.Error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category 4: Privacy, Security & General
        item {
            SectionHeader(icon = Icons.Rounded.Security, title = if (isArabic) "الخصوصية وعام" else "Privacy & General")
        }
        item {
            BydSurfaceCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsNavRow(
                        icon = Icons.Rounded.Security,
                        title = if (isArabic) "الخصوصية والتشفير" else "Privacy & Security",
                        subtitle = "ECDH P-256 + AES-256-GCM",
                        tint = BydColors.Success,
                        onClick = { activeDialog = "privacy" }
                    )
                    HorizontalDivider(color = BydColors.Border)
                    SettingsNavRow(
                        icon = Icons.Rounded.DirectionsCar,
                        title = if (isArabic) "توافق السيارات" else "Vehicle Compatibility",
                        subtitle = "BYD DiLink 3/4/5 • MG • Chery • Geely",
                        tint = BydColors.Primary,
                        onClick = { activeDialog = "compat" }
                    )
                    HorizontalDivider(color = BydColors.Border)
                    SettingsNavRow(
                        icon = Icons.Rounded.HelpOutline,
                        title = if (isArabic) "المساعدة وحل المشاكل" else "Help & Troubleshooting",
                        subtitle = if (isArabic) "خطوات الربط وحل الانقطاع" else "Setup & connectivity guide",
                        tint = BydColors.Warning,
                        onClick = { activeDialog = "help" }
                    )
                    HorizontalDivider(color = BydColors.Border)
                    SettingsNavRow(
                        icon = Icons.Rounded.Info,
                        title = if (isArabic) "حول التطبيق والتراخيص" else "About & Open Source Licenses",
                        subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        tint = BydColors.Primary,
                        onClick = onOpenAbout
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = BydColors.Primary, modifier = Modifier.size(17.dp))
        Text(title, color = BydColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(title, color = BydColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = BydColors.SecondaryText, fontSize = 12.sp)
            }
        }
        Text(">", color = BydColors.SecondaryText, fontWeight = FontWeight.Bold)
    }
}
