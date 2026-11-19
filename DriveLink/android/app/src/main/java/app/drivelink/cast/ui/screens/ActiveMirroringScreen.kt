package app.drivelink.cast.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drivelink.cast.BuildConfig
import app.drivelink.cast.core.model.ConnectionState
import app.drivelink.cast.core.model.DisplayOrientation
import app.drivelink.cast.core.model.TelemetryMetrics
import app.drivelink.cast.ui.components.BydHeroVehicleStage
import app.drivelink.cast.ui.components.BydSurfaceCard
import app.drivelink.cast.ui.theme.BydColors
import app.drivelink.cast.ui.theme.BydGradients
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ActiveMirroringScreen(
    state: ConnectionState.Streaming,
    telemetry: TelemetryMetrics,
    isArabic: Boolean,
    onToggleOrientation: () -> Unit,
    onLaunchOverlay: () -> Unit,
    onStopMirroring: () -> Unit,
    onReturnHome: () -> Unit
) {
    var elapsedSeconds by remember(state.device.id, state.encryptedChannelFingerprint) {
        mutableLongStateOf(0L)
    }
    LaunchedEffect(state.device.id, state.encryptedChannelFingerprint) {
        while (true) {
            delay(1_000L)
            elapsedSeconds += 1L
        }
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val secs = elapsedSeconds % 60
    val timerText = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)

    val (w, h) = state.profile.dimensionsFor(state.orientation)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BydGradients.DeepNavyBackdrop)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onReturnHome,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BydColors.ElevatedSurface)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = if (isArabic) "رجوع" else "Home",
                        tint = BydColors.PrimaryText
                    )
                }

                Text(
                    text = if (isArabic) "البث النشط" else "Mirroring",
                    color = BydColors.PrimaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(BydColors.Success.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(BydColors.Success)
                    )
                    Text("LIVE", color = BydColors.Success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Focused Mirroring Preview Card
        item {
            BydHeroVehicleStage(
                accentColor = BydColors.Success,
                isPulsing = true
            )
        }

        // Session Identity & Real Elapsed Time
        item {
            BydSurfaceCard(
                elevated = true,
                borderColor = BydColors.Success.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isArabic) "متصل بـ ${state.device.displayName}" else "Connected to ${state.device.displayName}",
                        color = BydColors.PrimaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timerText,
                        color = BydColors.Primary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = BydColors.Success, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${w}x${h} • ${state.profile.codec.name} • Key ${state.encryptedChannelFingerprint}",
                            color = BydColors.SecondaryText,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Real Telemetry Metrics Grid
        item {
            BydSurfaceCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricItem(label = "FPS", value = "${telemetry.actualFps}", color = BydColors.Success)
                    MetricItem(label = if (isArabic) "التأخير" else "RTT", value = "${telemetry.rttMs} ms", color = BydColors.Primary)
                    MetricItem(
                        label = if (isArabic) "السرعة" else "Bitrate",
                        value = String.format(Locale.US, "%.1fM", telemetry.currentBitrateMbps),
                        color = BydColors.PrimaryText
                    )
                    MetricItem(
                        label = if (isArabic) "الفقد" else "Loss",
                        value = String.format(Locale.US, "%.1f%%", telemetry.packetLossPercent),
                        color = if (telemetry.packetLossPercent > 2f) BydColors.Warning else BydColors.Success
                    )
                }
            }
        }

        // Real Supported Controls (Orientation, Overlay on fullTouch, Stop)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleOrientation,
                        border = BorderStroke(1.dp, BydColors.Border),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Rounded.ScreenRotation, contentDescription = null, tint = BydColors.Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = if (state.orientation == DisplayOrientation.LANDSCAPE) {
                                if (isArabic) "أفقي" else "Landscape"
                            } else {
                                if (isArabic) "عمودي" else "Portrait"
                            },
                            color = BydColors.PrimaryText,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) {
                        OutlinedButton(
                            onClick = onLaunchOverlay,
                            border = BorderStroke(1.dp, BydColors.Border),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Icon(Icons.Rounded.Layers, contentDescription = null, tint = BydColors.Primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = if (isArabic) "تحكم عائم" else "Overlay",
                                color = BydColors.PrimaryText,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Button(
                    onClick = onStopMirroring,
                    colors = ButtonDefaults.buttonColors(containerColor = BydColors.Error),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (isArabic) "إيقاف البث" else "Stop Mirroring",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onReturnHome,
                    border = BorderStroke(1.dp, BydColors.Border),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = if (isArabic) "العودة للرئيسية" else "Return to Home",
                        color = BydColors.SecondaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = BydColors.SecondaryText, fontSize = 11.sp)
    }
}
