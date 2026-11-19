import AVFoundation
import ReplayKit
import SwiftUI
import UIKit

private enum IosMirrorTab: Int, CaseIterable {
    case home
    case devices
    case settings
    case about
}

public struct MirrorDashboardView: View {
    @StateObject private var viewModel = MirrorViewModel()
    @StateObject private var pipController = PipOverlayController()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var selectedTab: IosMirrorTab = .home
    @State private var showActiveMirrorScreen = false
    @State private var showManualIpAlert = false
    @State private var pulseExpanded = false
    @State private var sessionElapsedSeconds: Int = 0
    @State private var sessionTimer: Timer?

    public init() {}

    public var body: some View {
        ZStack(alignment: .bottom) {
            LinearGradient(
                colors: [BydColors.primaryBackground, BydColors.background],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            // Attach AVSampleBufferDisplayLayer into the view hierarchy so iOS PiP can launch
            PipSampleLayerHost(displayLayer: pipController.displayLayer)
                .frame(width: 1, height: 1)
                .opacity(0.01)

            if isConnectingStage {
                connectingExperienceView
            } else if showActiveMirrorScreen, case let .streaming(device, profile, orientation, fp) = viewModel.connectionState {
                activeMirroringView(device: device, profile: profile, orientation: orientation, fingerprint: fp)
            } else {
                VStack(spacing: 0) {
                    topBarView
                    ScrollView {
                        Group {
                            switch selectedTab {
                            case .home:
                                homeTabView
                            case .devices:
                                devicesTabView
                            case .settings:
                                settingsTabView
                            case .about:
                                aboutTabView
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 14)
                    }
                    bottomTabBar
                }
            }

            if viewModel.showMiniOverlay && !showActiveMirrorScreen {
                MiniControlOverlayView(
                    orientation: viewModel.orientation,
                    onToggleOrientation: viewModel.toggleOrientation,
                    onDisconnect: viewModel.disconnect
                )
                .padding(.bottom, 76)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            pipController.configure()
            if !reduceMotion {
                withAnimation(.easeInOut(duration: 1.6).repeatForever(autoreverses: true)) {
                    pulseExpanded = true
                }
            }
        }
        .onDisappear {
            stopSessionTimer()
        }
        .onChange(of: viewModel.connectionState) { newState in
            if case .streaming = newState {
                showActiveMirrorScreen = true
                startSessionTimer()
            } else {
                showActiveMirrorScreen = false
                stopSessionTimer()
            }
        }
        .onChange(of: viewModel.orientation) { newOrientation in
            pipController.updateStatus(newOrientation == .landscape ? "BYD • أفقي" : "BYD • عمودي")
        }
        .alert(viewModel.isArabic ? "الاتصال برقم IP" : "Connect by IP", isPresented: $showManualIpAlert) {
            TextField("192.168.43.1", text: $viewModel.manualIpInput)
                .keyboardType(.numbersAndPunctuation)
                .autocorrectionDisabled()
            Button(viewModel.isArabic ? "ربط الآن" : "Connect") {
                viewModel.connectManualIp()
            }
            Button(viewModel.isArabic ? "إلغاء" : "Cancel", role: .cancel) {}
        } message: {
            Text(
                viewModel.isArabic
                    ? "أدخل رقم IP الظاهر على شاشة تطبيق BYD Receiver في السيارة:"
                    : "Enter the IP address shown on the BYD Receiver screen:"
            )
        }
        .sheet(item: $viewModel.pendingPairing) { prompt in
            VStack(spacing: 18) {
                Text(viewModel.isArabic ? "تأكيد الاقتران الآمن" : "Verify Secure Pairing")
                    .font(.title3.bold())
                    .foregroundColor(BydColors.primaryText)

                Text(
                    viewModel.isArabic
                        ? "تأكد من تطابق الرمز على شاشة \(prompt.device.displayName):"
                        : "Confirm this PIN matches \(prompt.device.displayName):"
                )
                .font(.subheadline)
                .foregroundColor(BydColors.secondaryText)
                .multilineTextAlignment(.center)

                Text(prompt.pin)
                    .font(.system(size: 40, weight: .heavy, design: .monospaced))
                    .tracking(6)
                    .foregroundColor(BydColors.primary)

                Text("Fingerprint: \(prompt.fingerprint)")
                    .font(.caption)
                    .foregroundColor(BydColors.success)

                HStack(spacing: 12) {
                    Button(viewModel.isArabic ? "إلغاء" : "Cancel") {
                        viewModel.rejectPairing()
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(BydColors.error.opacity(0.2))
                    .foregroundColor(BydColors.error)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    Button(viewModel.isArabic ? "تأكيد وحفظ" : "Trust Vehicle") {
                        viewModel.confirmTrustedVehicle()
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(BydColors.primary)
                    .foregroundColor(Color(red: 4/255, green: 16/255, blue: 22/255))
                    .font(.subheadline.bold())
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
            .padding(24)
            .background(BydColors.surface)
            .presentationDetents([.medium])
        }
    }

    private var isConnectingStage: Bool {
        switch viewModel.connectionState {
        case .discovering, .handshaking, .keyExchange, .negotiatingQuality, .reconnecting:
            return true
        default:
            return false
        }
    }

    // MARK: - Top Bar & Bottom Bar
    private var topBarView: some View {
        HStack(spacing: 10) {
            Image("BrandIcon")
                .resizable()
                .scaledToFill()
                .frame(width: 34, height: 34)
                .clipShape(RoundedRectangle(cornerRadius: 9))
                .overlay(RoundedRectangle(cornerRadius: 9).stroke(BydColors.border, lineWidth: 1))

            VStack(alignment: .leading, spacing: 1) {
                Text("BYD Wireless Mirror")
                    .font(.subheadline.bold())
                    .foregroundColor(BydColors.primaryText)
                    .lineLimit(1)
                Text(viewModel.isArabic ? "ربط الآيفون بشاشة السيارة" : "Connect your iPhone to your BYD")
                    .font(.caption2)
                    .foregroundColor(BydColors.secondaryText)
                    .lineLimit(1)
            }

            Spacer()

            statusPill

            Button {
                viewModel.toggleLanguage()
            } label: {
                Text(viewModel.isArabic ? "EN" : "AR")
                    .font(.caption.bold())
                    .foregroundColor(BydColors.primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(BydColors.elevatedSurface)
                    .overlay(Capsule().stroke(BydColors.border, lineWidth: 1))
                    .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
    }

    private var bottomTabBar: some View {
        VStack(spacing: 0) {
            Divider().background(BydColors.border)
            HStack {
                tabButton(tab: .home, icon: "house.fill", label: viewModel.isArabic ? "الرئيسية" : "Home")
                tabButton(tab: .devices, icon: "car.fill", label: viewModel.isArabic ? "الأجهزة" : "Devices")
                tabButton(tab: .settings, icon: "gearshape.fill", label: viewModel.isArabic ? "الإعدادات" : "Settings")
                tabButton(tab: .about, icon: "info.circle.fill", label: viewModel.isArabic ? "حول" : "About")
            }
            .padding(.horizontal, 10)
            .padding(.top, 8)
            .padding(.bottom, 6)
            .background(BydColors.primaryBackground)
        }
    }

    private func tabButton(tab: IosMirrorTab, icon: String, label: String) -> some View {
        let selected = selectedTab == tab
        return Button {
            selectedTab = tab
        } label: {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .semibold))
                Text(label)
                    .font(.caption2.weight(selected ? .bold : .medium))
            }
            .foregroundColor(selected ? BydColors.primary : BydColors.secondaryText)
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(selected ? BydColors.primary.opacity(0.12) : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    // MARK: - Home Tab
    private var homeTabView: some View {
        let isStreaming: Bool = {
            if case .streaming = viewModel.connectionState { return true }
            return false
        }()

        return VStack(spacing: 16) {
            heroVehicleCard(accent: isStreaming ? BydColors.success : BydColors.primary)

            VStack(spacing: 14) {
                Text(statusHeadline)
                    .font(.headline.bold())
                    .foregroundColor(BydColors.primaryText)
                    .multilineTextAlignment(.center)

                Text(
                    viewModel.isArabic
                        ? (viewModel.discoveredDevices.isEmpty
                            ? "غير متصل — تأكد من فتح مستقبل BYD على شاشة السيارة."
                            : "تم اكتشاف \(viewModel.discoveredDevices.count) شاشة جاهزة للربط.")
                        : (viewModel.discoveredDevices.isEmpty
                            ? "Not connected — open BYD Receiver on your car screen."
                            : "\(viewModel.discoveredDevices.count) compatible receiver(s) ready.")
                )
                .font(.caption)
                .foregroundColor(BydColors.secondaryText)
                .multilineTextAlignment(.center)

                if isStreaming {
                    diagnosticsHudRow
                }

                Button {
                    if isStreaming {
                        showActiveMirrorScreen = true
                    } else {
                        viewModel.onOneTapConnect()
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: isStreaming ? "checkmark.circle.fill" : "airplayvideo")
                        Text(
                            isStreaming
                                ? (viewModel.isArabic ? "عرض البث النشط" : "Open Active Mirroring")
                                : (viewModel.isArabic ? "بدء مشاركة الشاشة" : "START MIRRORING")
                        )
                        .fontWeight(.heavy)
                    }
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .background(
                        LinearGradient(
                            colors: isStreaming
                                ? [BydColors.success, BydColors.primary]
                                : [BydColors.primaryBlue, BydColors.primary],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .foregroundColor(Color(red: 4/255, green: 16/255, blue: 22/255))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }

                Button {
                    selectedTab = .devices
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(BydColors.primary)
                        Text(
                            viewModel.isArabic
                                ? "البحث عن الأجهزة (\(viewModel.discoveredDevices.count))"
                                : "Find Devices (\(viewModel.discoveredDevices.count))"
                        )
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(BydColors.primaryText)
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(BydColors.elevatedSurface)
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(BydColors.border, lineWidth: 1))
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }

                if viewModel.hasBroadcastExtension {
                    HStack(spacing: 10) {
                        ReplayKitBroadcastButton(bundleIdentifier: "com.byd.mirror.app.broadcast")
                            .frame(width: 48, height: 44)
                            .background(BydColors.elevatedSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        Text(viewModel.isArabic ? "بث النظام عبر ReplayKit" : "System-wide ReplayKit Broadcast")
                            .font(.caption)
                            .foregroundColor(BydColors.secondaryText)
                        Spacer()
                    }
                }
            }
            .padding(20)
            .background(BydColors.surface)
            .overlay(RoundedRectangle(cornerRadius: 22).stroke(BydColors.border, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 22))
        }
    }

    // MARK: - Devices Tab
    private var devicesTabView: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(viewModel.isArabic ? "الأجهزة المتاحة" : "Devices")
                        .font(.title3.bold())
                        .foregroundColor(BydColors.primaryText)
                    Text(viewModel.isArabic ? "اكتشاف عبر Bonjour و P2P المحلي" : "Local Bonjour & AWDL peer discovery")
                        .font(.caption)
                        .foregroundColor(BydColors.secondaryText)
                }
                Spacer()
                Button {
                    showManualIpAlert = true
                } label: {
                    Text(viewModel.isArabic ? "اتصال بـ IP" : "Manual IP")
                        .font(.caption.bold())
                        .foregroundColor(BydColors.primary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(BydColors.elevatedSurface)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(BydColors.border, lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }

            if case let .error(msg) = viewModel.connectionState {
                HStack(spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(BydColors.error)
                    Text(msg)
                        .font(.caption)
                        .foregroundColor(BydColors.primaryText)
                    Spacer()
                }
                .padding(14)
                .background(BydColors.error.opacity(0.14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(BydColors.error.opacity(0.45), lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 14))
            }

            if case let .streaming(activeDevice, _, _, _) = viewModel.connectionState {
                HStack(spacing: 12) {
                    Image(systemName: "airplayvideo")
                        .foregroundColor(BydColors.success)
                        .frame(width: 44, height: 44)
                        .background(BydColors.success.opacity(0.16))
                        .clipShape(RoundedRectangle(cornerRadius: 12))

                    VStack(alignment: .leading, spacing: 3) {
                        Text(activeDevice.displayName)
                            .font(.subheadline.bold())
                            .foregroundColor(BydColors.primaryText)
                        Text(viewModel.isArabic ? "متصل الآن • \(activeDevice.hostAddress)" : "Connected • \(activeDevice.hostAddress)")
                            .font(.caption)
                            .foregroundColor(BydColors.success)
                    }
                    Spacer()
                    Button(viewModel.isArabic ? "قطع" : "Disconnect") {
                        viewModel.disconnect()
                    }
                    .font(.caption.bold())
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(BydColors.error)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .padding(14)
                .background(BydColors.elevatedSurface)
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(BydColors.success.opacity(0.5), lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            if viewModel.discoveredDevices.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "wifi.exclamationmark")
                        .font(.system(size: 32))
                        .foregroundColor(BydColors.primary)
                    Text(viewModel.isArabic ? "لا توجد شاشات مكتشفة بعد" : "No receivers discovered yet")
                        .font(.headline)
                        .foregroundColor(BydColors.primaryText)
                    Text(
                        viewModel.isArabic
                            ? "افتح تطبيق BYD Receiver على شاشة السيارة وتأكد من اتصال الجهازين بنفس شبكة Wi-Fi أو نقطة الاتصال."
                            : "Open BYD Receiver on your car display on the same Wi-Fi/Hotspot, or connect manually by IP."
                    )
                    .font(.caption)
                    .foregroundColor(BydColors.secondaryText)
                    .multilineTextAlignment(.center)

                    HStack(spacing: 10) {
                        Button(viewModel.isArabic ? "مسح الآن" : "Scan Again") {
                            viewModel.onOneTapConnect()
                        }
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(BydColors.elevatedSurface)
                        .foregroundColor(BydColors.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))

                        Button(viewModel.isArabic ? "اتصال بـ IP" : "Manual IP") {
                            showManualIpAlert = true
                        }
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(BydColors.primary)
                        .foregroundColor(Color(red: 4/255, green: 16/255, blue: 22/255))
                        .fontWeight(.bold)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
                .padding(22)
                .frame(maxWidth: .infinity)
                .background(BydColors.surface)
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(BydColors.border, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 18))
            } else {
                ForEach(viewModel.discoveredDevices) { device in
                    Button {
                        viewModel.prepareConnection(to: device)
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "car.fill")
                                .foregroundColor(BydColors.primary)
                                .frame(width: 44, height: 44)
                                .background(BydColors.primary.opacity(0.14))
                                .clipShape(RoundedRectangle(cornerRadius: 12))

                            VStack(alignment: .leading, spacing: 3) {
                                HStack(spacing: 6) {
                                    Text(device.displayName)
                                        .font(.subheadline.bold())
                                        .foregroundColor(BydColors.primaryText)
                                    if viewModel.isTrusted(deviceId: device.id) {
                                        Image(systemName: "checkmark.shield.fill")
                                            .font(.caption)
                                            .foregroundColor(BydColors.success)
                                    }
                                }
                                Text("\(device.modelName) • \(device.hostAddress)")
                                    .font(.caption)
                                    .foregroundColor(BydColors.secondaryText)
                            }
                            Spacer()
                            Text(viewModel.isArabic ? "ربط" : "Connect")
                                .font(.caption.bold())
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(BydColors.primary)
                                .foregroundColor(Color(red: 4/255, green: 16/255, blue: 22/255))
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                        .padding(14)
                        .background(BydColors.elevatedSurface)
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(BydColors.border, lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                }
            }
        }
    }

    // MARK: - Connecting View
    private var connectingExperienceView: some View {
        let deviceName: String = {
            switch viewModel.connectionState {
            case .handshaking(let d), .keyExchange(let d), .negotiatingQuality(let d), .reconnecting(let d, _):
                return d.displayName
            default:
                return viewModel.isArabic ? "شاشة BYD DiLink" : "BYD DiLink Display"
            }
        }()

        let activeStep: Int = {
            switch viewModel.connectionState {
            case .discovering: return 0
            case .handshaking, .reconnecting: return 1
            case .keyExchange: return 2
            case .negotiatingQuality: return 3
            default: return 0
            }
        }()

        let steps = viewModel.isArabic
            ? ["البحث عن الشاشة", "تأسيس الاتصال", "تأمين المفاتيح ECDH P-256", "تهيئة جودة البث"]
            : ["Discovering Device", "Establishing Connection", "Negotiating Session (ECDH)", "Preparing Stream"]

        return VStack(spacing: 20) {
            Spacer()
            heroVehicleCard(accent: BydColors.primary)

            Text(viewModel.isArabic ? "جاري الاتصال..." : "Connecting...")
                .font(.title2.bold())
                .foregroundColor(BydColors.primaryText)
            Text(deviceName)
                .font(.subheadline.bold())
                .foregroundColor(BydColors.primary)

            VStack(alignment: .leading, spacing: 12) {
                ForEach(Array(steps.enumerated()), id: \.offset) { idx, title in
                    HStack(spacing: 12) {
                        if idx < activeStep {
                            Image(systemName: "checkmark.circle.fill").foregroundColor(BydColors.success)
                        } else if idx == activeStep {
                            ProgressView().tint(BydColors.primary)
                        } else {
                            Circle().fill(BydColors.border).frame(width: 18, height: 18)
                        }
                        Text(title)
                            .font(.subheadline.weight(idx == activeStep ? .bold : .regular))
                            .foregroundColor(idx <= activeStep ? BydColors.primaryText : BydColors.disabled)
                        Spacer()
                    }
                }
            }
            .padding(18)
            .background(BydColors.surface)
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(BydColors.border, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 18))

            Spacer()

            Button(viewModel.isArabic ? "إلغاء" : "Cancel") {
                viewModel.disconnect()
            }
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(BydColors.elevatedSurface)
            .foregroundColor(BydColors.error)
            .font(.headline)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(BydColors.border, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .padding(24)
    }

    // MARK: - Active Mirroring View
    private func activeMirroringView(
        device: BydDevice,
        profile: DisplayProfile,
        orientation: DisplayOrientation,
        fingerprint: String
    ) -> some View {
        let dims = profile.dimensions(for: orientation)
        let mins = sessionElapsedSeconds / 60
        let secs = sessionElapsedSeconds % 60
        let timerText = String(format: "%02d:%02d", mins, secs)

        return ScrollView {
            VStack(spacing: 16) {
                HStack {
                    Button {
                        showActiveMirrorScreen = false
                    } label: {
                        Image(systemName: "chevron.backward")
                            .foregroundColor(BydColors.primaryText)
                            .frame(width: 40, height: 40)
                            .background(BydColors.elevatedSurface)
                            .clipShape(Circle())
                    }
                    Spacer()
                    Text(viewModel.isArabic ? "البث النشط" : "Mirroring")
                        .font(.headline.bold())
                        .foregroundColor(BydColors.primaryText)
                    Spacer()
                    Text("LIVE")
                        .font(.caption2.bold())
                        .foregroundColor(BydColors.success)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(BydColors.success.opacity(0.16))
                        .clipShape(Capsule())
                }

                heroVehicleCard(accent: BydColors.success)

                VStack(spacing: 6) {
                    Text(viewModel.isArabic ? "متصل بـ \(device.displayName)" : "Connected to \(device.displayName)")
                        .font(.headline.bold())
                        .foregroundColor(BydColors.primaryText)
                    Text(timerText)
                        .font(.system(size: 28, weight: .heavy, design: .monospaced))
                        .foregroundColor(BydColors.primary)
                    Text("\(dims.width)x\(dims.height) • \(profile.codec.rawValue) • Key \(fingerprint)")
                        .font(.caption)
                        .foregroundColor(BydColors.secondaryText)
                }
                .frame(maxWidth: .infinity)
                .padding(18)
                .background(BydColors.surface)
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(BydColors.success.opacity(0.45), lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 18))

                diagnosticsHudRow

                HStack(spacing: 10) {
                    Button(action: viewModel.toggleOrientation) {
                        Label(
                            orientation == .landscape
                                ? (viewModel.isArabic ? "أفقي" : "Landscape")
                                : (viewModel.isArabic ? "عمودي" : "Portrait"),
                            systemImage: "rotate.right"
                        )
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(BydColors.elevatedSurface)
                        .foregroundColor(BydColors.primaryText)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }

                    Button {
                        pipController.updateStatus(orientation == .landscape ? "BYD • أفقي" : "BYD • عمودي")
                        pipController.startPip()
                    } label: {
                        Label("PiP", systemImage: "pip.enter")
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(BydColors.elevatedSurface)
                            .foregroundColor(BydColors.primary)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                }

                Button(action: viewModel.disconnect) {
                    Label(viewModel.isArabic ? "إيقاف البث" : "Stop Mirroring", systemImage: "stop.fill")
                        .font(.headline)
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .background(BydColors.error)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                }
            }
            .padding(20)
        }
    }

    // MARK: - Settings Tab
    private var settingsTabView: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(viewModel.isArabic ? "الإعدادات" : "Settings")
                .font(.title3.bold())
                .foregroundColor(BydColors.primaryText)

            VStack(alignment: .leading, spacing: 12) {
                Text(viewModel.isArabic ? "جودة الترميز" : "Video Codec")
                    .font(.caption.bold())
                    .foregroundColor(BydColors.secondaryText)
                HStack(spacing: 10) {
                    chipButton(title: "H.265 (HEVC)", selected: viewModel.preferHevc, accent: BydColors.primary) {
                        viewModel.setCodec(preferHevc: true)
                    }
                    chipButton(title: "H.264 (AVC)", selected: !viewModel.preferHevc, accent: BydColors.primary) {
                        viewModel.setCodec(preferHevc: false)
                    }
                }

                Divider().background(BydColors.border)

                Text(viewModel.isArabic ? "حجم شاشة السيارة" : "Car Screen Size")
                    .font(.caption.bold())
                    .foregroundColor(BydColors.secondaryText)
                HStack(spacing: 8) {
                    let options: [(String, String)] = [
                        ("auto", viewModel.isArabic ? "تلقائي" : "Auto"),
                        (DisplayProfile.diLink15_6FHD.id, "15.6\""),
                        (DisplayProfile.diLink12_8FHD.id, "12.8\""),
                        (DisplayProfile.diLink10_1HD.id, "10.1\"")
                    ]
                    ForEach(options, id: \.0) { pid, title in
                        chipButton(title: title, selected: viewModel.selectedProfileId == pid, accent: BydColors.success) {
                            viewModel.selectProfile(id: pid)
                        }
                    }
                }

                Divider().background(BydColors.border)

                Toggle(isOn: $viewModel.autoConnectEnabled) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(viewModel.isArabic ? "الربط التلقائي" : "Auto Reconnect")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(BydColors.primaryText)
                        Text(viewModel.isArabic ? "اتصال تلقائي عند الاقتراب من السيارة" : "Auto-link on BYD Wi-Fi")
                            .font(.caption)
                            .foregroundColor(BydColors.secondaryText)
                    }
                }
                .tint(BydColors.primary)
            }
            .padding(16)
            .background(BydColors.surface)
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(BydColors.border, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 18))

            if !viewModel.trustedVehicles.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text(viewModel.isArabic ? "السيارات المحفوظة" : "Saved Vehicles")
                        .font(.caption.bold())
                        .foregroundColor(BydColors.secondaryText)

                    ForEach(viewModel.trustedVehicles, id: \.id) { vehicle in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(vehicle.displayName)
                                    .font(.subheadline.weight(.medium))
                                    .foregroundColor(BydColors.primaryText)
                                Text("Fingerprint: \(vehicle.fingerprint)")
                                    .font(.caption2)
                                    .foregroundColor(BydColors.success)
                            }
                            Spacer()
                            Button {
                                viewModel.removeTrustedVehicle(deviceId: vehicle.id)
                            } label: {
                                Image(systemName: "trash")
                                    .foregroundColor(BydColors.error)
                            }
                        }
                        .padding(12)
                        .background(BydColors.elevatedSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
        }
    }

    // MARK: - About Tab
    private var aboutTabView: some View {
        let shortVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        let modeLabel = viewModel.hasBroadcastExtension ? "ReplayKit Broadcast Edition" : "Free-Sideload In-App Edition"

        return VStack(spacing: 16) {
            VStack(spacing: 10) {
                Image("BrandIcon")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 84, height: 84)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .overlay(RoundedRectangle(cornerRadius: 20).stroke(BydColors.primary.opacity(0.5), lineWidth: 1))

                Text("BYD Wireless Mirror")
                    .font(.title3.bold())
                    .foregroundColor(BydColors.primaryText)

                Text("Version \(shortVersion) (Build \(buildNumber)) • \(modeLabel)")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(BydColors.primary)

                Text(
                    viewModel.isArabic
                        ? "بث لاسلكي مباشر لشاشة الآيفون إلى شاشة BYD DiLink بتشفير محلي كامل ECDH P-256 + AES-256-GCM."
                        : "Low-latency local wireless screen mirroring from iPhone to BYD DiLink with ECDH P-256 + AES-256-GCM encryption."
                )
                .font(.caption)
                .foregroundColor(BydColors.secondaryText)
                .multilineTextAlignment(.center)
            }
            .padding(22)
            .frame(maxWidth: .infinity)
            .background(BydColors.surface)
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(BydColors.border, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 20))

            VStack(alignment: .leading, spacing: 10) {
                Label(viewModel.isArabic ? "الخصوصية المحلية" : "Privacy & Security", systemImage: "lock.shield.fill")
                    .font(.subheadline.bold())
                    .foregroundColor(BydColors.success)
                Text(
                    viewModel.isArabic
                        ? "لا يجمع التطبيق أي بيانات ولا يتصل بأي خادم سحابي. تبقى جميع البيانات داخل شبكة Wi-Fi المحلية."
                        : "Zero cloud telemetry. All discovery and streaming traffic remains strictly on your local Wi-Fi network."
                )
                .font(.caption)
                .foregroundColor(BydColors.secondaryText)

                Divider().background(BydColors.border)

                Label(viewModel.isArabic ? "مكتبات النظام المستخدمة" : "Open Source & Frameworks", systemImage: "chevron.left.forwardslash.chevron.right")
                    .font(.subheadline.bold())
                    .foregroundColor(BydColors.primary)
                Text("Swift 5.10 • SwiftUI • VideoToolbox • AudioToolbox • Network • CryptoKit • ReplayKit")
                    .font(.caption)
                    .foregroundColor(BydColors.secondaryText)
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(BydColors.surface)
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(BydColors.border, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 18))
        }
    }

    // MARK: - Helpers
    private func heroVehicleCard(accent: Color) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 20)
                .fill(BydColors.background)
                .overlay(
                    Circle()
                        .stroke(accent.opacity(pulseExpanded ? 0.35 : 0.15), lineWidth: 2)
                        .scaleEffect(pulseExpanded ? 1.05 : 0.92)
                )

            Image("BrandHeroVehicle")
                .resizable()
                .scaledToFill()
                .frame(height: 165)
                .clipShape(RoundedRectangle(cornerRadius: 20))
        }
        .frame(height: 165)
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(BydColors.border, lineWidth: 1))
    }

    private var diagnosticsHudRow: some View {
        HStack {
            hudMetric(label: "FPS", value: "\(viewModel.telemetry.actualFps)", color: BydColors.success)
            Spacer()
            hudMetric(label: viewModel.isArabic ? "التأخير" : "RTT", value: "\(viewModel.telemetry.rttMs)ms", color: BydColors.primary)
            Spacer()
            hudMetric(
                label: viewModel.isArabic ? "السرعة" : "Bitrate",
                value: String(format: "%.0fM", viewModel.telemetry.currentBitrateMbps),
                color: BydColors.primaryText
            )
            Spacer()
            hudMetric(
                label: viewModel.isArabic ? "فقدان" : "Loss",
                value: String(format: "%.1f%%", viewModel.telemetry.packetLossPercent),
                color: viewModel.telemetry.packetLossPercent > 2 ? BydColors.warning : BydColors.success
            )
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(BydColors.elevatedSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func hudMetric(label: String, value: String, color: Color) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.caption.bold()).foregroundColor(color)
            Text(label).font(.caption2).foregroundColor(BydColors.secondaryText)
        }
    }

    private func chipButton(title: String, selected: Bool, accent: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .foregroundColor(selected ? accent : BydColors.secondaryText)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(selected ? accent.opacity(0.18) : BydColors.elevatedSurface)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }

    private var statusPill: some View {
        let (title, color): (String, Color) = {
            switch viewModel.connectionState {
            case .idle: return (viewModel.isArabic ? "جاهز" : "Ready", BydColors.secondaryText)
            case .discovering: return (viewModel.isArabic ? "بحث" : "Scanning", BydColors.primary)
            case .handshaking, .keyExchange, .negotiatingQuality:
                return (viewModel.isArabic ? "توصيل" : "Securing", BydColors.warning)
            case .streaming: return (viewModel.isArabic ? "متصل" : "Live", BydColors.success)
            case .reconnecting: return (viewModel.isArabic ? "إعادة" : "Retry", BydColors.warning)
            case .error: return (viewModel.isArabic ? "خطأ" : "Error", BydColors.error)
            }
        }()

        return Text(title)
            .font(.caption.bold())
            .lineLimit(1)
            .foregroundColor(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(color.opacity(0.16))
            .clipShape(Capsule())
    }

    private var statusHeadline: String {
        switch viewModel.connectionState {
        case .idle:
            return viewModel.isArabic ? "جاهز للربط مع شاشة BYD" : "Ready to Mirror"
        case .discovering:
            return viewModel.isArabic ? "جاري البحث عن شاشة السيارة..." : "Searching for BYD screen..."
        case .handshaking(let device), .keyExchange(let device), .negotiatingQuality(let device):
            return viewModel.isArabic ? "جاري تأمين الاتصال مع \(device.displayName)..." : "Securing connection to \(device.displayName)..."
        case .streaming(let device, _, _, _):
            return viewModel.isArabic ? "الشاشة تبث الآن إلى \(device.displayName)" : "Connected to \(device.displayName)"
        case .reconnecting(_, let attempt):
            return viewModel.isArabic ? "إعادة الاتصال (محاولة \(attempt))..." : "Reconnecting (Attempt \(attempt))..."
        case .error(let msg):
            return msg
        }
    }

    private func startSessionTimer() {
        stopSessionTimer()
        sessionElapsedSeconds = 0
        sessionTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            Task { @MainActor in
                sessionElapsedSeconds += 1
            }
        }
    }

    private func stopSessionTimer() {
        sessionTimer?.invalidate()
        sessionTimer = nil
        sessionElapsedSeconds = 0
    }
}

private struct ReplayKitBroadcastButton: UIViewRepresentable {
    let bundleIdentifier: String

    func makeUIView(context: Context) -> RPSystemBroadcastPickerView {
        let picker = RPSystemBroadcastPickerView(frame: CGRect(x: 0, y: 0, width: 44, height: 44))
        picker.preferredExtension = bundleIdentifier
        picker.showsMicrophoneButton = false
        return picker
    }

    func updateUIView(_ uiView: RPSystemBroadcastPickerView, context: Context) {}
}

private struct PipSampleLayerHost: UIViewRepresentable {
    let displayLayer: AVSampleBufferDisplayLayer

    func makeUIView(context: Context) -> LayerContainerView {
        LayerContainerView(displayLayer: displayLayer)
    }

    func updateUIView(_ uiView: LayerContainerView, context: Context) {}

    final class LayerContainerView: UIView {
        private let displayLayer: AVSampleBufferDisplayLayer

        init(displayLayer: AVSampleBufferDisplayLayer) {
            self.displayLayer = displayLayer
            super.init(frame: .zero)
            layer.addSublayer(displayLayer)
        }

        required init?(coder: NSCoder) { fatalError() }

        override func layoutSubviews() {
            super.layoutSubviews()
            displayLayer.frame = bounds
        }
    }
}
