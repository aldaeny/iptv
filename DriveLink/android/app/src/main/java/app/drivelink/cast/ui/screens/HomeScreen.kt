package app.drivelink.cast.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.ConnectionState
import app.drivelink.cast.core.model.TelemetryMetrics
import app.drivelink.cast.ui.components.BydHeroVehicleStage
import app.drivelink.cast.ui.components.BydSurfaceCard
import app.drivelink.cast.ui.theme.BydColors
import app.drivelink.cast.ui.theme.BydGradients
import java.util.Locale

@Composable
fun HomeScreen(
    state: ConnectionState,
    discoveredDevices: List<BydDevice>,
    telemetry: TelemetryMetrics,
    isArabic: Boolean,
    onStartMirroring: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenActiveMirror: () -> Unit,
    onOpenManualIp: () -> Unit
) {
    val isStreaming = state is ConnectionState.Streaming
    val isBusy = state is ConnectionState.Discovering ||
        state is ConnectionState.Handshaking ||
        state is ConnectionState.KeyExchange ||
        state is ConnectionState.NegotiatingQuality ||
        state is ConnectionState.Reconnecting

    val accentColor = when (state) {
        is ConnectionState.Streaming -> BydColors.Success
        is ConnectionState.Error -> BydColors.Error
        is ConnectionState.Handshaking,
        is ConnectionState.KeyExchange,
        is ConnectionState.NegotiatingQuality,
        is ConnectionState.Reconnecting -> BydColors.Warning
        else -> BydColors.Primary
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BydGradients.DeepNavyBackdrop),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Automotive Hero Vehicle Showcase
        item {
            BydHeroVehicleStage(
                accentColor = accentColor,
                isPulsing = isBusy || isStreaming
            )
        }

        // Primary Mirroring Control Card with Prominent CTA
        item {
            BydSurfaceCard(
                borderColor = accentColor.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = homeHeadline(state, isArabic),
                        color = BydColors.PrimaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = homeSubtitle(state, discoveredDevices.size, isArabic),
                        color = BydColors.SecondaryText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )

                    // Circular Focal Action Ring + Primary CTA
                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.30f),
                                        BydColors.ElevatedSurface
                                    )
                                )
                            )
                            .border(
                                width = 2.dp,
                                brush = if (isStreaming) {
                                    Brush.linearGradient(listOf(BydColors.Success, BydColors.Primary))
                                } else {
                                    BydGradients.ElectricBlueToCyan
                                },
                                shape = CircleShape
                            )
                            .clickable(enabled = !isBusy) {
                                if (isStreaming) onOpenActiveMirror() else onStartMirroring()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isStreaming -> Icons.Rounded.CheckCircle
                                    state is ConnectionState.Error -> Icons.Rounded.ErrorOutline
                                    else -> Icons.Rounded.Cast
                                },
                                contentDescription = null,
                                tint = if (isStreaming) BydColors.Success else BydColors.PrimaryText,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = when {
                                    isStreaming -> if (isArabic) "عرض البث" else "Live View"
                                    isBusy -> if (isArabic) "جاري الربط" else "Connecting"
                                    else -> if (isArabic) "ابدأ البث" else "Start Mirroring"
                                },
                                color = BydColors.PrimaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    // Primary Full-Width CTA Button
                    Button(
                        onClick = {
                            if (isStreaming) onOpenActiveMirror() else onStartMirroring()
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStreaming) BydColors.Success else BydColors.Primary,
                            disabledContainerColor = BydColors.Disabled.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Icon(
                            imageVector = if (isStreaming) Icons.Rounded.Stop else Icons.Rounded.Cast,
                            contentDescription = null,
                            tint = Color(0xFF041016),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = when {
                                isStreaming -> if (isArabic) "إدارة البث النشط" else "Open Active Mirroring"
                                isBusy -> if (isArabic) "جاري الاتصال..." else "Connecting..."
                                else -> if (isArabic) "بدء مشاركة الشاشة" else "START MIRRORING"
                            },
                            color = Color(0xFF041016),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Secondary Action: Find Devices
                    OutlinedButton(
                        onClick = onOpenDevices,
                        border = BorderStroke(1.dp, BydColors.Border),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = BydColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = if (isArabic) {
                                "البحث عن الأجهزة (${discoveredDevices.size})"
                            } else {
                                "Find Devices (${discoveredDevices.size})"
                            },
                            color = BydColors.PrimaryText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Connected Device Card (Only when actually streaming)
        if (state is ConnectionState.Streaming) {
            item {
                BydSurfaceCard(
                    elevated = true,
                    borderColor = BydColors.Success.copy(alpha = 0.5f),
                    onClick = onOpenActiveMirror
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BydColors.Success.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DirectionsCar,
                                    contentDescription = null,
                                    tint = BydColors.Success
                                )
                            }
                            Column {
                                Text(
                                    text = state.device.displayName,
                                    color = BydColors.PrimaryText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${telemetry.actualFps} FPS • ${telemetry.rttMs} ms • " +
                                        String.format(Locale.US, "%.1f Mbps", telemetry.currentBitrateMbps),
                                    color = BydColors.Success,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Text(
                            text = if (isArabic) "التحكم" else "Controls",
                            color = BydColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            // Concise guidance card when not connected
            item {
                BydSurfaceCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "دليل التشغيل السريع" else "Quick Connection Guide",
                                color = BydColors.PrimaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isArabic) "اتصال بـ IP" else "Connect by IP",
                                color = BydColors.Primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenManualIp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = if (isArabic) {
                                "1. افتح تطبيق BYD Receiver على شاشة السيارة.\n2. اربط الهاتف والسيارة بنفس شبكة Wi-Fi أو نقطة الاتصال.\n3. اضغط «بدء مشاركة الشاشة» وأكد رمز الـ PIN."
                            } else {
                                "1. Open BYD Receiver on your car display.\n2. Join the same Wi-Fi or hotspot network.\n3. Tap START MIRRORING and confirm the 6-digit PIN."
                            },
                            color = BydColors.SecondaryText,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

private fun homeHeadline(state: ConnectionState, isArabic: Boolean): String = when (state) {
    is ConnectionState.Idle -> if (isArabic) "جاهز للربط مع شاشة BYD" else "Ready to Mirror"
    is ConnectionState.Discovering -> if (isArabic) "جاري البحث عن شاشة BYD..." else "Searching for BYD Screens..."
    is ConnectionState.Handshaking -> if (isArabic) "جاري الاتصال بـ ${state.device.displayName}" else "Connecting to ${state.device.displayName}"
    is ConnectionState.KeyExchange -> if (isArabic) "تأمين القناة المشفرة..." else "Establishing Encrypted Link..."
    is ConnectionState.NegotiatingQuality -> if (isArabic) "تهيئة جودة البث..." else "Negotiating Display Stream..."
    is ConnectionState.Streaming -> if (isArabic) "متصل الآن: ${state.device.displayName}" else "Connected to ${state.device.displayName}"
    is ConnectionState.Reconnecting -> if (isArabic) "إعادة الاتصال (محاولة ${state.attempt})" else "Reconnecting (Attempt ${state.attempt})"
    is ConnectionState.Error -> if (isArabic) "تعذر الاتصال" else "Connection Failed"
}

private fun homeSubtitle(state: ConnectionState, discoveredCount: Int, isArabic: Boolean): String = when (state) {
    is ConnectionState.Idle -> if (isArabic) {
        if (discoveredCount > 0) "تم العثور على $discoveredCount شاشة جاهزة للربط."
        else "غير متصل حالياً — تأكد من فتح المستقبل على شاشة السيارة."
    } else {
        if (discoveredCount > 0) "$discoveredCount compatible receiver(s) ready on local network."
        else "Not connected yet — open BYD Receiver on your car screen."
    }
    is ConnectionState.Streaming -> if (isArabic) {
        "قناة البث مشفرة عبر ECDH P-256 + AES-256-GCM"
    } else {
        "Live encrypted stream active (ECDH P-256 + AES-256-GCM)"
    }
    is ConnectionState.Error -> state.message
    else -> if (isArabic) "يرجى الانتظار بينما يتم تأسيس الاتصال اللاسلكي..." else "Establishing secure local wireless connection..."
}
