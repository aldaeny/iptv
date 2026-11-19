import CryptoKit
import Foundation

public enum ChannelCryptoError: Error {
    case missingLocalKey
    case missingSessionKey
    case invalidCiphertext
}

/// Manages end-to-end encrypted communication between iPhone and BYD DiLink display:
/// - Ephemeral ECDH P-256 key agreement
/// - HKDF-SHA256 session key derivation
/// - AES-256-GCM authenticated encryption with 16-byte wire header as AAD
public final class ChannelCryptoManager: @unchecked Sendable {

    private var privateKey: P256.KeyAgreement.PrivateKey?
    private var sessionKey: SymmetricKey?
    public private(set) var sessionFingerprint: String = "UNVERIFIED"
    public private(set) var pairingPin: String = "000000"

    private static let kdfInfo = Data("BYD-MIRROR-SESSION-KEY-V1".utf8)

    public init() {}

    public func generateEphemeralPublicKey() -> Data {
        let key = P256.KeyAgreement.PrivateKey()
        privateKey = key
        return key.publicKey.x963Representation
    }

    public func deriveSessionKey(peerPublicKeyData: Data, sessionSalt: Data) throws {
        guard let privateKey else {
            throw ChannelCryptoError.missingLocalKey
        }
        let peerPublicKey = try P256.KeyAgreement.PublicKey(x963Representation: peerPublicKeyData)
        let sharedSecret = try privateKey.sharedSecretFromKeyAgreement(with: peerPublicKey)

        let symmetricKey = sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: sessionSalt,
            sharedInfo: Self.kdfInfo,
            outputByteCount: 32
        )
        sessionKey = symmetricKey

        let keyData = symmetricKey.withUnsafeBytes { Data($0) }
        let digest = Array(SHA256.hash(data: keyData))
        sessionFingerprint = digest.prefix(4).map { String(format: "%02X", $0) }.joined(separator: "-")
        let pinInt = ((Int(digest[0]) & 0x7F) << 16) |
            (Int(digest[1]) << 8) |
            Int(digest[2])
        pairingPin = String(format: "%06d", pinInt % 1_000_000)
    }

    public func encrypt(plaintext: Data, aadHeader: Data) throws -> Data {
        guard let sessionKey else {
            throw ChannelCryptoError.missingSessionKey
        }
        let sealed = try AES.GCM.seal(plaintext, using: sessionKey, authenticating: aadHeader)
        guard let combined = sealed.combined else {
            throw ChannelCryptoError.invalidCiphertext
        }
        return combined // [12-byte nonce] + [ciphertext] + [16-byte tag]
    }

    public func decrypt(encryptedPayload: Data, aadHeader: Data) throws -> Data {
        guard let sessionKey else {
            throw ChannelCryptoError.missingSessionKey
        }
        let sealedBox = try AES.GCM.SealedBox(combined: encryptedPayload)
        return try AES.GCM.open(sealedBox, using: sessionKey, authenticating: aadHeader)
    }

    public func reset() {
        privateKey = nil
        sessionKey = nil
        sessionFingerprint = "UNVERIFIED"
        pairingPin = "000000"
    }
}
