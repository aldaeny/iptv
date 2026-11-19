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
                    .foregroundColor(Color(red: 0.0, green: 200.0/255.0, blue: 1.0))
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.attributes.carName)
                        .font(.headline)
                        .foregroundColor(Color(red: 244.0/255.0, green: 248.0/255.0, blue: 1.0))
                    Text("\(context.state.statusLabel) • \(context.state.orientationLabel)")
                        .font(.caption)
                        .foregroundColor(Color(red: 0.0, green: 208.0/255.0, blue: 132.0/255.0))
                }
                Spacer()
                Text("\(context.state.fps) FPS")
                    .font(.caption.bold())
                    .foregroundColor(Color(red: 244.0/255.0, green: 248.0/255.0, blue: 1.0))
            }
            .padding()
            .activityBackgroundTint(Color(red: 5.0/255.0, green: 9.0/255.0, blue: 20.0/255.0))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label("BYD", systemImage: "airplayvideo")
                        .font(.caption.bold())
                        .foregroundColor(Color(red: 0.0, green: 200.0/255.0, blue: 1.0))
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("\(context.state.fps) FPS")
                        .font(.caption.bold())
                        .foregroundColor(Color(red: 0.0, green: 208.0/255.0, blue: 132.0/255.0))
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("\(context.attributes.carName) • \(context.state.orientationLabel)")
                        .font(.caption)
                        .foregroundColor(Color(red: 244.0/255.0, green: 248.0/255.0, blue: 1.0))
                }
            } compactLeading: {
                Image(systemName: "airplayvideo")
                    .foregroundColor(Color(red: 0.0, green: 200.0/255.0, blue: 1.0))
            } compactTrailing: {
                Text("\(context.state.fps)")
                    .font(.caption2.bold())
                    .foregroundColor(Color(red: 0.0, green: 208.0/255.0, blue: 132.0/255.0))
            } minimal: {
                Image(systemName: "airplayvideo")
                    .foregroundColor(Color(red: 0.0, green: 200.0/255.0, blue: 1.0))
            }
        }
    }
}
