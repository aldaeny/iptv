import Combine
import Foundation
import Network
#if canImport(Darwin)
import Darwin
#endif

/// Discovers BYD & Chinese EV Wireless Mirroring sinks using BOTH Bonjour (`_byd-mirror._tcp`)
/// and zero-config UDP Discovery Beacon (port 47813).
public final class BonjourP2PDiscovery: ObservableObject {

    @Published public private(set) var discoveredDevices: [BydDevice] = []
    private var browser: NWBrowser?
    private var beaconSocketFd: Int32 = -1
    private var beaconThread: Thread?
    private var bonjourDevices: [BydDevice] = []
    private var beaconDevices: [BydDevice] = []
    private let queue = DispatchQueue(label: "com.byd.mirror.bonjour", qos: .userInitiated)

    public init() {}

    public func startDiscovery() {
        stopDiscovery()
        startBonjour()
        startUdpBeaconListener()
    }

    private func startBonjour() {
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
                self?.bonjourDevices = devices
                self?.publishMerged()
            }
        }

        nwBrowser.start(queue: queue)
        browser = nwBrowser
    }

    private func startUdpBeaconListener() {
        #if canImport(Darwin)
        let fd = Darwin.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        guard fd >= 0 else { return }

        var yes: Int32 = 1
        Darwin.setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, socklen_t(MemoryLayout<Int32>.size))
        Darwin.setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &yes, socklen_t(MemoryLayout<Int32>.size))
        Darwin.setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &yes, socklen_t(MemoryLayout<Int32>.size))

        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = UInt16(47813).bigEndian
        addr.sin_addr = in_addr(s_addr: INADDR_ANY)

        let bindOk = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        guard bindOk == 0 else {
            Darwin.close(fd)
            return
        }

        beaconSocketFd = fd
        let thread = Thread { [weak self] in
            var buf = [UInt8](repeating: 0, count: 1024)
            var src = sockaddr_in()
            var srcLen = socklen_t(MemoryLayout<sockaddr_in>.size)
            while true {
                let n = withUnsafeMutablePointer(to: &src) {
                    $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                        Darwin.recvfrom(fd, &buf, buf.count, 0, $0, &srcLen)
                    }
                }
                if n <= 0 { break }
                let data = Data(buf[0..<n])
                guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      (obj["proto"] as? String) == "byd-mirror-v1" else {
                    continue
                }
                var ipBuf = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                var addrCopy = src.sin_addr
                Darwin.inet_ntop(AF_INET, &addrCopy, &ipBuf, socklen_t(INET_ADDRSTRLEN))
                let host = String(cString: ipBuf)
                let name = (obj["name"] as? String) ?? "BYD DiLink"
                let model = (obj["model"] as? String) ?? "BYD / Chinese EV"
                let ctrlPort = UInt16((obj["port"] as? Int) ?? 47810)
                let mediaPort = UInt16((obj["mediaPort"] as? Int) ?? 47811)
                let hevc = (obj["hevc"] as? Bool) ?? false

                let dev = BydDevice(
                    id: "beacon-\(host)",
                    displayName: name,
                    modelName: model,
                    diLinkVersion: "5.0",
                    hostAddress: host,
                    controlPort: ctrlPort,
                    mediaPort: mediaPort,
                    transportType: .lanBonjour,
                    supportsRotatingScreen: true,
                    supportsHevc: hevc
                )
                DispatchQueue.main.async {
                    guard let self else { return }
                    if !self.beaconDevices.contains(where: { $0.hostAddress == host && $0.displayName == name }) {
                        self.beaconDevices = self.beaconDevices.filter { $0.hostAddress != host } + [dev]
                        self.publishMerged()
                    }
                }
            }
        }
        thread.start()
        beaconThread = thread
        #endif
    }

    private func publishMerged() {
        var seen = Set<String>()
        var merged: [BydDevice] = []
        for dev in beaconDevices + bonjourDevices {
            if seen.insert(dev.id).inserted {
                merged.append(dev)
            }
        }
        discoveredDevices = merged
    }

    public func stopDiscovery() {
        browser?.cancel()
        browser = nil
        #if canImport(Darwin)
        if beaconSocketFd >= 0 {
            Darwin.close(beaconSocketFd)
            beaconSocketFd = -1
        }
        #endif
        beaconThread = nil
    }
}
