import XCTest
@testable import BydMirrorCore

final class BydMirrorCoreTests: XCTestCase {

    func testInvalidSavedPortsFallBackAndNewSessionClearsConfirmation() throws {
        let suite = "DriveLink.test." + UUID().uuidString
        let prefs = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { prefs.removePersistentDomain(forName: suite) }
        let store = SharedSessionStore(defaults: prefs)
        prefs.set(70_000, forKey: "byd.target.controlPort")
        prefs.set(-1, forKey: "byd.target.mediaPort")
        XCTAssertEqual(store.loadTargetEndpoint().controlPort, 47810)
        XCTAssertEqual(store.loadTargetEndpoint().mediaPort, 47811)
        store.confirmPairingAndNotify(deviceId: "test", fingerprint: "prior")
        store.savePairingVerification(pin: "123456", fingerprint: "current")
        XCTAssertNil(store.confirmedFingerprint)
    }

    func testKeyExchangeAndEncryptedRoundTrip() throws {
        let phoneWrapper = BydProtocolWrapper(crypto: ChannelCryptoManager())
        let bydCrypto = ChannelCryptoManager()

        let phonePub = phoneWrapper.beginKeyExchangePayload()
        let bydPub = bydCrypto.generateEphemeralPublicKey()
        let salt = Data((0..<16).map { UInt8($0) })

        let keyExchangeResp = salt + bydPub
        let phoneFingerprint = try phoneWrapper.completeKeyExchange(responsePayload: keyExchangeResp)
        try bydCrypto.deriveSessionKey(peerPublicKeyData: phonePub, sessionSalt: salt)

        XCTAssertEqual(phoneFingerprint, bydCrypto.sessionFingerprint)

        let rawPayload = Data("BYD-H265-FRAME".utf8)
        let encoded = try phoneWrapper.encodeFrame(
            opcode: .videoNalu,
            rawPayload: rawPayload,
            timestampUs: 12345,
            encrypt: true
        )

        let receiverWrapper = BydProtocolWrapper(crypto: bydCrypto)
        let decoded = try receiverWrapper.decodeFrame(encoded, decrypt: true)

        XCTAssertEqual(decoded.opcode, .videoNalu)
        XCTAssertEqual(decoded.timestampUs, 12345)
        XCTAssertEqual(decoded.payload, rawPayload)
    }

    func testQualityNegotiatorThrottlesWhenThermalCritical() {
        let negotiator = QualityNegotiator()
        let base = DisplayProfile.diLink15_6FHD
        let hot = TelemetryMetrics(rttMs: 90, packetLossPercent: 6.5, thermalLevel: .critical)

        let adapted = negotiator.adaptProfile(base, telemetry: hot)
        XCTAssertEqual(adapted.targetFps, 24)
        XCTAssertEqual(adapted.baseWidth, 1280)
        XCTAssertEqual(adapted.baseHeight, 720)
        XCTAssertLessThan(adapted.bitrateMbps, base.bitrateMbps)
    }

    func testVideoUdpMtuFragmentationProducesValidFragments() throws {
        let wrapper = BydProtocolWrapper(crypto: ChannelCryptoManager())
        let largeNalu = Data(repeating: 0xAB, count: 3000)
        let packets = try wrapper.encodeVideoUdpFrames(
            annexBNalu: largeNalu,
            timestampUs: 999,
            encrypt: false
        )

        XCTAssertEqual(packets.count, 3)
        for (idx, packetData) in packets.enumerated() {
            let decoded = try wrapper.decodeFrame(packetData, decrypt: false)
            XCTAssertEqual(decoded.opcode, .videoNaluFrag)
            XCTAssertEqual(decoded.payload[5], UInt8(idx))
            XCTAssertEqual(decoded.payload[7], 3)
        }
    }

    func testHevcFallbackToH264() throws {
        let wrapper = BydProtocolWrapper(crypto: ChannelCryptoManager())
        
        // Test that session start with HEVC mime type works
        let hevcStart = try wrapper.encodeFrame(
            opcode: .sessionStart,
            rawPayload: Data("video/hevc".utf8),
            encrypt: false
        )
        let hevcDecoded = try wrapper.decodeFrame(hevcStart, decrypt: false)
        XCTAssertEqual(hevcDecoded.opcode, .sessionStart)
        XCTAssertEqual(String(data: hevcDecoded.payload, encoding: .utf8), "video/hevc")
        
        // Test that session start with H264 mime type works
        let h264Start = try wrapper.encodeFrame(
            opcode: .sessionStart,
            rawPayload: Data("video/avc".utf8),
            encrypt: false
        )
        let h264Decoded = try wrapper.decodeFrame(h264Start, decrypt: false)
        XCTAssertEqual(h264Decoded.opcode, .sessionStart)
        XCTAssertEqual(String(data: h264Decoded.payload, encoding: .utf8), "video/avc")
    }

}
