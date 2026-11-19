package com.byd.mirror.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.mirror.R
import com.byd.mirror.core.model.ConnectionState
import com.byd.mirror.core.session.PairingPrompt
import com.byd.mirror.ui.theme.BydColors
import com.byd.mirror.ui.theme.BydGradients

enum class MirrorTab {
    HOME,
    DEVICES,
    SETTINGS,
    ABOUT
}

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isAnimatorDisabled(context) }
}

private fun isAnimatorDisabled(context: Context): Boolean {
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)
}

@Composable
fun BydTopBar(
    state: ConnectionState,
    isArabic: Boolean,
    onToggleLanguage: () -> Unit,
    onOpenDevices: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.byd_app_icon_preview),
                contentDescription = "BYD Wireless Mirror",
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, BydColors.Border, RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Column {
                Text(
                    text = "BYD Wireless Mirror",
                    color = BydColors.PrimaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isArabic) "اتصال لاسلكي مؤمّن بالسيارة" else "Connect your phone to your BYD",
                    color = BydColors.SecondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BydStatusBadge(state = state, isArabic = isArabic)

            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BydColors.ElevatedSurface)
                    .border(1.dp, BydColors.Border, RoundedCornerShape(50))
                    .clickable(onClick = onToggleLanguage)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isArabic) "EN" else "AR",
                    color = BydColors.Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onOpenDevices,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BydColors.ElevatedSurface)
                    .border(1.dp, BydColors.Border, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DirectionsCar,
                    contentDescription = if (isArabic) "الأجهزة" else "Devices",
                    tint = BydColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun BydStatusBadge(state: ConnectionState, isArabic: Boolean) {
    val (label, color) = when (state) {
        is ConnectionState.Idle -> (if (isArabic) "غير متصل" else "Ready") to BydColors.SecondaryText
        is ConnectionState.Discovering -> (if (isArabic) "بحث" else "Scanning") to BydColors.Primary
        is ConnectionState.Handshaking,
        is ConnectionState.KeyExchange,
        is ConnectionState.NegotiatingQuality -> (if (isArabic) "توصيل" else "Connecting") to BydColors.Warning
        is ConnectionState.Streaming -> (if (isArabic) "متصل" else "Live") to BydColors.Success
        is ConnectionState.Reconnecting -> (if (isArabic) "إعادة ربط" else "Retry") to BydColors.Warning
        is ConnectionState.Error -> (if (isArabic) "خطأ" else "Failed") to BydColors.Error
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun BydBottomNavBar(
    currentTab: MirrorTab,
    isArabic: Boolean,
    onSelectTab: (MirrorTab) -> Unit
) {
    val items = listOf(
        Triple(MirrorTab.HOME, Icons.Rounded.Home, if (isArabic) "الرئيسية" else "Home"),
        Triple(MirrorTab.DEVICES, Icons.Rounded.DirectionsCar, if (isArabic) "الأجهزة" else "Devices"),
        Triple(MirrorTab.SETTINGS, Icons.Rounded.Settings, if (isArabic) "الإعدادات" else "Settings"),
        Triple(MirrorTab.ABOUT, Icons.Rounded.Info, if (isArabic) "حول" else "About")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BydColors.PrimaryBackground)
    ) {
        HorizontalDivider(color = BydColors.Border, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (tab, icon, label) ->
                val selected = tab == currentTab
                val tint = if (selected) BydColors.Primary else BydColors.SecondaryText
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) BydColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelectTab(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
fun BydSurfaceCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    borderColor: Color = BydColors.Border,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val container = if (elevated) BydColors.ElevatedSurface else BydColors.Surface
    if (onClick != null) {
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = container),
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(20.dp),
            modifier = modifier.fillMaxWidth(),
            content = content
        )
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = container),
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(20.dp),
            modifier = modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
fun BydHeroVehicleStage(
    accentColor: Color,
    isPulsing: Boolean,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val pulseProgress = if (isPulsing && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "wireless_pulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_expand"
        )
        value
    } else {
        0.35f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(172.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(BydColors.Background)
            .border(1.dp, BydColors.Border, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height * 0.56f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = 0.28f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.72f
                ),
                radius = size.minDimension * 0.72f,
                center = center
            )
            val baseR = size.minDimension * 0.25f
            val ringRadius = baseR + (size.minDimension * 0.36f * pulseProgress)
            drawCircle(
                color = accentColor.copy(alpha = (1f - pulseProgress) * 0.45f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Image(
            painter = painterResource(id = R.drawable.byd_hero_vehicle),
            contentDescription = "BYD Vehicle",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(22.dp))
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            BydColors.Background.copy(alpha = 0.72f)
                        )
                    )
                )
        )
    }
}

@Composable
fun SignalStrengthBars(dbm: Int?, accent: Color) {
    val activeBars = when {
        dbm == null || dbm == 0 -> 0
        dbm >= -55 -> 4
        dbm >= -67 -> 3
        dbm >= -78 -> 2
        else -> 1
    }
    if (activeBars == 0) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..4) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = (6 + i * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= activeBars) accent else BydColors.Border)
            )
        }
    }
}

@Composable
fun BydPinVerificationDialog(
    prompt: PairingPrompt,
    isArabic: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        containerColor = BydColors.Surface,
        title = {
            Text(
                text = if (isArabic) "تأكيد الاقتران الآمن" else "Verify Secure Pairing",
                color = BydColors.PrimaryText,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isArabic) {
                        "تأكد من تطابق رمز التحقق على شاشة ${prompt.device.displayName}:"
                    } else {
                        "Confirm this PIN matches ${prompt.device.displayName}:"
                    },
                    color = BydColors.SecondaryText,
                    fontSize = 14.sp
                )
                Text(
                    text = prompt.pin,
                    color = BydColors.Primary,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
                Text(
                    text = "Fingerprint: ${prompt.fingerprint}",
                    color = BydColors.Success,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BydColors.Primary)
            ) {
                Text(
                    text = if (isArabic) "تأكيد وحفظ" else "Trust Vehicle",
                    color = Color(0xFF041016),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text(
                    text = if (isArabic) "إلغاء" else "Cancel",
                    color = BydColors.Error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
fun BydManualIpDialog(
    initialIp: String,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    val ipState = remember(initialIp) { androidx.compose.runtime.mutableStateOf(initialIp) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BydColors.Surface,
        title = {
            Text(
                text = if (isArabic) "الاتصال برقم IP" else "Connect by IP",
                color = BydColors.PrimaryText,
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
                    color = BydColors.SecondaryText,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = ipState.value,
                    onValueChange = { ipState.value = it },
                    placeholder = { Text("192.168.43.1", color = BydColors.Disabled) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BydColors.PrimaryText,
                        unfocusedTextColor = BydColors.PrimaryText,
                        focusedBorderColor = BydColors.Primary,
                        unfocusedBorderColor = BydColors.Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConnect(ipState.value) },
                enabled = ipState.value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BydColors.Primary)
            ) {
                Text(
                    text = if (isArabic) "ربط الآن" else "Connect",
                    color = Color(0xFF041016),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isArabic) "إلغاء" else "Cancel", color = BydColors.SecondaryText)
            }
        }
    )
}
