# DriveLink Cast — Build Report

- **Repository:** `aldaeny/iptv`
- **Branch:** `arena/f1a53a8c-iptv` (branched from `master` @ `4d976a0bc8fabc6be41432f782fee582c9876145`)
- **Unified Tree:** `DriveLink/` only (the former duplicate `byd-mirror/` tree was merged and removed)
- **Target Modules:**
  - Android Phone (`DriveLink/android`): `app.drivelink.cast` (`standard` + `fullTouch` flavors, `versionCode=3`, `versionName="1.4.1"`)
  - DriveLink Receiver (`DriveLink/receiver-dilink`): `app.drivelink.receiver` (`release` + `debug`, `versionCode=3`, `versionName="1.4.1"`)
  - iOS (`DriveLink/ios`): `Project.yml` (Full) & `Project-free.yml` (Free Sideload single-App-ID)
  - Windows / Linux Simulator (`DriveLink/simulator`): Go 1.22 + embedded WebCodecs HMI

## Build Execution Status

| Target | Status | Root Cause / Details |
| :--- | :--- | :--- |
| Android Phone (`standard` / `fullTouch` Release APKs) | **BLOCKED** | Local sandbox lacks JDK 17, Gradle 8.10.2, and Android SDK (`ANDROID_HOME`); GitHub Actions returned `HTTP 422: Actions has been disabled for this repository` when dispatching `drivelink-android-release.yml (job: android)` on `arena/b9db39ae-iptv`. |
| BYD DiLink Receiver (`app-release.apk`) | **BLOCKED** | Same environment blocker (missing local JDK/SDK; GitHub Actions disabled on repository). |
| iOS (`BydWirelessMirror-unsigned.ipa` / `FreeSideload.ipa`) | **BLOCKED** | Linux sandbox lacks macOS, Xcode, Swift 5.10, and XcodeGen; GitHub Actions disabled on repository. |
| Go Simulator (`BydScreenSimulator-Windows.exe` / `BydScreenSimulator-Linux`) | **BLOCKED** | Local sandbox lacks Go 1.22 compiler; GitHub Actions disabled on repository. |

## Required Action to Produce Release APKs

1. Re-enable GitHub Actions on `aldaeny/iptv` (`Settings -> Actions -> General -> Allow all actions`) and trigger `.github/workflows/drivelink-android-release.yml (job: android)` on branch `arena/b9db39ae-iptv`, **or**
2. Run locally on a machine with JDK 17 + Android SDK 35 + Gradle 8.10.2:
   ```bash
   cd DriveLink/android
   gradle testStandardDebugUnitTest lintStandardDebug assembleStandardRelease assembleFullTouchRelease

   cd ../receiver-dilink
   gradle testDebugUnitTest lintDebug assembleRelease
   ```
