import Foundation

/// Cross-process state bridge between the main iOS app and ReplayKit BroadcastExtension
/// backed by App Group UserDefaults and Darwin notifications.
public final class SharedSessionStore: @unchecked Sendable {

    public static let appGroupId = "group.com.byd.mirror.shared"
    public static let orientationChangedNotification = "com.byd.mirror.darwin.orientationChanged" as CFString
    public static let stopBroadcastNotification = "com.byd.mirror.darwin.stopBroadcast" as CFString
    public static let pairingUpdatedNotification = "com.byd.mirror.darwin.pairingUpdated" as CFString
    public static let pairingConfirmedNotification = "com.byd.mirror.darwin.pairingConfirmed" as CFString
    public static let broadcastStateChangedNotification = "com.byd.mirror.darwin.broadcastState" as CFString

    private enum Keys {
        static let deviceId = "byd.target.deviceId"
        static let host = "byd.target.host"
        static let controlPort = "byd.target.controlPort"
        static let mediaPort = "byd.target.mediaPort"
        static let orientation = "byd.stream.orientation"
        static let pairingPin = "byd.stream.pairingPin"
        static let fingerprint = "byd.stream.fingerprint"
        static let deviceName = "byd.target.deviceName"
        static let broadcastActive = "byd.stream.active"
        static let broadcastError = "byd.stream.error"
        static let trustedDeviceIds = "byd.trusted.deviceIds"
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

    private func postDarwinNotification(_ name: CFString) {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        CFNotificationCenterPostNotification(center, CFNotificationName(name), nil, nil, true)
    }
}
