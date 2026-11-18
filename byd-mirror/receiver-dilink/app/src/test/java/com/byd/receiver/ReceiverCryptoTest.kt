package com.byd.receiver

import com.byd.receiver.crypto.ReceiverCryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class ReceiverCryptoTest {

    @Test
    fun `generates 6 digit pairing pin and fingerprint`() {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val phonePair = generator.generateKeyPair()

        val receiverCrypto = ReceiverCryptoManager()
        val (salt, pubKey) = receiverCrypto.generateKeyExchangeResponse(phonePair.public.encoded)

        assertEquals(16, salt.size)
        assertEquals(6, receiverCrypto.pairingPin.length)
        assertEquals(11, receiverCrypto.sessionFingerprint.length) // XX-XX-XX-XX
        assert(pubKey.isNotEmpty())

        val aad = ByteArray(16) { it.toByte() }
        val emptyEnc = receiverCrypto.encrypt(ByteArray(0), aad)
        assertEquals(28, emptyEnc.size)
        val emptyDec = receiverCrypto.decrypt(emptyEnc, aad)
        assertEquals(0, emptyDec.size)
    }

    @Test
    fun `supports raw 65-byte X9_63 key exchange for iOS interoperability`() {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val iosPair = generator.generateKeyPair()
        val ecPub = iosPair.public as ECPublicKey
        fun to32(b: ByteArray) = when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
        val rawX963 = byteArrayOf(0x04) + to32(ecPub.w.affineX.toByteArray()) + to32(ecPub.w.affineY.toByteArray())
        assertEquals(65, rawX963.size)

        val receiverCrypto = ReceiverCryptoManager()
        val (salt, respPub) = receiverCrypto.generateKeyExchangeResponse(rawX963)
        assertEquals(16, salt.size)
        assertEquals(65, respPub.size)
        assertEquals(0x04.toByte(), respPub[0])
        assertArrayEquals(
            receiverCrypto.decrypt(receiverCrypto.encrypt("BYD-IOS".toByteArray(), ByteArray(16)), ByteArray(16)),
            "BYD-IOS".toByteArray()
        )
    }
}
