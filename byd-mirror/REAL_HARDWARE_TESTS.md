# REAL HARDWARE TESTS & FIELD VALIDATION PLAN — BYD Wireless Mirror

> **Policy:** Simulator and Node/Python loopback tests do **NOT** prove real BYD DiLink compatibility. Complete all sections below on physical devices and a real vehicle after rebuilding from the latest commit.

## 1. Prerequisites for External Execution
- **Android Sender APK:** Build `standardRelease` (or `fullTouchRelease`) containing the `MirrorForegroundService.kt` `Activity.RESULT_OK` fix.
- **BYD Receiver APK:** Build `BydMirror-DiLink-Receiver.apk` from `byd-mirror/receiver-dilink/`.
- **iPhone IPA:** Build `Project-free.yml` (in-app capture, `$0` free sideload) or `Project.yml` (full-screen Broadcast Extension, paid Apple Dev / TrollStore).
- **Windows Simulator:** Build `BydScreenSimulator-Windows.exe` from `byd-mirror/simulator/`.

## 2. Step-by-Step Vehicle Execution Checklist
1. **Discovery:** Connect phone and BYD DiLink (or PC simulator) to the same hotspot/Wi-Fi; verify auto-discovery `< 3s` (NSD/Bonjour + UDP beacon `47813`).
2. **Pairing & PIN:** Verify identical 6-digit PIN appears on both screens and connection stays open up to `60s` while confirming.
3. **Wireless Screen Mirroring & Video:** Verify immediate H.264/H.265 rendering (confirm non-zero FPS after `MirrorForegroundService` fix).
4. **Audio Routing:** Play YouTube/Maps audio; confirm AAC-LC 48kHz stereo plays cleanly on BYD car speakers and Receiver Mute toggle works.
5. **Touch / Input (Android `fullTouch`):** Enable Accessibility Service and verify tap + swipe coordinates match portrait & landscape screens.
6. **Orientation Rotation:** Rotate phone and trigger BYD rotating screen (`PORTRAIT` ↔ `LANDSCAPE`); confirm zero crash and re-sync `< 1.5s`.
7. **Background & Lock Behavior:** Switch apps on phone (Android or iOS Broadcast Extension) and verify stream continues.
8. **Disconnect & Reconnect:** Toggle Wi-Fi off for `3s` and back on; verify 3-attempt auto-reconnect recovers within `< 5s`.
9. **Endurance Stability Runs:** Execute `30-min`, `60-min`, and `120-min` drives while logging metrics below.

## 3. Field Measurement Log Template
| Metric | Target | Android ↔ BYD | iPhone ↔ BYD | Android ↔ Sim | iPhone ↔ Sim |
| --- | --- | --- | --- | --- | --- |
| Discovery & Connect Time | `<= 3.0 s` | *TBD* | *TBD* | *TBD* | *TBD* |
| Avg / P95 End-to-End Latency | `<= 80 ms` / `<= 150 ms` | *TBD* | *TBD* | *TBD* | *TBD* |
| Measured FPS & Dropped Frames | `30/60 FPS`, `< 1%` drop | *TBD* | *TBD* | *TBD* | *TBD* |
| A/V Sync Drift | `<= 50 ms` | *TBD* | *TBD* | *TBD* | *TBD* |
| Battery Drain & Peak Temp | `< 12%/hr`, no thermal drop | *TBD* | *TBD* | *TBD* | *TBD* |
| Wi-Fi Interference Recovery | `< 5 s` | *TBD* | *TBD* | *TBD* | *TBD* |
| 30m / 60m / 120m Stability | `0` crashes / disconnects | *TBD* | *TBD* | *TBD* | *TBD* |
