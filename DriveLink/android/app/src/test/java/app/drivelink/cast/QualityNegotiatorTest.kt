package app.drivelink.cast

import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.model.TelemetryMetrics
import app.drivelink.cast.core.model.ThermalLevel
import app.drivelink.cast.core.protocol.QualityNegotiator
import org.junit.Assert.assertEquals
import org.junit.Test

class QualityNegotiatorTest {
    private val negotiator = QualityNegotiator()

    @Test
    fun lossOrThermalPressureDoesNotIncreaseAnAlreadyLowProfile() {
        val small = DisplayProfile.DILINK_12_8_FHD.copy(
            baseWidth = 320, baseHeight = 240, targetFps = 20, bitrateMbps = 2
        )
        assertEquals(small, negotiator.adaptProfile(small, TelemetryMetrics(packetLossPercent = 10f)))
        assertEquals(small, negotiator.adaptProfile(small, TelemetryMetrics(thermalLevel = ThermalLevel.CRITICAL)))
    }

    @Test
    fun criticalThermalPressureStillReducesFullHd() {
        val result = negotiator.adaptProfile(
            DisplayProfile.DILINK_12_8_FHD, TelemetryMetrics(thermalLevel = ThermalLevel.CRITICAL)
        )
        assertEquals(1280, result.baseWidth)
        assertEquals(720, result.baseHeight)
        assertEquals(24, result.targetFps)
        assertEquals(8, result.bitrateMbps)
    }
}
