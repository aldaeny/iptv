import Foundation

public struct TrustedVehicle: Codable, Equatable, Sendable {
    public let id: String
    public let displayName: String
    public let fingerprint: String
    public let pairedAtEpoch: TimeInterval

    public init(id: String, displayName: String, fingerprint: String, pairedAtEpoch: TimeInterval) {
        self.id = id
        self.displayName = displayName
        self.fingerprint = fingerprint
        self.pairedAtEpoch = pairedAtEpoch
    }
}

/// Persists verified BYD vehicles in the App Group.
///
/// This type used to write to the Keychain with an App Group identifier passed as
/// `kSecAttrAccessGroup`. That is not a Keychain access group (it needs a dedicated entitlement),
/// so reads could silently return nothing and the extension could not see the app's list. The
/// records contain only public ids/fingerprints, so an App Group `UserDefaults` store is the
/// correct shared container. The initializer keeps its `accessGroup` parameter for source
/// compatibility but deliberately ignores it.
public final class KeychainTrustedVehiclesStore: @unchecked Sendable {

    private let defaults: UserDefaults
    private let key = "drivelink.trusted.vehicles.v2"

    public init(accessGroup: String? = nil) {
        _ = accessGroup
        self.defaults = UserDefaults(suiteName: SharedSessionStore.appGroupId) ?? .standard
    }

    public func save(_ vehicle: TrustedVehicle) {
        var list = loadAll().filter { $0.id != vehicle.id }
        list.append(vehicle)
        persist(list)
    }

    public func isTrusted(deviceId: String) -> Bool {
        loadAll().contains { $0.id == deviceId }
    }

    public func loadAll() -> [TrustedVehicle] {
        guard let data = defaults.data(forKey: key),
              let list = try? JSONDecoder().decode([TrustedVehicle].self, from: data) else {
            return []
        }
        return list.sorted { $0.displayName < $1.displayName }
    }

    public func remove(deviceId: String) {
        persist(loadAll().filter { $0.id != deviceId })
    }

    private func persist(_ list: [TrustedVehicle]) {
        guard let data = try? JSONEncoder().encode(list) else { return }
        defaults.set(data, forKey: key)
    }
}
