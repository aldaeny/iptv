import AVFoundation
import ReplayKit
import SwiftUI
import UIKit

public struct MirrorDashboardView: View {
    @StateObject private var viewModel = MirrorViewModel()
    @StateObject private var pipController = PipOverlayController()

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
                    streamSettingsCard
                    autoConnectSecurityCard
                    manualIpCard
                    discoveredDevicesSection
                    trustedVehiclesSection
                }
                .padding(20)
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
        .sheet(item: $viewModel.pendingPairing) { prompt in
            VStack(spacing: 18) {
                Text(viewModel.isArabic ? "تأكيد الاقتران الآمن" : "Verify Secure Pairing")
                    .font(.title3.bold())
                    .foregroundColor(BydColors.textPrimary)

                Text(
                    viewModel.isArabic
                        ? "تحقق من تطابق الرمز الظاهر على شاشة \(prompt.device.displayName):"
                        : "Confirm the 6-digit PIN matches \(prompt.device.displayName):"
                )
                .font(.subheadline)
                .foregroundColor(BydColors.textSecondary)
                .multilineTextAlignment(.center)

                Text(prompt.pin)
                    .font(.system(size: 40, weight: .heavy, design: .monospaced))
                    .tracking(6)
                    .foregroundColor(BydColors.evCyan)

                Text("Fingerprint: \(prompt.fingerprint)")
                    .font(.caption)
                    .foregroundColor(BydColors.evEmerald)

                HStack(spacing: 12) {
                    Button(viewModel.isArabic ? "إلغاء" : "Cancel") {
                        viewModel.rejectPairing()
                    }
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(BydColors.dangerRed.opacity(0.2))
                    .foregroundColor(BydColors.dangerRed)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    Button(viewModel.isArabic ? "مطابق وحفظ السيارة" : "Match & Trust") {
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
            VStack(alignment: .leading, spacing: 4) {
                Text("BYD WIRELESS MIRROR")
                    .font(.caption.bold())
                    .tracking(2)
                    .foregroundColor(BydColors.evCyan)
                Text(viewModel.isArabic ? "ربط شاشة السيارة لاسلكياً" : "Wireless Car Display Link")
                    .font(.title2.bold())
                    .foregroundColor(BydColors.textPrimary)
            }
            Spacer()
            HStack(spacing: 8) {
                Button {
                    viewModel.toggleLanguage()
                } label: {
                    Text(viewModel.isArabic ? "EN" : "AR")
                        .font(.caption2.bold())
                        .foregroundColor(BydColors.evCyan)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(BydColors.surfaceElevated)
                        .clipShape(Capsule())
                }
                statusPill
            }
        }
    }

    private var heroConnectionCard: some View {
        let dims = viewModel.activeProfile.dimensions(for: viewModel.orientation)
        let isStreaming: Bool = {
            if case .streaming = viewModel.connectionState { return true }
            return false
        }()

        return VStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [BydColors.evCyan.opacity(0.28), BydColors.surfaceElevated],
                            center: .center,
                            startRadius: 8,
                            endRadius: 52
                        )
                    )
                    .frame(width: 84, height: 84)

                Image(systemName: "airplayvideo")
                    .font(.system(size: 34, weight: .semibold))
                    .foregroundColor(isStreaming ? BydColors.evEmerald : BydColors.evCyan)
            }

            Text(statusHeadline)
                .font(.headline)
                .foregroundColor(BydColors.textPrimary)
                .multilineTextAlignment(.center)

            Text("\(dims.width)x\(dims.height) • \(viewModel.activeProfile.targetFps) FPS • \(viewModel.activeProfile.codec.rawValue)")
                .font(.caption)
                .foregroundColor(BydColors.textSecondary)

            if isStreaming {
                diagnosticsHudRow
            }

            Button(action: viewModel.onOneTapConnect) {
                Text(
                    isStreaming
                        ? (viewModel.isArabic ? "إنهاء الاتصال" : "Disconnect")
                        : (viewModel.isArabic ? "اتصال بضغطة واحدة" : "One-Tap Connect")
                )
                .font(.headline)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(isStreaming ? BydColors.dangerRed : BydColors.evCyan)
                .foregroundColor(isStreaming ? .white : Color(red: 4/255, green: 16/255, blue: 22/255))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            HStack(spacing: 12) {
                Button(action: viewModel.toggleOrientation) {
                    Label(
                        viewModel.orientation == .landscape
                            ? (viewModel.isArabic ? "أفقي" : "Landscape")
                            : (viewModel.isArabic ? "عمودي" : "Portrait"),
                        systemImage: "rotate.right"
                    )
                    .font(.subheadline.weight(.medium))
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(BydColors.surfaceElevated)
                    .foregroundColor(BydColors.textPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                Button {
                    pipController.updateStatus(
                        viewModel.orientation == .landscape ? "BYD • أفقي" : "BYD • عمودي"
                    )
                    pipController.startPip()
                } label: {
                    Image(systemName: "pip.enter")
                        .frame(width: 44, height: 44)
                        .background(BydColors.surfaceElevated)
                        .foregroundColor(BydColors.evCyan)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                ReplayKitBroadcastButton(bundleIdentifier: "com.byd.mirror.app.broadcast")
                    .frame(width: 52, height: 44)
                    .background(BydColors.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding(22)
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

    private var streamSettingsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(viewModel.isArabic ? "إعدادات البث والشاشة" : "Stream Codec & Display Size")
                .font(.subheadline.weight(.semibold))
                .foregroundColor(BydColors.textPrimary)

            HStack(spacing: 8) {
                chipButton(title: "H.265 (HEVC)", selected: viewModel.preferHevc, accent: BydColors.evCyan) {
                    viewModel.setCodec(preferHevc: true)
                }
                chipButton(title: "H.264 (AVC)", selected: !viewModel.preferHevc, accent: BydColors.evCyan) {
                    viewModel.setCodec(preferHevc: false)
                }
            }

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
        .padding(16)
        .background(BydColors.surfaceCard)
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(BydColors.borderSubtle, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    private func chipButton(title: String, selected: Bool, accent: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundColor(selected ? accent : BydColors.textSecondary)
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(selected ? accent.opacity(0.18) : BydColors.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }

    private var autoConnectSecurityCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle(isOn: $viewModel.autoConnectEnabled) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(viewModel.isArabic ? "الربط التلقائي" : "Auto-Connect")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(BydColors.textPrimary)
                    Text(
                        viewModel.isArabic
                            ? "تفعيل الاتصال عند دخول نطاق واي فاي BYD"
                            : "Activate when entering BYD Wi-Fi range"
                    )
                    .font(.caption)
                    .foregroundColor(BydColors.textSecondary)
                }
            }
            .tint(BydColors.evCyan)

            HStack(spacing: 8) {
                Image(systemName: "lock.shield.fill")
                    .foregroundColor(BydColors.evEmerald)
                Text(
                    viewModel.isArabic
                        ? "تشفير القناة ECDH P-256 + AES-256-GCM"
                        : "Encrypted ECDH P-256 + AES-256-GCM"
                )
                .font(.caption)
                .foregroundColor(BydColors.evEmerald)
            }
        }
        .padding(16)
        .background(BydColors.surfaceCard)
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(BydColors.borderSubtle, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    private var manualIpCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(viewModel.isArabic ? "الاتصال اليدوي عبر IP (عند حجب الاكتشاف)" : "Manual IP Connect (Bonjour Fallback)")
                .font(.subheadline.weight(.semibold))
                .foregroundColor(BydColors.textPrimary)

            HStack(spacing: 8) {
                TextField("192.168.43.1", text: $viewModel.manualIpInput)
                    .keyboardType(.numbersAndPunctuation)
                    .autocorrectionDisabled()
                    .padding(10)
                    .background(BydColors.surfaceElevated)
                    .foregroundColor(BydColors.textPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: 10))

                Button(action: viewModel.connectManualIp) {
                    Text(viewModel.isArabic ? "ربط" : "Connect")
                        .font(.subheadline.bold())
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(BydColors.evCyan)
                        .foregroundColor(Color(red: 4/255, green: 16/255, blue: 22/255))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .disabled(viewModel.manualIpInput.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(16)
        .background(BydColors.surfaceCard)
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(BydColors.borderSubtle, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    private var discoveredDevicesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(
                viewModel.isArabic
                    ? "الشاشات المكتشفة (\(viewModel.discoveredDevices.count))"
                    : "Discovered Screens (\(viewModel.discoveredDevices.count))"
            )
            .font(.caption.bold())
            .foregroundColor(BydColors.textSecondary)

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
                                    Text(viewModel.isArabic ? "موثوقة" : "Trusted")
                                        .font(.caption2.bold())
                                        .foregroundColor(BydColors.evEmerald)
                                }
                            }
                            Text("\(device.modelName) • DiLink \(device.diLinkVersion)")
                                .font(.caption)
                                .foregroundColor(BydColors.textSecondary)
                        }
                        Spacer()
                        Text(viewModel.isArabic ? "ربط" : "Connect")
                            .font(.subheadline.bold())
                            .foregroundColor(BydColors.evCyan)
                    }
                    .padding(14)
                    .background(BydColors.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
            }
        }
    }

    @ViewBuilder
    private var trustedVehiclesSection: some View {
        if !viewModel.trustedVehicles.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text(
                    viewModel.isArabic
                        ? "السيارات الموثوقة (\(viewModel.trustedVehicles.count))"
                        : "Trusted Vehicles (\(viewModel.trustedVehicles.count))"
                )
                .font(.caption.bold())
                .foregroundColor(BydColors.textSecondary)

                ForEach(viewModel.trustedVehicles, id: \.id) { vehicle in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(vehicle.displayName)
                                .font(.subheadline.weight(.medium))
                                .foregroundColor(BydColors.textPrimary)
                            Text("Fingerprint: \(vehicle.fingerprint)")
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
                    .padding(14)
                    .background(BydColors.surfaceCard)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(BydColors.borderSubtle, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
            }
        }
    }

    private var statusPill: some View {
        let (title, color): (String, Color) = {
            switch viewModel.connectionState {
            case .idle: return (viewModel.isArabic ? "جاهز" : "Ready", BydColors.textSecondary)
            case .discovering: return (viewModel.isArabic ? "جاري البحث..." : "Scanning...", BydColors.evCyan)
            case .handshaking, .keyExchange, .negotiatingQuality:
                return (viewModel.isArabic ? "مصافحة آمنة" : "Securing", BydColors.warningAmber)
            case .streaming: return (viewModel.isArabic ? "متصل" : "Live", BydColors.evEmerald)
            case .reconnecting: return (viewModel.isArabic ? "إعادة الربط" : "Reconnecting", BydColors.warningAmber)
            case .error: return (viewModel.isArabic ? "خطأ" : "Error", BydColors.dangerRed)
            }
        }()

        return Text(title)
            .font(.caption.bold())
            .foregroundColor(color)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(color.opacity(0.16))
            .clipShape(Capsule())
    }

    private var statusHeadline: String {
        switch viewModel.connectionState {
        case .idle:
            return viewModel.isArabic ? "اضغط للبحث والربط مع شاشة BYD" : "Tap to scan & mirror to BYD screen"
        case .discovering:
            return viewModel.isArabic ? "جاري البحث عن شاشة BYD عبر Bonjour P2P..." : "Scanning for BYD screen via Bonjour P2P..."
        case .handshaking(let device):
            return viewModel.isArabic ? "مصافحة بروتوكول \(device.displayName)" : "Handshaking with \(device.displayName)"
        case .keyExchange:
            return viewModel.isArabic ? "تبادل مفاتيح التشفير ECDH P-256..." : "Exchanging ECDH P-256 keys..."
        case .negotiatingQuality:
            return viewModel.isArabic ? "ضبط الدقة المناسبة للشاشة..." : "Negotiating display quality..."
        case .streaming(let device, _, _, _):
            return viewModel.isArabic ? "بث مباشر إلى \(device.displayName)" : "Streaming live to \(device.displayName)"
        case .reconnecting(_, let attempt):
            return viewModel.isArabic ? "إعادة الاتصال (محاولة \(attempt))" : "Reconnecting (Attempt \(attempt))"
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
