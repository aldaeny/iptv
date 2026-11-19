import Foundation

#if canImport(ActivityKit) && !os(macOS)
import ActivityKit

/// Manages Dynamic Island & Lock Screen Live Activity updates during active BYD mirroring.
@available(iOS 16.1, *)
@MainActor
public final class LiveActivityManager {
    private var currentActivity: Activity<BydMirrorActivityAttributes>?

    public init() {}

    public func start(carName: String, orientation: DisplayOrientation, fps: Int) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        let attrs = BydMirrorActivityAttributes(carName: carName)
        let state = BydMirrorActivityAttributes.ContentState(
            statusLabel: "LIVE • AES-256",
            orientationLabel: orientation == .landscape ? "أفقي" : "عمودي",
            fps: fps
        )
        currentActivity = try? Activity.request(
            attributes: attrs,
            content: ActivityContent(state: state, staleDate: nil),
            pushType: nil
        )
    }

    public func update(orientation: DisplayOrientation, fps: Int) {
        let state = BydMirrorActivityAttributes.ContentState(
            statusLabel: "LIVE • AES-256",
            orientationLabel: orientation == .landscape ? "أفقي" : "عمودي",
            fps: fps
        )
        Task {
            await currentActivity?.update(ActivityContent(state: state, staleDate: nil))
        }
    }

    public func stop() {
        Task {
            await currentActivity?.end(nil, dismissalPolicy: .immediate)
            currentActivity = nil
        }
    }
}
#endif
