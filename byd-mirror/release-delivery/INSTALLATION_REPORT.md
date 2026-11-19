# BYD Wireless Mirror — Installation & Safety Report

- **Installation Status:** **NOT EXECUTED** (APK compilation and physical Android / BYD DiLink device attachment unavailable in sandbox).

## Intended Installation Targets (Once Built)

1. **Android Phone (`standard` — recommended for general use):**
   - Package ID: `com.byd.mirror`
   - Artifact: `byd-mirror/android/app/build/outputs/apk/standard/release/app-standard-release.apk`
   - Install: `adb install -r app-standard-release.apk`
2. **Android Phone (`fullTouch` — includes floating overlay & reverse-touch AccessibilityService):**
   - Package ID: `com.byd.mirror.touch`
   - Artifact: `byd-mirror/android/app/build/outputs/apk/fullTouch/release/app-fullTouch-release.apk`
   - Install: `adb install -r app-fullTouch-release.apk`
   - Post-install: Grant Display Over Other Apps (`SYSTEM_ALERT_WINDOW`) and optionally enable `BydTouchAccessibilityService` only when parked.
3. **BYD DiLink Car Head Unit (`receiver-dilink`):**
   - Package ID: `com.byd.receiver`
   - Artifact: `byd-mirror/receiver-dilink/app/build/outputs/apk/release/app-release.apk`
   - Install on DiLink Android unit: `adb install -r app-release.apk`

## Safety & Verification Notes

- Only configure, pair (6-digit ECDH PIN), and test mirroring while the vehicle is safely parked.
- **BYD Vehicle Compatibility:** **NOT VERIFIED** without physical BYD DiLink head unit hardware testing.
