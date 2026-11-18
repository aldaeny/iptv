import SwiftUI

/// Compact floating HUD card (also adaptable for PiP status rendering)
/// offering one-tap orientation switching and disconnect controls.
public struct MiniControlOverlayView: View {
    let orientation: DisplayOrientation
    let onToggleOrientation: () -> Void
    let onDisconnect: () -> Void

    public init(
        orientation: DisplayOrientation,
        onToggleOrientation: @escaping () -> Void,
        onDisconnect: @escaping () -> Void
    ) {
        self.orientation = orientation
        self.onToggleOrientation = onToggleOrientation
        self.onDisconnect = onDisconnect
    }

    public var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(BydColors.evEmerald)
                .frame(width: 8, height: 8)

            Text("BYD • LIVE")
                .font(.caption.bold())
                .foregroundColor(BydColors.evEmerald)

            Button(action: onToggleOrientation) {
                Label(
                    orientation == .landscape ? "أفقي" : "عمودي",
                    systemImage: "rotate.right"
                )
                .font(.caption.bold())
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(BydColors.surfaceElevated)
                .foregroundColor(BydColors.textPrimary)
                .clipShape(Capsule())
            }

            Button(action: onDisconnect) {
                Text("إنهاء")
                    .font(.caption.bold())
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(BydColors.dangerRed)
                    .foregroundColor(.white)
                    .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(BydColors.surfaceCard.opacity(0.95))
        .overlay(
            Capsule().stroke(BydColors.evCyan.opacity(0.6), lineWidth: 1)
        )
        .clipShape(Capsule())
        .shadow(color: .black.opacity(0.5), radius: 12, y: 6)
    }
}
