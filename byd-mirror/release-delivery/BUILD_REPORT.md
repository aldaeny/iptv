# BYD Wireless Mirror — Build Report

- **Repository:** `aldaeny/iptv`
- **Branch:** `arena/b9db39ae-iptv`
- **Redesign Commit:** `7f798d99f009111cd1863a9d42b3c6c718e29a1a`
- **Target Modules:**
  - Android Phone (`byd-mirror/android`): `com.byd.mirror` (`standard` + `fullTouch` flavors, `versionCode=2`, `versionName="1.0.1"`)
  - BYD DiLink Receiver (`byd-mirror/receiver-dilink`): `com.byd.receiver` (`release` + `debug`, `versionCode=2`, `versionName="1.0.1"`)
  - iOS (`byd-mirror/ios`): `Project.yml` (Full) & `Project-free.yml` (Free Sideload single-App-ID)
  - Windows / Linux Simulator (`byd-mirror/simulator`): Go 1.22 + embedded WebCodecs HMI

## Build Execution Status

| Target | Status | Root Cause / Details |
| :--- | :--- | :--- |
| Android Phone (`standard` / `fullTouch` Release APKs) | **BLOCKED** | Local sandbox lacks JDK 17, Gradle 8.10.2, and Android SDK (`ANDROID_HOME`); GitHub Actions returned `HTTP 422: Actions has been disabled for this repository` when dispatching `byd-mirror-build.yml` on `arena/b9db39ae-iptv`. |
| BYD DiLink Receiver (`app-release.apk`) | **BLOCKED** | Same environment blocker (missing local JDK/SDK; GitHub Actions disabled on repository). |
| iOS (`BydWirelessMirror-unsigned.ipa` / `FreeSideload.ipa`) | **BLOCKED** | Linux sandbox lacks macOS, Xcode, Swift 5.10, and XcodeGen; GitHub Actions disabled on repository. |
| Go Simulator (`BydScreenSimulator-Windows.exe` / `Linux`) | **BLOCKED** | Local sandbox lacks Go 1.22 compiler; GitHub Actions disabled on repository. |

## Required Action to Produce Release APKs

1. Re-enable GitHub Actions on `aldaeny/iptv` (`Settings -> Actions -> General -> Allow all actions`) and trigger `.github/workflows/byd-mirror-build.yml` on branch `arena/b9db39ae-iptv`, **or**
2. Run locally on a machine with JDK 17 + Android SDK 35 + Gradle 8.10.2:
   ```bash
   cd byd-mirror/android
   gradle testStandardDebugUnitTest lintStandardDebug assembleStandardRelease assembleFullTouchRelease

   cd ../receiver-dilink
   gradle testDebugUnitTest lintDebug assembleRelease
   ```
