import AVFoundation
import ReplayKit
import SwiftUI
import UIKit

public struct MirrorDashboardView: View {
    @StateObject private var viewModel = MirrorViewModel()
    @StateObject private var pipController = PipOverlayController()
    @State private var showSettingsSheet = false
    @State private var showManualIpAlert = false

    public init() {}

    public var body: some View {
        ZStack(alignment: .bottom) {
            BydColors.obsidianBg.ignoresSafeArea()

            // Attach AVSampleBufferDisplayLayer into the view hierarchy so iOS PiP can launch
            PipSampleLayerHost(displayLayer: pipController.displayLayer)
                .frame(width: 1, height: 1)
                .opacity(0.01)

            ScrollView {
                VStack(spacing: 18) {
                    headerView
                    heroConnectionCard
                    discoveredDevicesSection
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }

            if viewModel.showMiniOverlay {
                MiniControlOverlayView(
                    orientation: viewModel.orientation,
                    onToggleOrientation: viewModel.toggleOrientation,
                    onDisconnect: viewModel.disconnect
                )
                .padding(.bottom, 18)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            pipController.configure()
        }
        .onChange(of: viewModel.orientation) { newOrientation in
            pipController.updateStatus(newOrientation == .landscape ? "BYD • أفقي" : "BYD • عمودي")
        }
        .sheet(isPresented: $showSettingsSheet) {
            advancedSettingsSheet
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
                Text(viewModel.isArabic ? "تأكيد الاقتران" : "Verify Pairing")
                    .font(.title3.bold())
                    .foregroundColor(BydColors.textPrimary)

                Text(
                    viewModel.isArabic
                        ? "تأكد من تطابق الرمز على شاشة \(prompt.device.displayName):"
                        : "Confirm this PIN matches \(prompt.device.displayName):"
                )
                .font(.subheadline)
                .foregroundColor(BydColors.textSecondary)
                .multilineTextAlignment(.center)

                Text(prompt.pin)
                    .font(.system(size: 40, weight: .heavy, design: .monospaced))
                    .tracking(6)
                    .foregroundColor(BydColors.evCyan)

                HStack(spacing: 12) {
                    Button(viewModel.isArabic ? "إلغاء" : "Cancel") {
                        viewModel.rejectPairing()
                    }
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(BydColors.dangerRed.opacity(0.2))
                    .foregroundColor(BydColors.dangerRed)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    Button(viewModel.isArabic ? "تأكيد وحفظ" : "Trust Vehicle") {
                        viewModel.confirmTrustedVehicle()
                    }
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(BydColors.evCyan)
                    .foregroundColor(Color(red: 4/255, green: 16/255, blue: 22/255))
                    .font(.subheadline.bold())
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
            .padding(24)
            .background(BydColors.surfaceCard)
            .presentationDetents([.medium])
        }
    }

    private var headerView: some View {
        HStack {
            Text(viewModel.isArabic ? "ربط شاشة BYD" : "BYD Mirror")
                .font(.title2.bold())
                .foregroundColor(BydColors.textPrimary)
                .lineLimit(1)

            Spacer()

            HStack(spacing: 8) {
                statusPill

                Button {
                    viewModel.toggleLanguage()
                } label: {
                    Text(viewModel.isArabic ? "EN" : "AR")
                        .font(.caption.bold())
                        .foregroundColor(BydColors.evCyan)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(BydColors.surfaceElevated)
                        .clipShape(Capsule())
                }

                Button {
                    showSettingsSheet = true
                } label: {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 16))
                        .foregroundColor(BydColors.textPrimary)
                        .frame(width: 34, height: 34)
                        .background(BydColors.surfaceElevated)
                        .clipShape(Circle())
                }
            }
        }
    }

    private var heroConnectionCard: some View {
        let isStreaming: Bool = {
            if case .streaming = viewModel.connectionState { return true }
            return false
        }()

        return VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [(isStreaming ? BydColors.evEmerald : BydColors.evCyan).opacity(0.28), BydColors.surfaceElevated],
                            center: .center,
                            startRadius: 8,
                            endRadius: 52
                        )
                    )
                    .frame(width: 88, height: 88)

                Image(systemName: "airplayvideo")
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundColor(isStreaming ? BydColors.evEmerald : BydColors.evCyan)
            }

            Text(statusHeadline)
                .font(.headline)
                .foregroundColor(BydColors.textPrimary)
                .multilineTextAlignment(.center)

            if isStreaming {
                diagnosticsHudRow
            }

            Button(action: viewModel.onOneTapConnect) {
                Text(
                    isStreaming
                        ? (viewModel.isArabic ? "إيقاف البث" : "Stop Mirroring")
                        : (viewModel.isArabic ? "اتصال بضغطة واحدة" : "One-Tap Connect")
                )
                .font(.headline)
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .background(isStreaming ? BydColors.dangerRed : BydColors.evCyan)
                .foregroundColor(isStreaming ? .white : Color(red: 4/255, green: 16/255, blue: 22/255))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            if isStreaming {
                HStack(spacing: 10) {
                    Button(action: viewModel.toggleOrientation) {
                        Label(
                            viewModel.orientation == .landscape
                                ? (viewModel.isArabic ? "أفقي (تدوير)" : "Landscape")
                                : (viewModel.isArabic ? "عمودي (تدوير)" : "Portrait"),
                            systemImage: "rotate.right"
                        )
                        .font(.subheadline.weight(.medium))
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(BydColors.surfaceElevated)
                        .foregroundColor(BydColors.textPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    if viewModel.hasBroadcastExtension {
                        ReplayKitBroadcastButton(bundleIdentifier: "com.byd.mirror.app.broadcast")
                            .frame(width: 52, height: 44)
                            .background(BydColors.surfaceElevated)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
        }
        .padding(24)
        .background(BydColors.surfaceCard)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(BydColors.borderSubtle, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }

    private var diagnosticsHudRow: some View {
        HStack {
            hudMetric(label: "FPS", value: "\(viewModel.telemetry.actualFps)", color: BydColors.evEmerald)
            Spacer()
            hudMetric(label: viewModel.isArabic ? "التأخير" : "RTT", value: "\(viewModel.telemetry.rttMs)ms", color: BydColors.evCyan)
            Spacer()
            hudMetric(
                label: viewModel.isArabic ? "السرعة" : "Bitrate",
                value: String(format: "%.0fM", viewModel.telemetry.currentBitrateMbps),
                color: BydColors.textPrimary
            )
            Spacer()
            hudMetric(
                label: viewModel.isArabic ? "فقدان" : "Loss",
                value: String(format: "%.1f%%", viewModel.telemetry.packetLossPercent),
                color: viewModel.telemetry.packetLossPercent > 2 ? BydColors.warningAmber : BydColors.evEmerald
            )
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(BydColors.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func hudMetric(label: String, value: String, color: Color) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.caption.bold())
                .foregroundColor(color)
            Text(label)
                .font(.caption2)
                .foregroundColor(BydColors.textSecondary)
        }
    }

    private var discoveredDevicesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(
                    viewModel.isArabic
                        ? "شاشات BYD المتاحة (\(viewModel.discoveredDevices.count))"
                        : "Available Screens (\(viewModel.discoveredDevices.count))"
                )
                .font(.caption.bold())
                .foregroundColor(BydColors.textSecondary)

                Spacer()

                Button {
                    showManualIpAlert = true
                } label: {
                    Text(viewModel.isArabic ? "اتصال بـ IP يدوياً" : "Connect by IP")
                        .font(.caption.bold())
                        .foregroundColor(BydColors.evCyan)
                }
            }

            if viewModel.discoveredDevices.isEmpty {
                Text(
                    viewModel.isArabic
                        ? "1. افتح تطبيق BYD Receiver على شاشة السيارة.\n2. تأكد أن الآيفون والسيارة على نفس الشبكة أو نقطة الاتصال، ثم اضغط «اتصال بضغطة واحدة»."
                        : "1. Open BYD Receiver on your car screen.\n2. Connect iPhone & car to the same Wi-Fi/Hotspot, then tap One-Tap Connect."
                )
                .font(.caption)
                .foregroundColor(BydColors.textSecondary)
                .lineSpacing(4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(BydColors.surfaceCard)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(BydColors.borderSubtle, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 16))
            } else {
                ForEach(viewModel.discoveredDevices) { device in
                    Button {
                        viewModel.prepareConnection(to: device)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(device.displayName)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundColor(BydColors.textPrimary)
                                    if viewModel.isTrusted(deviceId: device.id) {
                                        Text(viewModel.isArabic ? "محفوظة" : "Trusted")
                                            .font(.caption2.bold())
                                            .foregroundColor(BydColors.evEmerald)
                                    }
                                }
                                Text("\(device.modelName) • \(device.hostAddress)")
                                    .font(.caption)
                                    .foregroundColor(BydColors.textSecondary)
                            }
                            Spacer()
                            Text(viewModel.isArabic ? "ربط" : "Connect")
                                .font(.subheadline.bold())
                                .foregroundColor(BydColors.evCyan)
                        }
                        .padding(16)
                        .background(BydColors.surfaceElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                }
            }
        }
    }

    private var advancedSettingsSheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(viewModel.isArabic ? "جودة الترميز" : "Video Codec")
                            .font(.caption.bold())
                            .foregroundColor(BydColors.textSecondary)
                        HStack(spacing: 10) {
                            chipButton(title: "H.265", selected: viewModel.preferHevc, accent: BydColors.evCyan) {
                                viewModel.setCodec(preferHevc: true)
                            }
                            chipButton(title: "H.264", selected: !viewModel.preferHevc, accent: BydColors.evCyan) {
                                viewModel.setCodec(preferHevc: false)
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        Text(viewModel.isArabic ? "حجم شاشة السيارة" : "Car Screen Size")
                            .font(.caption.bold())
                            .foregroundColor(BydColors.textSecondary)
                        HStack(spacing: 8) {
                            let options: [(String, String)] = [
                                ("auto", viewModel.isArabic ? "تلقائي" : "Auto"),
                                (DisplayProfile.diLink15_6FHD.id, "15.6\""),
                                (DisplayProfile.diLink12_8FHD.id, "12.8\""),
                                (DisplayProfile.diLink10_1HD.id, "10.1\"")
                            ]
                            ForEach(options, id: \.0) { pid, title in
                                chipButton(title: title, selected: viewModel.selectedProfileId == pid, accent: BydColors.evEmerald) {
                                    viewModel.selectProfile(id: pid)
                                }
                            }
                        }
                    }

                    Toggle(isOn: $viewModel.autoConnectEnabled) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(viewModel.isArabic ? "الربط التلقائي" : "Auto-Connect")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(BydColors.textPrimary)
                            Text(
                                viewModel.isArabic
                                    ? "اتصال تلقائي عند الاقتراب من السيارة"
                                    : "Auto-link on BYD Wi-Fi"
                            )
                            .font(.caption)
                            .foregroundColor(BydColors.textSecondary)
                        }
                    }
                    .tint(BydColors.evCyan)

                    Button {
                        pipController.updateStatus(
                            viewModel.orientation == .landscape ? "BYD • أفقي" : "BYD • عمودي"
                        )
                        pipController.startPip()
                    } label: {
                        Label(
                            viewModel.isArabic ? "تشغيل نافذة التحكم المصغرة (PiP)" : "Open Mini PiP Controls",
                            systemImage: "pip.enter"
                        )
                        .font(.subheadline.weight(.medium))
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(BydColors.surfaceElevated)
                        .foregroundColor(BydColors.evCyan)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    if !viewModel.trustedVehicles.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(
                                viewModel.isArabic
                                    ? "السيارات المحفوظة (\(viewModel.trustedVehicles.count))"
                                    : "Saved Vehicles (\(viewModel.trustedVehicles.count))"
                            )
                            .font(.caption.bold())
                            .foregroundColor(BydColors.textSecondary)

                            ForEach(viewModel.trustedVehicles, id: \.id) { vehicle in
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(vehicle.displayName)
                                            .font(.subheadline.weight(.medium))
                                            .foregroundColor(BydColors.textPrimary)
                                        Text("ID: \(vehicle.fingerprint)")
                                            .font(.caption2)
                                            .foregroundColor(BydColors.evEmerald)
                                    }
                                    Spacer()
                                    Button {
                                        viewModel.removeTrustedVehicle(deviceId: vehicle.id)
                                    } label: {
                                        Image(systemName: "trash")
                                            .foregroundColor(BydColors.dangerRed)
                                    }
                                }
                                .padding(12)
                                .background(BydColors.surfaceElevated)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                        }
                    }

                    HStack(spacing: 8) {
                        Image(systemName: "lock.shield.fill")
                            .foregroundColor(BydColors.evEmerald)
                        Text(
                            viewModel.isArabic
                                ? "اتصال مشفر بالكامل ECDH P-256 + AES-256-GCM"
                                : "End-to-end encrypted ECDH P-256"
                        )
                        .font(.caption)
                        .foregroundColor(BydColors.evEmerald)
                    }
                }
                .padding(20)
            }
            .background(BydColors.surfaceCard.ignoresSafeArea())
            .navigationTitle(viewModel.isArabic ? "الإعدادات المتقدمة" : "Advanced Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(viewModel.isArabic ? "تم" : "Done") {
                        showSettingsSheet = false
                    }
                    .foregroundColor(BydColors.evCyan)
                }
            }
        }
        .preferredColorScheme(.dark)
        .presentationDetents([.medium, .large])
    }

    private func chipButton(title: String, selected: Bool, accent: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .foregroundColor(selected ? accent : BydColors.textSecondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(selected ? accent.opacity(0.18) : BydColors.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }

    private var statusPill: some View {
        let (title, color): (String, Color) = {
            switch viewModel.connectionState {
            case .idle: return (viewModel.isArabic ? "جاهز" : "Ready", BydColors.textSecondary)
            case .discovering: return (viewModel.isArabic ? "جاري البحث" : "Scanning", BydColors.evCyan)
            case .handshaking, .keyExchange, .negotiatingQuality:
                return (viewModel.isArabic ? "جاري الربط" : "Securing", BydColors.warningAmber)
            case .streaming: return (viewModel.isArabic ? "متصل" : "Live", BydColors.evEmerald)
            case .reconnecting: return (viewModel.isArabic ? "إعادة الربط" : "Reconnecting", BydColors.warningAmber)
            case .error: return (viewModel.isArabic ? "خطأ" : "Error", BydColors.dangerRed)
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
            return viewModel.isArabic ? "جاهز للربط مع شاشة BYD" : "Ready to mirror to BYD screen"
        case .discovering:
            return viewModel.isArabic ? "جاري البحث عن شاشة السيارة..." : "Searching for your BYD screen..."
        case .handshaking(let device), .keyExchange(let device), .negotiatingQuality(let device):
            return viewModel.isArabic ? "جاري تأمين الاتصال مع \(device.displayName)..." : "Securing connection to \(device.displayName)..."
        case .streaming(let device, _, _, _):
            return viewModel.isArabic ? "الشاشة تبث الآن إلى \(device.displayName)" : "Mirroring live to \(device.displayName)"
        case .reconnecting(_, let attempt):
            return viewModel.isArabic ? "إعادة الاتصال بالسيارة (محاولة \(attempt))..." : "Reconnecting (Attempt \(attempt))..."
        case .error(let msg):
            return msg
        }
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
