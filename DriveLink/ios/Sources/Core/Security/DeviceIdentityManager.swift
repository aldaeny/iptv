import Foundation
import CryptoKit
import Security

/// Installation-scoped P-256 identity stored in the Keychain.
///
/// The private key never leaves the device; only the X9.63 public point and a session-bound DER
/// signature are sent. This is not Apple attestation: the receiver learns "the same install".
/// If the Keychain is unavailable (for example an extension without access), `createProof` returns
/// nil and the receiver must fall back to explicit manual approval.
public final class DeviceIdentityManager: @unchecked Sendable {

    public static let shared = DeviceIdentityManager()

    private let service = "app.drivelink.cast.device-identity"
    private let account = "identity-v1"
    private let signingPrefix = "BYD-WMP-IDENTITY-V1|"

    private init() {}

    public var hasIdentity: Bool {
        (try? loadOrCreateKey()) != nil
    }

    public func publicKeyX963() -> Data? {
        guard let key = try? loadOrCreateKey() else { return nil }
        return key.publicKey.x963Representation
    }

    /// Wire format: `[1B version][2B pubLen][65B X9.63][2B sigLen][DER signature]`.
    public func createProof(sessionFingerprint: String) -> Data? {
        guard let key = try? loadOrCreateKey() else { return nil }
        let payload = Data((signingPrefix + sessionFingerprint).utf8)
        guard let signature = try? key.signature(for: payload).derRepresentation else {
            return nil
        }
        let publicKey = key.publicKey.x963Representation
        guard publicKey.count <= Int(UInt16.max), signature.count <= Int(UInt16.max) else {
            return nil
        }
        var proof = Data(capacity: 5 + publicKey.count + signature.count)
        proof.append(1)
        appendUInt16(UInt16(publicKey.count), to: &proof)
        proof.append(publicKey)
        appendUInt16(UInt16(signature.count), to: &proof)
        proof.append(signature)
        return proof
    }

    /// Revocation helper; also used by tests that must not leave a persistent identity.
    public func deleteIdentity() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }

    private func appendUInt16(_ value: UInt16, to data: inout Data) {
        data.append(UInt8((value >> 8) & 0xFF))
        data.append(UInt8(value & 0xFF))
    }

    private func loadOrCreateKey() throws -> P256.Signing.PrivateKey {
        if let stored = readKeychain(),
           let key = try? P256.Signing.PrivateKey(rawRepresentation: stored) {
            return key
        }
        let key = P256.Signing.PrivateKey()
        try writeKeychain(key.rawRepresentation)
        return key
    }

    private func readKeychain() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else { return nil }
        return result as? Data
    }

    private func writeKeychain(_ data: Data) throws {
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(base as CFDictionary)
        var query = base
        query[kSecValueData as String] = data
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        query[kSecAttrSynchronizable as String] = false
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw NSError(domain: "app.drivelink.cast.identity", code: Int(status))
        }
    }
}
