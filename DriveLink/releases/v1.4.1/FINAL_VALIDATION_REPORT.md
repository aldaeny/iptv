# DriveLink 1.4.1 — سجل البناء والتحقق النهائي

> **هذا التقرير يسجّل فقط ما نُفِّذ فعلياً.** لا يوجد في هذا الإصدار أي APK أو AAB أو IPA أو توقيع أو تثبيت
> ناتج عن هذه الدورة؛ كل بند لم يُنفَّذ مكتوب صراحةً أنه **NOT EXECUTED / BLOCKED** مع سببه.

- **Commit الذي جرى التحقق منه:** `78bc8f5` على الفرع `arena/bf1a2348-iptv` (الأساس `3bd6a1a` = master الموحّد)
- **تاريخ التقرير:** 2026-11-19
- **شجرة المصدر:** `DriveLink/` فقط (222+ ملفاً) — لا وجود لـ`byd-mirror/`

---

## 1. تأكيد بنية المشروع الموحّدة (منفّذ)

| الفحص | النتيجة | الدليل |
|---|---|---|
| شجرة مصدر واحدة | **PASS** | `git ls-tree master` ← `DriveLink` فقط، `byd-mirror` غير موجود |
| هوية الحزم | **PASS** | `app.drivelink.cast` / `.touch` / `app.drivelink.receiver` / iOS `app.drivelink.cast.app[.free]` |
| الإصدار | **PASS** | Android `versionCode 3` + `versionName 1.4.1`، iOS `CFBundleVersion 3` + `1.4.1` |
| أدوات مساعدة | **PASS** | `verify_isolation.py`, `verify_protocol.py`, `verify_release.py`, `crypto_fuzz_suite.mjs`, `simulator_ui_wiring.mjs` |

## 2. تحقق إعداد Gradle / Java / SDK / التوقيع (منفّذ على مستوى المصدر)

| الفحص | النتيجة | ملاحظات |
|---|---|---|
| AGP / Kotlin / Gradle | **PASS** | AGP `8.7.2`، Kotlin `2.0.21`، Gradle wrapper `8.10.2` (blob `a4b76b95…` مطابق للموثّق) |
| Java compatibility | **PASS** | `sourceCompatibility/targetCompatibility = VERSION_17`، `jvmTarget = "17"` |
| SDK levels | **PASS** | `compileSdk 35`، `buildToolsVersion 35.0.0`، `minSdk 26`، `targetSdk 34` |
| موقع `buildToolsVersion` | **PASS بعد الإصلاح** | كان داخل `defaultConfig` (غير صالح في AGP 8) → نُقل إلى `android { }` |
| إعداد التوقيع | **PASS** | `release` يستخدم `signingConfigs.findByName("release")` فقط، بلا fallback إلى debug، وغير قابل للتصحيح |
| متغيرات التوقيع | **PASS** | `DRIVELINK_KEYSTORE_PATH/_PASSWORD`, `DRIVELINK_KEY_ALIAS/_PASSWORD` |
| wrapper قابل للتنفيذ | **PASS** | `gradlew` نمط `100755` في git لكلا المشروعين |

## 3. حالة بناء Android لكل متغيّر

| المتغيّر | الحزمة | الحالة | السبب |
|---|---|---|---|
| Standard (release) | `app.drivelink.cast` | **BLOCKED** | لا JDK/JDK-17 ولا Android SDK ولا Gradle في هذه البيئة، والحظر الشبكي يمنع `dl.google.com`/`services.gradle.org`/`repo1.maven.org` (كلها `000`). راجع §8 |
| FullTouch (release) | `app.drivelink.cast.touch` | **BLOCKED** | نفس السبب |
| Receiver (release) | `app.drivelink.receiver` | **BLOCKED** | نفس السبب |
| Free APK | — | **غير مُعرَّف أصلاً** | «Free» في هذا المشروع هو **هدف iOS** (`Project-free.yml`)، وليس flavour أندرويد؛ لم أخترع flavour جديداً |
| Debug smoke APKs | الثلاثة | **BLOCKED** | نفس السبب (لا محاكي/جهاز أيضاً) |

ما **تم** فعله بدلاً من الادّعاء: بناء مسار CI كامل ينتج هذه الملفات الثلاثة فعلياً عند توفّر Actions (§5)،
وأداة تحقق `verify-android-build.sh` التي تفحص الحزمة/الإصدار/التوقيع/SHA-256 لكل APK.

## 4. تحقق مشروع iOS وجدوى الأرشيف

| الفحص | النتيجة | ملاحظات |
|---|---|---|
| XcodeGen specs قابلة للتحميل | **PASS** | `Project.yml` (تطبيق + امتدادان)، `Project-free.yml` (هدف واحد) |
| تكرار الأهداف في Free | **PASS بعد التحقق** | المصادر تحتوي معالج ReplayKit وLive Activity، لذا إضافة امتدادات في Free كانت ستُكرّر الأنواع داخل وحدة واحدة؛ الملف يعلن هدفاً واحداً صراحةً |
| الإصدار/الملحقات/App Group/Bonjour | **PASS** | `1.4.1 (3)`، معرّفات `*.broadcast`/`*.liveactivity`، `group.app.drivelink.cast.shared`، `_byd-mirror._tcp/_udp` |
| أصول Swift | **PASS** | 26 ملف Swift، 0 تكرار نوع، 0 خطأ تحليل (tree-sitter)، كل مراجع `Image("…")` موجودة |
| `swift test` / `xcodebuild` / IPA | **NOT EXECUTED** | لا macOS ولا Xcode في Linux sandbox؛ **لا يوجد IPA موقّع ولا غير موقّع** |

## 5. إعداد GitHub Actions القابل لإعادة الإنتاج

| البند | الحالة |
|---|---|
| `drivelink-android-release.yml` | **مُعدّ** — `verify` على PR/Push، و`android`+`ios` على `workflow_dispatch` فقط |
| تثبيت الإجراءات بـ SHA | **PASS** — checkout/setup-java/setup-node/setup-go/upload-artifact/gradle-actions |
| إصدارات مثبّتة | **PASS** — Gradle 8.10.2، JDK 17، build-tools 35.0.0 (`env:` واحد) |
| بناء قابل لإعادة الإنتاج | **PASS (إعداد)** — Go بـ`-trimpath -buildid=`، Gradle بمخزن/`--no-daemon`، نسخ أدوات مثبّت |
| نشر الإصدار | **gated** — `drivelink-publish-release.yml` يعيد التحقق من كل APK + SHA-256 قبل إنشاء Release |
| تشغيل فعلي للـ workflow | **BLOCKED** — `HTTP 422: Actions has been disabled for this repository` (تحقق مباشر عند `workflow_dispatch` لـ`check.yml` أيضاً؛ ليس خاصاً بهذا الـworkflow) |

## 6. التحقق من الحزم والصلاحيات والبروتوكول (منفّذ)

```
PASS  source package isolation, extensions, shared group and release signing configuration
PASS  ALL VERIFICATION CHECKS PASSED! (XML/Plist validated: 26)
PASS  ALL SOURCE RELEASE GATES PASSED            (البوابة الجديدة)
PASS  CRYPTO, INTEROP, FUZZ, LIVE LOOPBACK TCP/UDP & WEBCODECS SUITE: ALL PASSED!
PASS  ALL SIMULATOR UI WIRING CHECKS PASSED
```

- **الصلاحيات (PASS):** INTERNET، ACCESS/CHANGE_WIFI_STATE، CHANGE_WIFI_MULTICAST_STATE، NEARBY_WIFI_DEVICES،
  RECORD_AUDIO، FOREGROUND_SERVICE(+MEDIA_PROJECTION)، POST_NOTIFICATIONS، WAKE_LOCK؛ fullTouch فقط:
  `SYSTEM_ALERT_WINDOW` + `BIND_ACCESSIBILITY_SERVICE` (standard لا يحملهما — فحص آلي).
- **البروتوكول (PASS):** `BYD-WMP v1.0`، `_byd-mirror._tcp.`، TCP `47810`، UDP `47811`، beacon `47813`،
  web UI `47812`، ECDH-P256 + HKDF-SHA256 + AES-256-GCM، لا تغيير في أي منها.

## 7. التثبيت والاختبار الدخاني

| البيئة | الحالة |
|---|---|
| محاكي Android (KVM) | **NOT AVAILABLE** — `/dev/kvm` غير موجود |
| جهاز USB | **NOT AVAILABLE** — `/dev/bus/usb` غير موجود |
| `adb install` لأي APK | **NOT EXECUTED** (لا APK ولا adb) |
| اختبار دخاني للمحاكي الويب (بديل حقيقي نفّذته) | **PASS** — خدمة `index.html` على المنفذ 47812: كل الأصول `200`، السكربت يعمل، HUD يتفاعل مع رسائل المستقبل، اللمس العكسي يُرسل، الإيقاف اليدوي يعمل (§9) |

## 8. العوائق المتبقية (Remaining blockers)

1. **GitHub Actions معطّل على مستوى المستودع** — `HTTP 422` من `POST /actions/workflows/{id}/dispatches`
   لأي workflow، ولا يملك رمز الجلسة صلاحية تفعيله (`403 Resource not accessible by integration`).
   **المطلوب من المالك:** Settings → Actions → General → Allow all actions.
2. **لا سلسلة أدوات أندرويد في هذه البيئة** — لا JDK/SDK/Gradle، وكل مستضيفي الحزم محجوبون
   (`dl.google.com`, `repo1.maven.org`, `services.gradle.org` → `000`).
3. **لا macOS/Xcode** — لا يمكن إنتاج أو توقيع IPA هنا.
4. **لا مفتاح توقيع إصدار** (`DRIVELINK_KEYSTORE_*` غير موجود في البيئة) — بدونه حتى مع Actions سيبقى
   الإصدار **unsigned** ولن يُنشر تلقائياً (الـworkflow يعلّم ذلك ويوقفه).
5. **لا جهاز/محاكي/BYD DiLink** — التحقق المادي للبث 1080p@60 واللمس العكسي وتوافق السيارة: **UNVERIFIED**.

## 9. الاختبار الدخاني للمحاكي (الدليل الفعلي الوحيد لتشغيل واجهة)

| الفحص | النتيجة |
|---|---|
| `GET /index.html` | 200 — 38,227 بايت |
| `GET /app-icon.png` / `favicon.png` / `hero-car.png` | 200 — 63,969 / 5,154 / 517,843 بايت |
| لا موارد خارجية | PASS (لا `http(s)://` في src/href) |
| تشغيل السكربت في DOM stub | PASS بلا استثناءات |
| WebSocket على نفس الأصل | PASS (`ws://<host>/ws`) |
| رسالة `streaming` → `LIVE • 60 FPS` + `H.264` + اسم الهاتف | PASS |
| اللمس العكسي `pointerdown/up` → إطارَي TOUCH (action 0 ثم 1) | PASS |
| إيقاف الاتصال يدوياً | PASS (`الاتصال متوقف`) |
| تبديل ملف السيارة يحفظ المنافذ | PASS (`TCP 47810 / UDP 47811`) |

**إصلاح جذري أثناء هذا الاختبار:** دالة `sendCanvasTouch()` كانت **مفقودة** في `index.html` (تُستدعى في 4
مواضع)، فكان كل لمس يرمي `ReferenceError` ولا يصل أي إطار لمس إلى المستقبل. أُعيدت الدالة بحراسة حالة
Socket، وأُضيف اختبار `tests/simulator_ui_wiring.mjs` يمنع تكراره، وتم أيضاً عرض codec البث الحقيقي في
شارة الكودك بدل استبدالها برسالة «NO WEBCODECS».

## 10. البصمات (SHA-256) للملفات الحاكمة في هذا الالتزام

| الملف | بايت | SHA-256 |
|---|---|---|
| `DriveLink/tests/verify_release.py` | 16,550 | `0298bedd506a48be0ba88fa3e839bd268f257e41fc44b8dc45259b2a2302ea91` |
| `DriveLink/tests/simulator_ui_wiring.mjs` | 5,317 | `7ca44b032b50b01bbbe3ffc50fecf193652695983db9b635ea2a268274695b48` |
| `DriveLink/tools/verify-android-build.sh` | 2,667 | `7f3b15c6d097e8e4aceb1a50324f2b1b039cb8c2a2ca71a834a94c34467cf75f` |
| `DriveLink/simulator/web/index.html` | 38,994 | `67d83a5e0a58de115c9cbfe96ee156637eca68564d34099a2cd9f764e343bd93` |
| `DriveLink/android/app/build.gradle.kts` | 3,584 | `e60b7caa5c7cc28f2b3e8f293f9440d33cfbdff1d806958d439a13dd7210c944` |
| `DriveLink/receiver-dilink/app/build.gradle.kts` | 2,627 | `24bcbf2c56b862ac8fddc9158abffee91c1b0d6400aecb6c4f40271bea435438` |
| `DriveLink/ios/Project.yml` | 1,636 | `86a42bb750f5a511a6399616effc7a8b674e50fd5e1453e85e9e09f7f7c0c857` |
| `DriveLink/ios/Project-free.yml` | 837 | `ea8b3257e88b64261cd010331c51327c6e820fedda3418b173eb29d4692f4fae` |
| `gradle-wrapper.jar` (blob) | 43,583 | `2db75c40…8046` (git blob `a4b76b9530d66f5e68d973ea569d8e19de379189`) |

> لا توجد بصمات APK/IPA في هذا الجدول لأن **لا ملف منها أُنتج**. أي بصمة لملف غير موجود = تلفيق، وهو ممنوع.

## 11. القرار النهائي

### **NO-GO للنشر العام** — **GO لخط البناء** (build pipeline ready)

- **لم يُنشر أي Release**، ولم تُنشأ أي أصول تنزيل، لأن شروط النشر (§5/§8) لم تتحقق: لا APK/IPA، لا توقيع،
  لا تثبيت، وActions معطّل.
- **ما هو جاهز (GO):** المصدر موحّد ومُتحقَّق منه بـ5 بوابات آلية؛ عوائق بناء حقيقية أُصلحت جذرياً
  (`buildToolsVersion`، مسار التوقيع غير القابل للرجوع، غياب اللمس العكسي، تكرار أهداف iOS)؛ وworkflow
  بناء + workflow نشر مُقيّد يعيدان التحقق من كل شيء تلقائياً.
- **الخطوة الواحدة المطلوبة من المالك:** تفعيل Actions على `aldaeny/iptv` + إضافة أسرار
  `DRIVELINK_KEYSTORE_BASE64/_PASSWORD/_ALIAS/_KEY_PASSWORD` والمتغير `DRIVELINK_SIGNER_SHA256`.
  بعدها: تشغيل `DriveLink CI & Android Release Build` (workflow_dispatch) ← ثم
  `DriveLink Publish Release` بـ`run_id` الناجح. حينها فقط تصبح أرقام التنزيل والبصمات قابلة للنشر،
  وستُضاف إلى هذا التقرير من مخرجات التشغيل الحقيقية لا من التقدير.
