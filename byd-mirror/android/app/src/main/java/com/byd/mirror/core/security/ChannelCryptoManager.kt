package com.byd.mirror.core.security

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages end-to-end encrypted communication between the phone and BYD DiLink display:
 * - Ephemeral ECDH P-256 key agreement
 * - HKDF-SHA256 session key derivation
 * - AES-256-GCM authenticated encryption with 16-byte header AAD
 */
class ChannelCryptoManager {

    private val secureRandom = SecureRandom()
    private var localKeyPair: KeyPair? = null
    private var sessionKey: SecretKey? = null
    var sessionFingerprint: String = "UNVERIFIED"
        private set
    var pairingPin: String = "000000"
        private set

    fun generateEphemeralKeyPair(): ByteArray {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val pair = generator.generateKeyPair()
        localKeyPair = pair
        return pair.public.encoded
    }

    fun deriveSessionKey(peerPublicKeyBytes: ByteArray, sessionSalt: ByteArray): SecretKey {
        val pair = requireNotNull(localKeyPair) { "Local ephemeral keypair not initialized" }
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(pair.private)
        agreement.doPhase(peerPublicKey, true)
        val sharedSecret = agreement.generateSecret()

        val derivedBytes = hkdfSha256(
            ikm = sharedSecret,
            salt = sessionSalt,
            info = KDF_INFO,
            outputLengthBytes = AES_KEY_SIZE_BYTES
        )

        val key = SecretKeySpec(derivedBytes, "AES")
        sessionKey = key
        sessionFingerprint = computeFingerprint(derivedBytes)
        pairingPin = computePairingPin(derivedBytes)
        return key
    }

    fun encrypt(plaintext: ByteArray, aadHeader: ByteArray): ByteArray {
        val key = requireNotNull(sessionKey) { "Session key not established" }
        val nonce = ByteArray(GCM_NONCE_BYTES).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aadHeader)
        val ciphertextWithTag = cipher.doFinal(plaintext)

        return ByteBuffer.allocate(nonce.size + ciphertextWithTag.size)
            .put(nonce)
            .put(ciphertextWithTag)
            .array()
    }

    fun decrypt(encryptedPayload: ByteArray, aadHeader: ByteArray): ByteArray {
        val key = requireNotNull(sessionKey) { "Session key not established" }
        require(encryptedPayload.size >= GCM_NONCE_BYTES + (GCM_TAG_BITS / 8)) {
            "Encrypted payload too short"
        }

        val nonce = encryptedPayload.copyOfRange(0, GCM_NONCE_BYTES)
        val ciphertextWithTag = encryptedPayload.copyOfRange(GCM_NONCE_BYTES, encryptedPayload.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aadHeader)
        return cipher.doFinal(ciphertextWithTag)
    }

    fun reset() {
        localKeyPair = null
        sessionKey = null
        sessionFingerprint = "UNVERIFIED"
        pairingPin = "000000"
    }

    private fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLengthBytes: Int
    ): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prkMac = Mac.getInstance("HmacSHA256")
        prkMac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = prkMac.doFinal(ikm)

        val okm = ByteArray(outputLengthBytes)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < outputLengthBytes) {
            val expandMac = Mac.getInstance("HmacSHA256")
            expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
            expandMac.update(t)
            expandMac.update(info)
            expandMac.update(counter.toByte())
            t = expandMac.doFinal()

            val copyLen = minOf(t.size, outputLengthBytes - offset)
            System.arraycopy(t, 0, okm, offset, copyLen)
            offset += copyLen
            counter++
        }
        return okm
    }

    private fun computeFingerprint(keyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        return digest.take(4).joinToString("-") { "%02X".format(it) }
    }

    private fun computePairingPin(keyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        val pinInt = ((digest[0].toInt() and 0x7F) shl 16) or
            ((digest[1].toInt() and 0xFF) shl 8) or
            (digest[2].toInt() and 0xFF)
        return "%06d".format(pinInt % 1_000_000)
    }

    companion object {
        private const val AES_KEY_SIZE_BYTES = 32
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private val KDF_INFO = "BYD-MIRROR-SESSION-KEY-V1".toByteArray(Charsets.UTF_8)
    }
}
