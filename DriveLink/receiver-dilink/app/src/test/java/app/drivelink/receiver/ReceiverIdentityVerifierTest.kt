package app.drivelink.receiver

import app.drivelink.receiver.protocol.ReceiverIdentityVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class ReceiverIdentityVerifierTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun proof(
        sessionFingerprint: String,
        publicKey: ByteArray = keyPair.public.encoded,
        tamper: Boolean = false
    ): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(ReceiverIdentityVerifier.signingPayload(sessionFingerprint))
            sign()
        }.also { if (tamper) it[it.size - 1] = (it.last().toInt() xor 0x01).toByte() }
        return ByteBuffer.allocate(1 + 2 + publicKey.size + 2 + signature.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(1)
            .putShort(publicKey.size.toShort())
            .put(publicKey)
            .putShort(signature.size.toShort())
            .put(signature)
            .array()
    }

    @Test
    fun verifiesSpkiProofAndReturnsStableFingerprint() {
        val parsed = ReceiverIdentityVerifier.verify(proof("SESSION-1"), "SESSION-1")
        assertNotNull(parsed)
        assertEquals(64, parsed!!.fingerprint.length)
        assertTrue(parsed.publicKeySpki.contentEquals(keyPair.public.encoded))
    }

    @Test
    fun rejectsProofForAnotherSession() {
        assertNull(ReceiverIdentityVerifier.verify(proof("SESSION-1"), "SESSION-2"))
    }

    @Test
    fun rejectsTamperedSignature() {
        assertNull(ReceiverIdentityVerifier.verify(proof("SESSION-1", tamper = true), "SESSION-1"))
    }

    @Test
    fun acceptsRawX963PointAndCanonicalisesIt() {
        val ec = keyPair.public as ECPublicKey
        val x = ec.w.affineX.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
        val y = ec.w.affineY.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
        val raw = ByteArray(65)
        raw[0] = 0x04
        System.arraycopy(x, 0, raw, 1 + (32 - x.size), x.size)
        System.arraycopy(y, 0, raw, 33 + (32 - y.size), y.size)

        val parsed = ReceiverIdentityVerifier.verify(proof("IOS-SESSION", raw), "IOS-SESSION")
        assertNotNull(parsed)
        assertEquals(91, parsed!!.publicKeySpki.size)
        assertEquals(
            ReceiverIdentityVerifier.fingerprint(parsed.publicKeySpki),
            parsed.fingerprint
        )
    }

    @Test
    fun rejectsMalformedLengths() {
        assertNull(ReceiverIdentityVerifier.verify(byteArrayOf(1, 0, 5), "S"))
        assertNull(ReceiverIdentityVerifier.verify(ByteArray(0), "S"))
    }
}
