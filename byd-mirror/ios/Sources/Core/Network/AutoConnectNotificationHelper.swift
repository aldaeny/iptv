import Foundation

#if canImport(UserNotifications)
import UserNotifications

/// Prompts the user with a one-tap notification when entering BYD vehicle Wi-Fi range,
/// since iOS requires explicit user confirmation to start ReplayKit system broadcasting.
public final class AutoConnectNotificationHelper: @unchecked Sendable {

    public init() {}

    public func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    public func notifyBydCarInRange(ssid: String?, deviceName: String?) {
        let content = UNMutableNotificationContent()
        content.title = "تم رصد سيارة BYD قريبة"
        let carLabel = deviceName ?? ssid ?? "BYD DiLink"
        content.body = "اضغط لبدء العرض اللاسلكي على \(carLabel)"
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "com.byd.mirror.in-range",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }
}
#endif
