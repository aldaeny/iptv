import Combine
import Foundation

public struct PairingPrompt: Identifiable, Equatable, Sendable {
    public let id: String
    public let device: BydDevice
    public let pin: String
    public let fingerprint: String
}

@MainActor
public final class MirrorViewModel: ObservableObject {

    @Published public private(set) var connectionState: ConnectionState = .idle
    @Published public private(set) var discoveredDevices: [BydDevice] = []
    @Published public private(set) var activeProfile: DisplayProfile = .diLink15_6FHD
    @Published public private(set) var orientation: DisplayOrientation = .landscape
    @Published public private(set) var telemetry = TelemetryMetrics()
    @Published public var autoConnectEnabled: Bool = true
    @Published public var showMiniOverlay: Bool = false
    @Published public var pendingPairing: PairingPrompt?
    @Published public private(set) var trustedVehicles: [TrustedVehicle] = []
    @Published public var isArabic: Bool = true
    @Published public private(set) var preferHevc: Bool = true
    @Published public private(set) var selectedProfileId: String = "auto"
    @Published public var manualIpInput: String = ""

    private let discovery = BonjourP2PDiscovery()
    private let autoConnect = AutoConnectCoordinator()
    private let thermalMonitor = ThermalPowerMonitor()
    private let qualityNegotiator = QualityNegotiator()
    private let sharedStore = SharedSessionStore()
    private let trustedStore = KeychainTrustedVehiclesStore(accessGroup: SharedSessionStore.appGroupId)
    #if canImport(UserNotifications)
    private let autoConnectNotifier = AutoConnectNotificationHelper()
    #endif
    #if canImport(ActivityKit) && !os(macOS)
    private lazy var liveActivityManager: Any? = {
        if #available(iOS 16.1, *) { return LiveActivityManager() }
        return nil
    }()
    #endif
    private var selectedDevice: BydDevice?
    private var cancellables = Set<AnyCancellable>()

    public init() {
        discovery.$discoveredDevices
            .receive(on: DispatchQueue.main)
            .assign(to: &$discoveredDevices)

        thermalMonitor.$thermalLevel
            .receive(on: DispatchQueue.main)
            .sink { [weak self] level in
                guard let self else { return }
                self.telemetry.thermalLevel = level
                self.activeProfile = self.qualityNegotiator.adaptProfile(self.activeProfile, telemetry: self.telemetry)
            }
            .store(in: &cancellables)

        Publishers.CombineLatest3(
            autoConnect.$inBydWifiRange,
            discovery.$discoveredDevices,
            $autoConnectEnabled
        )
        .receive(on: DispatchQueue.main)
        .sink { [weak self] inRange, devices, enabled in
            guard let self, enabled, inRange, self.connectionState == .idle else { return }
            let preferred = devices.first { self.trustedStore.isTrusted(deviceId: $0.id) } ?? devices.first
            if let preferred {
                self.prepareConnection(to: preferred)
            }
            #if canImport(UserNotifications)
            self.autoConnectNotifier.notifyBydCarInRange(
                ssid: self.autoConnect.currentSsid,
                deviceName: preferred?.displayName
            )
            #endif
        }
        .store(in: &cancellables)

        let settings = sharedStore.loadStreamSettings()
        preferHevc = settings.preferHevc
        selectedProfileId = settings.profileId
        trustedVehicles = trustedStore.loadAll()
        #if canImport(UserNotifications)
        autoConnectNotifier.requestPermission()
        #endif
        registerExtensionObservers()
        autoConnect.startMonitoring()
        thermalMonitor.startMonitoring()
    }

    public func onOneTapConnect() {
        if case .streaming = connectionState {
            disconnect()
            return
        }
        connectionState = .discovering
        discovery.startDiscovery()

        if let device = discoveredDevices.first {
            prepareConnection(to: device)
        }
    }

    /// Saves the chosen BYD screen to App Group SharedSessionStore so ReplayKit's SampleHandler
    /// owns the single live TCP/UDP session without socket conflict.
    public func prepareConnection(to device: BydDevice) {
        selectedDevice = device
        sharedStore.saveTargetDevice(device)
        sharedStore.saveStreamSettings(preferHevc: preferHevc, profileId: selectedProfileId)
        activeProfile = resolvedProfile(for: device)
        connectionState = .handshaking(device)
    }

    public func connectManualIp() {
        let host = manualIpInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty else { return }
        let device = BydDevice(
            id: "manual-\(host)",
            displayName: "BYD DiLink (\(host))",
            modelName: "BYD DiLink",
            diLinkVersion: "5.0",
            hostAddress: host,
            controlPort: 47810,
            mediaPort: 47811,
            transportType: .lanBonjour,
            supportsRotatingScreen: true,
            supportsHevc: preferHevc
        )
        prepareConnection(to: device)
    }

    public func setCodec(preferHevc: Bool) {
        self.preferHevc = preferHevc
        sharedStore.saveStreamSettings(preferHevc: preferHevc, profileId: selectedProfileId)
        activeProfile = resolvedProfile(for: selectedDevice)
    }

    public func selectProfile(id: String) {
        selectedProfileId = id
        sharedStore.saveStreamSettings(preferHevc: preferHevc, profileId: id)
        activeProfile = resolvedProfile(for: selectedDevice)
    }

    public func toggleLanguage() {
        isArabic.toggle()
    }

    private func resolvedProfile(for device: BydDevice?) -> DisplayProfile {
        let base: DisplayProfile
        switch selectedProfileId {
        case DisplayProfile.diLink10_1HD.id:
            base = .diLink10_1HD
        case DisplayProfile.diLink12_8FHD.id:
            base = .diLink12_8FHD
        case DisplayProfile.diLink15_6FHD.id:
            base = .diLink15_6FHD
        default:
            base = device.map { qualityNegotiator.initialProfile(for: $0) } ?? .diLink15_6FHD
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

    public func confirmTrustedVehicle() {
        guard let prompt = pendingPairing else { return }
        trustedStore.save(
            TrustedVehicle(
                id: prompt.device.id,
                displayName: prompt.device.displayName,
                fingerprint: prompt.fingerprint,
                pairedAtEpoch: Date().timeIntervalSince1970
            )
        )
        sharedStore.confirmPairingAndNotify(deviceId: prompt.device.id)
        trustedVehicles = trustedStore.loadAll()
        pendingPairing = nil
    }

    public func removeTrustedVehicle(deviceId: String) {
        trustedStore.remove(deviceId: deviceId)
        trustedVehicles = trustedStore.loadAll()
    }

    public func rejectPairing() {
        pendingPairing = nil
        disconnect()
    }

    public func isTrusted(deviceId: String) -> Bool {
        trustedStore.isTrusted(deviceId: deviceId) || sharedStore.isDeviceTrustedInGroup(deviceId)
    }

    public func toggleOrientation() {
        orientation = (orientation == .landscape) ? .portrait : .landscape
        sharedStore.saveOrientation(orientation)
        #if canImport(ActivityKit) && !os(macOS)
        if #available(iOS 16.1, *), let mgr = liveActivityManager as? LiveActivityManager {
            mgr.update(orientation: orientation, fps: activeProfile.targetFps)
        }
        #endif
        if case let .streaming(device, profile, _, fp) = connectionState {
            connectionState = .streaming(device: device, profile: profile, orientation: orientation, fingerprint: fp)
        }
    }

    public func disconnect() {
        sharedStore.requestStopBroadcast()
        #if canImport(ActivityKit) && !os(macOS)
        if #available(iOS 16.1, *), let mgr = liveActivityManager as? LiveActivityManager {
            mgr.stop()
        }
        #endif
        pendingPairing = nil
        showMiniOverlay = false
        connectionState = .idle
    }

    private func registerExtensionObservers() {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        let observer = Unmanaged.passUnretained(self).toOpaque()

        CFNotificationCenterAddObserver(
            center,
            observer,
            { _, observerPtr, _, _, _ in
                guard let observerPtr else { return }
                let vm = Unmanaged<MirrorViewModel>.fromOpaque(observerPtr).takeUnretainedValue()
                Task { @MainActor in
                    guard let verification = vm.sharedStore.loadPairingVerification(),
                          let device = vm.selectedDevice,
                          !vm.isTrusted(deviceId: device.id) else {
                        return
                    }
                    vm.pendingPairing = PairingPrompt(
                        id: device.id,
                        device: device,
                        pin: verification.pin,
                        fingerprint: verification.fingerprint
                    )
                }
            },
            SharedSessionStore.pairingUpdatedNotification,
            nil,
            .deliverImmediately
        )

        CFNotificationCenterAddObserver(
            center,
            observer,
            { _, observerPtr, _, _, _ in
                guard let observerPtr else { return }
                let vm = Unmanaged<MirrorViewModel>.fromOpaque(observerPtr).takeUnretainedValue()
                Task { @MainActor in
                    let status = vm.sharedStore.loadBroadcastState()
                    if let error = status.error {
                        vm.connectionState = .error(error)
                        vm.showMiniOverlay = false
                    } else if status.active, let device = vm.selectedDevice {
                        let fp = vm.sharedStore.loadPairingVerification()?.fingerprint ?? "ECDH-P256"
                        vm.connectionState = .streaming(
                            device: device,
                            profile: vm.activeProfile,
                            orientation: vm.orientation,
                            fingerprint: fp
                        )
                        vm.showMiniOverlay = true
                        #if canImport(ActivityKit) && !os(macOS)
                        if #available(iOS 16.1, *), let mgr = vm.liveActivityManager as? LiveActivityManager {
                            mgr.start(carName: device.displayName, orientation: vm.orientation, fps: vm.activeProfile.targetFps)
                        }
                        #endif
                    } else {
                        vm.connectionState = .idle
                        vm.showMiniOverlay = false
                        #if canImport(ActivityKit) && !os(macOS)
                        if #available(iOS 16.1, *), let mgr = vm.liveActivityManager as? LiveActivityManager {
                            mgr.stop()
                        }
                        #endif
                    }
                }
            },
            SharedSessionStore.broadcastStateChangedNotification,
            nil,
            .deliverImmediately
        )

        CFNotificationCenterAddObserver(
            center,
            observer,
            { _, observerPtr, _, _, _ in
                guard let observerPtr else { return }
                let vm = Unmanaged<MirrorViewModel>.fromOpaque(observerPtr).takeUnretainedValue()
                Task { @MainActor in
                    let latest = vm.sharedStore.loadTelemetry()
                    vm.telemetry.actualFps = latest.fps
                    vm.telemetry.rttMs = latest.rttMs
                    vm.telemetry.packetLossPercent = Float(latest.packetLossPercent)
                    vm.telemetry.currentBitrateMbps = Float(latest.bitrateMbps)
                }
            },
            SharedSessionStore.telemetryUpdatedNotification,
            nil,
            .deliverImmediately
        )
    }

    deinit {
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        CFNotificationCenterRemoveEveryObserver(center, Unmanaged.passUnretained(self).toOpaque())
    }
}
