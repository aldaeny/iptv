package app.drivelink.cast.core.protocol

import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.model.TelemetryMetrics
import app.drivelink.cast.core.model.ThermalLevel
import app.drivelink.cast.core.model.VideoCodec

/**
 * Automatically selects optimal resolution, codec, FPS, and bitrate
 * based on BYD DiLink screen capabilities, network RTT/loss, and thermal load.
 */
class QualityNegotiator {

    fun initialProfileFor(device: BydDevice): DisplayProfile {
        val base = when {
            device.diLinkVersion.startsWith("5") || device.modelName.contains("15.6") ->
                DisplayProfile.DILINK_15_6_FHD
            device.diLinkVersion.startsWith("4") ->
                DisplayProfile.DILINK_12_8_FHD
            else ->
                DisplayProfile.DILINK_10_1_HD
        }

        val codec = if (device.supportsHevc && base.codec == VideoCodec.H265) {
            VideoCodec.H265
        } else {
            VideoCodec.H264
        }
        return base.copy(codec = codec)
    }

    fun adaptProfile(current: DisplayProfile, telemetry: TelemetryMetrics): DisplayProfile {
        val thermalCapFps = when (telemetry.thermalLevel) {
            ThermalLevel.NOMINAL -> current.targetFps
            ThermalLevel.MODERATE -> minOf(current.targetFps, 45)
            ThermalLevel.SEVERE -> minOf(current.targetFps, 30)
            ThermalLevel.CRITICAL -> 24
        }

        val networkStepDown = telemetry.rttMs > 70 || telemetry.packetLossPercent > 2.5f
        val networkHealthy = telemetry.rttMs in 1..35 && telemetry.packetLossPercent < 0.3f

        val adjustedBitrate = when {
            telemetry.thermalLevel >= ThermalLevel.SEVERE -> maxOf(4, current.bitrateMbps - 4)
            networkStepDown -> maxOf(4, (current.bitrateMbps * 0.75f).toInt())
            networkHealthy && telemetry.thermalLevel == ThermalLevel.NOMINAL ->
                minOf(16, current.bitrateMbps + 2)
            else -> current.bitrateMbps
        }

        val (width, height) = if (telemetry.thermalLevel == ThermalLevel.CRITICAL ||
            (networkStepDown && telemetry.packetLossPercent > 5.0f)
        ) {
            1280 to 720
        } else {
            current.baseWidth to current.baseHeight
        }

        return current.copy(
            baseWidth = width,
            baseHeight = height,
            targetFps = thermalCapFps,
            bitrateMbps = adjustedBitrate
        )
    }
}
