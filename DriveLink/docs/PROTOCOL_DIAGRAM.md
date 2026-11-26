# مواصفات بروتوكول الربط اللاسلكي لشاشات BYD (`BYD-WMP v1.2.0` — wire `0x01`)

## 1. الاكتشاف والنقل (Discovery & Transport)

- **خدمة الشبكة المحلية (NSD / Bonjour + UDP Beacon):**
  - نوع الخدمة: `_byd-mirror._tcp.`
  - منارة الاكتشاف الصفرية (UDP Discovery Beacon): منفذ `47813` (بث JSON دوري كل ثانية: `{"proto":"byd-mirror-v1","name":...,"model":...,"port":47810,"mediaPort":47811,"hevc":...}` عبر البث العام والشبكات الفرعية المباشرة، وتنتهي صلاحية الجهاز غير المرئي بعد `6.5` ثوانٍ).
  - منفذ التحكم والمصافحة (TCP): `47810` (مفعّل بـ `TCP_NODELAY` مع مهلة اتصال `4s` وقراءة مصافحة `6s`، ثم مهلة موافقة `60s`).
  - منفذ البث منخفض الكمون (UDP): `47811`، مع **تحوّل تلقائي إلى TCP `47810`** (`useTcpMediaFallback`) إذا لم يستقبل المستقبل أي إطارات فيديو (`rxFps == 0` بعد إرسال `>= 15` إطاراً) بسبب حجب جدار حماية ويندوز أو عزل الراوتر.
- **الاتصال المباشر (Peer-to-Peer):**
  - **Android:** `WifiP2pManager` (Wi-Fi Direct) لبادئة `DIRECT-BYD-` أو عبر هوتسبوت السيارة (`BYD-DiLink-*`).
  - **iOS:** `Network.framework` (`NWBrowser` / `NWConnection` مع `includePeerToPeer = true`) + مستمع UDP `47813`.

---

## 2. هيكل الحزمة الثنائية (Binary Frame Format)

كل حزمة في القناة المشفرة تبدأ بترويسة ثابتة طولها **16 بايت (Big-Endian)**:

| الإزاحة (Bytes) | الحجم | الحقل | الوصف |
| --- | --- | --- | --- |
| `0..1` | 2 | `magic` | القيمة الثابتة `0x4259` (`"BY"`) |
| `2` | 1 | `version` | رقم إصدار البروتوكول (`0x01`) |
| `3` | 1 | `opcode` | نوع الرسالة (انظر جدول Opcodes) |
| `4..7` | 4 | `sequence` | رقم تسلسلي متزايد موجب (`seq > 0`) لمنع إعادة التشغيل (`AntiReplayWindow`) |
| `8..11` | 4 | `timestampUs` | الطابع الزمني بالميكروثانية (PTS) |
| `12..15` | 4 | `payloadLength` | طول الحمولة السلكية بالبايت (`0..65536`) |

عند تفعيل التشفير (بعد إرسال `KEY_EXCHANGE_RESP` بنص واضح)، تكون الحمولة بصيغة:
`[12-byte Nonce (IV)] + [Ciphertext (0..N bytes)] + [16-byte AES-256-GCM Auth Tag]` (الحد الأدنى لحمولة مشفرة فارغة هو **`28` بايت**)، مع تمرير الترويسة (16 بايت) كـ **AAD**.

---

## 3. رموز العمليات (Opcodes)

| Opcode | الاسم | الاتجاه | الوصف |
| --- | --- | --- | --- |
| `0x01` | `HELLO_SYN` | Phone → Receiver | بدء المصافحة وإرسال معرف الهاتف |
| `0x02` | `HELLO_ACK` | Receiver → Phone | تأكيد المصافحة وإرسال طراز الشاشة وإصدار DiLink |
| `0x03` | `KEY_EXCHANGE_INIT` | Phone → Receiver | المفتاح العام المؤقت (Android: X.509 SPKI 91B / iOS: X9.63 65B) |
| `0x04` | `KEY_EXCHANGE_RESP` | Receiver → Phone | `[16B Salt] + [Peer-Matched Public Key]` (تُرسل بنص واضح ثم يُفعّل التشفير) |
| `0x05` | `CAPABILITIES_REQ` | Phone → Receiver | طلب الدقة ومعدل الإطارات (مشفر) |
| `0x06` | `CAPABILITIES_RESP` | Receiver → Phone | رد الشاشة بقدراتها الفعلية (مثل `1920x1080@60|H265,H264`) |
| `0x07` | `SESSION_START` | Phone → Receiver | **طلب** بدء البث ونوع الترميز؛ لا يبدأ فك التشفير قبل موافقة المستقبل الصريحة |
| `0x08` | `SESSION_STOP` | Both | إنهاء الجلسة بأمان (0 بايت نصية → 28 بايت مشفرة) |
| `0x09` | `ORIENTATION_CHANGE` | Phone → Receiver | تبديل العرض بين الأفقي (`0x01`) والعمودي (`0x00`) |
| `0x0A` | `DEVICE_IDENTITY` | Phone → Receiver | إثبات هوية التثبيت: `[1B ver][2B pubLen][SPKI أو X9.63][2B sigLen][DER ECDSA]` موقّع على `BYD-WMP-IDENTITY-V1\|<sessionFingerprint>` |
| `0x10` | `VIDEO_NALU` | Phone → Receiver | إطار فيديو Annex-B (`00 00 00 01` أو `00 00 01`) لـ H.264 / H.265 |
| `0x11` | `AUDIO_AAC` | Phone → Receiver | إطار صوتي AAC-LC 48kHz Stereo |
| `0x12` | `KEYFRAME_REQUEST` | Receiver → Phone | طلب إطار IDR فوري (0 بايت نصية → 28 بايت مشفرة) |
| `0x13` | `VIDEO_NALU_FRAG` | Phone → Receiver | تجزئة إطار يتجاوز MTU (`[4B frameId][2B fragIdx][2B totalFrags][chunk]` حتى 256 جزءاً) |
| `0x14` | `TOUCH_EVENT` | Receiver → Phone | لمس عكسي مطبّع (`[4B xNorm Float][4B yNorm Float][1B action: 0=DOWN,1=UP,2=MOVE]`) |
| `0x20` | `HEARTBEAT_PING` | Phone → Receiver | قياس زمن الوصول (RTT) |
| `0x21` | `HEARTBEAT_PONG` | Receiver → Phone | استجابة RTT |
| `0x22` | `QOS_TELEMETRY` | Receiver → Phone | `[4B rxFps][4B droppedPackets]` كل ثانية |
| `0x23` | `THERMAL_THROTTLE` | Phone → Receiver | إشعار خفض الجودة بسبب حرارة الهاتف |

---

## 4. الأمان والتشفير ومكافحة إعادة التشغيل

1. توليد زوج مفاتيح **ECDH P-256** مؤقت جديد لكل جلسة.
2. كشف تلقائي لصيغة المفتاح العام (X.509 SPKI لأندرويد وX9.63 65B لـ iOS) في كلٍ من `ReceiverCryptoManager.kt` و`simulator/main.go`.
3. اشتقاق مفتاح **AES-256-GCM** عبر **HKDF-SHA256** (`Info = "BYD-MIRROR-SESSION-KEY-V1"`).
4. حساب **PIN من 6 أرقام** من أول 3 بايتات من `SHA-256(sessionKey)` (`& 0x7F` mod `1_000_000`) للمقارنة البصرية. يبقى `0x07` طلباً حتى يوافق المستخدم على شاشة السيارة؛ لا فك تشفير قبل الموافقة.
5. رفض أي حزمة UDP أو TCP وسائط قبل تفعيل التشفير، مع نافذة منع إعادة التشغيل (`AntiReplayWindow`) لـ 64 حزمة على UDP وتسلسل تصاعدي صارم على TCP.
6. `DEVICE_IDENTITY` اختياري للتوافق: عند غيابه أو فشل التحقق يلزم اعتماد يدوي؛ لا ثقة تلقائية. الثقة مخزنة كـ `SHA-256(SPKI)` وليست `deviceId`. لا يوجد hardware attestation.
