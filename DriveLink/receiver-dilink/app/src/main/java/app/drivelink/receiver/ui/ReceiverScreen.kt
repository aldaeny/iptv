package app.drivelink.receiver.ui

import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.drivelink.receiver.protocol.ReceiverState

@Composable
fun ReceiverScreen(
    state: ReceiverState,
    localIps: List<String> = emptyList(),
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onSurfaceReady: (android.view.Surface?) -> Unit,
    onTouchNormalized: (xNorm: Float, yNorm: Float, action: Byte) -> Unit = { _, _, _ -> }
) {
    val bg = Color(0xFF080B10)
    val card = Color(0xFF111827)
    val cyan = Color(0xFF00D4FF)
    val emerald = Color(0xFF10B981)
    val red = Color(0xFFEF4444)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
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
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                } else {
                    Modifier.fillMaxHeight().aspectRatio(9f / 16f)
                }
            )
        }

        when (state) {
            is ReceiverState.WaitingForPhone -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(24.dp))
                        .background(card.copy(alpha = 0.94f))
                        .border(1.dp, cyan.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("BYD DiLink WIRELESS RECEIVER", color = cyan, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("جاهز لاستقبال البث اللاسلكي من الهاتف", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Ready for Wireless Phone Mirroring", color = Color(0xFFCBD5E1), fontSize = 14.sp)
                    val ipText = if (localIps.isNotEmpty()) localIps.joinToString(" / ") else "Waiting for Wi-Fi / Hotspot"
                    Text("IP: $ipText • TCP:47810 • UDP:47811", color = cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("NSD: _byd-mirror._tcp. • Beacon UDP:47813", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
            is ReceiverState.PairingVerification -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(24.dp))
                        .background(card.copy(alpha = 0.96f))
                        .border(1.dp, emerald, RoundedCornerShape(24.dp))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("التحقق من الاقتران الآمن", color = emerald, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("الجهاز: ${state.phoneName}", color = Color.White, fontSize = 16.sp)
                    Text(state.pin, color = cyan, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
                    Text("Fingerprint: ${state.fingerprint}", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    OutlinedButton(onClick = onDisconnect) {
                        Text("رفض / إلغاء • Reject", color = red, fontWeight = FontWeight.Bold)
                    }
                }
            }
            is ReceiverState.MirroringActive -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(card.copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LIVE • ${state.fps} FPS", color = emerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(state.fingerprint, color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Button(
                        onClick = onToggleMute,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text(if (isMuted) "كتم الصوت: مفعّل" else "الصوت: يعمل", color = cyan, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = red)
                    ) {
                        Text("قطع الاتصال", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
