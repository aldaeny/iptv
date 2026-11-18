package com.byd.mirror.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.byd.mirror.BuildConfig
import com.byd.mirror.MainActivity
import com.byd.mirror.R
import com.byd.mirror.core.session.MirrorSessionCoordinator
import com.byd.mirror.core.streaming.AudioCaptureEncoder
import com.byd.mirror.core.streaming.ScreenCaptureEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service hosting MediaProjection video + internal audio capture,
 * synchronized through the shared MirrorSessionCoordinator.
 */
class MirrorForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaProjection: MediaProjection? = null
    private var videoEncoder: ScreenCaptureEncoder? = null
    private var audioEncoder: AudioCaptureEncoder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                ensureNotificationChannel()
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != -1 && resultData != null) {
                    startCapture(resultCode, resultData)
                }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mgr.getMediaProjection(resultCode, data) ?: return
        mediaProjection = projection

        val densityDpi = resources.displayMetrics.densityDpi
        val protocol = MirrorSessionCoordinator.protocolWrapper

        val vEncoder = ScreenCaptureEncoder(
            mediaProjection = projection,
            scope = serviceScope,
            onEncodedNalu = { nalu, ptsUs ->
                protocol.sendVideoFrame(nalu, ptsUs)
            },
            onProjectionStopped = {
                stopSelf()
            }
        ).apply {
            start(
                MirrorSessionCoordinator.activeProfile.value,
                MirrorSessionCoordinator.orientation.value,
                densityDpi
            )
        }
        videoEncoder = vEncoder

        MirrorSessionCoordinator.onKeyframeRequested = { vEncoder.requestKeyFrame() }
        MirrorSessionCoordinator.onReconfigureEncoder = { profile, orientation ->
            vEncoder.reconfigure(profile, orientation, densityDpi)
        }
        MirrorSessionCoordinator.onStopServices = {
            if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) {
                runCatching { stopService(Intent(this, MirrorOverlayService::class.java)) }
            }
            stopSelf()
        }

        audioEncoder = AudioCaptureEncoder(
            mediaProjection = projection,
            scope = serviceScope,
            onEncodedAac = { aac, ptsUs ->
                protocol.sendAudioFrame(aac, ptsUs)
            }
        ).apply {
            runCatching { start() }
        }
    }

    override fun onDestroy() {
        MirrorSessionCoordinator.onKeyframeRequested = null
        MirrorSessionCoordinator.onReconfigureEncoder = null
        MirrorSessionCoordinator.onStopServices = null
        audioEncoder?.stop()
        videoEncoder?.releaseProjection()
        mediaProjection?.stop()
        serviceScope.launch { MirrorSessionCoordinator.disconnect() }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BYD Wireless Mirroring",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MirrorForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_body))
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "إيقاف البث / Stop",
                stopIntent
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.byd.mirror.action.START"
        const val ACTION_STOP = "com.byd.mirror.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "byd_mirror_channel"
        private const val NOTIFICATION_ID = 4781
    }
}
