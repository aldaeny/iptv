# المعمارية التقنية وخارطة التنفيذ — DriveLink Cast

## 1. نظرة عامة على المعمارية (Layered Architecture)

يعتمد النظام على 5 طبقات متطابقة المفهوم بين **Android (Kotlin)** و **iOS (Swift)**:

1. **طبقة الاكتشاف والنقل (Network & Discovery Layer)**
   - **Android:** `WifiP2pTransport` (`WifiP2pManager`) + `NsdDiscoveryManager` (`NsdManager`) + `AutoConnectMonitor`.
   - **iOS:** `BonjourP2PDiscovery` (`NWBrowser` مع `includePeerToPeer = true`) + `PeerTransportClient` (`NWConnection`) + `AutoConnectCoordinator`.
2. **طبقة الأمان (Security Layer)**
   - `ChannelCryptoManager`: مصافحة مفاتيح **ECDH P-256**، اشتقاق مفاتيح **HKDF-SHA256**، وتشفير **AES-256-GCM** لحزم التحكم والوسائط.
3. **طبقة محاكاة البروتوكول (Protocol Wrapper & QoS)**
   - `BydProtocolWrapper`: تغليف وتفكيك الإطارات الثنائية، إدارة آلة الحالة للمصافحة (`Handshake`)، وإرسال أوامر تدوير الشاشة (`Portrait`/`Landscape`).
   - `QualityNegotiator`: اختيار الدقة ومعدل الإطارات تلقائياً حسب شاشات BYD (`15.6"`, `12.8"`, `10.1"`) وحالة الشبكة (RTT & Packet Loss).
4. **طبقة الالتقاط والترميز وإدارة الطاقة (Streaming & Thermal Layer)**
   - **Android:** `ScreenCaptureEncoder` (`MediaProjection` + `VirtualDisplay` + `MediaCodec` Hardware H.264/HEVC) + `ThermalPowerManager`.
   - **iOS:** `SampleHandler` (`RPBroadcastSampleHandler`) + `HardwareVideoEncoder` (`VideoToolbox` `VTCompressionSession`) + `ThermalPowerMonitor`.
5. **طبقة الواجهة والتحكم العائم (UX/UI & Floating Control)**
   - تصميم داكن مستوحى من أنظمة السيارات الكهربائية الفاخرة (Obsidian `#080B10` + EV Cyan `#00D4FF` + Emerald `#10B981`).
   - **Android:** زر اتصال واحد `MirrorDashboardScreen` + خدمة عائمة حقيقية فوق التطبيقات `MirrorOverlayService` (`TYPE_APPLICATION_OVERLAY`).
   - **iOS:** زر اتصال واحد مع منتقي البث `RPSystemBroadcastPickerView` + وحدة تحكم عائمة via **Picture-in-Picture (`AVPictureInPictureController`)** مع دعم المعاينة المصغرة (نظراً لقيود iOS الأمنية التي تمنع النوافذ العائمة العامة فوق التطبيقات الأخرى).

---

## 2. الفروقات الجوهرية والقيود بين Android و iOS

| الميزة | Android (Kotlin) | iOS (Swift) |
| --- | --- | --- |
| **الاتصال المباشر P2P** | Wi-Fi Direct (`WifiP2pManager`) + NSD | `Network.framework` (`includePeerToPeer = true` / AWDL) + Bonjour |
| **التقاط الشاشة بالنظام** | `MediaProjection` + `VirtualDisplay` | `ReplayKit` (`RPBroadcastSampleHandler` في Extension منفصل) |
| **لوحة التحكم العائمة (Overlay)** | `WindowManager` (`TYPE_APPLICATION_OVERLAY`) فوق كل التطبيقات | `AVPictureInPictureController` (PiP) + Live Activity (`ActivityKit`) |
| **الربط التلقائي في الخلفية** | `ConnectivityManager.NetworkCallback` + `WifiP2pManager` | `NWPathMonitor` + `NEHotspotConfigurationManager` / إشعار تفاعلي لبدء ReplayKit (iOS يشترط تأكيد المستخدم لبدء بث الشاشة) |

---

## 3. خارطة التنفيذ (4 مراحل)

1. **المرحلة الأولى — بروتوكول المصافحة والاتصال اللاسلكي:**
   - اكتشاف شاشات BYD عبر `_byd-mirror._tcp.` و Wi-Fi P2P.
   - تنفيذ `HELLO_SYN/ACK` و `KEY_EXCHANGE_INIT/RESP` بتشفير `ECDH P-256 + AES-256-GCM`.
2. **المرحلة الثانية — تحسين سرعة نقل البيانات (Low Latency):**
   - تفعيل الترميز العتادي بدون نسخ (`Zero-Copy Surface` في `MediaCodec` و `VTCompressionSession` على iOS).
   - تفعيل `Low Latency / RealTime` وضبط حزم `VIDEO_NALU` و `KEYFRAME_REQUEST`.
3. **المرحلة الثالثة — التوافق مع إصدارات BYD DiLink:**
   - اختبار التبديل التلقائي بين الوضع الأفقي والعمودي للشاشات الدوارة (`1920x1080` ↔ `1080x1920`) عبر `DiLink 3.0 / 4.0 / 5.0`.
4. **المرحلة الرابعة — ضبط الطاقة والحرارة:**
   - مراقبة `PowerManager.OnThermalStatusChangedListener` (Android) و `ProcessInfo.thermalState` (iOS) لخفض الإطارات من `60fps` إلى `30fps` أو `24fps` وتخفيض معدل البت تدريجياً لمنع ارتفاع حرارة الهاتف.
