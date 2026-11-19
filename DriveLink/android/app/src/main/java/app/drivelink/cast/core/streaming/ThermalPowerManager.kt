package app.drivelink.cast.core.streaming

import android.content.Context
import android.os.Build
import android.os.PowerManager
import app.drivelink.cast.core.model.ThermalLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors device thermal status (Phase 4) to prevent overheating during continuous
 * wireless mirroring by stepping down frame rate and bitrate cleanly.
 */
class ThermalPowerManager(context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _thermalLevel = MutableStateFlow(ThermalLevel.NOMINAL)
    val thermalLevel: StateFlow<ThermalLevel> = _thermalLevel.asStateFlow()

    private var listener: Any? = null

    fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val statusListener = PowerManager.OnThermalStatusChangedListener { status ->
                _thermalLevel.value = mapStatus(status)
            }
            listener = statusListener
            powerManager.addThermalStatusListener(statusListener)
            _thermalLevel.value = mapStatus(powerManager.currentThermalStatus)
        }
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val statusListener = listener as? PowerManager.OnThermalStatusChangedListener
            if (statusListener != null) {
                powerManager.removeThermalStatusListener(statusListener)
            }
            listener = null
        }
    }

    private fun mapStatus(status: Int): ThermalLevel {
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.NOMINAL
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
            else -> ThermalLevel.CRITICAL
        }
    }
}
