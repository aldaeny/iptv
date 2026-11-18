import Foundation

#if canImport(ActivityKit) && !os(macOS)
import ActivityKit

@available(iOS 16.1, *)
public struct BydMirrorActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var statusLabel: String
        public var orientationLabel: String
        public var fps: Int

        public init(statusLabel: String, orientationLabel: String, fps: Int) {
            self.statusLabel = statusLabel
            self.orientationLabel = orientationLabel
            self.fps = fps
        }
    }

    public var carName: String

    public init(carName: String) {
        self.carName = carName
    }
}
#endif
