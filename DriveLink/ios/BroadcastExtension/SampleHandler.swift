import Foundation
import ReplayKit

/// ReplayKit Broadcast Upload Extension (single live TCP/UDP owner):
/// - Reads target BYD endpoint, codec, profile & orientation from SharedSessionStore (App Group)
/// - Completes full ECDH P-256 + AES-256-GCM handshake over TCP 47810
/// - Gates SESSION_START behind PIN confirmation for untrusted vehicles
/// - Streams Annex-B VideoToolbox H.265/H.264 (MTU-fragmented) & AudioToolbox AAC-LC over UDP 47811
/// - Publishes live RTT/FPS/loss telemetry & auto-reconnects on transient Wi-Fi drops
final class SampleHandler: RPBroadcastSampleHandler {

    private let protocolWrapper = BydProtocolWrapper()
    private let transport = PeerTransportClient()
    private let sharedStore = SharedSessionStore()
    private let trustedStore = KeychainTrustedVehiclesStore(accessGroup: SharedSessionStore.appGroupId)
    private var videoEncoder: HardwareVideoEncoder?
    private var audioEncoder: AudioAacEncoder?
    private var pingTimer: DispatchSourceTimer?
    private var targetDeviceId: String = "byd-default"
    private var targetHost: String = "192.168.49.1"
    private var targetControlPort: UInt16 = 47810
    private var targetMediaPort: UInt16 = 47811
    private var activeProfile: DisplayProfile = .diLink15_6FHD
    private var handshakeComplete = false
    private var capabilitiesReady = false
    private var userStopped = false
    private var reconnectAttempts = 0
    private var lastRttMs = 0
    private var lastFps = 60
    private var lastLossPct = 0.0
    private var sentVideoFrames = 0
    private var lastRxSeq: UInt32 = 0
    private var useTcpMediaFallback = false

    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        userStopped = false
        reconnectAttempts = 0

        let saved = sharedStore.loadTargetEndpoint()
        targetDeviceId = saved.id
        targetHost = (setupInfo?["bydHost"] as? String) ?? saved.host
        targetControlPort = (setupInfo?["bydPort"] as? NSNumber)?.uint16Value ?? saved.controlPort
        targetMediaPort = (setupInfo?["bydMediaPort"] as? NSNumber)?.uint16Value ?? saved.mediaPort

        let orientation = sharedStore.loadOrientation()
        let streamSettings = sharedStore.loadStreamSettings()
        activeProfile = resolveProfile(profileId: streamSettings.profileId, preferHevc: streamSettings.preferHevc)

        registerDarwinObservers()

        videoEncoder = HardwareVideoEncoder(isHevc: streamSettings.preferHevc) { [weak self] annexBData, ptsUs in
            guard let self, self.handshakeComplete else { return }
            self.sentVideoFrames += 1
            if self.useTcpMediaFallback {
                if let packets = try? self.protocolWrapper.encodeVideoTcpFrames(
                    annexBNalu: annexBData,
                    timestampUs: ptsUs,
                    encrypt: self.protocolWrapper.isEncrypted
                ) {
                    for packet in packets {
                        self.transport.sendControl(packet)
                    }
                }
            } else if let packets = try? self.protocolWrapper.encodeVideoUdpFrames(
                annexBNalu: annexBData,
                timestampUs: ptsUs,
                encrypt: self.protocolWrapper.isEncrypted
            ) {
                for packet in packets {
                    self.transport.sendMediaUdp(packet)
                }
            }
        }

        audioEncoder = AudioAacEncoder { [weak self] aacData, ptsUs in
            guard let self, self.handshakeComplete else { return }
            if let frame = try? self.protocolWrapper.encodeFrame(
                opcode: .audioAac,
                rawPayload: aacData,
                timestampUs: ptsUs,
                encrypt: self.protocolWrapper.isEncrypted,
                isUdpMedia: !self.useTcpMediaFallback
            ) {
                if self.useTcpMediaFallback {
                    self.transport.sendControl(frame)
                } else {
                    self.transport.sendMediaUdp(frame)
                }
            }
        }

        try? videoEncoder?.configure(profile: activeProfile, orientation: orientation)
        connectTransport()
    }

    private func resolveProfile(profileId: String, preferHevc: Bool) -> DisplayProfile {
        let base: DisplayProfile
        switch profileId {
        case DisplayProfile.diLink10_1HD.id:
            base = .diLink10_1HD
        case DisplayProfile.diLink12_8FHD.id:
            base = .diLink12_8FHD
        default:
            base = .diLink15_6FHD
        }
        return DisplayProfile(
            id: base.id,
            label: base.label,
            baseWidth: base.baseWidth,
            baseHeight: base.baseHeight,
            targetFps: base.targetFps,
            bitrateMbps: base.bitrateMbps,
            codec: preferHevc ? .h265 : .h264
        )
    }

    private func connectTransport() {
        transport.connect(
            host: targetHost,
            controlPort: targetControlPort,
            mediaPort: targetMediaPort,
            onReady: { [weak self] in
                self?.reconnectAttempts = 0
                self?.startHandshakeSequence()
            },
            onFailed: { [weak self] error in
                self?.handleTransportFailure(error)
            }
        )
    }

    private func handleTransportFailure(_ error: Error) {
        if userStopped {
            return
        }
        let canAutoReconnect = trustedStore.isTrusted(deviceId: targetDeviceId) || sharedStore.isDeviceTrustedInGroup(targetDeviceId)
        if canAutoReconnect && reconnectAttempts < 3 {
            reconnectAttempts += 1
            handshakeComplete = false
            capabilitiesReady = false
            pingTimer?.cancel()
            pingTimer = nil
            transport.disconnect()
            protocolWrapper.reset()
            let delaySec = Double(reconnectAttempts) * 1.5
            DispatchQueue.global(qos: .userInitiated).asyncAfter(deadline: .now() + delaySec) { [weak self] in
                guard let self, !self.userStopped else { return }
                self.connectTransport()
            }
            return
        }
        sharedStore.setBroadcastState(active: false, error: error.localizedDescription)
        finishBroadcastWithError(error)
    }

    private func startHandshakeSequence() {
        lastRxSeq = 0
        sentVideoFrames = 0
        useTcpMediaFallback = false
        transport.startFrameReadLoop(
            onFrame: { [weak self] frameData in
                self?.handleIncomingControlFrame(frameData)
            },
            onDisconnected: { [weak self] in
                guard let self, !self.userStopped else { return }
                self.handleTransportFailure(NSError(domain: "app.drivelink.cast", code: -1, userInfo: [NSLocalizedDescriptionKey: "Control channel disconnected"]))
            }
        )

        if let synFrame = try? protocolWrapper.encodeFrame(
            opcode: .helloSyn,
            rawPayload: Data("BYD-IOS-1.0".utf8),
            encrypt: false
        ) {
            transport.sendControl(synFrame)
        }
    }

    private func handleIncomingControlFrame(_ data: Data) {
        guard let packet = try? protocolWrapper.decodeFrame(data, decrypt: protocolWrapper.isEncrypted) else {
            return
        }
        guard packet.sequence > lastRxSeq else { return }
        lastRxSeq = packet.sequence
        switch packet.opcode {
        case .helloAck:
            let pubKey = protocolWrapper.beginKeyExchangePayload()
            if let keyInit = try? protocolWrapper.encodeFrame(
                opcode: .keyExchangeInit,
                rawPayload: pubKey,
                encrypt: false
            ) {
                transport.sendControl(keyInit)
            }

        case .keyExchangeResp:
            if let fingerprint = try? protocolWrapper.completeKeyExchange(responsePayload: packet.payload) {
                sharedStore.savePairingVerification(pin: protocolWrapper.pairingPin, fingerprint: fingerprint)
                let capPayload = Data("\(activeProfile.id)|\(activeProfile.baseWidth)x\(activeProfile.baseHeight)@\(activeProfile.targetFps)".utf8)
                if let capReq = try? protocolWrapper.encodeFrame(
                    opcode: .capabilitiesReq,
                    rawPayload: capPayload,
                    encrypt: true
                ) {
                    transport.sendControl(capReq)
                }
            }

        case .capabilitiesResp:
            capabilitiesReady = true
            if trustedStore.isTrusted(deviceId: targetDeviceId) || sharedStore.isDeviceTrustedInGroup(targetDeviceId) {
                activateMediaSession()
            }

        case .keyframeRequest:
            videoEncoder?.requestKeyFrame()

        case .heartbeatPong:
            if packet.payload.count >= 8 {
                var sentNsBE: UInt64 = 0
                _ = withUnsafeMutableBytes(of: &sentNsBE) { packet.payload.prefix(8).copyBytes(to: $0) }
                let sentNs = UInt64(bigEndian: sentNsBE)
                let nowNs = DispatchTime.now().uptimeNanoseconds
                if nowNs >= sentNs {
                    lastRttMs = max(1, Int((nowNs - sentNs) / 1_000_000))
                    publishTelemetry()
                }
            }

        case .qosTelemetry:
            if packet.payload.count >= 8 {
                var fpsBE: Int32 = 0
                var droppedBE: Int32 = 0
                _ = withUnsafeMutableBytes(of: &fpsBE) { packet.payload.subdata(in: 0..<4).copyBytes(to: $0) }
                _ = withUnsafeMutableBytes(of: &droppedBE) { packet.payload.subdata(in: 4..<8).copyBytes(to: $0) }
                let fps = max(0, Int(Int32(bigEndian: fpsBE)))
                let dropped = max(0, Int(Int32(bigEndian: droppedBE)))
                lastFps = fps
                lastLossPct = (fps + dropped > 0) ? (Double(dropped) * 100.0 / Double(fps + dropped)) : 0.0
                if fps == 0, !useTcpMediaFallback, sentVideoFrames >= 15 {
                    useTcpMediaFallback = true
                    videoEncoder?.requestKeyFrame()
                }
                publishTelemetry()
            }

        case .sessionStop:
            userStopped = true
            finishBroadcastWithError(NSError(domain: "app.drivelink.cast", code: 0))

        default:
            break
        }
    }

    private func publishTelemetry() {
        sharedStore.saveTelemetry(
            fps: lastFps,
            rttMs: lastRttMs,
            packetLossPercent: lastLossPct,
            bitrateMbps: Double(activeProfile.bitrateMbps)
        )
    }

    private func activateMediaSession() {
        guard capabilitiesReady, !handshakeComplete else { return }
        let mime = activeProfile.codec == .h265 ? "video/hevc" : "video/avc"
        if let startFrame = try? protocolWrapper.encodeFrame(
            opcode: .sessionStart,
            rawPayload: Data(mime.utf8),
            encrypt: true
        ) {
            transport.sendControl(startFrame)
            handshakeComplete = true
            sharedStore.setBroadcastState(active: true, error: nil)
            startHeartbeatLoop()
        }
    }

    private func startHeartbeatLoop() {
        pingTimer?.cancel()
        let timer = DispatchSource.makeTimerSource(queue: DispatchQueue.global(qos: .utility))
        timer.schedule(deadline: .now() + 1.5, repeating: 2.0)
        timer.setEventHandler { [weak self] in
            guard let self, self.handshakeComplete else { return }
            var nowBE = DispatchTime.now().uptimeNanoseconds.bigEndian
            let payload = Data(bytes: &nowBE, count: 8)
            if let pingFrame = try? self.protocolWrapper.encodeFrame(
                opcode: .heartbeatPing,
                rawPayload: payload,
                encrypt: self.protocolWrapper.isEncrypted
            ) {
                self.transport.sendControl(pingFrame)
            }
        }
        timer.resume()
        pingTimer = timer
    }

    private func registerDarwinObservers() {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        let observer = Unmanaged.passUnretained(self).toOpaque()

        CFNotificationCenterAddObserver(
            center,
            observer,
            { _, observerPtr, _, _, _ in
                guard let observerPtr else { return }
                let handler = Unmanaged<SampleHandler>.fromOpaque(observerPtr).takeUnretainedValue()
                let nextOrientation = handler.sharedStore.loadOrientation()
                try? handler.videoEncoder?.configure(profile: handler.activeProfile, orientation: nextOrientation)
                if let frame = try? handler.protocolWrapper.makeOrientationFrame(nextOrientation) {
                    handler.transport.sendControl(frame)
                }
            },
            SharedSessionStore.orientationChangedNotification,
            nil,
            .deliverImmediately
        )

        CFNotificationCenterAddObserver(
            center,
            observer,
            { _, observerPtr, _, _, _ in
                guard let observerPtr else { return }
                let handler = Unmanaged<SampleHandler>.fromOpaque(observerPtr).takeUnretainedValue()
                handler.activateMediaSession()
            },
            SharedSessionStore.pairingConfirmedNotification,
            nil,
            .deliverImmediately
        )

        CFNotificationCenterAddObserver(
            center,
            observer,
            { _, observerPtr, _, _, _ in
                guard let observerPtr else { return }
                let handler = Unmanaged<SampleHandler>.fromOpaque(observerPtr).takeUnretainedValue()
                handler.userStopped = true
                handler.finishBroadcastWithError(NSError(domain: "app.drivelink.cast", code: 0))
            },
            SharedSessionStore.stopBroadcastNotification,
            nil,
            .deliverImmediately
        )
    }

    override func broadcastFinished() {
        userStopped = true
        pingTimer?.cancel()
        pingTimer = nil
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        CFNotificationCenterRemoveEveryObserver(center, Unmanaged.passUnretained(self).toOpaque())

        handshakeComplete = false
        capabilitiesReady = false
        sharedStore.setBroadcastState(active: false, error: nil)
        if let stopFrame = try? protocolWrapper.encodeFrame(
            opcode: .sessionStop,
            rawPayload: Data(),
            encrypt: protocolWrapper.isEncrypted
        ) {
            transport.sendControl(stopFrame)
        }
        audioEncoder?.stop()
        videoEncoder?.stop()
        transport.disconnect()
        protocolWrapper.reset()
    }

    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer, with sampleBufferType: RPSampleBufferType) {
        guard handshakeComplete else { return }
        switch sampleBufferType {
        case .video:
            videoEncoder?.encode(sampleBuffer: sampleBuffer)
        case .audioApp:
            audioEncoder?.encode(sampleBuffer: sampleBuffer)
        case .audioMic:
            break
        @unknown default:
            break
        }
    }
}
