import AVFoundation
import ReplayKit
import SwiftUI

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
                    autoConnectSecurityCard
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
                Text("تأكيد الاقتران الآمن")
                    .font(.title3.bold())
                    .foregroundColor(BydColors.textPrimary)

                Text("تحقق من تطابق الرمز الظاهر على شاشة \(prompt.device.displayName):")
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
                    Button("إلغاء") {
                        viewModel.rejectPairing()
                    }
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(BydColors.dangerRed.opacity(0.2))
                    .foregroundColor(BydColors.dangerRed)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    Button("مطابق وحفظ السيارة") {
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
                Text("ربط شاشة السيارة لاسلكياً")
                    .font(.title2.bold())
                    .foregroundColor(BydColors.textPrimary)
            }
            Spacer()
            statusPill
        }
    }

    private var heroConnectionCard: some View {
        let dims = viewModel.activeProfile.dimensions(for: viewModel.orientation)
        let isStreaming: Bool = {
            if case .streaming = viewModel.connectionState { return true }
            return false
        }()

        return VStack(spacing: 16) {
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
                    .frame(width: 92, height: 92)

                Image(systemName: "airplayvideo")
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundColor(isStreaming ? BydColors.evEmerald : BydColors.evCyan)
            }

            Text(statusHeadline)
                .font(.headline)
                .foregroundColor(BydColors.textPrimary)
                .multilineTextAlignment(.center)

            Text("\(dims.width)x\(dims.height) • \(viewModel.activeProfile.targetFps) FPS • \(viewModel.activeProfile.codec.rawValue)")
                .font(.caption)
                .foregroundColor(BydColors.textSecondary)

            Button(action: viewModel.onOneTapConnect) {
                Text(isStreaming ? "إنهاء الاتصال" : "اتصال بضغطة واحدة")
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
                        viewModel.orientation == .landscape ? "أفقي" : "عمودي",
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
        .padding(24)
        .background(BydColors.surfaceCard)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(BydColors.borderSubtle, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }

    private var autoConnectSecurityCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle(isOn: $viewModel.autoConnectEnabled) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("الربط التلقائي")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(BydColors.textPrimary)
                    Text("تفعيل الاتصال عند دخول نطاق واي فاي BYD")
                        .font(.caption)
                        .foregroundColor(BydColors.textSecondary)
                }
            }
            .tint(BydColors.evCyan)

            HStack(spacing: 8) {
                Image(systemName: "lock.shield.fill")
                    .foregroundColor(BydColors.evEmerald)
                Text("تشفير القناة ECDH P-256 + AES-256-GCM")
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

    private var discoveredDevicesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("الشاشات المكتشفة (\(viewModel.discoveredDevices.count))")
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
                                    Text("موثوقة")
                                        .font(.caption2.bold())
                                        .foregroundColor(BydColors.evEmerald)
                                }
                            }
                            Text("\(device.modelName) • DiLink \(device.diLinkVersion)")
                                .font(.caption)
                                .foregroundColor(BydColors.textSecondary)
                        }
                        Spacer()
                        Text("ربط")
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
                Text("السيارات الموثوقة (\(viewModel.trustedVehicles.count))")
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
            case .idle: return ("جاهز", BydColors.textSecondary)
            case .discovering: return ("جاري البحث...", BydColors.evCyan)
            case .handshaking, .keyExchange, .negotiatingQuality: return ("مصافحة آمنة", BydColors.warningAmber)
            case .streaming: return ("متصل", BydColors.evEmerald)
            case .reconnecting: return ("إعادة الربط", BydColors.warningAmber)
            case .error: return ("خطأ", BydColors.dangerRed)
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
        case .idle: return "اضغط للبحث والربط مع شاشة BYD"
        case .discovering: return "جاري البحث عن شاشة BYD عبر Bonjour P2P..."
        case .handshaking(let device): return "مصافحة بروتوكول \(device.displayName)"
        case .keyExchange: return "تبادل مفاتيح التشفير ECDH P-256..."
        case .negotiatingQuality: return "ضبط الدقة المناسبة للشاشة..."
        case .streaming(let device, _, _, _): return "بث مباشر إلى \(device.displayName)"
        case .reconnecting(_, let attempt): return "إعادة الاتصال (محاولة \(attempt))"
        case .error(let msg): return msg
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
