package app.drivelink.cast

import app.drivelink.cast.core.security.DeviceIdentitySigner
import app.drivelink.cast.core.security.IdentityProofCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class DeviceIdentityProofTest {

    private class FakeSigner : DeviceIdentitySigner {
        private val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        override fun publicKeySpki(): ByteArray = keyPair.public.encoded

        override fun sign(payload: ByteArray): ByteArray =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(payload)
                sign()
            }
    }

    @Test
    fun proofContainsVersionLengthsAndSessionBoundSignature() {
        val signer = FakeSigner()
        val fingerprint = "A1B2C3D4E5F6"
        val proof = IdentityProofCodec.create(fingerprint, signer)
        assertNotNull(proof)
        val buffer = ByteBuffer.wrap(proof!!).order(ByteOrder.BIG_ENDIAN)
        assertEquals(1, buffer.get().toInt())
        val publicKey = ByteArray(buffer.short.toInt()).also { buffer.get(it) }
        val signature = ByteArray(buffer.short.toInt()).also { buffer.get(it) }
        assertTrue(publicKey.contentEquals(signer.publicKeySpki()))

        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(
                java.security.KeyFactory.getInstance("EC")
                    .generatePublic(java.security.spec.X509EncodedKeySpec(publicKey))
            )
            update(IdentityProofCodec.signingPayload(fingerprint))
        }
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun signatureDoesNotVerifyForAnotherSession() {
        val signer = FakeSigner()
        val proof = IdentityProofCodec.create("SESSION-A", signer)
        val buffer = ByteBuffer.wrap(proof!!).order(ByteOrder.BIG_ENDIAN)
        buffer.get()
        val publicKey = ByteArray(buffer.short.toInt()).also { buffer.get(it) }
        val signature = ByteArray(buffer.short.toInt()).also { buffer.get(it) }

        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(
                java.security.KeyFactory.getInstance("EC")
                    .generatePublic(java.security.spec.X509EncodedKeySpec(publicKey))
            )
            update(IdentityProofCodec.signingPayload("SESSION-B"))
        }
        assertTrue(!verifier.verify(signature))
    }

    @Test
    fun nullKeyProducesNoProof() {
        val signer = object : DeviceIdentitySigner {
            override fun publicKeySpki(): ByteArray? = null
            override fun sign(payload: ByteArray): ByteArray? = null
        }
        assertEquals(null, IdentityProofCodec.create("SESSION", signer))
    }
}
