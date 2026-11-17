package com.byd.receiver.crypto

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

class ReceiverCryptoManager {

    private val random = SecureRandom()
    private var keyPair: KeyPair? = null
    private var sessionKey: SecretKey? = null
    var sessionFingerprint: String = "----"
        private set
    var pairingPin: String = "000000"
        private set

    fun generateKeyExchangeResponse(peerPublicKeyX509: ByteArray): Pair<ByteArray, ByteArray> {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        val localPair = generator.generateKeyPair()
        keyPair = localPair

        val peerPub = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(peerPublicKeyX509))

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(localPair.private)
        agreement.doPhase(peerPub, true)
        val sharedSecret = agreement.generateSecret()

        val salt = ByteArray(16).also { random.nextBytes(it) }
        val derived = hkdfSha256(sharedSecret, salt, KDF_INFO, 32)
        sessionKey = SecretKeySpec(derived, "AES")

        val hash = MessageDigest.getInstance("SHA-256").digest(derived)
        sessionFingerprint = hash.take(4).joinToString("-") { "%02X".format(it) }
        val pinInt = ((hash[0].toInt() and 0x7F) shl 16) or
            ((hash[1].toInt() and 0xFF) shl 8) or
            (hash[2].toInt() and 0xFF)
        pairingPin = "%06d".format(pinInt % 1_000_000)

        return salt to localPair.public.encoded
    }

    fun encrypt(plaintext: ByteArray, aadHeader: ByteArray): ByteArray {
        val key = requireNotNull(sessionKey) { "Session key not established" }
        val nonce = ByteArray(GCM_NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aadHeader)
        val out = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(nonce.size + out.size).put(nonce).put(out).array()
    }

    fun decrypt(encryptedPayload: ByteArray, aadHeader: ByteArray): ByteArray {
        val key = requireNotNull(sessionKey) { "Session key not established" }
        require(encryptedPayload.size > GCM_NONCE_BYTES + (GCM_TAG_BITS / 8))
        val nonce = encryptedPayload.copyOfRange(0, GCM_NONCE_BYTES)
        val body = encryptedPayload.copyOfRange(GCM_NONCE_BYTES, encryptedPayload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aadHeader)
        return cipher.doFinal(body)
    }

    fun reset() {
        keyPair = null
        sessionKey = null
        sessionFingerprint = "----"
        pairingPin = "000000"
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int): ByteArray {
        val prkMac = Mac.getInstance("HmacSHA256")
        prkMac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = prkMac.doFinal(ikm)

        val okm = ByteArray(len)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < len) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copy = minOf(t.size, len - offset)
            System.arraycopy(t, 0, okm, offset, copy)
            offset += copy
            counter++
        }
        return okm
    }

    companion object {
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private val KDF_INFO = "BYD-MIRROR-SESSION-KEY-V1".toByteArray(Charsets.UTF_8)
    }
}
