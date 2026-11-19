import SwiftUI

public enum BydColors {
    // Phase 2 Design System Tokens
    public static let background = Color(red: 5/255, green: 9/255, blue: 20/255)
    public static let primaryBackground = Color(red: 8/255, green: 15/255, blue: 28/255)
    public static let surface = Color(red: 16/255, green: 27/255, blue: 45/255)
    public static let elevatedSurface = Color(red: 21/255, green: 36/255, blue: 59/255)
    public static let border = Color(red: 32/255, green: 55/255, blue: 84/255)
    public static let primary = Color(red: 0/255, green: 200/255, blue: 255/255)
    public static let primaryBlue = Color(red: 22/255, green: 119/255, blue: 255/255)
    public static let secondaryCyan = Color(red: 0/255, green: 229/255, blue: 255/255)
    public static let success = Color(red: 0/255, green: 208/255, blue: 132/255)
    public static let warning = Color(red: 255/255, green: 181/255, blue: 71/255)
    public static let error = Color(red: 255/255, green: 69/255, blue: 69/255)
    public static let primaryText = Color(red: 244/255, green: 248/255, blue: 255/255)
    public static let secondaryText = Color(red: 154/255, green: 174/255, blue: 199/255)
    public static let disabled = Color(red: 101/255, green: 116/255, blue: 138/255)

    // Backwards-compatible aliases
    public static let obsidianBg = background
    public static let surfaceCard = surface
    public static let surfaceElevated = elevatedSurface
    public static let evCyan = primary
    public static let evEmerald = success
    public static let warningAmber = warning
    public static let dangerRed = error
    public static let textPrimary = primaryText
    public static let textSecondary = secondaryText
    public static let borderSubtle = border
}
