import Foundation

#if canImport(ReplayKit) && !os(macOS)
import ReplayKit
#endif

/// Single-process in-app mirroring controller using ReplayKit RPScreenRecorder.
/// Requires ZERO App Extensions, ZERO App Groups, and ZERO paid Apple Developer entitlements
/// so it installs and runs cleanly with a $0 free Apple ID via SideStore / AltStore / Sideloadly.
public final class DirectMirrorSession: @unchecked Sendable {

    private let protocolWrapper = BydProtocolWrapper()
    private let transport = PeerTransportClient()
    private var videoEncoder: HardwareVideoEncoder?
    private var audioEncoder: AudioAacEncoder?
    private var pingTimer: DispatchSourceTimer?

    private var activeDevice: BydDevice?
    private var activeProfile: DisplayProfile = .diLink15_6FHD
    private var activeOrientation: DisplayOrientation = .landscape
    private var isDeviceTrusted = false
    private var capabilitiesReady = false
    private var streamingActive = false
    private var userStopped = false
    private var reconnectAttempts = 0
    private var lastRttMs = 0
    private var lastFps = 60
    private var lastLossPct: Float = 0
    private var sessionFingerprint = "ECDH-P256"
    private var sentVideoFrames = 0
    private var lastRxSeq: UInt32 = 0
    private var useTcpMediaFallback = false

    public var onPairingVerification: (@Sendable (BydDevice, String, String) -> Void)?
    public var onStreamingStarted: (@Sendable (BydDevice, DisplayProfile, String) -> Void)?
    public var onTelemetry: (@Sendable (Int, Int, Float, Float) -> Void)?
    public var onReconnecting: (@Sendable (BydDevice, Int) -> Void)?
    public var onDisconnected: (@Sendable (String?) -> Void)?

    public init() {}

    public func start(
        device: BydDevice,
        profile: DisplayProfile,
        orientation: DisplayOrientation,
        trusted: Bool
    ) {
        stop(notify: false)
        userStopped = false
        reconnectAttempts = 0
        activeDevice = device
        activeProfile = profile
        activeOrientation = orientation
        isDeviceTrusted = trusted
        connectTransport()
    }

    public func confirmPairingAndStartStream(fingerprint: String) {
        guard protocolWrapper.isEncrypted, fingerprint == sessionFingerprint, capabilitiesReady else { return }
        isDeviceTrusted = true
        activateMediaSession()
    }

    public func updateOrientation(_ orientation: DisplayOrientation) {
        activeOrientation = orientation
        try? videoEncoder?.configure(profile: activeProfile, orientation: orientation)
        if streamingActive, let frame = try? protocolWrapper.makeOrientationFrame(orientation) {
            transport.sendControl(frame)
        }
    }

    public func updateProfile(_ profile: DisplayProfile) {
        activeProfile = profile
        try? videoEncoder?.configure(profile: profile, orientation: activeOrientation)
        if streamingActive {
            let mime = profile.codec == .h265 ? "video/hevc" : "video/avc"
            if let startFrame = try? protocolWrapper.encodeFrame(opcode: .sessionStart, rawPayload: Data(mime.utf8), encrypt: true) {
                transport.sendControl(startFrame)
            }
        }
    }

    public func stop(notify: Bool = true) {
        userStopped = true
        pingTimer?.cancel()
        pingTimer = nil
        #if canImport(ReplayKit) && !os(macOS)
        if RPScreenRecorder.shared().isRecording {
            RPScreenRecorder.shared().stopCapture { _ in }
        }
        #endif
        if streamingActive, let stopFrame = try? protocolWrapper.encodeFrame(
            opcode: .sessionStop,
            rawPayload: Data(),
            encrypt: protocolWrapper.isEncrypted
        ) {
            transport.sendControl(stopFrame)
        }
        streamingActive = false
        capabilitiesReady = false
        sentVideoFrames = 0
        lastRxSeq = 0
        useTcpMediaFallback = false
        audioEncoder?.stop()
        videoEncoder?.stop()
        audioEncoder = nil
        videoEncoder = nil
        transport.disconnect()
        protocolWrapper.reset()
        if notify {
            onDisconnected?(nil)
        }
    }

    private func connectTransport() {
        guard let device = activeDevice else { return }
        transport.connect(
            host: device.hostAddress,
            controlPort: device.controlPort,
            mediaPort: device.mediaPort,
            onReady: { [weak self] in
                self?.reconnectAttempts = 0
                self?.startHandshake()
            },
            onFailed: { [weak self] error in
                self?.handleTransportFailure(error)
            }
        )
    }

    private func startHandshake() {
        lastRxSeq = 0
        sentVideoFrames = 0
        useTcpMediaFallback = false
        transport.startFrameReadLoop(
            onFrame: { [weak self] frameData in
                self?.handleControlFrame(frameData)
            },
            onDisconnected: { [weak self] in
                guard let self, !self.userStopped else { return }
                self.handleTransportFailure(NSError(domain: "app.drivelink.cast", code: -1, userInfo: [NSLocalizedDescriptionKey: "Connection dropped"]))
            }
        )
        if let syn = try? protocolWrapper.encodeFrame(
            opcode: .helloSyn,
            rawPayload: Data("BYD-IOS-DIRECT-1.0".utf8),
            encrypt: false
        ) {
            transport.sendControl(syn)
        }
    }

    private func handleControlFrame(_ data: Data) {
        guard let packet = try? protocolWrapper.decodeFrame(data, decrypt: protocolWrapper.isEncrypted) else {
            return
        }
        guard packet.sequence > lastRxSeq else { return }
        lastRxSeq = packet.sequence
        switch packet.opcode {
        case .helloAck:
            let pub = protocolWrapper.beginKeyExchangePayload()
            if let frame = try? protocolWrapper.encodeFrame(opcode: .keyExchangeInit, rawPayload: pub, encrypt: false) {
                transport.sendControl(frame)
            }

        case .keyExchangeResp:
            if let fp = try? protocolWrapper.completeKeyExchange(responsePayload: packet.payload),
               activeDevice != nil {
                sessionFingerprint = fp
                if let proof = DeviceIdentityManager.shared.createProof(sessionFingerprint: fp),
                   let identityFrame = try? protocolWrapper.encodeFrame(
                       opcode: .deviceIdentity,
                       rawPayload: proof,
                       encrypt: true
                   ) {
                    transport.sendControl(identityFrame)
                }
                let cap = Data("\(activeProfile.id)|\(activeProfile.baseWidth)x\(activeProfile.baseHeight)@\(activeProfile.targetFps)".utf8)
                if let req = try? protocolWrapper.encodeFrame(opcode: .capabilitiesReq, rawPayload: cap, encrypt: true) {
                    transport.sendControl(req)
                }
            }

        case .capabilitiesResp:
            guard protocolWrapper.isEncrypted, let device = activeDevice else { return }
            capabilitiesReady = true
            onPairingVerification?(device, protocolWrapper.pairingPin, sessionFingerprint)

        case .keyframeRequest:
            videoEncoder?.requestKeyFrame()

        case .heartbeatPong:
            if packet.payload.count >= 8 {
                var sentBE: UInt64 = 0
                _ = withUnsafeMutableBytes(of: &sentBE) { packet.payload.prefix(8).copyBytes(to: $0) }
                let sentNs = UInt64(bigEndian: sentBE)
                let nowNs = DispatchTime.now().uptimeNanoseconds
                if nowNs >= sentNs {
                    lastRttMs = max(1, Int((nowNs - sentNs) / 1_000_000))
                    onTelemetry?(lastFps, lastRttMs, lastLossPct, Float(activeProfile.bitrateMbps))
                }
            }

        case .qosTelemetry:
            if packet.payload.count >= 8 {
                var fpsBE: Int32 = 0
                var dropBE: Int32 = 0
                _ = withUnsafeMutableBytes(of: &fpsBE) { packet.payload.subdata(in: 0..<4).copyBytes(to: $0) }
                _ = withUnsafeMutableBytes(of: &dropBE) { packet.payload.subdata(in: 4..<8).copyBytes(to: $0) }
                let fps = max(0, Int(Int32(bigEndian: fpsBE)))
                let dropped = max(0, Int(Int32(bigEndian: dropBE)))
                lastFps = fps
                lastLossPct = (fps + dropped > 0) ? (Float(dropped) * 100.0 / Float(fps + dropped)) : 0
                if fps == 0, !useTcpMediaFallback, sentVideoFrames >= 15 {
                    useTcpMediaFallback = true
                    videoEncoder?.requestKeyFrame()
                }
                onTelemetry?(lastFps, lastRttMs, lastLossPct, Float(activeProfile.bitrateMbps))
            }

        case .sessionStop:
            stop(notify: true)

        default:
            break
        }
    }

    private func activateMediaSession() {
        guard capabilitiesReady, !streamingActive, let device = activeDevice else { return }
        let mime = activeProfile.codec == .h265 ? "video/hevc" : "video/avc"
        guard let startFrame = try? protocolWrapper.encodeFrame(
            opcode: .sessionStart,
            rawPayload: Data(mime.utf8),
            encrypt: true
        ) else { return }

        videoEncoder = HardwareVideoEncoder(isHevc: activeProfile.codec == .h265) { [weak self] annexB, ptsUs in
            guard let self, self.streamingActive else { return }
            self.sentVideoFrames += 1
            if self.useTcpMediaFallback {
                if let packets = try? self.protocolWrapper.encodeVideoTcpFrames(
                    annexBNalu: annexB,
                    timestampUs: ptsUs,
                    encrypt: true
                ) {
                    for pkt in packets {
                        self.transport.sendControl(pkt)
                    }
                }
            } else if let packets = try? self.protocolWrapper.encodeVideoUdpFrames(
                annexBNalu: annexB,
                timestampUs: ptsUs,
                encrypt: true
            ) {
                for pkt in packets {
                    self.transport.sendMediaUdp(pkt)
                }
            }
        }

        audioEncoder = AudioAacEncoder { [weak self] aac, ptsUs in
            guard let self, self.streamingActive else { return }
            if let frame = try? self.protocolWrapper.encodeFrame(
                opcode: .audioAac,
                rawPayload: aac,
                timestampUs: ptsUs,
                encrypt: true,
                isUdpMedia: !self.useTcpMediaFallback
            ) {
                if self.useTcpMediaFallback {
                    self.transport.sendControl(frame)
                } else {
                    self.transport.sendMediaUdp(frame)
                }
            }
        }

        do { try videoEncoder?.configure(profile: activeProfile, orientation: activeOrientation) } catch {
            stop(notify: false)
            onDisconnected?(error.localizedDescription)
            return
        }
        transport.sendControl(startFrame)
        streamingActive = true
        onStreamingStarted?(device, activeProfile, sessionFingerprint)
        startHeartbeatLoop()
        startInAppScreenCapture()
    }

    private func startInAppScreenCapture() {
        #if canImport(ReplayKit) && !os(macOS)
        let recorder = RPScreenRecorder.shared()
        recorder.isMicrophoneEnabled = false
        recorder.startCapture(handler: { [weak self] sampleBuffer, bufferType, error in
            guard let self, error == nil, self.streamingActive else { return }
            switch bufferType {
            case .video:
                self.videoEncoder?.encode(sampleBuffer: sampleBuffer)
            case .audioApp:
                self.audioEncoder?.encode(sampleBuffer: sampleBuffer)
            default:
                break
            }
        }, completionHandler: { [weak self] error in
            if let error {
                self?.onDisconnected?(error.localizedDescription)
                self?.stop(notify: false)
            }
        })
        #endif
    }

    private func startHeartbeatLoop() {
        pingTimer?.cancel()
        let timer = DispatchSource.makeTimerSource(queue: DispatchQueue.global(qos: .utility))
        timer.schedule(deadline: .now() + 1.5, repeating: 2.0)
        timer.setEventHandler { [weak self] in
            guard let self, self.streamingActive else { return }
            var nowBE = DispatchTime.now().uptimeNanoseconds.bigEndian
            let payload = Data(bytes: &nowBE, count: 8)
            if let pingFrame = try? self.protocolWrapper.encodeFrame(
                opcode: .heartbeatPing,
                rawPayload: payload,
                encrypt: true
            ) {
                self.transport.sendControl(pingFrame)
            }
        }
        timer.resume()
        pingTimer = timer
    }

    private func handleTransportFailure(_ error: Error) {
        if userStopped { return }
        if isDeviceTrusted, reconnectAttempts < 3, let device = activeDevice {
            reconnectAttempts += 1
            streamingActive = false
            capabilitiesReady = false
            pingTimer?.cancel()
            pingTimer = nil
            transport.disconnect()
            protocolWrapper.reset()
            onReconnecting?(device, reconnectAttempts)
            let waitSec = Double(reconnectAttempts) * 1.5
            DispatchQueue.global(qos: .userInitiated).asyncAfter(deadline: .now() + waitSec) { [weak self] in
                guard let self, !self.userStopped else { return }
                self.connectTransport()
            }
            return
        }
        stop(notify: false)
        onDisconnected?(error.localizedDescription)
    }
}
