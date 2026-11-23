package app.drivelink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restores the listening service after a head-unit reboot. Only BOOT_COMPLETED/quick-boot are
 * accepted; this receiver cannot be used to start the service on demand from another app.
 */
class ReceiverBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON -> {
                runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, DriveLinkReceiverService::class.java)
                    )
                }
            }
            else -> Unit
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
