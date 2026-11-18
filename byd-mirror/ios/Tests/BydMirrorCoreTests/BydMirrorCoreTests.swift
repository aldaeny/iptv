import XCTest
@testable import BydMirrorCore

final class BydMirrorCoreTests: XCTestCase {

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
}
