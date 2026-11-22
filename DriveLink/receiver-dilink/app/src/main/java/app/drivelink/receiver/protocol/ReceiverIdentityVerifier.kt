package app.drivelink.receiver.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

data class VerifiedSenderIdentity(
    val publicKeySpki: ByteArray,
    val fingerprint: String
) {
    override fun equals(other: Any?): Boolean =
        other is VerifiedSenderIdentity && fingerprint == other.fingerprint

    override fun hashCode(): Int = fingerprint.hashCode()
}

/**
 * Verifies `DEVICE_IDENTITY 0x0A` proofs. Android sends X.509 SPKI while iOS sends raw X9.63
 * (65-byte uncompressed P-256 point); both are canonicalised to SPKI before hashing so a phone's
 * fingerprint is stable regardless of platform.
 */
object ReceiverIdentityVerifier {

    const val VERSION: Byte = 1
    const val SIGNING_PREFIX = "BYD-WMP-IDENTITY-V1|"

    private val P256_SPKI_PREFIX = byteArrayOf(
        0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(),
        0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(),
        0x3D, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00
    )

    fun signingPayload(sessionFingerprint: String): ByteArray =
        (SIGNING_PREFIX + sessionFingerprint).toByteArray(Charsets.UTF_8)

    fun verify(proof: ByteArray, sessionFingerprint: String): VerifiedSenderIdentity? {
        if (proof.size < 5) return null
        val buffer = ByteBuffer.wrap(proof).order(ByteOrder.BIG_ENDIAN)
        if (buffer.get() != VERSION) return null
        val publicKeyLength = buffer.short.toInt() and 0xFFFF
        if (publicKeyLength <= 0 || publicKeyLength > buffer.remaining()) return null
        val publicKeyBytes = ByteArray(publicKeyLength).also { buffer.get(it) }
        if (buffer.remaining() < 2) return null
        val signatureLength = buffer.short.toInt() and 0xFFFF
        if (signatureLength <= 0 || signatureLength != buffer.remaining()) return null
        val signatureBytes = ByteArray(signatureLength).also { buffer.get(it) }

        val spki = canonicalSpki(publicKeyBytes) ?: return null
        val key = runCatching {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
        }.getOrNull() ?: return null
        val valid = runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(signingPayload(sessionFingerprint))
                verify(signatureBytes)
            }
        }.getOrDefault(false)
        if (!valid) return null
        return VerifiedSenderIdentity(spki, fingerprint(spki))
    }

    fun fingerprint(publicKeySpki: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeySpki)
        val hex = "0123456789ABCDEF"
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                append(hex[value ushr 4])
                append(hex[value and 0x0F])
            }
        }
    }

    /** Converts a raw X9.63 P-256 point to SPKI; passes valid SPKI through unchanged. */
    fun canonicalSpki(publicKeyBytes: ByteArray): ByteArray? = when {
        publicKeyBytes.size == 65 && publicKeyBytes[0] == 0x04.toByte() ->
            P256_SPKI_PREFIX + publicKeyBytes
        publicKeyBytes.size > P256_SPKI_PREFIX.size &&
            publicKeyBytes.copyOfRange(0, P256_SPKI_PREFIX.size).contentEquals(P256_SPKI_PREFIX) ->
            publicKeyBytes
        else -> null
    }

    fun publicKey(publicKeySpki: ByteArray): PublicKey? = runCatching {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeySpki))
    }.getOrNull()
}
