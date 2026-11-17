# مواصفات بروتوكول الربط اللاسلكي لشاشات BYD (`BYD-WMP v1.0`)

## 1. الاكتشاف والنقل (Discovery & Transport)

- **خدمة الشبكة المحلية (NSD / Bonjour):**
  - نوع الخدمة: `_byd-mirror._tcp.`
  - منفذ التحكم (TCP / TLS-wrapped): `47810`
  - منفذ البث منخفض الكمون (UDP / RTP-like): `47811`
- **الاتصال المباشر (Peer-to-Peer):**
  - **Android:** عبر `WifiP2pManager` (Wi-Fi Direct) للربط المباشر دون راوتر خارجي مع بادئة SSID `DIRECT-BYD-` أو عبر شبكة هوتسبوت السيارة (`BYD-DiLink-*`).
  - **iOS:** عبر `Network.framework` (`NWBrowser` / `NWConnection`) مع تفعيل `includePeerToPeer = true` لدعم AWDL و Wi-Fi المحلي.

---

## 2. هيكل الحزمة الثنائية (Binary Frame Format)

كل حزمة في القناة المشفرة تبدأ بترويسة ثابتة طولها **16 بايت (Big-Endian)**:

| الإزاحة (Bytes) | الحجم | الحقل | الوصف |
| --- | --- | --- | --- |
| `0..1` | 2 | `magic` | القيمة الثابتة `0x4259` (`"BY"`) |
| `2` | 1 | `version` | رقم إصدار البروتوكول (`0x01`) |
| `3` | 1 | `opcode` | نوع الرسالة (انظر جدول Opcodes) |
| `4..7` | 4 | `sequence` | رقم تسلسلي متزايد (UInt32) لاكتشاف الفقد ومنع إعادة التشغيل |
| `8..11` | 4 | `timestampUs` | الطابع الزمني بالميكروثانية (PTS) لمزامنة الصوت والصورة |
| `12..15` | 4 | `payloadLength` | طول الحمولة بالبايت (حتى `65536`) |

عند تفعيل التشفير (بعد إتمام `KEY_EXCHANGE_RESP`)، تكون الحمولة مشفّرة بصيغة:
`[12-byte Nonce (IV)] + [Ciphertext] + [16-byte AES-256-GCM Auth Tag]`، مع تمرير الترويسة (16 بايت) كـ **AAD (Additional Authenticated Data)** لمنع التلاعب بالترويسة.

---

## 3. رموز العمليات (Opcodes)

| Opcode | الاسم | الاتجاه | الوصف |
| --- | --- | --- | --- |
| `0x01` | `HELLO_SYN` | Phone → BYD | بدء المصافحة وإرسال معرف الهاتف وإصدار البروتوكول |
| `0x02` | `HELLO_ACK` | BYD → Phone | تأكيد المصافحة وإرسال نموذج السيارة وإصدار DiLink |
| `0x03` | `KEY_EXCHANGE_INIT` | Phone → BYD | إرسال المفتاح العام المؤقت (ECDH P-256 Ephemeral Public Key) |
| `0x04` | `KEY_EXCHANGE_RESP` | BYD → Phone | المفتاح العام لشاشة BYD + بصمة التحقق لإنشاء مفتاح الجلسة |
| `0x05` | `CAPABILITIES_REQ` | Phone → BYD | طلب دقة الشاشة، التوجيه المدعوم (عمودي/أفقي)، والترميز |
| `0x06` | `CAPABILITIES_RESP` | BYD → Phone | رد الشاشة (مثل `1920x1080@60fps`, `H265/H264`) |
| `0x07` | `SESSION_START` | Phone → BYD | بدء بث الشاشة بالدقة ومعدل البت المتفق عليهما |
| `0x08` | `SESSION_STOP` | Both | إنهاء جلسة العرض اللاسلكي بأمان |
| `0x09` | `ORIENTATION_CHANGE` | Phone → BYD | تبديل العرض بين العمودي (`PORTRAIT`) والأفقي (`LANDSCAPE`) |
| `0x10` | `VIDEO_NALU` | Phone → BYD | إطار فيديو مضغوط بصيغة Annex-B (`00 00 00 01`) لـ H.264 / H.265 |
| `0x11` | `AUDIO_AAC` | Phone → BYD | إطار صوتي مضغوط (AAC-LC 48kHz Stereo) |
| `0x12` | `KEYFRAME_REQUEST` | BYD → Phone | طلب إطار IDR فوري عند فقد حزمة فيديو أو جزء من إطار |
| `0x13` | `VIDEO_NALU_FRAG` | Phone → BYD | جزء من إطار فيديو يتجاوز MTU (`[4B frameId][2B fragIdx][2B totalFrags][chunk <= 1150B]`) |
| `0x14` | `TOUCH_EVENT` | BYD → Phone | التحكم العكسي باللمس (`[4B xNorm Float][4B yNorm Float][1B action: 0=DOWN,1=UP,2=MOVE]`) |
| `0x20` | `HEARTBEAT_PING` | Phone → BYD | قياس زمن الوصول (RTT) |
| `0x21` | `HEARTBEAT_PONG` | BYD → Phone | استجابة قياس زمن الوصول |
| `0x22` | `QOS_TELEMETRY` | BYD → Phone | تقرير فقد الحزم والاهتزاز (Jitter) لضبط الجودة تلقائياً |
| `0x23` | `THERMAL_THROTTLE` | Phone → BYD | إشعار بخفض معدل الإطارات/الدقة بسبب حرارة الهاتف |

---

## 4. الأمان والتشفير (Security Handshake)

1. يولّد كل طرف زوج مفاتيح **ECDH P-256** مؤقت لكل جلسة (Forward Secrecy).
2. يتبادل الطرفان المفاتيح العامة عبر `KEY_EXCHANGE_INIT` و `KEY_EXCHANGE_RESP`.
3. يُشتق مفتاح الجلسة المتماثل (256-bit) عبر **HKDF-SHA256** باستخدام:
   - `IKM`: السر المشترك من ECDH
   - `Salt`: معرّف الجلسة العشوائي (`sessionNonce`)
   - `Info`: `"BYD-MIRROR-SESSION-KEY-V1"`
4. تُشفّر جميع إطارات الفيديو والصوت وأوامر التحكم باستخدام **AES-256-GCM**.
