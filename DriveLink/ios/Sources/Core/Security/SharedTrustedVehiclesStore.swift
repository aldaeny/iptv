import Foundation

public struct TrustedVehicleRecord: Codable, Equatable, Sendable {
    public let fingerprint: String
    public let displayName: String
    public let pairedAtEpochMs: Int64

    public init(fingerprint: String, displayName: String, pairedAtEpochMs: Int64) {
        self.fingerprint = fingerprint
        self.displayName = displayName
        self.pairedAtEpochMs = pairedAtEpochMs
    }
}

/// App-Group-backed trust records shared by the app and its extension.
///
/// The previous Keychain store referenced an App Group identifier as a Keychain access group
/// without that entitlement; that silently failed. This store deliberately uses UserDefaults in
/// the already-declared App Group and persists only public fingerprints, never private material.
public final class SharedTrustedVehiclesStore: @unchecked Sendable {

    private let defaults: UserDefaults
    private let key = "drivelink.trusted.vehicles.v1"

    public init(defaults: UserDefaults? = UserDefaults(suiteName: SharedSessionStore.appGroupId)) {
        self.defaults = defaults ?? .standard
    }

    public func all() -> [TrustedVehicleRecord] {
        guard let data = defaults.data(forKey: key),
              let records = try? JSONDecoder().decode([TrustedVehicleRecord].self, from: data) else {
            return []
        }
        return records.sorted { $0.displayName < $1.displayName }
    }

    public func isTrusted(fingerprint: String) -> Bool {
        let normalized = fingerprint.uppercased()
        return all().contains { $0.fingerprint == normalized }
    }

    public func trust(fingerprint: String, displayName: String, nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        let normalized = fingerprint.uppercased()
        guard !normalized.isEmpty else { return }
        var records = all().filter { $0.fingerprint != normalized }
        records.append(
            TrustedVehicleRecord(
                fingerprint: normalized,
                displayName: displayName.isEmpty ? "BYD Phone" : displayName,
                pairedAtEpochMs: nowMs
            )
        )
        persist(records)
    }

    public func remove(fingerprint: String) {
        let normalized = fingerprint.uppercased()
        persist(all().filter { $0.fingerprint != normalized })
    }

    public func clear() {
        defaults.removeObject(forKey: key)
    }

    private func persist(_ records: [TrustedVehicleRecord]) {
        guard let data = try? JSONEncoder().encode(records) else { return }
        defaults.set(data, forKey: key)
    }
}
