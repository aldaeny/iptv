import Foundation

public struct QualityNegotiator: Sendable {

    public init() {}

    public func initialProfile(for device: BydDevice) -> DisplayProfile {
        let base: DisplayProfile
        if device.diLinkVersion.hasPrefix("5") || device.modelName.contains("15.6") {
            base = .diLink15_6FHD
        } else if device.diLinkVersion.hasPrefix("4") {
            base = .diLink12_8FHD
        } else {
            base = .diLink10_1HD
        }

        let codec: VideoCodec = (device.supportsHevc && base.codec == .h265) ? .h265 : .h264
        return DisplayProfile(
            id: base.id,
            label: base.label,
            baseWidth: base.baseWidth,
            baseHeight: base.baseHeight,
            targetFps: base.targetFps,
            bitrateMbps: base.bitrateMbps,
            codec: codec
        )
    }

    public func adaptProfile(_ current: DisplayProfile, telemetry: TelemetryMetrics) -> DisplayProfile {
        let thermalCapFps: Int
        switch telemetry.thermalLevel {
        case .nominal: thermalCapFps = current.targetFps
        case .moderate: thermalCapFps = min(current.targetFps, 45)
        case .severe: thermalCapFps = min(current.targetFps, 30)
        case .critical: thermalCapFps = 24
        }

        let networkStepDown = telemetry.rttMs > 70 || telemetry.packetLossPercent > 2.5
        let networkHealthy = (1...35).contains(telemetry.rttMs) && telemetry.packetLossPercent < 0.3

        let bitrate: Int
        if telemetry.thermalLevel >= .severe {
            bitrate = max(4, current.bitrateMbps - 4)
        } else if networkStepDown {
            bitrate = max(4, Int(Float(current.bitrateMbps) * 0.75))
        } else if networkHealthy && telemetry.thermalLevel == .nominal {
            bitrate = min(16, current.bitrateMbps + 2)
        } else {
            bitrate = current.bitrateMbps
        }

        let downgradeRes = telemetry.thermalLevel == .critical || (networkStepDown && telemetry.packetLossPercent > 5.0)
        let width = downgradeRes ? 1280 : current.baseWidth
        let height = downgradeRes ? 720 : current.baseHeight

        return DisplayProfile(
            id: current.id,
            label: current.label,
            baseWidth: width,
            baseHeight: height,
            targetFps: thermalCapFps,
            bitrateMbps: bitrate,
            codec: current.codec
        )
    }
}
