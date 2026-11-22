package app.drivelink.receiver.ui

import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.drivelink.receiver.R
import app.drivelink.receiver.protocol.ReceiverState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object ReceiverColors {
    val Background = Color(0xFF050914)
    val PrimaryBackground = Color(0xFF080F1C)
    val Surface = Color(0xFF101B2D)
    val ElevatedSurface = Color(0xFF15243B)
    val Border = Color(0xFF203754)
    val Primary = Color(0xFF00C8FF)
    val PrimaryBlue = Color(0xFF1677FF)
    val Success = Color(0xFF00D084)
    val Error = Color(0xFFFF4545)
    val PrimaryText = Color(0xFFF4F8FF)
    val SecondaryText = Color(0xFF9AAEC7)
}

private enum class ReceiverTab {
    HOME,
    SETTINGS,
    HELP
}

@Composable
fun ReceiverScreen(
    state: ReceiverState,
    localIps: List<String> = emptyList(),
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    onSurfaceReady: (Surface?) -> Unit,
    onTouchNormalized: (xNorm: Float, yNorm: Float, action: Byte) -> Unit = { _, _, _ -> }
) {
    var selectedTab by remember { mutableStateOf(ReceiverTab.HOME) }
    var currentTimeText by remember { mutableStateOf("--:--") }
    var elapsedSeconds by remember(state is ReceiverState.MirroringActive) { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        while (true) {
            currentTimeText = fmt.format(Date())
            delay(15_000L)
        }
    }

    LaunchedEffect(state is ReceiverState.MirroringActive) {
        if (state is ReceiverState.MirroringActive) {
            while (true) {
                delay(1_000L)
                elapsedSeconds += 1L
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ReceiverColors.Background)
    ) {
        // Persistent Video SurfaceView (always mounted so MediaCodec Surface stays valid)
        val isLandscape = (state as? ReceiverState.MirroringActive)?.isLandscape ?: true
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        setOnTouchListener { view, event ->
                            if (view.width <= 0 || view.height <= 0) return@setOnTouchListener false
                            val actionByte: Byte = when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> 0
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 1
                                MotionEvent.ACTION_MOVE -> 2
                                else -> return@setOnTouchListener false
                            }
                            val xNorm = (event.x / view.width.toFloat()).coerceIn(0f, 1f)
                            val yNorm = (event.y / view.height.toFloat()).coerceIn(0f, 1f)
                            onTouchNormalized(xNorm, yNorm, actionByte)
                            true
                        }
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                onSurfaceReady(holder.surface)
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
                                onSurfaceReady(holder.surface)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                onSurfaceReady(null)
                            }
                        })
                    }
                },
                modifier = if (isLandscape) {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                } else {
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(9f / 16f)
                }
            )
        }

        when (state) {
            is ReceiverState.WaitingForPhone -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(ReceiverColors.PrimaryBackground, ReceiverColors.Background)
                            )
                        )
                ) {
                    // Left Automotive HMI Navigation Sidebar
                    Column(
                        modifier = Modifier
                            .width(104.dp)
                            .fillMaxHeight()
                            .background(ReceiverColors.PrimaryBackground)
                            .border(1.dp, ReceiverColors.Border)
                            .padding(vertical = 18.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.drivelink_app_icon_preview),
                            contentDescription = "BYD Receiver",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, ReceiverColors.Primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(Modifier.height(6.dp))

                        SidebarNavButton(
                            icon = Icons.Rounded.Home,
                            label = "Home",
                            selected = selectedTab == ReceiverTab.HOME,
                            onClick = { selectedTab = ReceiverTab.HOME }
                        )
                        SidebarNavButton(
                            icon = Icons.Rounded.Settings,
                            label = "Controls",
                            selected = selectedTab == ReceiverTab.SETTINGS,
                            onClick = { selectedTab = ReceiverTab.SETTINGS }
                        )
                        SidebarNavButton(
                            icon = Icons.Rounded.HelpOutline,
                            label = "Help",
                            selected = selectedTab == ReceiverTab.HELP,
                            onClick = { selectedTab = ReceiverTab.HELP }
                        )

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = currentTimeText,
                            color = ReceiverColors.SecondaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Main Automotive Landscape Content Area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Top Automotive Status Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(ReceiverColors.Primary)
                                )
                                Text(
                                    text = "BYD DiLink WIRELESS RECEIVER",
                                    color = ReceiverColors.Primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.5.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(ReceiverColors.Surface)
                                        .border(1.dp, ReceiverColors.Border, RoundedCornerShape(50))
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Wifi, contentDescription = null, tint = ReceiverColors.Primary, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (localIps.isNotEmpty()) localIps.first() else "Waiting for Wi-Fi",
                                        color = ReceiverColors.PrimaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Button(
                                    onClick = onToggleMute,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isMuted) ReceiverColors.Error.copy(alpha = 0.2f) else ReceiverColors.ElevatedSurface
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                        contentDescription = null,
                                        tint = if (isMuted) ReceiverColors.Error else ReceiverColors.Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isMuted) "Muted" else "Audio On",
                                        color = ReceiverColors.PrimaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Tab Body
                        when (selectedTab) {
                            ReceiverTab.HOME -> ReceiverWaitingHomePanel(localIps = localIps)
                            ReceiverTab.SETTINGS -> ReceiverControlsPanel(
                                isMuted = isMuted,
                                localIps = localIps,
                                onToggleMute = onToggleMute
                            )
                            ReceiverTab.HELP -> ReceiverHelpPanel()
                        }
                    }
                }
            }

            is ReceiverState.PairingVerification -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ReceiverColors.Background.copy(alpha = 0.86f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ReceiverColors.Surface),
                        border = BorderStroke(1.5.dp, ReceiverColors.Success),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.width(520.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "SECURE PAIRING VERIFICATION",
                                color = ReceiverColors.Success,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = "التحقق من الاقتران الآمن • ${state.phoneName}",
                                color = ReceiverColors.PrimaryText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = state.pin,
                                color = ReceiverColors.Primary,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 8.sp
                            )
                            Text(
                                text = "Channel Key: ${state.fingerprint}",
                                color = ReceiverColors.SecondaryText,
                                fontSize = 14.sp
                            )
                            state.identityFingerprint?.let { identity ->
                                Text(
                                    text = "Sender Identity: ${identity.take(16)}…",
                                    color = ReceiverColors.SecondaryText,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (state.trusted) {
                                        "جهاز موثوق • Trusted sender"
                                    } else {
                                        "جهاز جديد • New sender"
                                    },
                                    color = if (state.trusted) ReceiverColors.Success else ReceiverColors.Primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            state.warning?.let { warning ->
                                Text(
                                    text = warning,
                                    color = ReceiverColors.Error,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (state.approved) {
                                Text(
                                    text = "تمت الموافقة، بانتظار بدء البث • Approved, waiting for stream",
                                    color = ReceiverColors.Success,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Button(
                                    onClick = onApprove,
                                    colors = ButtonDefaults.buttonColors(containerColor = ReceiverColors.Success),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                ) {
                                    Text(
                                        text = "موافقة وبدء العرض • Approve & Start",
                                        color = Color(0xFF04140C),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onDeny,
                                border = BorderStroke(1.dp, ReceiverColors.Error),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                            ) {
                                Text(
                                    text = "رفض / إلغاء • Reject Connection",
                                    color = ReceiverColors.Error,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            is ReceiverState.MirroringActive -> {
                val mins = elapsedSeconds / 60
                val secs = elapsedSeconds % 60
                val durationLabel = String.format(Locale.US, "%02d:%02d", mins, secs)

                // Compact high-contrast automotive HUD pill over active mirror stream
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ReceiverColors.Surface.copy(alpha = 0.88f))
                        .border(1.dp, ReceiverColors.Border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ReceiverColors.Success)
                        )
                        Text(
                            text = "${state.phoneName} • ${state.fps} FPS • $durationLabel",
                            color = ReceiverColors.PrimaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onToggleMute,
                        colors = ButtonDefaults.buttonColors(containerColor = ReceiverColors.ElevatedSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = if (isMuted) ReceiverColors.Error else ReceiverColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isMuted) "كتم" else "صوت",
                            color = ReceiverColors.PrimaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = ReceiverColors.Error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "قطع الاتصال",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarNavButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ReceiverColors.Primary.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) ReceiverColors.Primary.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) ReceiverColors.Primary else ReceiverColors.SecondaryText,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (selected) ReceiverColors.PrimaryText else ReceiverColors.SecondaryText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ReceiverWaitingHomePanel(localIps: List<String>) {
    val ipDisplay = if (localIps.isNotEmpty()) localIps.joinToString(" / ") else "Waiting for Wi-Fi / Hotspot"
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Left/Center: Automotive Hero Stage
        Card(
            colors = CardDefaults.cardColors(containerColor = ReceiverColors.Surface),
            border = BorderStroke(1.dp, ReceiverColors.Border),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1.45f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Image(
                    painter = painterResource(id = R.drawable.drivelink_hero_vehicle),
                    contentDescription = "BYD Car Display",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "BYD Wireless Mirror",
                    color = ReceiverColors.PrimaryText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "شاشة السيارة جاهزة لاستقبال بث الهاتف لاسلكياً",
                    color = ReceiverColors.SecondaryText,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(ReceiverColors.Primary.copy(alpha = 0.14f))
                        .border(1.dp, ReceiverColors.Primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Cast, contentDescription = null, tint = ReceiverColors.Primary)
                    Text(
                        text = "IP: $ipDisplay",
                        color = ReceiverColors.Primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Right: Live Receiver Status Tiles
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusInfoTile(
                icon = Icons.Rounded.Wifi,
                title = "Discovery & Ports",
                value = "Ready • TCP 47810 / UDP 47811",
                subtitle = "NSD _byd-mirror._tcp. • Beacon UDP 47813",
                accent = ReceiverColors.Primary
            )
            StatusInfoTile(
                icon = Icons.Rounded.Lock,
                title = "Channel Security",
                value = "ECDH P-256 + AES-256-GCM",
                subtitle = "6-digit PIN pairing for untrusted phones",
                accent = ReceiverColors.Success
            )
            StatusInfoTile(
                icon = Icons.Rounded.DirectionsCar,
                title = "Display & Audio",
                value = "H.264 / H.265 • AAC 48kHz",
                subtitle = "Park safely before pairing or adjusting controls",
                accent = ReceiverColors.PrimaryBlue
            )
        }
    }
}

@Composable
private fun ReceiverControlsPanel(
    isMuted: Boolean,
    localIps: List<String>,
    onToggleMute: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ReceiverColors.Surface),
            border = BorderStroke(1.dp, ReceiverColors.Border),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Car Speaker Audio", color = ReceiverColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isMuted) "Audio output is currently muted" else "Receiving phone audio through car speakers",
                        color = ReceiverColors.SecondaryText,
                        fontSize = 14.sp
                    )
                }
                Button(
                    onClick = onToggleMute,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMuted) ReceiverColors.Error else ReceiverColors.Primary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Text(
                        text = if (isMuted) "Unmute Audio" else "Mute Audio",
                        color = if (isMuted) Color.White else Color(0xFF041016),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = ReceiverColors.Surface),
            border = BorderStroke(1.dp, ReceiverColors.Border),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Receiver Network Addresses", color = ReceiverColors.PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (localIps.isNotEmpty()) localIps.joinToString("\n") else "No active IPv4 address detected",
                    color = ReceiverColors.Primary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ReceiverHelpPanel() {
    Card(
        colors = CardDefaults.cardColors(containerColor = ReceiverColors.Surface),
        border = BorderStroke(1.dp, ReceiverColors.Border),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "BYD DiLink Connection Guide • دليل الاتصال بشاشة السيارة",
                color = ReceiverColors.Primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "1. Connect your phone and BYD DiLink head unit to the same Wi-Fi hotspot.\n" +
                    "2. Open BYD Wireless Mirror on your phone and tap START MIRRORING.\n" +
                    "3. If auto-discovery is unavailable, use Connect by IP and enter the IP shown on the Home tab.\n" +
                    "4. Confirm the 6-digit pairing PIN matches both screens.\n" +
                    "5. Safety notice: Always configure and test mirroring while the vehicle is safely parked.",
                color = ReceiverColors.PrimaryText,
                fontSize = 16.sp,
                lineHeight = 26.sp
            )
        }
    }
}

@Composable
private fun StatusInfoTile(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    accent: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ReceiverColors.Surface),
        border = BorderStroke(1.dp, ReceiverColors.Border),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = ReceiverColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(value, color = ReceiverColors.PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = ReceiverColors.SecondaryText, fontSize = 11.sp)
            }
        }
    }
}
