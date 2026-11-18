import Foundation

public enum TransportType: String, Sendable {
    case peerToPeerAWDL
    case lanBonjour
    case airPlayBridge
}

public struct BydDevice: Identifiable, Hashable, Sendable {
    public let id: String
    public let displayName: String
    public let modelName: String
    public let diLinkVersion: String
    public let hostAddress: String
    public let controlPort: UInt16
    public let mediaPort: UInt16
    public let transportType: TransportType
    public let supportsRotatingScreen: Bool
    public let supportsHevc: Bool

    public init(
        id: String,
        displayName: String,
        modelName: String,
        diLinkVersion: String,
        hostAddress: String,
        controlPort: UInt16 = 47810,
        mediaPort: UInt16 = 47811,
        transportType: TransportType = .peerToPeerAWDL,
        supportsRotatingScreen: Bool = true,
        supportsHevc: Bool = true
    ) {
        self.id = id
        self.displayName = displayName
        self.modelName = modelName
        self.diLinkVersion = diLinkVersion
        self.hostAddress = hostAddress
        self.controlPort = controlPort
        self.mediaPort = mediaPort
        self.transportType = transportType
        self.supportsRotatingScreen = supportsRotatingScreen
        self.supportsHevc = supportsHevc
    }
}

public enum DisplayOrientation: String, Sendable {
    case landscape
    case portrait
}

public enum VideoCodec: String, Sendable {
    case h264 = "H264"
    case h265 = "H265"
}

public struct DisplayProfile: Equatable, Sendable {
    public let id: String
    public let label: String
    public let baseWidth: Int
    public let baseHeight: Int
    public let targetFps: Int
    public let bitrateMbps: Int
    public let codec: VideoCodec

    public init(
        id: String,
        label: String,
        baseWidth: Int,
        baseHeight: Int,
        targetFps: Int,
        bitrateMbps: Int,
        codec: VideoCodec
    ) {
        self.id = id
        self.label = label
        self.baseWidth = baseWidth
        self.baseHeight = baseHeight
        self.targetFps = targetFps
        self.bitrateMbps = bitrateMbps
        self.codec = codec
    }

    public func dimensions(for orientation: DisplayOrientation) -> (width: Int, height: Int) {
        switch orientation {
        case .landscape:
            return (max(baseWidth, baseHeight), min(baseWidth, baseHeight))
        case .portrait:
            return (min(baseWidth, baseHeight), max(baseWidth, baseHeight))
        }
    }

    public static let diLink15_6FHD = DisplayProfile(
        id: "dilink_15_6_fhd",
        label: "BYD DiLink 15.6\" FHD",
        baseWidth: 1920,
        baseHeight: 1080,
        targetFps: 60,
        bitrateMbps: 14,
        codec: .h265
    )

    public static let diLink12_8FHD = DisplayProfile(
        id: "dilink_12_8_fhd",
        label: "BYD DiLink 12.8\" FHD",
        baseWidth: 1920,
        baseHeight: 1080,
        targetFps: 60,
        bitrateMbps: 12,
        codec: .h264
    )

    public static let diLink10_1HD = DisplayProfile(
        id: "dilink_10_1_hd",
        label: "BYD DiLink 10.1\" HD",
        baseWidth: 1280,
        baseHeight: 720,
        targetFps: 30,
        bitrateMbps: 6,
        codec: .h264
    )
}

public enum ThermalLevel: Int, Comparable, Sendable {
    case nominal = 0
    case moderate = 1
    case severe = 2
    case critical = 3

    public static func < (lhs: ThermalLevel, rhs: ThermalLevel) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

public struct TelemetryMetrics: Equatable, Sendable {
    public var rttMs: Int
    public var jitterMs: Int
    public var packetLossPercent: Float
    public var actualFps: Int
    public var currentBitrateMbps: Float
    public var thermalLevel: ThermalLevel

    public init(
        rttMs: Int = 0,
        jitterMs: Int = 0,
        packetLossPercent: Float = 0,
        actualFps: Int = 60,
        currentBitrateMbps: Float = 12,
        thermalLevel: ThermalLevel = .nominal
    ) {
        self.rttMs = rttMs
        self.jitterMs = jitterMs
        self.packetLossPercent = packetLossPercent
        self.actualFps = actualFps
        self.currentBitrateMbps = currentBitrateMbps
        self.thermalLevel = thermalLevel
    }
}

public enum ConnectionState: Equatable, Sendable {
    case idle
    case discovering
    case handshaking(BydDevice)
    case keyExchange(BydDevice)
    case negotiatingQuality(BydDevice)
    case streaming(device: BydDevice, profile: DisplayProfile, orientation: DisplayOrientation, fingerprint: String)
    case reconnecting(device: BydDevice, attempt: Int)
    case error(String)
}
