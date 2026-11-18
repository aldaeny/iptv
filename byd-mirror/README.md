# BYD Wireless Mirroring — هيكل التطبيق الأصلي (Kotlin & Swift)

تطبيق ربط وعرض الشاشة لاسلكياً بين الهواتف الذكية وشاشات سيارات **BYD (DiLink)** بدون وصلات USB، مبني وفق وثيقة المتطلبات التقنية.

## هيكل المشروع

```text
byd-mirror/
├── docs/
│   ├── ARCHITECTURE.md               # المعمارية الطبقية والفروقات بين Android و iOS وخارطة التنفيذ
│   └── PROTOCOL_SPEC.md              # مواصفات البروتوكول الثنائي والتشفير (ECDH + AES-256-GCM)
├── shared/
│   └── protocol/
│       └── byd_mirror_protocol.json  # ثوابت البروتوكول وملفات تعريف شاشات BYD
├── receiver-dilink/                  # تطبيق المستقبل لشاشة سيارة BYD (DiLink Receiver APK)
│   └── app/src/main/java/com/byd/receiver/
│       ├── crypto/                   # مصافحة ECDH P-256 + توليد رمز PIN 6 أرقام
│       ├── network/                  # تسجيل خدمة _byd-mirror._tcp. عبر NSD
│       ├── protocol/                 # خادم TCP:47810 + UDP:47811 + تقارير QoS
│       ├── decoder/                  # فك تشفير MediaCodec للعرض المباشر + AudioTrack
│       └── ui/                       # شاشة العرض الدوارة 16:9 / 9:16 ورمز الاقتران
├── android/                          # مشروع Kotlin + Jetpack Compose (تطبيق الهاتف)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── app/src/main/java/com/byd/mirror/
│       ├── core/model/               # نماذج الأجهزة وحالات الاتصال وجودة العرض
│       ├── core/security/            # ECDH P-256 + HKDF-SHA256 + AES-256-GCM + حفظ السيارات الموثوقة
│       ├── core/session/             # MirrorSessionCoordinator (توحيد الجلسة بين الواجهة والخدمات)
│       ├── core/protocol/            # محاكاة البروتوكول (TCP+UDP) وضبط الجودة التلقائي
│       ├── core/network/             # Wi-Fi Direct (P2P) + NSD + الربط التلقائي
│       ├── core/streaming/           # MediaProjection + MediaCodec + AudioPlaybackCapture AAC + الحرارة
│       ├── service/                  # Foreground Service + لوحة التحكم العائمة (Overlay)
│       └── ui/                       # واجهة Jetpack Compose داكنة + تأكيد PIN
└── ios/                              # مشروع Swift + SwiftUI + ReplayKit Extension
    ├── Package.swift
    ├── Project.yml                   # تعريف XcodeGen + App Group (group.com.byd.mirror.shared)
    ├── BydMirror.entitlements
    ├── BroadcastExtension/           # RPBroadcastSampleHandler (مصافحة كاملة + VideoToolbox + AAC + UDP)
    └── Sources/
        ├── Core/Models/              # نماذج البيانات المشتركة
        ├── Core/Security/            # CryptoKit P256 + HKDF + AES.GCM + Keychain للسيارات الموثوقة
        ├── Core/Session/             # SharedSessionStore (App Group + Darwin IPC)
        ├── Core/Protocol/            # BydProtocolWrapper + QualityNegotiator
        ├── Core/Network/             # NWBrowser (Bonjour + AWDL P2P) + NWConnection (TCP+UDP) + AutoConnect
        ├── Core/Streaming/           # VideoToolbox + AudioToolbox AAC + PiP Overlay + ThermalMonitor
        └── UI/                       # واجهة SwiftUI الداكنة + شاشة تأكيد PIN + لوحة التحكم المصغرة
```

## المزايا المنفذة في الهيكل البرمجي

- **اتصال بضغطة زر واحدة (One-Tap Connect):** شاشة رئيسية داكنة بطابع السيارات الكهربائية (`#080B10` / `#00D4FF`) تدعم العربية والإنجليزية.
- **مغلف البروتوكول (`BydProtocolWrapper`):** ترويسة ثنائية `16-byte` مع مصافحة ثنائية الاتجاه وإدارة جلسات العرض.
- **أمان القناة (`ChannelCryptoManager`):** تبادل مفاتيح `ECDH P-256` واشتقاق `HKDF-SHA256` وتشفير `AES-256-GCM` مع AAD للترويسة.
- **إدارة الجودة التلقائية (`QualityNegotiator`):** مواءمة الدقة (`1920x1080`, `1280x720`) والإطارات (`60/30/24 fps`) مع شاشات BYD الدوارة وقياسات RTT وفقدان الحزم.
- **الربط التلقائي (`AutoConnectMonitor` / `AutoConnectCoordinator`):** رصد شبكة السيارة (`BYD-DiLink-*` / `DIRECT-BYD-*`) وخدمات `_byd-mirror._tcp.`.
- **لوحة التحكم العائمة:**
  - Android: `MirrorOverlayService` فوق التطبيقات (`TYPE_APPLICATION_OVERLAY`) للتبديل بين العمودي/الأفقي وإنهاء الاتصال.
  - iOS: `PipOverlayController` (`AVPictureInPictureController`) + `MiniControlOverlayView`.
- **حماية الحرارة والطاقة:** خفض تلقائي لمعدل الإطارات والدقة عند ارتفاع حرارة المعالج (`ThermalPowerManager` / `ThermalPowerMonitor`).
