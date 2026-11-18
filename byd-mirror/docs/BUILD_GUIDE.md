# BUILD GUIDE — BYD Wireless Mirror

## 1. Local Verification (No Native Compilers Required)
```bash
python3 byd-mirror/tests/verify_protocol.py
```

## 2. Android Phone Sender (`byd-mirror/android/`)
- **Requirements:** JDK 17, Android SDK (`compileSdk 34`, `minSdk 26`), Gradle 8.10.2.
- **Flavors:**
  - `standard` (`com.byd.mirror`): Play-Protect-reduced APK without `SYSTEM_ALERT_WINDOW` or `BIND_ACCESSIBILITY_SERVICE`.
  - `fullTouch` (`com.byd.mirror.touch`): Includes floating overlay + Accessibility reverse-touch service.
- **Commands:**
```bash
cd byd-mirror/android
./gradlew testStandardDebugUnitTest assembleStandardRelease assembleFullTouchRelease
```

## 3. BYD DiLink Receiver (`byd-mirror/receiver-dilink/`)
- **Requirements:** JDK 17, Android SDK 34.
```bash
cd byd-mirror/receiver-dilink
./gradlew testDebugUnitTest assembleRelease
```

## 4. iOS Sender (`byd-mirror/ios/`)
- **Requirements:** macOS with Xcode 15+, Swift 5.9+, XcodeGen (`brew install xcodegen`).
- **SwiftPM Unit Tests:**
```bash
cd byd-mirror/ios
swift test
```
- **Zero-Cost Free Sideload IPA (1 App ID, no Extensions/App Groups):**
```bash
cd byd-mirror/ios
xcodegen generate --spec Project-free.yml
xcodebuild -project BydWirelessMirrorFree.xcodeproj -scheme BydWirelessMirror -configuration Release -destination 'generic/platform=iOS' CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO build
```
- **Full 3-Target IPA (System-Wide `BroadcastExtension` + `WidgetExtension`):**
```bash
cd byd-mirror/ios
xcodegen generate
xcodebuild -project BydWirelessMirror.xcodeproj -scheme BydWirelessMirror -configuration Release -destination 'generic/platform=iOS' CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO build
```

## 5. Windows / Cross-Platform EV Screen Simulator (`byd-mirror/simulator/`)
- **Requirements:** Go 1.22+ (zero external Go module dependencies).
```bash
cd byd-mirror/simulator
go test -v ./...
GOOS=windows GOARCH=amd64 go build -ldflags="-s -w" -o BydScreenSimulator-Windows.exe .
```
