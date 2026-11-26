package app.drivelink.cast.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drivelink.cast.core.model.ConnectionState
import app.drivelink.cast.ui.components.BydHeroVehicleStage
import app.drivelink.cast.ui.components.BydSurfaceCard
import app.drivelink.cast.ui.theme.BydColors
import app.drivelink.cast.ui.theme.BydGradients

@Composable
fun ConnectingScreen(
    state: ConnectionState,
    isArabic: Boolean,
    onCancel: () -> Unit
) {
    val activeStep = when (state) {
        is ConnectionState.Discovering -> 0
        is ConnectionState.Handshaking, is ConnectionState.Reconnecting -> 1
        is ConnectionState.KeyExchange -> 2
        is ConnectionState.NegotiatingQuality -> 3
        is ConnectionState.Streaming -> 4
        else -> 0
    }

    val deviceName = when (state) {
        is ConnectionState.Handshaking -> state.device.displayName
        is ConnectionState.KeyExchange -> state.device.displayName
        is ConnectionState.NegotiatingQuality -> state.device.displayName
        is ConnectionState.Reconnecting -> state.device.displayName
        else -> if (isArabic) "شاشة BYD DiLink" else "BYD DiLink Display"
    }

    val stages = if (isArabic) {
        listOf(
            "البحث عن شاشة السيارة",
            "تأسيس قناة الاتصال TCP/UDP",
            "تأمين المفاتيح (ECDH P-256)",
            "تجهيز جودة البث والترميز"
        )
    } else {
        listOf(
            "Discovering Device",
            "Establishing Connection",
            "Negotiating Session (ECDH P-256)",
            "Preparing Stream"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BydGradients.DeepNavyBackdrop)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Arrangement.SpaceBetween)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(BydColors.Primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = BydColors.Primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            BydHeroVehicleStage(
                accentColor = BydColors.Primary,
                isPulsing = true
            )

            Text(
                text = if (state is ConnectionState.Reconnecting) {
                    if (isArabic) "إعادة الاتصال (محاولة ${state.attempt})..." else "Reconnecting (Attempt ${state.attempt})..."
                } else {
                    if (isArabic) "جاري الاتصال..." else "Connecting..."
                },
                color = BydColors.PrimaryText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = deviceName,
                color = BydColors.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = if (isArabic) {
                    "يرجى الانتظار بينما ننشئ اتصالاً لاسلكياً مشفراً"
                } else {
                    "Please wait while we establish a secure wireless connection"
                },
                color = BydColors.SecondaryText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        // Authentic Protocol Stage List (No fabricated percentages)
        BydSurfaceCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                stages.forEachIndexed { index, label ->
                    val done = index < activeStep
                    val current = index == activeStep
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when {
                            done -> Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = BydColors.Success,
                                modifier = Modifier.size(20.dp)
                            )
                            current -> CircularProgressIndicator(
                                color = BydColors.Primary,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            else -> Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(BydColors.Border)
                            )
                        }
                        Text(
                            text = label,
                            color = when {
                                done -> BydColors.Success
                                current -> BydColors.PrimaryText
                                else -> BydColors.Disabled
                            },
                            fontSize = 13.sp,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onCancel,
            border = BorderStroke(1.dp, BydColors.Border),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = BydColors.Error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                text = if (isArabic) "إلغاء الاتصال" else "Cancel",
                color = BydColors.PrimaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
