# DriveLink — ربط وعرض شاشة الهاتف لاسلكياً على شاشة السيارة

المشروع الفرعي الوحيد في هذا المستودع لتطبيقات **DriveLink** (سابقاً `byd-mirror` و`DriveLink`): تطبيق هاتف
لاسلكي (Android + iOS)، وتطبيق استقبال لشاشة السيارة، ومحاكي شاشات للاختبار. جميع الشيفرة والوثائق
والاختبارات وملفات CI صارت في شجرة واحدة تحت `DriveLink/` بعد توحيد الفروع؛ لا توجد نسخة مكرّرة بعد الآن.

| المجلد | المحتوى |
|---|---|
| `android/` | تطبيق الهاتف Kotlin/Compose بنسختي `standard` و`fullTouch` |
| `receiver-dilink/` | تطبيق الاستقبال على شاشة السيارة (Android) |
| `ios/` | تطبيق Swift وReplayKit ومواصفات XcodeGen لـ`Project.yml` (Full) و`Project-free.yml` (Free) |
| `simulator/` | محاكي Go مع واجهة ويب (استقبال البث الحقيقي من الهاتف + بث تجريبي) |
| `shared/`, `tests/` | تعريف البروتوكول والاختبارات المستقلة |
| `tools/` | أدوات التحقق من مخرجات البناء (APK/التوقيع/SHA-256) |
| `docs/` | وثائق المصدر المرجعية (بناء، تثبيت، بروتوكول، معمارية، تشغيل) |
| `releases/` | تقارير الإصدار المرشح وسجلات البناء |

## الهوية الموحّدة

| العنصر | القيمة |
|---|---|
| نسخة المشروع | `1.4.1` (`versionCode 3`) |
| تطبيق الهاتف `standard` | `app.drivelink.cast` |
| تطبيق الهاتف `fullTouch` | `app.drivelink.cast.touch` |
| تطبيق الاستقبال | `app.drivelink.receiver` |
| iOS Full | `app.drivelink.cast.app` |
| iOS Free | `app.drivelink.cast.app.free` |
| ملحقات iOS | `app.drivelink.cast.app.broadcast`, `app.drivelink.cast.app.liveactivity` |
| App Group مشترك | `group.app.drivelink.cast.shared` |
| متغيرات توقيع Android | `DRIVELINK_KEYSTORE_PATH` / `_PASSWORD` / `DRIVELINK_KEY_ALIAS` / `DRIVELINK_KEY_PASSWORD` |
| البروتوكول | `BYD-WMP v1.0` — TCP `47810`، UDP `47811`، منارة الاكتشاف UDP `47813`، خدمة Bonjour `_byd-mirror._tcp.` |

> أسماء البروتوكول والخدمات (`BYD-WMP`, `_byd-mirror._tcp.`, معرّفات الحزم الأصلية) بقيت كما هي عمداً
> للحفاظ على التوافق مع شاشات BYD DiLink الحقيقية؛ التوحيد طال الشيفرة والمسارات والهوية التحريرية فقط.

## الاختبار والتحقق

من جذر المستودع:

```sh
python3 DriveLink/tests/verify_isolation.py    # الحزم والملحقات وApp Group وتوقيع الإصدار
python3 DriveLink/tests/verify_protocol.py     # ثوابت البروتوكول والتكافؤ بين المنصات والفريمينج
python3 DriveLink/tests/verify_release.py      # بوابات الإصدار المصدرية (DSL، موارد، imports، iOS، منافذ)
node    DriveLink/tests/crypto_fuzz_suite.mjs  # التشفير والـ fuzz وتجربة loopback حيّة (TCP/UDP + WebCodecs)
cd DriveLink/simulator && go test -race ./...  # اختبارات المحاكي
```

والثلاثة الأولى تعمل الآن بنجاح على هذه الشجرة.

## البناء

- **Android:** `cd DriveLink/android && ./gradlew assembleStandardDebug assembleFullTouchDebug`، وللاستقبال
  `cd DriveLink/receiver-dilink && ./gradlew assembleDebug`. البناء الموقّع يحتاج متغيرات `DRIVELINK_*`
  أعلاه؛ بدونه يبقى مسار `release` بلا توقيع بدل الرجوع إلى مفتاح debug.
- **iOS:** يلزم macOS + Xcode + XcodeGen: `cd DriveLink/ios && xcodegen generate --spec Project.yml`
  ثم `xcodebuild -scheme BydWirelessMirror -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build`.
  راجع [دليل البناء](docs/BUILD_GUIDE.md) و[دليل iOS](docs/IOS_GUIDE.md).
- **المحاكي:** `cd DriveLink/simulator && go run .` ثم افتح `http://127.0.0.1:47812` على نفس الجهاز.
- **CI:** `.github/workflows/drivelink-android-release.yml` — مهمة `verify` تعمل عند كل PR/دفعة تخص
  `DriveLink/**`، ومهمة `android` (بناء وتوقيع والتحقق من APK) تُشغَّل يدوياً عبر `workflow_dispatch`.

لم تُنتج APK أو IPA في هذه البيئة، ولم يُنفَّذ بناء Android/iOS هنا؛ التفاصيل في
[releases/v1.4.1/BUILD_REPORT.md](releases/v1.4.1/BUILD_REPORT.md) و[release-delivery/](release-delivery/).
