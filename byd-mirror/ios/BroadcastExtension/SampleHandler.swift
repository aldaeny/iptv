import Foundation
import ReplayKit

/// ReplayKit Broadcast Upload Extension (single live TCP/UDP owner):
/// - Reads target BYD endpoint & orientation from SharedSessionStore (App Group)
/// - Completes full ECDH P-256 + AES-256-GCM handshake over TCP 47810
/// - Gates SESSION_START behind PIN confirmation for untrusted vehicles
/// - Streams Annex-B VideoToolbox H.265/H.264 (MTU-fragmented) & AudioToolbox AAC-LC over UDP 47811
final class SampleHandler: RPBroadcastSampleHandler {

    private let protocolWrapper = BydProtocolWrapper()
    private let transport = PeerTransportClient()
    private let sharedStore = SharedSessionStore()
    private let trustedStore = KeychainTrustedVehiclesStore(accessGroup: SharedSessionStore.appGroupId)
    private var videoEncoder: HardwareVideoEncoder?
    private var audioEncoder: AudioAacEncoder?
    private var targetDeviceId: String = "byd-default"
    private var handshakeComplete = false
    private var capabilitiesReady = false

    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        let saved = sharedStore.loadTargetEndpoint()
        targetDeviceId = saved.id
        let host = (setupInfo?["bydHost"] as? String) ?? saved.host
        let controlPort = (setupInfo?["bydPort"] as? NSNumber)?.uint16Value ?? saved.controlPort
        let mediaPort = (setupInfo?["bydMediaPort"] as? NSNumber)?.uint16Value ?? saved.mediaPort
        let orientation = sharedStore.loadOrientation()

        registerDarwinObservers()

        videoEncoder = HardwareVideoEncoder(isHevc: true) { [weak self] annexBData, ptsUs in
            guard let self, self.handshakeComplete else { return }
            if let packets = try? self.protocolWrapper.encodeVideoUdpFrames(
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
                encrypt: self.protocolWrapper.isEncrypted
            ) {
                self.transport.sendMediaUdp(frame)
            }
        }

        try? videoEncoder?.configure(profile: .diLink15_6FHD, orientation: orientation)

        transport.connect(
            host: host,
            controlPort: controlPort,
            mediaPort: mediaPort,
            onReady: { [weak self] in
                self?.startHandshakeSequence()
            },
            onFailed: { [weak self] error in
                self?.sharedStore.setBroadcastState(active: false, error: error.localizedDescription)
                self?.finishBroadcastWithError(error)
            }
        )
    }

    private func startHandshakeSequence() {
        transport.startFrameReadLoop { [weak self] frameData in
            self?.handleIncomingControlFrame(frameData)
        }

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
                let capPayload = Data("dilink_15_6_fhd|1920x1080@60".utf8)
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

        case .sessionStop:
            finishBroadcastWithError(NSError(domain: "com.byd.mirror", code: 0))

        default:
            break
        }
    }

    private func activateMediaSession() {
        guard capabilitiesReady, !handshakeComplete else { return }
        if let startFrame = try? protocolWrapper.encodeFrame(
            opcode: .sessionStart,
            rawPayload: Data("video/hevc".utf8),
            encrypt: true
        ) {
            transport.sendControl(startFrame)
            handshakeComplete = true
            sharedStore.setBroadcastState(active: true, error: nil)
        }
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
                try? handler.videoEncoder?.configure(profile: .diLink15_6FHD, orientation: nextOrientation)
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
                handler.finishBroadcastWithError(NSError(domain: "com.byd.mirror", code: 0))
            },
            SharedSessionStore.stopBroadcastNotification,
            nil,
            .deliverImmediately
        )
    }

    override func broadcastFinished() {
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
