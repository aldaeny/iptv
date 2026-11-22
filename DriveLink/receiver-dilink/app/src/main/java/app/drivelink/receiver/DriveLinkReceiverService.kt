package app.drivelink.receiver

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.drivelink.receiver.network.NsdServiceRegistrar
import app.drivelink.receiver.protocol.BydReceiverServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground owner of the listening sockets and NSD registration. `mediaPlayback` is the closest
 * declared type for a media sink; the notification makes the always-listening state visible.
 */
class DriveLinkReceiverService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var registrar: NsdServiceRegistrar
    private lateinit var server: BydReceiverServer

    override fun onCreate() {
        super.onCreate()
        startForeground(
            DriveLinkReceiverApp.SERVICE_NOTIFICATION_ID,
            buildNotification()
        )
        val app = application as DriveLinkReceiverApp
        server = app.server
        registrar = NsdServiceRegistrar(this)
        registrar.register(port = BydReceiverServer.CONTROL_PORT)
        server.start(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_APPROVE -> server.approvePendingSession()
            ACTION_DENY -> server.denyPendingSession()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { registrar.unregister() }
        server.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, DriveLinkReceiverService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, DriveLinkReceiverApp.SERVICE_CHANNEL_ID)
            .setContentTitle("DriveLink Receiver")
            .setContentText("جاهز لاستقبال شاشة الهاتف • Listening for a phone")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .addAction(0, "إيقاف", stop)
            .build()
    }

    companion object {
        const val ACTION_APPROVE = "app.drivelink.receiver.APPROVE"
        const val ACTION_DENY = "app.drivelink.receiver.DENY"
        const val ACTION_STOP = "app.drivelink.receiver.STOP"
    }
}
