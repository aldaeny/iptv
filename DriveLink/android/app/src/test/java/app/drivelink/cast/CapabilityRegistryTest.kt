package app.drivelink.cast

import app.drivelink.cast.core.capability.CapabilityId
import app.drivelink.cast.core.capability.CapabilityRegistry
import app.drivelink.cast.core.capability.CapabilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRegistryTest {

    @Test
    fun everyCapabilityHasEvidence() {
        val capabilities = CapabilityRegistry.forThisBuild()
        assertEquals(CapabilityId.entries.size, capabilities.size)
        capabilities.forEach { assertTrue(it.evidence.isNotBlank()) }
    }

    @Test
    fun unsupportedClaimsStayUnsupported() {
        val capabilities = CapabilityRegistry.forThisBuild()
        listOf(CapabilityId.CARPLAY, CapabilityId.ANDROID_AUTO, CapabilityId.USB_VIDEO)
            .forEach { id ->
                val capability = capabilities.first { it.id == id }
                assertEquals(CapabilityState.UNSUPPORTED, capability.state)
                assertFalse(capability.state.assertsUsable)
            }
    }

    @Test
    fun onlyAvailableAndDegradedAreUsable() {
        assertTrue(CapabilityState.AVAILABLE.assertsUsable)
        assertTrue(CapabilityState.DEGRADED.assertsUsable)
        assertFalse(CapabilityState.NOT_VERIFIED.assertsUsable)
        assertFalse(CapabilityState.REQUIRES_PERMISSION.assertsUsable)
        assertFalse(CapabilityState.FAILED.assertsUsable)
    }

    @Test
    fun transportIsAdvertisedWithBaselineEvidence() {
        val transport = CapabilityRegistry.byId(CapabilityId.BYD_WMP_TRANSPORT)
        assertNotNull(transport)
        assertEquals(CapabilityState.AVAILABLE, transport!!.state)
        assertTrue(transport.evidence.contains("45056352610"))
    }
}
