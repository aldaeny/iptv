import Combine
import Foundation
import Network

#if canImport(NetworkExtension)
import NetworkExtension
#endif

/// Monitors Wi-Fi reachability to detect BYD vehicle Wi-Fi coverage
/// and surface Auto-Connect readiness.
public final class AutoConnectCoordinator: ObservableObject {

    @Published public private(set) var inBydWifiRange: Bool = false
    @Published public private(set) var currentSsid: String?

    private let monitor = NWPathMonitor(requiredInterfaceType: .wifi)
    private let queue = DispatchQueue(label: "com.byd.mirror.autoconnect")

    private static let bydSsidPrefixes = ["BYD-DiLink-", "BYD-Auto-", "BYD-Link-", "DIRECT-BYD-"]

    public init() {}

    public func startMonitoring() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard path.status == .satisfied else {
                DispatchQueue.main.async {
                    self?.currentSsid = nil
                    self?.inBydWifiRange = false
                }
                return
            }
            self?.checkCurrentWifiSsid()
        }
        monitor.start(queue: queue)
    }

    public func stopMonitoring() {
        monitor.cancel()
    }

    private func checkCurrentWifiSsid() {
        #if canImport(NetworkExtension) && !os(macOS)
        NEHotspotNetwork.fetchCurrent { [weak self] network in
            let ssid = network?.ssid
            let matches = ssid.map { s in
                Self.bydSsidPrefixes.contains { s.localizedCaseInsensitiveContains($0) }
            } ?? false
            DispatchQueue.main.async {
                self?.currentSsid = ssid
                self?.inBydWifiRange = matches
            }
        }
        #endif
    }
}
