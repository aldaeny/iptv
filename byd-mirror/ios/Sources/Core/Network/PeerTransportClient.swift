import Foundation
import Network

/// Dual-channel transport client using Apple Network.framework:
/// - TCP (`47810`) for handshake, key exchange, orientation, and QoS telemetry
/// - UDP (`47811`) for low-latency encrypted VIDEO_NALU & AUDIO_AAC frames
public final class PeerTransportClient: @unchecked Sendable {

    private var tcpConnection: NWConnection?
    private var udpConnection: NWConnection?
    private let queue = DispatchQueue(label: "com.byd.mirror.transport", qos: .userInteractive)

    public init() {}

    public func connect(
        host: String,
        controlPort: UInt16 = 47810,
        mediaPort: UInt16 = 47811,
        onReady: @escaping @Sendable () -> Void,
        onFailed: @escaping @Sendable (Error) -> Void
    ) {
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
        udpConn.start(queue: queue)
        udpConnection = udpConn

        conn.stateUpdateHandler = { state in
            switch state {
            case .ready:
                onReady()
            case .failed(let error):
                onFailed(error)
            default:
                break
            }
        }

        conn.start(queue: queue)
        tcpConnection = conn
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

    public func startFrameReadLoop(onFrame: @escaping @Sendable (Data) -> Void) {
        receiveExact(BydProtocolWrapper.headerSizeBytes) { [weak self] header in
            guard let self, let header, header.count == BydProtocolWrapper.headerSizeBytes else { return }
            let payloadLen = Int(
                (UInt32(header[12]) << 24) |
                (UInt32(header[13]) << 16) |
                (UInt32(header[14]) << 8) |
                UInt32(header[15])
            )
            guard payloadLen >= 0, payloadLen <= 65_536 else { return }

            if payloadLen == 0 {
                onFrame(header)
                self.startFrameReadLoop(onFrame: onFrame)
                return
            }

            self.receiveExact(payloadLen) { [weak self] body in
                guard let self, let body, body.count == payloadLen else { return }
                onFrame(header + body)
                self.startFrameReadLoop(onFrame: onFrame)
            }
        }
    }

    public func disconnect() {
        tcpConnection?.cancel()
        udpConnection?.cancel()
        tcpConnection = nil
        udpConnection = nil
    }
}
