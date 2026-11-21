package app.drivelink.cast

import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.model.TelemetryMetrics
import app.drivelink.cast.core.model.ThermalLevel
import app.drivelink.cast.core.protocol.QualityNegotiator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityNegotiatorTest {

    private val negotiator = QualityNegotiator()

    @Test
    fun `selects 15_6 FHD profile for DiLink 5_0`() {
        val device = BydDevice(
            id = "byd-01",
            displayName = "BYD Seal",
            modelName = "BYD DiLink 15.6\"",
            diLinkVersion = "5.0",
            hostAddress = "192.168.49.1"
        )
        val profile = negotiator.initialProfileFor(device)
        assertEquals(1920, profile.baseWidth)
        assertEquals(1080, profile.baseHeight)
        assertEquals(60, profile.targetFps)
    }

    @Test
    fun `throttles fps and resolution under critical thermal state`() {
        val base = DisplayProfile.DILINK_15_6_FHD
        val hotTelemetry = TelemetryMetrics(
            rttMs = 95,
            packetLossPercent = 6.0f,
            thermalLevel = ThermalLevel.CRITICAL
        )
        val adapted = negotiator.adaptProfile(base, hotTelemetry)
        assertEquals(24, adapted.targetFps)
        assertEquals(1280, adapted.baseWidth)
        assertEquals(720, adapted.baseHeight)
        assertTrue(adapted.bitrateMbps < base.bitrateMbps)
    }

    @Test
    fun lossOrThermalPressureDoesNotIncreaseAnAlreadyLowProfile() {
        val small = DisplayProfile.DILINK_12_8_FHD.copy(
            baseWidth = 320, baseHeight = 240, targetFps = 20, bitrateMbps = 2
        )
        assertEquals(small, negotiator.adaptProfile(small, TelemetryMetrics(packetLossPercent = 10f)))
        assertEquals(small, negotiator.adaptProfile(small, TelemetryMetrics(thermalLevel = ThermalLevel.CRITICAL)))
    }
}
