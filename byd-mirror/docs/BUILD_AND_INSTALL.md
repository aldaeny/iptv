# دليل البناء والتشغيل والتثبيت — BYD Wireless Mirror

## 1. المتطلبات الأساسية لبيئة المطورين
- **Android / BYD DiLink:** Android Studio (Ladybug أو أحدث)، JDK 17، و Android SDK 35.
- **iOS:** جهاز Mac مع Xcode 15.4+ وأداة `xcodegen` (`brew install xcodegen`) وحساب Apple Developer لتوقيع `ReplayKit` و`App Groups` و`WidgetKit`.

---

## 2. بناء تطبيق الهاتف وتطبيق السيارة (Android)

### أ) توليد Gradle Wrapper والبناء
```bash
cd byd-mirror/android
gradle wrapper --gradle-version 8.10.2
./gradlew assembleDebug testDebugUnitTest

cd ../receiver-dilink
gradle wrapper --gradle-version 8.10.2
./gradlew assembleDebug testDebugUnitTest
```

### ب) تثبيت المستقبل على شاشة سيارة BYD DiLink
1. فعّل وضع المطورين وتصحيح USB/Wi-Fi ADB على شاشة BYD (أو ثبّت حزمة `app-debug.apk` عبر USB Flash Drive إذا كان النظام يسمح بتثبيت APK).
2. أو عبر ADB:
```bash
adb connect <BYD_IP>:5555
adb install -r byd-mirror/receiver-dilink/app/build/outputs/apk/debug/app-debug.apk
```
3. **ملاحظة في حال كانت شاشة السيارة مقفلة ضد تثبيت APK:**
   - يدعم تطبيق الهاتف تلقائياً محول **`MiracastDisplayAdapter`** (`MediaRouter` / Wi-Fi Display) للربط المباشر مع شاشات BYD التي تدعم العرض اللاسلكي المدمج (WFD/Miracast) دون تثبيت المستقبل.

### ج) تفعيل التحكم العكسي باللمس ونافذة Overlay على الهاتف
- **لوحة التحكم العائمة:** امنح التطبيق إذن `الظهور فوق التطبيقات الأخرى` (`SYSTEM_ALERT_WINDOW`).
- **التحكم باللمس من شاشة السيارة:** من `الإعدادات ← تسهيل الاستخدام (Accessibility)`، فعّل خدمة **BYD Wireless Mirror Touch**.

---

## 3. بناء تطبيق الآيفون (iOS)
```bash
cd byd-mirror/ios
xcodegen generate
open BydWirelessMirror.xcodeproj
```
1. اختر **Signing & Capabilities** لكل الأهداف الثلاثة:
   - `BydWirelessMirror`
   - `BydBroadcastExtension`
   - `BydLiveActivityExtension`
2. تأكد من تفعيل Capability:
   - **App Groups:** `group.com.byd.mirror.shared`
   - **Access WiFi Information**
   - **Keychain Sharing:** `com.byd.mirror.shared`
   - **Background Modes:** `Audio` و `Picture in Picture`
3. شغّل التطبيق على جهاز iPhone حقيقي (محاكي iOS لا يدعم ReplayKit System Broadcast أو AWDL P2P).
