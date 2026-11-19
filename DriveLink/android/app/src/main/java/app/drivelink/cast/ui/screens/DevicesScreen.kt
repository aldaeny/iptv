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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.ConnectionState
import app.drivelink.cast.core.model.TransportType
import app.drivelink.cast.core.security.TrustedVehicle
import app.drivelink.cast.ui.components.BydSurfaceCard
import app.drivelink.cast.ui.components.SignalStrengthBars
import app.drivelink.cast.ui.theme.BydColors
import app.drivelink.cast.ui.theme.BydGradients

@Composable
fun DevicesScreen(
    state: ConnectionState,
    devices: List<BydDevice>,
    trustedVehicles: List<TrustedVehicle>,
    isScanning: Boolean,
    isArabic: Boolean,
    isTrusted: (String) -> Boolean,
    onRefreshScan: () -> Unit,
    onConnectDevice: (BydDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRemoveTrusted: (String) -> Unit,
    onOpenManualIp: () -> Unit
) {
    val activeDevice = (state as? ConnectionState.Streaming)?.device
    val availableDevices = devices.filter { it.id != activeDevice?.id }
    val isConnecting = state is ConnectionState.Handshaking ||
        state is ConnectionState.KeyExchange ||
        state is ConnectionState.NegotiatingQuality

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BydGradients.DeepNavyBackdrop),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row with Scan / Refresh & Manual IP
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isArabic) "الأجهزة والشاشات" else "Devices",
                        color = BydColors.PrimaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isArabic) "اكتشاف شاشات BYD والمستقبلات المتاحة" else "Real-time Wi-Fi Direct, NSD & Miracast discovery",
                        color = BydColors.SecondaryText,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenManualIp,
                        border = BorderStroke(1.dp, BydColors.Border),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Rounded.Lan, contentDescription = null, tint = BydColors.Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (isArabic) "IP" else "IP", color = BydColors.PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onRefreshScan,
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = BydColors.Primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = Color(0xFF041016),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color(0xFF041016), modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = if (isScanning) {
                                if (isArabic) "فحص..." else "Scanning"
                            } else {
                                if (isArabic) "تحديث" else "Scan"
                            },
                            color = Color(0xFF041016),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Error State Banner if connection failed
        if (state is ConnectionState.Error) {
            item {
                BydSurfaceCard(borderColor = BydColors.Error.copy(alpha = 0.5f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = BydColors.Error)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) "فشل الاتصال السابق" else "Last Connection Failed",
                                color = BydColors.Error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(state.message, color = BydColors.SecondaryText, fontSize = 12.sp)
                        }
                        Button(
                            onClick = onRefreshScan,
                            colors = ButtonDefaults.buttonColors(containerColor = BydColors.ElevatedSurface)
                        ) {
                            Text(if (isArabic) "إعادة" else "Retry", color = BydColors.Primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Connected Device Section
        if (activeDevice != null) {
            item {
                Text(
                    text = if (isArabic) "الجهاز المتصل حالياً" else "Connected Device",
                    color = BydColors.Success,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                BydSurfaceCard(
                    elevated = true,
                    borderColor = BydColors.Success.copy(alpha = 0.55f)
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
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BydColors.Success.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.CastConnected, contentDescription = null, tint = BydColors.Success)
                            }
                            Column {
                                Text(activeDevice.displayName, color = BydColors.PrimaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    text = "${activeDevice.modelName} • ${activeDevice.hostAddress}",
                                    color = BydColors.SecondaryText,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (isArabic) "البث متصل الآن" else "Connected & Streaming",
                                    color = BydColors.Success,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(containerColor = BydColors.Error),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(if (isArabic) "قطع" else "Disconnect", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Available Devices Section
        item {
            Text(
                text = if (isArabic) "الأجهزة المتاحة (${availableDevices.size})" else "Available Devices (${availableDevices.size})",
                color = BydColors.SecondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (availableDevices.isEmpty()) {
            item {
                BydSurfaceCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(BydColors.ElevatedSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(color = BydColors.Primary, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                            } else {
                                Icon(Icons.Rounded.WifiFind, contentDescription = null, tint = BydColors.Primary, modifier = Modifier.size(28.dp))
                            }
                        }
                        Text(
                            text = if (isScanning) {
                                if (isArabic) "جاري مسح الشبكة عن شاشات BYD..." else "Scanning local Wi-Fi & P2P for receivers..."
                            } else {
                                if (isArabic) "لم يتم العثور على شاشات متاحة بعد" else "No available receivers found yet"
                            },
                            color = BydColors.PrimaryText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isArabic) {
                                "تأكد من تشغيل مستقبل BYD على شاشة السيارة واتصال الجهازين بنفس الشبكة، أو استخدم الاتصال المباشر بـ IP."
                            } else {
                                "Ensure BYD Receiver is open on your vehicle display on the same Wi-Fi/Hotspot, or connect manually by IP."
                            },
                            color = BydColors.SecondaryText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onRefreshScan,
                                enabled = !isScanning,
                                border = BorderStroke(1.dp, BydColors.Border),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(if (isArabic) "إعادة المسح" else "Scan Again", color = BydColors.Primary)
                            }
                            Button(
                                onClick = onOpenManualIp,
                                colors = ButtonDefaults.buttonColors(containerColor = BydColors.Primary),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(if (isArabic) "اتصال بـ IP" else "Manual IP", color = Color(0xFF041016), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            items(availableDevices, key = { it.id }) { device ->
                val trusted = isTrusted(device.id)
                val transportLabel = when (device.transportType) {
                    TransportType.WIFI_DIRECT -> "Wi-Fi Direct"
                    TransportType.LAN_NSD -> "LAN NSD"
                    TransportType.MIRACAST_SINK -> "Miracast"
                }
                BydSurfaceCard(
                    elevated = true,
                    onClick = if (!isConnecting) ({ onConnectDevice(device) }) else null
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
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BydColors.Primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = BydColors.Primary)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = device.displayName,
                                        color = BydColors.PrimaryText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    if (trusted) {
                                        Icon(
                                            imageVector = Icons.Rounded.VerifiedUser,
                                            contentDescription = "Trusted",
                                            tint = BydColors.Success,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${device.modelName} • $transportLabel • ${device.hostAddress}",
                                    color = BydColors.SecondaryText,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (device.transportType == TransportType.WIFI_DIRECT && device.signalStrengthDbm < 0) {
                                SignalStrengthBars(dbm = device.signalStrengthDbm, accent = BydColors.Success)
                            }
                            Button(
                                onClick = { onConnectDevice(device) },
                                enabled = !isConnecting,
                                colors = ButtonDefaults.buttonColors(containerColor = BydColors.Primary),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = if (isArabic) "ربط" else "Connect",
                                    color = Color(0xFF041016),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Previously Paired / Trusted Vehicles (real Keystore/SharedPrefs entries only)
        if (trustedVehicles.isNotEmpty()) {
            item {
                Text(
                    text = if (isArabic) "السيارات الموثوقة المحفوظة (${trustedVehicles.size})" else "Paired Vehicles (${trustedVehicles.size})",
                    color = BydColors.SecondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(trustedVehicles, key = { it.id }) { vehicle ->
                BydSurfaceCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(vehicle.displayName, color = BydColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Key ID: ${vehicle.fingerprint}", color = BydColors.Success, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onRemoveTrusted(vehicle.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = if (isArabic) "حذف" else "Forget",
                                tint = BydColors.Error
                            )
                        }
                    }
                }
            }
        }
    }
}
