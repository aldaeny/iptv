# PHASE 2 BLOCKERS — BYD Wireless Mirror

## 1. Environment / Build Tooling Blockers (Sandbox Container)
1. **No JDK 17 / Android SDK / Gradle in Container:** Blocks local compilation and unit test execution for `byd-mirror/android/` and `byd-mirror/receiver-dilink/`. Outbound egress firewall blocks `deb.debian.org`, `dl.google.com`, and `release-assets.githubusercontent.com`.
2. **No Go 1.22 Compiler in Container:** Blocks local execution of `byd-mirror/simulator/simulator_test.go` (`go test -race`) and cross-compilation of `BydScreenSimulator-Windows.exe`.
3. **No macOS / Xcode / Swift Toolchain in Container:** Blocks `swift test` and `xcodebuild` for `Project.yml` and `Project-free.yml`.

## 2. Real Hardware & Vehicle Validation Blockers
1. **No Physical Android / iPhone Devices Connected via USB/Wi-Fi:** Blocks post-fix live MediaProjection, ReplayKit, and end-to-end FPS/latency measurements.
2. **No Physical BYD DiLink Vehicle Head Unit:** Blocks verification of APK installation permissions on dealer DiLink firmware, rotating display aspect-ratio transitions, car speaker audio routing, and reverse-touch calibration on an actual car.
3. **No Physical MG / Chery / Geely / Changan Vehicles:** Blocks validation of Track A vs Track C (CarPlay AI Box) compatibility on non-BYD Chinese OEM head units.

## 3. Platform & OS Policy Limitations (Non-Bug Permanent Constraints)
1. **iOS Free Sideloading (`$0`):** `BydWirelessMirror-FreeSideload.ipa` captures **in-app** content only (`RPScreenRecorder`); system-wide capture across third-party apps requires `BroadcastExtension` (`BydWirelessMirror-unsigned.ipa`) signed with paid Apple Developer / TrollStore extension provisioning.
2. **iOS Reverse Touch:** Prohibited by iOS security architecture.
3. **Android 14+ Play Protect:** Sideloaded APKs declaring `FOREGROUND_SERVICE_MEDIA_PROJECTION` or Accessibility/Overlay triggers may be flagged by Enhanced Fraud Protection unless installed via `adb` or with Play Protect temporarily paused.
