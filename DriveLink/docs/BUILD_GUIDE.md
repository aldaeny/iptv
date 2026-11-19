# BUILD GUIDE — DriveLink Cast

## 1. Local Verification (No Native Compilers Required)
```bash
python3 DriveLink/tests/verify_protocol.py
```

## 2. Android Phone Sender (`DriveLink/android/`)
- **Requirements:** JDK 17, Android SDK (`compileSdk 35`, `minSdk 26`), Gradle 8.10.2.
- **Flavors:**
  - `standard` (`app.drivelink.cast`): Play-Protect-reduced APK without `SYSTEM_ALERT_WINDOW` or `BIND_ACCESSIBILITY_SERVICE`.
  - `fullTouch` (`app.drivelink.cast.touch`): Includes floating overlay + Accessibility reverse-touch service.
- **Commands:**
```bash
cd DriveLink/android
./gradlew testStandardDebugUnitTest assembleStandardRelease assembleFullTouchRelease
```

## 3. BYD DiLink Receiver (`DriveLink/receiver-dilink/`)
- **Requirements:** JDK 17, Android SDK 35, Build Tools 35.0.0.
```bash
cd DriveLink/receiver-dilink
./gradlew testDebugUnitTest assembleRelease
```

## 4. iOS Sender (`DriveLink/ios/`)
- **Requirements:** macOS with Xcode 15.3+, Swift 5.10+, XcodeGen (`brew install xcodegen`).
- **SwiftPM Unit Tests:**
```bash
cd DriveLink/ios
swift test
```
- **Zero-Cost Free Sideload IPA (1 App ID, no Extensions/App Groups):**
```bash
cd DriveLink/ios
xcodegen generate --spec Project-free.yml
xcodebuild -project BydWirelessMirrorFree.xcodeproj -scheme BydWirelessMirror -configuration Release -destination 'generic/platform=iOS' CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO build
```
- **Full 3-Target IPA (System-Wide `BroadcastExtension` + `WidgetExtension`):**
```bash
cd DriveLink/ios
xcodegen generate --spec Project.yml
xcodebuild -project BydWirelessMirror.xcodeproj -scheme BydWirelessMirror -configuration Release -destination 'generic/platform=iOS' CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO build
```

## 5. Windows / Cross-Platform EV Screen Simulator (`DriveLink/simulator/`)
- **Requirements:** Go 1.22+ (zero external Go module dependencies).
```bash
cd DriveLink/simulator
go test -v ./...
GOOS=windows GOARCH=amd64 go build -ldflags="-s -w" -o BydScreenSimulator-Windows.exe .
```
