import Combine
import Foundation
import Network

/// Discovers BYD Wireless Mirroring sinks using Bonjour (`_byd-mirror._tcp`)
/// with Apple Peer-to-Peer Wi-Fi (AWDL) enabled for direct phone-to-car links.
public final class BonjourP2PDiscovery: ObservableObject {

    @Published public private(set) var discoveredDevices: [BydDevice] = []
    private var browser: NWBrowser?
    private let queue = DispatchQueue(label: "com.byd.mirror.bonjour", qos: .userInitiated)

    public init() {}

    public func startDiscovery() {
        stopDiscovery()

        let params = NWParameters()
        params.includePeerToPeer = true

        let descriptor = NWBrowser.Descriptor.bonjour(type: "_byd-mirror._tcp", domain: nil)
        let nwBrowser = NWBrowser(for: descriptor, using: params)

        nwBrowser.browseResultsChangedHandler = { [weak self] results, _ in
            let devices: [BydDevice] = results.compactMap { result in
                guard case let .service(name, _, _, _) = result.endpoint else { return nil }
                return BydDevice(
                    id: name,
                    displayName: name,
                    modelName: "BYD DiLink 15.6\"",
                    diLinkVersion: "5.0",
                    hostAddress: "\(name).local",
                    transportType: .peerToPeerAWDL
                )
            }
            DispatchQueue.main.async {
                self?.discoveredDevices = devices
            }
        }

        nwBrowser.start(queue: queue)
        browser = nwBrowser
    }

    public func stopDiscovery() {
        browser?.cancel()
        browser = nil
    }
}
