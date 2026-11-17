package com.byd.receiver

import com.byd.receiver.crypto.ReceiverCryptoManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
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
    }
}
