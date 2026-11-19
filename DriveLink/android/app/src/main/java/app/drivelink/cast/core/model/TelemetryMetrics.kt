package app.drivelink.cast.core.model

enum class ThermalLevel {
    NOMINAL,
    MODERATE,
    SEVERE,
    CRITICAL
}

data class TelemetryMetrics(
    val rttMs: Int = 0,
    val jitterMs: Int = 0,
    val packetLossPercent: Float = 0f,
    val actualFps: Int = 60,
    val currentBitrateMbps: Float = 12f,
    val thermalLevel: ThermalLevel = ThermalLevel.NOMINAL,
    val batteryTempCelsius: Float = 32.0f
)
