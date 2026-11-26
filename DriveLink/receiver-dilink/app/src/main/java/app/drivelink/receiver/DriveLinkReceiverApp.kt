package app.drivelink.receiver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import app.drivelink.receiver.protocol.BydReceiverServer
import app.drivelink.receiver.security.TrustedSenderStore

/**
 * Process-wide owner of the receiver server. The foreground service keeps the process alive; the
 * activity only observes/approves, so closing the UI no longer tears down an active mirror session.
 */
class DriveLinkReceiverApp : Application() {

    lateinit var server: BydReceiverServer
        private set

    lateinit var trustStore: TrustedSenderStore
        private set

    override fun onCreate() {
        super.onCreate()
        trustStore = TrustedSenderStore(this)
        server = BydReceiverServer(trustStore = trustStore)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "DriveLink Receiver",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the BYD DiLink receiver listening while the screen is off."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "drivelink_receiver_service"
        const val SERVICE_NOTIFICATION_ID = 47810
    }
}
