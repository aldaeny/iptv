# دليل المطور - DriveLink Cast

## نظرة عامة

DriveLink Cast هو نظام عرض شاشة لاسلكي مصمم للعمل مع شاشات السيارات (خاصة BYD DiLink). يتكون من أربعة مكونات رئيسية:

1. **مرسل Android** - تطبيق الهاتف (standard + fullTouch)
2. **مرسل iOS** - تطبيق iOS مع ReplayKit Broadcast Extension
3. **مستقبل Android** - تطبيق يعمل على شاشة السيارة
4. **محاكي Go** - للاختبار والتطوير مع واجهة ويب

## البنية المعمارية

### البروتوكول (BYD-WMP v1.1)

| المعامل | القيمة |
|----------|--------|
| بايتات السحر (Magic) | 0x4259 ("BY") |
| نسخة البروتوكول | 0x01 |
| حجم الرأس | 16 بايت |
| أقصى حمولة | 65536 بايت |
| تشفير | AES-256-GCM |
| تبادل مفاتيح | ECDH-P256 + HKDF-SHA256 |

### المنافذ
- **TCP 47810** - التحكم والمصافحة
- **UDP 47811** - بث الفيديو/الصوت
- **UDP 47813** - إشارات الاكتشاف (Beacon)
- **TCP 47812** - واجهة ويب المحاكي
- **mDNS** - `_byd-mirror._tcp.` على 224.0.0.251:5353

### أوبكودز (Opcodes)

| الكود | الاسم | الوصف |
|------|------|---------|
| 0x01 | HELLO_SYN | بدء الاتصال |
| 0x02 | HELLO_ACK | تأكيد الاتصال |
| 0x03 | KEY_EXCHANGE_INIT | بدء تبادل المفاتيح |
| 0x04 | KEY_EXCHANGE_RESP | رد تبادل المفاتيح |
| 0x05 | CAPABILITIES_REQ | طلب القدرات |
| 0x06 | CAPABILITIES_RESP | رد القدرات (H264,H265) |
| 0x07 | SESSION_START | بدء الجلسة |
| 0x08 | SESSION_STOP | إنهاء الجلسة |
| 0x09 | ORIENTATION_CHANGE | تغيير الاتجاه |
| 0x10 | VIDEO_NALU | وحدة NAL للفيديو |
| 0x11 | AUDIO_AAC | إطار صوت AAC |
| 0x12 | KEYFRAME_REQUEST | طلب إطار مفتاح |
| 0x13 | VIDEO_NALU_FRAG | قطعة فيديو مجزأة |
| 0x14 | TOUCH_EVENT | حدث لمس |
| 0x20 | HEARTBEAT_PING | نبضة قلب |
| 0x21 | HEARTBEAT_PONG | رد نبضة قلب |
| 0x22 | QOS_TELEMETRY | بيانات جودة الخدمة |

### تنسيق حزمة VIDEO_NALU_FRAG (0x13)

```
[Frame ID: 4 bytes][Fragment Index: 2 bytes][Total Fragments: 2 bytes][Payload...]
```

### تنسيق حزمة TOUCH_EVENT (0x14)

```
[X normalized: float32][Y normalized: float32][Action: 1 byte]
```
حيث Action: 0=Down, 1=Move, 2=Up

## بناء المشروع

### المتطلبات المسبقة

**Android:**
- Android Studio / Gradle 8+
- JDK 17+
- Android SDK API 34+

**iOS:**
- macOS + Xcode 15+
- XcodeGen (`brew install xcodegen`)

**Go Simulator:**
- Go 1.21+

### بناء Android

```bash
cd DriveLink/android

# تصحيح (Debug)
./gradlew assembleStandardDebug assembleFullTouchDebug

# مستقبل السيارة
cd ../receiver-dilink && ./gradlew assembleDebug

# بناء موقع (Release) - يحتاج مفاتيح توقيع
export DRIVELINK_KEYSTORE_PATH=/path/to/keystore
export DRIVELINK_KEYSTORE_PASSWORD=xxx
export DRIVELINK_KEY_ALIAS=xxx
export DRIVELINK_KEY_PASSWORD=xxx
./gradlew assembleStandardRelease assembleFullTouchRelease
```

### بناء iOS

```bash
cd DriveLink/ios

# نسخة كاملة (Full)
xcodegen generate --spec Project.yml
xcodebuild -project BydWirelessMirror.xcodeproj -scheme BydWirelessMirror \
  -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build

# نسخة مجانية (Free)
xcodegen generate --spec Project-free.yml
xcodebuild -project BydWirelessMirrorFree.xcodeproj -scheme BydWirelessMirror \
  -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

### تشغيل المحاكي

```bash
cd DriveLink/simulator
go run .
# يفتح تلقائياً http://127.0.0.1:47812
```

### إعداد جدار الحماية (Windows)

```bash
# تلقائي
DRIVELINK_CONFIGURE_FIREWALL=1 go run .

# يدوي
netsh advfirewall firewall add rule name="BYD Screen Simulator TCP" dir=in action=allow protocol=TCP localport=47810
netsh advfirewall firewall add rule name="BYD Screen Simulator UDP" dir=in action=allow protocol=UDP localport=47811,47813,5353
```

## الاختبار

### اختبارات المحاكي (Go)
```bash
cd DriveLink/simulator
go test -race ./...
```

### اختبارات التحقق (Python)
```bash
cd DriveLink/tests
python3 verify_protocol.py      # التحقق من البروتوكول عبر المنصات
python3 verify_isolation.py     # التحقق من عزل الحزم والتوقيع
python3 verify_release.py       # بوابات الإصدار
```

### اختبارات Fuzzing التشفير (Node.js)
```bash
cd DriveLink/tests
node crypto_fuzz_suite.mjs
```

### حلقة اختبار محلية (Android)
```bash
cd DriveLink/tests
./run-host-loopback.sh
```

## إضافة ميزات جديدة

### 1. إضافة ترميز فيديو جديد

1. حدّث `shared/protocol/byd_mirror_protocol.json`:
   - أضف الترميز في `videoCodecs`
   - حدّث `codecs` في `bydDisplayProfiles`

2. حدّث المحاكي (`simulator/main.go`):
   - أضف دعم الترميز في `handleIncomingMedia`
   - حدّث `opCapabilitiesResp`

3. حدّث Android (`ScreenCaptureEncoder.kt`):
   - أضف إعداد MediaFormat للترميز الجديد

4. حدّث iOS (`HardwareVideoEncoder.swift`):
   - أضف دعم الترميز في VideoToolbox

5. حدّث المستقبل (`VideoAudioDecoder.kt`):
   - أضف فك الترميز

### 2. إضافة ملف تعريف شاشة جديد

في `shared/protocol/byd_mirror_protocol.json`:

```json
{
  "id": "custom_display",
  "label": "Custom Display Name",
  "diLinkVersions": ["5.0"],
  "landscape": { "width": 1920, "height": 1080 },
  "portrait": { "width": 1080, "height": 1920 },
  "maxFps": 60,
  "recommendedBitrateMbps": 15,
  "codecs": ["H265", "H264"]
}
```

### 3. إضافة أوبكود جديد

1. أضف الكود في `packetFraming.opcodes` في JSON
2. أضف الثابتين في Go (`main.go`) و Kotlin/Swift
3. أضف المعالجة في المحاكي، Android، iOS، والمستقبل
4. أضف اختبارات في `verify_protocol.py`

## مسارات الملفات المهمة

```
DriveLink/
├── shared/protocol/byd_mirror_protocol.json   # تعريف البروتوكول (مصدر الحقيقة)
├── android/app/src/main/java/app/drivelink/cast/
│   ├── core/protocol/BydProtocolWrapper.kt    # بروتوكول Android
│   ├── core/security/ChannelCryptoManager.kt  # تشفير Android
│   ├── core/streaming/ScreenCaptureEncoder.kt # ترميز الفيديو Android
│   └── service/BydTouchAccessibilityService.kt # خدمة اللمس
├── ios/Sources/Core/
│   ├── Protocol/BydProtocolWrapper.swift      # بروتوكول iOS
│   ├── Streaming/HardwareVideoEncoder.swift   # ترميز الفيديو iOS
│   └── Session/DirectMirrorSession.swift      # إدارة الجلسة iOS
├── receiver-dilink/app/src/main/java/app/drivelink/receiver/
│   ├── protocol/BydReceiverServer.kt          # خادم المستقبل
│   ├── crypto/ReceiverCryptoManager.kt        # تشفير المستقبل
│   └── decoder/VideoAudioDecoder.kt           # فك الترميز
├── simulator/main.go                          # المحاكي (Go)
├── simulator/web/index.html                   # واجهة ويب المحاكي
└── tests/                                     # اختبارات التحقق
```

## استكشاف الأخطاء

### مشاكل شائعة

| المشكلة | السبب المحتمل | الحل |
|----------|---------------|-------|
| فشل المصافحة | مفاتيح غير متطابقة | تحقق من `kdfInfo` ونسخة البروتوكول |
| فيديو لا يظهر | ترميز غير مدعوم | تأكد من توافق `mime` بين المرسل والمستقبل |
| تأخير عالي | شبكة/ترميز | قلل البت ريت، تحقق من Jitter |
| لمس لا يعمل (FullTouch) | خدمة الوصولية معطلة | فعّل `BydTouchAccessibilityService` |
| اكتشاف mDNS لا يعمل | جدار حماية | افتح منفذ 5353 UDP |

### سجلات التصحيح

**Android:** `adb logcat | grep -i byd`
**iOS:** Xcode Console أو `log stream --predicate 'subsystem contains "drivelink"'`
**Simulator:** سجلات وحدة التحكم (stdout)
**Receiver:** `adb logcat | grep -i drivelink.receiver`

## المساهمة

1. تأكد من مرور جميع الاختبارات: `python3 tests/verify_protocol.py`
2. حدّث تعريف البروتوكول في `shared/protocol/byd_mirror_protocol.json`
3. نفذ التغييرات في جميع المنصات الأربع
4. أضف اختبارات للحالة الجديدة
5. تأكد من التوافق العكسي (Backward Compatibility)

## الترخيص

MIT License - راجع ملف LICENSE في جذر المستودع.
