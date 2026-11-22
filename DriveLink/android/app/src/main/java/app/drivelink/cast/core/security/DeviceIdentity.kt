package app.drivelink.cast.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Installation-scoped identity key. The private key never leaves Android Keystore; only the public
 * key and a session-bound signature cross the network. This is not hardware attestation: the
 * receiver learns "the same install", not "the same physical phone".
 */
interface DeviceIdentitySigner {
    fun publicKeySpki(): ByteArray?
    fun sign(payload: ByteArray): ByteArray?
}

class AndroidDeviceIdentity(@Suppress("UNUSED_PARAMETER") context: Context) : DeviceIdentitySigner {

    override fun publicKeySpki(): ByteArray? = runCatching {
        ensureKey()
        keyStore().getCertificate(ALIAS)?.publicKey?.encoded
    }.getOrNull()

    override fun sign(payload: ByteArray): ByteArray? = runCatching {
        val key = ensureKey()
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(key)
            update(payload)
            sign()
        }
    }.getOrNull()

    @Synchronized
    private fun ensureKey(): PrivateKey {
        val store = keyStore()
        (store.getKey(ALIAS, null) as? PrivateKey)?.let { return it }
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        generator.generateKeyPair()
        return store.getKey(ALIAS, null) as PrivateKey
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "drivelink.device-identity.v1"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}

/**
 * Wire format for `DEVICE_IDENTITY 0x0A`:
 * `[1B version=1][2B publicKeyLen][publicKey][2B signatureLen][DER signature]`
 *
 * The signature covers `BYD-WMP-IDENTITY-V1|<sessionFingerprint>`, so a proof captured from one
 * ECDH session is useless in another.
 */
object IdentityProofCodec {

    const val VERSION: Byte = 1
    const val SIGNING_PREFIX = "BYD-WMP-IDENTITY-V1|"

    fun signingPayload(sessionFingerprint: String): ByteArray =
        (SIGNING_PREFIX + sessionFingerprint).toByteArray(Charsets.UTF_8)

    fun create(sessionFingerprint: String, signer: DeviceIdentitySigner): ByteArray? {
        val publicKey = signer.publicKeySpki() ?: return null
        val signature = signer.sign(signingPayload(sessionFingerprint)) ?: return null
        if (publicKey.size > 0xFFFF || signature.size > 0xFFFF) return null
        return ByteBuffer.allocate(1 + 2 + publicKey.size + 2 + signature.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(VERSION)
            .putShort(publicKey.size.toShort())
            .put(publicKey)
            .putShort(signature.size.toShort())
            .put(signature)
            .array()
    }
}
