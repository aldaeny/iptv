package app.drivelink.cast.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.drivelink.cast.BuildConfig
import app.drivelink.cast.R
import app.drivelink.cast.ui.components.BydSurfaceCard
import app.drivelink.cast.ui.theme.BydColors
import app.drivelink.cast.ui.theme.BydGradients

@Composable
fun AboutScreen(isArabic: Boolean) {
    val edition = if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) {
        if (isArabic) "نسخة التحكم الكامل (Full-Touch)" else "Full-Touch Edition"
    } else {
        if (isArabic) "النسخة القياسية (Standard)" else "Standard Edition"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BydGradients.DeepNavyBackdrop),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BydSurfaceCard(borderColor = BydColors.Primary.copy(alpha = 0.4f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.drivelink_app_icon_preview),
                        contentDescription = "BYD Wireless Mirror Icon",
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, BydColors.Primary.copy(alpha = 0.55f), RoundedCornerShape(22.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = "BYD Wireless Mirror",
                        color = BydColors.PrimaryText,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • $edition",
                        color = BydColors.Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isArabic) {
                            "ربط لاسلكي مباشر وعالي السلاسة لشاشة الهاتف مع شاشات BYD DiLink والسيارات المتوافقة عبر شبكة Wi-Fi المحلية فقط."
                        } else {
                            "Direct, low-latency encrypted wireless screen mirroring from your smartphone to BYD DiLink displays over local Wi-Fi."
                        },
                        color = BydColors.SecondaryText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        item {
            BydSurfaceCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = BydColors.Success)
                        Text(
                            text = if (isArabic) "سياسة الخصوصية المحلية" else "Privacy Policy",
                            color = BydColors.PrimaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = if (isArabic) {
                            "لا يتم إرسال أي بيانات أو بث شاشة أو صوت إلى خوادم خارجية. تتم جميع عمليات الاكتشاف والمصادقة (ECDH P-256) وتشفير الإطارات (AES-256-GCM) محلياً بين الهاتف والسيارة فقط."
                        } else {
                            "No screen frames, audio, or telemetry leave your local Wi-Fi network. Discovery, ECDH P-256 pairing, and AES-256-GCM encryption happen strictly between your phone and car."
                        },
                        color = BydColors.SecondaryText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )

                    HorizontalDivider(color = BydColors.Border)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, tint = BydColors.Primary)
                        Text(
                            text = if (isArabic) "تراخيص المصادر المفتوحة" else "Open Source Licenses",
                            color = BydColors.PrimaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = "Kotlin • Jetpack Compose • AndroidX Core / Lifecycle / Security Crypto • Kotlinx Coroutines • Material Icons",
                        color = BydColors.SecondaryText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(color = BydColors.Border)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.HelpOutline, contentDescription = null, tint = BydColors.Warning)
                        Text(
                            text = if (isArabic) "المساعدة والدعم" else "Troubleshooting & Safety",
                            color = BydColors.PrimaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = if (isArabic) {
                            "• تأكد من اتصال الهاتف والسيارة بنفس نقطة الاتصال.\n• في حال فشل الاكتشاف التلقائي، استخدم الاتصال اليدوي بـ IP (منفذ التحكم TCP 47810).\n• استخدم البث أثناء التوقف الآمن فقط."
                        } else {
                            "• Keep phone and car on the same Wi-Fi/Hotspot.\n• Use Manual IP if mDNS/P2P is blocked (TCP 47810 / UDP 47811).\n• Test and adjust settings only while safely parked."
                        },
                        color = BydColors.SecondaryText,
                        fontSize = 12.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}
