package app.drivelink.receiver.security

import android.content.Context

data class TrustedSender(
    val fingerprint: String,
    val displayName: String,
    val pairedAtEpochMs: Long
)

/**
 * Stores public-key fingerprints only; no private material and no plaintext PINs are persisted.
 * The app sandbox protects integrity, and an attacker able to rewrite this file already controls the
 * head unit. Removing an entry revokes automatic approval on the next connection.
 */
class TrustedSenderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun trust(fingerprint: String, displayName: String) {
        val normalized = fingerprint.uppercase()
        if (normalized.isBlank()) return
        prefs.edit()
            .putString(KEY_PREFIX + normalized, "$displayName|${System.currentTimeMillis()}")
            .apply()
    }

    @Synchronized
    fun isTrusted(fingerprint: String): Boolean =
        prefs.contains(KEY_PREFIX + fingerprint.uppercase())

    @Synchronized
    fun list(): List<TrustedSender> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith(KEY_PREFIX) || value !is String) return@mapNotNull null
        val parts = value.split('|', limit = 2)
        val name = parts.getOrNull(0).orEmpty().ifBlank { "BYD Phone" }
        val pairedAt = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        TrustedSender(key.removePrefix(KEY_PREFIX), name, pairedAt)
    }.sortedBy { it.displayName }

    @Synchronized
    fun remove(fingerprint: String) {
        prefs.edit().remove(KEY_PREFIX + fingerprint.uppercase()).apply()
    }

    @Synchronized
    fun clear() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach(editor::remove)
        editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "drivelink_trusted_senders"
        const val KEY_PREFIX = "sender."
    }
}
