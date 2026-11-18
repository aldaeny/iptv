import Foundation
import Security

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

/// Persists verified BYD vehicles in the iOS Keychain (shared via App Group when configured).
public final class KeychainTrustedVehiclesStore: @unchecked Sendable {

    private let service = "com.byd.mirror.trusted-vehicles"
    private let account = "vehicles-list"
    private let accessGroup: String?

    public init(accessGroup: String? = nil) {
        self.accessGroup = accessGroup
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
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        if let accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else {
            return []
        }
        return (try? JSONDecoder().decode([TrustedVehicle].self, from: data)) ?? []
    }

    public func remove(deviceId: String) {
        let updated = loadAll().filter { $0.id != deviceId }
        persist(updated)
    }

    private func persist(_ list: [TrustedVehicle]) {
        guard let data = try? JSONEncoder().encode(list) else { return }
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        if let accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        SecItemDelete(query as CFDictionary)
        var addQuery = query
        addQuery[kSecValueData as String] = data
        SecItemAdd(addQuery as CFDictionary, nil)
    }
}
