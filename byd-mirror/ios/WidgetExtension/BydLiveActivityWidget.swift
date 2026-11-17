import ActivityKit
import SwiftUI
import WidgetKit

@main
struct BydMirrorWidgetBundle: WidgetBundle {
    var body: some Widget {
        BydLiveActivityWidget()
    }
}

struct BydLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: BydMirrorActivityAttributes.self) { context in
            HStack(spacing: 12) {
                Image(systemName: "airplayvideo")
                    .foregroundColor(Color(red: 0, green: 0.83, blue: 1))
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.attributes.carName)
                        .font(.headline)
                        .foregroundColor(.white)
                    Text("\(context.state.statusLabel) • \(context.state.orientationLabel)")
                        .font(.caption)
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                }
                Spacer()
                Text("\(context.state.fps) FPS")
                    .font(.caption.bold())
                    .foregroundColor(.white)
            }
            .padding()
            .activityBackgroundTint(Color(red: 0.03, green: 0.04, blue: 0.06))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label("BYD", systemImage: "airplayvideo")
                        .font(.caption.bold())
                        .foregroundColor(Color(red: 0, green: 0.83, blue: 1))
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("\(context.state.fps) FPS")
                        .font(.caption.bold())
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("\(context.attributes.carName) • \(context.state.orientationLabel)")
                        .font(.caption)
                        .foregroundColor(.white)
                }
            } compactLeading: {
                Image(systemName: "airplayvideo")
                    .foregroundColor(Color(red: 0, green: 0.83, blue: 1))
            } compactTrailing: {
                Text("\(context.state.fps)")
                    .font(.caption2.bold())
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
            } minimal: {
                Image(systemName: "airplayvideo")
                    .foregroundColor(Color(red: 0, green: 0.83, blue: 1))
            }
        }
    }
}
