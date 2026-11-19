import Foundation

/// Cross-process state bridge between the main iOS app and ReplayKit BroadcastExtension
/// backed by App Group UserDefaults and Darwin notifications.
public final class SharedSessionStore: @unchecked Sendable {

    public static let appGroupId = "group.app.drivelink.cast.shared"
    public static let orientationChangedNotification = "app.drivelink.cast.darwin.orientationChanged" as CFString
    public static let stopBroadcastNotification = "app.drivelink.cast.darwin.stopBroadcast" as CFString
    public static let pairingUpdatedNotification = "app.drivelink.cast.darwin.pairingUpdated" as CFString
    public static let pairingConfirmedNotification = "app.drivelink.cast.darwin.pairingConfirmed" as CFString
    public static let broadcastStateChangedNotification = "app.drivelink.cast.darwin.broadcastState" as CFString
    public static let telemetryUpdatedNotification = "app.drivelink.cast.darwin.telemetryUpdated" as CFString

    private enum Keys {
        static let deviceId = "byd.target.deviceId"
        static let host = "byd.target.host"
        static let controlPort = "byd.target.controlPort"
        static let mediaPort = "byd.target.mediaPort"
        static let orientation = "byd.stream.orientation"
        static let preferHevc = "byd.stream.preferHevc"
        static let profileId = "byd.stream.profileId"
        static let pairingPin = "byd.stream.pairingPin"
        static let fingerprint = "byd.stream.fingerprint"
        static let deviceName = "byd.target.deviceName"
        static let broadcastActive = "byd.stream.active"
        static let broadcastError = "byd.stream.error"
        static let trustedDeviceIds = "byd.trusted.deviceIds"
        static let telemetryFps = "byd.telemetry.fps"
        static let telemetryRtt = "byd.telemetry.rtt"
        static let telemetryLoss = "byd.telemetry.loss"
        static let telemetryBitrate = "byd.telemetry.bitrate"
    }

    private let defaults: UserDefaults

    public init(defaults: UserDefaults? = UserDefaults(suiteName: SharedSessionStore.appGroupId)) {
        self.defaults = defaults ?? .standard
    }

    public func saveTargetDevice(_ device: BydDevice) {
        defaults.set(device.id, forKey: Keys.deviceId)
        defaults.set(device.hostAddress, forKey: Keys.host)
        defaults.set(Int(device.controlPort), forKey: Keys.controlPort)
        defaults.set(Int(device.mediaPort), forKey: Keys.mediaPort)
        defaults.set(device.displayName, forKey: Keys.deviceName)
    }

    public func loadTargetEndpoint() -> (id: String, host: String, controlPort: UInt16, mediaPort: UInt16, name: String) {
        let id = defaults.string(forKey: Keys.deviceId) ?? "byd-default"
        let host = defaults.string(forKey: Keys.host) ?? "192.168.49.1"
        let cPort = UInt16(defaults.integer(forKey: Keys.controlPort) > 0 ? defaults.integer(forKey: Keys.controlPort) : 47810)
        let mPort = UInt16(defaults.integer(forKey: Keys.mediaPort) > 0 ? defaults.integer(forKey: Keys.mediaPort) : 47811)
        let name = defaults.string(forKey: Keys.deviceName) ?? "BYD DiLink"
        return (id, host, cPort, mPort, name)
    }

    public func saveOrientation(_ orientation: DisplayOrientation) {
        defaults.set(orientation.rawValue, forKey: Keys.orientation)
        postDarwinNotification(Self.orientationChangedNotification)
    }

    public func loadOrientation() -> DisplayOrientation {
        guard let raw = defaults.string(forKey: Keys.orientation),
              let parsed = DisplayOrientation(rawValue: raw) else {
            return .landscape
        }
        return parsed
    }

    public func savePairingVerification(pin: String, fingerprint: String) {
        defaults.set(pin, forKey: Keys.pairingPin)
        defaults.set(fingerprint, forKey: Keys.fingerprint)
        postDarwinNotification(Self.pairingUpdatedNotification)
    }

    public func loadPairingVerification() -> (pin: String, fingerprint: String)? {
        guard let pin = defaults.string(forKey: Keys.pairingPin),
              let fp = defaults.string(forKey: Keys.fingerprint) else {
            return nil
        }
        return (pin, fp)
    }

    public func confirmPairingAndNotify(deviceId: String) {
        var ids = Set(defaults.stringArray(forKey: Keys.trustedDeviceIds) ?? [])
        ids.insert(deviceId)
        defaults.set(Array(ids), forKey: Keys.trustedDeviceIds)
        postDarwinNotification(Self.pairingConfirmedNotification)
    }

    public func isDeviceTrustedInGroup(_ deviceId: String) -> Bool {
        (defaults.stringArray(forKey: Keys.trustedDeviceIds) ?? []).contains(deviceId)
    }

    public func setBroadcastState(active: Bool, error: String? = nil) {
        defaults.set(active, forKey: Keys.broadcastActive)
        defaults.set(error, forKey: Keys.broadcastError)
        postDarwinNotification(Self.broadcastStateChangedNotification)
    }

    public func loadBroadcastState() -> (active: Bool, error: String?) {
        (defaults.bool(forKey: Keys.broadcastActive), defaults.string(forKey: Keys.broadcastError))
    }

    public func requestStopBroadcast() {
        postDarwinNotification(Self.stopBroadcastNotification)
    }

    public func saveStreamSettings(preferHevc: Bool, profileId: String) {
        defaults.set(preferHevc, forKey: Keys.preferHevc)
        defaults.set(profileId, forKey: Keys.profileId)
    }

    public func loadStreamSettings() -> (preferHevc: Bool, profileId: String) {
        let hasHevcKey = defaults.object(forKey: Keys.preferHevc) != nil
        let hevc = hasHevcKey ? defaults.bool(forKey: Keys.preferHevc) : false
        let pid = defaults.string(forKey: Keys.profileId) ?? "auto"
        return (hevc, pid)
    }

    public func saveTelemetry(fps: Int, rttMs: Int, packetLossPercent: Double, bitrateMbps: Double) {
        defaults.set(fps, forKey: Keys.telemetryFps)
        defaults.set(rttMs, forKey: Keys.telemetryRtt)
        defaults.set(packetLossPercent, forKey: Keys.telemetryLoss)
        defaults.set(bitrateMbps, forKey: Keys.telemetryBitrate)
        postDarwinNotification(Self.telemetryUpdatedNotification)
    }

    public func loadTelemetry() -> (fps: Int, rttMs: Int, packetLossPercent: Double, bitrateMbps: Double) {
        let fps = defaults.integer(forKey: Keys.telemetryFps)
        let rtt = defaults.integer(forKey: Keys.telemetryRtt)
        let loss = defaults.double(forKey: Keys.telemetryLoss)
        let br = defaults.double(forKey: Keys.telemetryBitrate)
        return (fps > 0 ? fps : 60, rtt, loss, br > 0 ? br : 12.0)
    }

    private func postDarwinNotification(_ name: CFString) {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        CFNotificationCenterPostNotification(center, CFNotificationName(name), nil, nil, true)
    }
}
