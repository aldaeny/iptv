# ANDROID BUILD EVIDENCE — BYD Wireless Mirror

## 1. Local Sandbox Build Attempt (Current Unpushed Working Tree)
- **Command:** `bash DriveLink/ci/run_drivelink_builds.sh` (checks `java`, `gradle`, `./gradlew`, `ANDROID_HOME`)
- **Result:** **BLOCKED** — `SKIP: JDK / Gradle / Android SDK not installed in current environment` (`which java javac kotlinc gradle` exit 1; `deb.debian.org` and `dl.google.com` blocked by egress firewall).
- **Local Pre-Build Static & AST Verification (Executed & Passed):**
  - `tree-sitter-kotlin 1.1.0` AST parse across all Android `.kt`/`.kts` files: `0` syntax errors.
  - `verify_protocol.py`: validated all Android XML manifests/resources and verified `Activity.RESULT_OK` in `MirrorForegroundService.kt`, `mainHandler.post` in `BydTouchAccessibilityService.kt`, and separated `udpSequenceCounter`.
- **Android Build Output for Current Unpushed Code:**
  - **APK Path:** *None produced locally* (`BLOCKED` until pushed to CI or built on a host with JDK 17 + Android SDK).
  - **APK SHA-256 / Package / Signing / Installability:** `BLOCKED` for current working tree.

## 2. Historical Remote CI Reference (Baseline Commit `c9b43b4` ONLY — Pre-Fixes)
> **Warning:** These remote artifacts were built **before** the local `MirrorForegroundService.kt` `Activity.RESULT_OK` fix and subsequent Phase 1/2/Final fixes. Do NOT ship `c9b43b4` artifacts.

- **Workflow Run:** `https://github.com/aldaeny/iptv/actions/runs/44223614822` (`BYD Mirror Build & Test`, `build-android-phone` succeeded in `3m36s`, `gradle 8.10.2`, `Temurin JDK 17`, `compileSdk 35`, `targetSdk 34`, `minSdk 26`).
- **Command Executed in CI:** `gradle testStandardDebugUnitTest assembleStandardRelease assembleFullTouchRelease`
- **Remote Assets Produced at `c9b43b4`:**
  - `BydMirror-Phone.apk`: `11,501,294` bytes (`app.drivelink.cast`, `versionCode 2`, `versionName 1.0.1`, debug-signed v1/v2)
  - `BydMirror-Phone-ReverseTouch.apk`: `11,501,532` bytes (`app.drivelink.cast.touch`, `versionCode 2`, `versionName 1.0.1`, debug-signed v1/v2)
- **Exact Command to Rebuild Current Fixes Locally:**
  `cd DriveLink/android && gradle testStandardDebugUnitTest lintStandardDebug assembleStandardRelease assembleFullTouchRelease`
