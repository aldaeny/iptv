package app.drivelink.cast.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class TrustedVehicle(
    val id: String,
    val displayName: String,
    val fingerprint: String,
    val pairedAtEpochMs: Long
)

/**
 * Persists user-verified BYD vehicles inside Android Keystore-backed EncryptedSharedPreferences
 * for secure Auto-Connect without repeated pairing prompts.
 */
class TrustedVehiclesStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "byd_trusted_vehicles_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences("byd_trusted_vehicles_fallback", Context.MODE_PRIVATE)
    }

    fun saveTrustedVehicle(vehicle: TrustedVehicle) {
        prefs.edit()
            .putString(keyFor(vehicle.id), "${vehicle.displayName}|${vehicle.fingerprint}|${vehicle.pairedAtEpochMs}")
            .apply()
    }

    fun isTrusted(deviceId: String): Boolean = prefs.contains(keyFor(deviceId))

    fun listTrusted(): List<TrustedVehicle> {
        return prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(PREFIX)) return@mapNotNull null
            val parts = (value as? String)?.split("|") ?: return@mapNotNull null
            if (parts.size < 3) return@mapNotNull null
            TrustedVehicle(
                id = key.removePrefix(PREFIX),
                displayName = parts[0],
                fingerprint = parts[1],
                pairedAtEpochMs = parts[2].toLongOrNull() ?: 0L
            )
        }
    }

    fun removeTrustedVehicle(deviceId: String) {
        prefs.edit().remove(keyFor(deviceId)).apply()
    }

    private fun keyFor(deviceId: String): String = "$PREFIX$deviceId"

    companion object {
        private const val PREFIX = "veh_"
    }
}
