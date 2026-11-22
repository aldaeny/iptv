import Foundation

public enum CapabilityState: String, Codable, CaseIterable, Sendable {
    case available
    case requiresPermission
    case degraded
    case unsupported
    case notVerified
    case blockedByPlatform
    case blockedByHardware
    case failed

    /// Only these may be shown as usable today.
    public var assertsUsable: Bool {
        self == .available || self == .degraded
    }
}

public enum CapabilityId: String, Codable, CaseIterable, Hashable, Sendable {
    case bydWmpTransport
    case wifiDirect
    case miracast
    case screenMirroring
    case reverseTouch
    case audioStreaming
    case hevcEncoding
    case carplay
    case androidAuto
    case usbVideo
    case bluetoothVideo
}

public struct Capability: Identifiable, Equatable, Sendable {
    public let id: CapabilityId
    public let state: CapabilityState
    public let evidence: String
    public let notes: String

    public init(id: CapabilityId, state: CapabilityState, evidence: String, notes: String = "") {
        self.id = id
        self.state = state
        self.evidence = evidence
        self.notes = notes
    }
}

/// The iOS counterpart of the Android registry. It never advertises CarPlay or Android Auto:
/// both are explicitly unsupported and out of scope without Apple entitlement / platform support.
public struct CapabilityRegistry: Sendable {

    public static let ciBuildEvidence =
        "GitHub Actions run 44966996220 (baseline ed63730, before Phase 1). Current tree not built."

    public let capabilities: [Capability]

    public init() {
        capabilities = Self.defaults()
    }

    public init(capabilities: [Capability]) {
        self.capabilities = capabilities
    }

    public func get(_ id: CapabilityId) -> Capability? {
        capabilities.first { $0.id == id }
    }

    public static func defaults() -> [Capability] {
        [
            Capability(
                id: .bydWmpTransport,
                state: .available,
                evidence: ciBuildEvidence
            ),
            Capability(
                id: .wifiDirect,
                state: .notVerified,
                evidence: "Bonjour discovery exists; no physical Wi-Fi Direct group test recorded."
            ),
            Capability(
                id: .miracast,
                state: .notVerified,
                evidence: "No iOS Miracast sender implementation; AirPlay route is unrelated."
            ),
            Capability(
                id: .screenMirroring,
                state: .requiresPermission,
                evidence: "ReplayKit broadcast extension; user must start the broadcast."
            ),
            Capability(
                id: .reverseTouch,
                state: .unsupported,
                evidence: "iOS does not allow an app to inject touches into other apps.",
                notes: "Blocked by platform policy; no workaround is claimed."
            ),
            Capability(
                id: .audioStreaming,
                state: .notVerified,
                evidence: "AAC encoder exists; no head-unit audio test recorded."
            ),
            Capability(
                id: .hevcEncoding,
                state: .notVerified,
                evidence: "VideoToolbox HEVC path exists; no H.265 pair test recorded."
            ),
            Capability(
                id: .carplay,
                state: .unsupported,
                evidence: "No CarPlay entitlement, MFi hardware, or CarPlay code.",
                notes: "Out of scope without Apple approval."
            ),
            Capability(
                id: .androidAuto,
                state: .unsupported,
                evidence: "No Android Auto implementation."
            ),
            Capability(
                id: .usbVideo,
                state: .unsupported,
                evidence: "No USB video transport implementation."
            ),
            Capability(
                id: .bluetoothVideo,
                state: .unsupported,
                evidence: "No Bluetooth video transport implementation."
            )
        ]
    }
}
