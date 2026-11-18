import Combine
import Foundation

/// Monitors iOS thermal pressure & Low Power Mode (Phase 4) to prevent overheating
/// during extended wireless screen mirroring sessions.
public final class ThermalPowerMonitor: ObservableObject {

    @Published public private(set) var thermalLevel: ThermalLevel = .nominal
    @Published public private(set) var isLowPowerModeEnabled: Bool = false

    private var cancellables = Set<AnyCancellable>()

    public init() {
        refresh()
    }

    public func startMonitoring() {
        NotificationCenter.default
            .publisher(for: ProcessInfo.thermalStateDidChangeNotification)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.refresh() }
            .store(in: &cancellables)

        NotificationCenter.default
            .publisher(for: Notification.Name.NSProcessInfoPowerStateDidChange)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.refresh() }
            .store(in: &cancellables)
    }

    public func stopMonitoring() {
        cancellables.removeAll()
    }

    private func refresh() {
        isLowPowerModeEnabled = ProcessInfo.processInfo.isLowPowerModeEnabled
        switch ProcessInfo.processInfo.thermalState {
        case .nominal: thermalLevel = .nominal
        case .fair: thermalLevel = .moderate
        case .serious: thermalLevel = .severe
        case .critical: thermalLevel = .critical
        @unknown default: thermalLevel = .moderate
        }
    }
}
