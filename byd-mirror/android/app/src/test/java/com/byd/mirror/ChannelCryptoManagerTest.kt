package com.byd.mirror

import com.byd.mirror.core.security.ChannelCryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.SecureRandom

class ChannelCryptoManagerTest {

    @Test
    fun `ecdh key exchange produces matching session keys and decrypts payload`() {
        val phoneCrypto = ChannelCryptoManager()
        val bydScreenCrypto = ChannelCryptoManager()

        val phonePub = phoneCrypto.generateEphemeralKeyPair()
        val bydPub = bydScreenCrypto.generateEphemeralKeyPair()

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        phoneCrypto.deriveSessionKey(bydPub, salt)
        bydScreenCrypto.deriveSessionKey(phonePub, salt)

        assertEquals(phoneCrypto.sessionFingerprint, bydScreenCrypto.sessionFingerprint)

        val aadHeader = ByteArray(16) { it.toByte() }
        val secretFrame = "BYD-NALU-FRAME-DATA".toByteArray()
        val encrypted = phoneCrypto.encrypt(secretFrame, aadHeader)
        val decrypted = bydScreenCrypto.decrypt(encrypted, aadHeader)

        assertArrayEquals(secretFrame, decrypted)
    }
}
