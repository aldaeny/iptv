# ANDROID GUIDE — DriveLink Cast Sender

## 1. Architecture & Flavors
- **`standard` (`BydMirror-Phone.apk`):** Ideal for Android 14+ devices where Google Play Protect Enhanced Fraud Protection flags sideloaded apps that declare `BIND_ACCESSIBILITY_SERVICE` or `SYSTEM_ALERT_WINDOW`.
- **`fullTouch` (`BydMirror-Phone-ReverseTouch.apk`):** Adds `MirrorOverlayService` and `BydTouchAccessibilityService` for reverse touch control from BYD DiLink screens.

## 2. Screen & Audio Capture Lifecycle (Android 8.0–15+)
- Uses `MediaProjection` inside `MirrorForegroundService` (`foregroundServiceType="mediaProjection"`).
- `ScreenCaptureEncoder.kt` registers `MediaProjection.Callback` before `createVirtualDisplay(...)` (mandatory on Android 14 / API 34+).
- Caches `BUFFER_FLAG_CODEC_CONFIG` (`SPS/PPS/VPS`) and prepends it to every `IDR` keyframe; sets `KEY_REPEAT_PREVIOUS_FRAME_AFTER = 100_000L` so static UI screens continue refreshing at 10+ FPS; automatically falls back from `H.265` to `H.264` if hardware HEVC encoding is unavailable.
- `AudioCaptureEncoder.kt` uses `AudioPlaybackCaptureConfiguration` on Android 10+ (`API 29+`) and falls back to `MediaRecorder.AudioSource.MIC` on Android 8.0–9.0 (`API 26–28`).

## 3. Networking & Recovery
- Discovers receivers via **NSD (`_byd-mirror._tcp.`)**, **UDP Discovery Beacon (`47813`)** (with 6.5s TTL expiry), **Wi-Fi Direct (`WifiP2pTransport`)**, and **Manual IP**.
- Streams over **UDP `47811`** and automatically fails over to encrypted **TCP `47810`** if `QOS_TELEMETRY` reports `0 FPS` after 15 sent frames.
- Reconnects silently up to 3 attempts (`1.5s`, `3.0s`, `5.0s`) on transient drops for trusted vehicles.
