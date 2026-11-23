import Foundation

public enum ProtocolOpcode: UInt8, Sendable {
    case helloSyn = 0x01
    case helloAck = 0x02
    case keyExchangeInit = 0x03
    case keyExchangeResp = 0x04
    case capabilitiesReq = 0x05
    case capabilitiesResp = 0x06
    case sessionStart = 0x07
    case sessionStop = 0x08
    case orientationChange = 0x09
    case deviceIdentity = 0x0A
    case videoNalu = 0x10
    case audioAac = 0x11
    case keyframeRequest = 0x12
    case videoNaluFrag = 0x13
    case touchEvent = 0x14
    case heartbeatPing = 0x20
    case heartbeatPong = 0x21
    case qosTelemetry = 0x22
    case thermalThrottle = 0x23
}

public struct BydPacket: Sendable {
    public let opcode: ProtocolOpcode
    public let sequence: UInt32
    public let timestampUs: UInt32
    public let payload: Data

    public init(opcode: ProtocolOpcode, sequence: UInt32, timestampUs: UInt32, payload: Data) {
        self.opcode = opcode
        self.sequence = sequence
        self.timestampUs = timestampUs
        self.payload = payload
    }
}

public enum BydProtocolError: Error {
    case invalidMagic
    case unsupportedVersion
    case unknownOpcode(UInt8)
    case invalidSequence
    case invalidPayloadLength
    case truncatedFrame
}

/// API wrapper that exposes BYD DiLink head unit as a low-latency wireless external display sink.
public final class BydProtocolWrapper: @unchecked Sendable {

    public static let magicBytes: UInt16 = 0x4259 // "BY"
    public static let protocolVersion: UInt8 = 0x01
    public static let headerSizeBytes = 16
    public static let encryptionOverheadBytes = 28 // 12-byte GCM nonce + 16-byte tag
    public static let udpChunkLimitBytes = 1_150
    public static let tcpChunkLimitBytes = 32_000

    private let crypto: ChannelCryptoManager
    private var sequence: UInt32 = 0
    private var udpSequence: UInt32 = 0
    private var frameId: UInt32 = 0
    private let lock = NSLock()
    public private(set) var isEncrypted = false
    public var pairingPin: String { crypto.pairingPin }

    public init(crypto: ChannelCryptoManager = ChannelCryptoManager()) {
        self.crypto = crypto
    }

    public func buildHeader(
        opcode: ProtocolOpcode,
        sequence: UInt32,
        timestampUs: UInt32,
        payloadLength: UInt32
    ) -> Data {
        var data = Data(capacity: Self.headerSizeBytes)
        var magicBE = Self.magicBytes.bigEndian
        var seqBE = sequence.bigEndian
        var tsBE = timestampUs.bigEndian
        var lenBE = payloadLength.bigEndian

        data.append(Data(bytes: &magicBE, count: 2))
        data.append(Self.protocolVersion)
        data.append(opcode.rawValue)
        data.append(Data(bytes: &seqBE, count: 4))
        data.append(Data(bytes: &tsBE, count: 4))
        data.append(Data(bytes: &lenBE, count: 4))
        return data
    }

    public func encodeFrame(
        opcode: ProtocolOpcode,
        rawPayload: Data,
        timestampUs: UInt32 = 0,
        encrypt: Bool,
        isUdpMedia: Bool = false
    ) throws -> Data {
        lock.lock()
        let seq: UInt32
        if isUdpMedia {
            udpSequence &+= 1
            seq = udpSequence
        } else {
            sequence &+= 1
            seq = sequence
        }
        lock.unlock()

        let wireLength = UInt32(rawPayload.count + (encrypt ? Self.encryptionOverheadBytes : 0))
        let header = buildHeader(
            opcode: opcode,
            sequence: seq,
            timestampUs: timestampUs,
            payloadLength: wireLength
        )
        let wirePayload = encrypt ? try crypto.encrypt(plaintext: rawPayload, aadHeader: header) : rawPayload
        return header + wirePayload
    }

    public func encodeVideoUdpFrames(
        annexBNalu: Data,
        timestampUs: UInt32,
        encrypt: Bool
    ) throws -> [Data] {
        if annexBNalu.count <= Self.udpChunkLimitBytes {
            return [try encodeFrame(opcode: .videoNalu, rawPayload: annexBNalu, timestampUs: timestampUs, encrypt: encrypt, isUdpMedia: true)]
        }

        lock.lock()
        frameId &+= 1
        let currentFrameId = frameId
        lock.unlock()

        let chunkSize = Self.udpChunkLimitBytes
        let totalFrags = UInt16((annexBNalu.count + chunkSize - 1) / chunkSize)
        var packets: [Data] = []
        packets.reserveCapacity(Int(totalFrags))

        for fragIdx in 0..<Int(totalFrags) {
            let start = fragIdx * chunkSize
            let end = min(annexBNalu.count, start + chunkSize)
            var fragPayload = Data(capacity: 8 + (end - start))
            var fIdBE = currentFrameId.bigEndian
            var idxBE = UInt16(fragIdx).bigEndian
            var totalBE = totalFrags.bigEndian
            fragPayload.append(Data(bytes: &fIdBE, count: 4))
            fragPayload.append(Data(bytes: &idxBE, count: 2))
            fragPayload.append(Data(bytes: &totalBE, count: 2))
            fragPayload.append(annexBNalu.subdata(in: start..<end))

            packets.append(
                try encodeFrame(
                    opcode: .videoNaluFrag,
                    rawPayload: fragPayload,
                    timestampUs: timestampUs,
                    encrypt: encrypt,
                    isUdpMedia: true
                )
            )
        }
        return packets
    }

    public func decodeFrame(_ frameData: Data, decrypt: Bool) throws -> BydPacket {
        guard frameData.count >= Self.headerSizeBytes else {
            throw BydProtocolError.truncatedFrame
        }
        let header = frameData.prefix(Self.headerSizeBytes)
        let magic = UInt16(header[0]) << 8 | UInt16(header[1])
        guard magic == Self.magicBytes else {
            throw BydProtocolError.invalidMagic
        }
        let version = header[2]
        guard version == Self.protocolVersion else {
            throw BydProtocolError.unsupportedVersion
        }
        guard let opcode = ProtocolOpcode(rawValue: header[3]) else {
            throw BydProtocolError.unknownOpcode(header[3])
        }

        let seq = readUInt32BE(header, offset: 4)
        guard seq > 0 && seq <= UInt32(Int32.max) else {
            throw BydProtocolError.invalidSequence
        }
        let ts = readUInt32BE(header, offset: 8)
        let payloadLen = Int(readUInt32BE(header, offset: 12))
        guard payloadLen >= 0 && payloadLen <= 65_536 else {
            throw BydProtocolError.invalidPayloadLength
        }

        guard frameData.count >= Self.headerSizeBytes + payloadLen else {
            throw BydProtocolError.truncatedFrame
        }

        let wirePayload = frameData.subdata(in: Self.headerSizeBytes..<(Self.headerSizeBytes + payloadLen))
        let payload = decrypt ? try crypto.decrypt(encryptedPayload: wirePayload, aadHeader: header) : wirePayload

        return BydPacket(opcode: opcode, sequence: seq, timestampUs: ts, payload: payload)
    }

    public func beginKeyExchangePayload() -> Data {
        crypto.generateEphemeralPublicKey()
    }

    public func completeKeyExchange(responsePayload: Data) throws -> String {
        guard responsePayload.count > 16 else {
            throw BydProtocolError.truncatedFrame
        }
        let salt = responsePayload.prefix(16)
        let peerPubKey = responsePayload.suffix(from: 16)
        try crypto.deriveSessionKey(peerPublicKeyData: peerPubKey, sessionSalt: salt)
        isEncrypted = true
        return crypto.sessionFingerprint
    }

    public func encodeVideoTcpFrames(
        annexBNalu: Data,
        timestampUs: UInt32,
        encrypt: Bool
    ) throws -> [Data] {
        if annexBNalu.count <= Self.tcpChunkLimitBytes {
            return [try encodeFrame(opcode: .videoNalu, rawPayload: annexBNalu, timestampUs: timestampUs, encrypt: encrypt)]
        }

        lock.lock()
        frameId &+= 1
        let currentFrameId = frameId
        lock.unlock()

        let chunkSize = Self.tcpChunkLimitBytes
        let totalFrags = UInt16((annexBNalu.count + chunkSize - 1) / chunkSize)
        var packets: [Data] = []
        packets.reserveCapacity(Int(totalFrags))

        for fragIdx in 0..<Int(totalFrags) {
            let start = fragIdx * chunkSize
            let end = min(annexBNalu.count, start + chunkSize)
            var fragPayload = Data(capacity: 8 + (end - start))
            var fIdBE = currentFrameId.bigEndian
            var idxBE = UInt16(fragIdx).bigEndian
            var totalBE = totalFrags.bigEndian
            fragPayload.append(Data(bytes: &fIdBE, count: 4))
            fragPayload.append(Data(bytes: &idxBE, count: 2))
            fragPayload.append(Data(bytes: &totalBE, count: 2))
            fragPayload.append(annexBNalu.subdata(in: start..<end))

            packets.append(
                try encodeFrame(
                    opcode: .videoNaluFrag,
                    rawPayload: fragPayload,
                    timestampUs: timestampUs,
                    encrypt: encrypt
                )
            )
        }
        return packets
    }

    public func makeOrientationFrame(_ orientation: DisplayOrientation) throws -> Data {
        let byte: UInt8 = orientation == .landscape ? 0x01 : 0x00
        return try encodeFrame(opcode: .orientationChange, rawPayload: Data([byte]), encrypt: isEncrypted)
    }

    public func reset() {
        lock.lock()
        sequence = 0
        udpSequence = 0
        lock.unlock()
        isEncrypted = false
        crypto.reset()
    }

    private func readUInt32BE(_ data: Data, offset: Int) -> UInt32 {
        (UInt32(data[offset]) << 24) |
        (UInt32(data[offset + 1]) << 16) |
        (UInt32(data[offset + 2]) << 8) |
        UInt32(data[offset + 3])
    }
}
