import Foundation
import Network

/// Dual-channel transport client using Apple Network.framework:
/// - TCP (`47810`) for handshake, key exchange, orientation, and QoS telemetry
/// - UDP (`47811`) for low-latency encrypted VIDEO_NALU & AUDIO_AAC frames
public final class PeerTransportClient: @unchecked Sendable {

    private var tcpConnection: NWConnection?
    private var udpConnection: NWConnection?
    private var generation: UInt64 = 0
    private let queue = DispatchQueue(label: "app.drivelink.cast.transport", qos: .userInteractive)

    public init() {}

    public func connect(
        host: String,
        controlPort: UInt16 = 47810,
        mediaPort: UInt16 = 47811,
        onReady: @escaping @Sendable () -> Void,
        onFailed: @escaping @Sendable (Error) -> Void
    ) {
        queue.async { [weak self] in
            guard let self else { return }
            self.generation &+= 1
            let activeGen = self.generation
            self.tcpConnection?.cancel()
            self.udpConnection?.cancel()

            let tcpOptions = NWProtocolTCP.Options()
            tcpOptions.noDelay = true

            let tcpParams = NWParameters(tls: nil, tcp: tcpOptions)
            tcpParams.includePeerToPeer = true
            tcpParams.serviceClass = .interactiveVideo

            let nwControlPort = NWEndpoint.Port(rawValue: controlPort) ?? 47810
            let conn = NWConnection(host: NWEndpoint.Host(host), port: nwControlPort, using: tcpParams)

            let udpParams = NWParameters.udp
            udpParams.includePeerToPeer = true
            udpParams.serviceClass = .interactiveVideo
            let nwMediaPort = NWEndpoint.Port(rawValue: mediaPort) ?? 47811
            let udpConn = NWConnection(host: NWEndpoint.Host(host), port: nwMediaPort, using: udpParams)
            udpConn.start(queue: self.queue)
            self.udpConnection = udpConn

            conn.stateUpdateHandler = { [weak self] state in
                guard let self, self.generation == activeGen else { return }
                switch state {
                case .ready:
                    onReady()
                case .failed(let error):
                    onFailed(error)
                default:
                    break
                }
            }

            conn.start(queue: self.queue)
            self.tcpConnection = conn
        }
    }

    public func sendControl(_ data: Data) {
        tcpConnection?.send(content: data, completion: .contentProcessed { _ in })
    }

    public func sendMediaUdp(_ data: Data) {
        if let udpConnection, data.count <= 65_000 {
            udpConnection.send(content: data, completion: .contentProcessed { _ in })
        } else {
            sendControl(data)
        }
    }

    public func receiveExact(_ length: Int, completion: @escaping @Sendable (Data?) -> Void) {
        tcpConnection?.receive(minimumIncompleteLength: length, maximumLength: length) { data, _, _, _ in
            completion(data)
        }
    }

    public func startFrameReadLoop(
        onFrame: @escaping @Sendable (Data) -> Void,
        onDisconnected: @escaping @Sendable () -> Void = {}
    ) {
        queue.async { [weak self] in
            guard let self else { return }
            self.readNextFrame(generation: self.generation, onFrame: onFrame, onDisconnected: onDisconnected)
        }
    }

    private func readNextFrame(
        generation activeGen: UInt64,
        onFrame: @escaping @Sendable (Data) -> Void,
        onDisconnected: @escaping @Sendable () -> Void
    ) {
        receiveExact(BydProtocolWrapper.headerSizeBytes) { [weak self] header in
            guard let self, self.generation == activeGen else { return }
            guard let header, header.count == BydProtocolWrapper.headerSizeBytes else {
                onDisconnected()
                return
            }
            let payloadLen = Int(
                (UInt32(header[12]) << 24) |
                (UInt32(header[13]) << 16) |
                (UInt32(header[14]) << 8) |
                UInt32(header[15])
            )
            guard payloadLen >= 0, payloadLen <= 65_536 else {
                onDisconnected()
                return
            }

            if payloadLen == 0 {
                onFrame(header)
                self.readNextFrame(generation: activeGen, onFrame: onFrame, onDisconnected: onDisconnected)
                return
            }

            self.receiveExact(payloadLen) { [weak self] body in
                guard let self, self.generation == activeGen else { return }
                guard let body, body.count == payloadLen else {
                    onDisconnected()
                    return
                }
                onFrame(header + body)
                self.readNextFrame(generation: activeGen, onFrame: onFrame, onDisconnected: onDisconnected)
            }
        }
    }

    public func disconnect() {
        queue.async { [weak self] in
            guard let self else { return }
            self.generation &+= 1
            self.tcpConnection?.cancel()
            self.udpConnection?.cancel()
            self.tcpConnection = nil
            self.udpConnection = nil
        }
    }
}
