# AUDIT BASELINE — BYD Wireless Mirror (`byd-mirror/`)

## 1. Repository & Environment State (Verified)
- **Repository:** `aldaeny/iptv`
- **Target Branch:** `arena/9ae6c195-iptv`
- **Baseline Commit SHA (prior to audit fixes):** `c9b43b47413e7f57a1baecc33888418086a3db6d`
- **Local Safety Backup:** `/tmp/byd-mirror-baseline-c9b43b4.bundle` (`git bundle create`, verified `1.3M`)
- **Working Directory Scope:** Strictly confined to `byd-mirror/` (original IPTV files untouched).
- **Files in `byd-mirror/` at Baseline:** `110` files
- **Local Sandbox Toolchains:**
  - `python3` (`/usr/bin/python3`) — **AVAILABLE**
  - `node` (`/usr/local/bin/node`), `npm` (`/usr/local/bin/npm`) — **AVAILABLE**
  - `java` / `javac` / `kotlinc` / `gradle` — **NOT INSTALLED** in sandbox
  - `swift` / `swiftc` / `xcodebuild` — **NOT INSTALLED** in sandbox
  - `go` — **NOT INSTALLED** in sandbox
  - `adb` / physical car head unit — **NOT AVAILABLE** in sandbox

## 2. Subsystems, Languages, Build Systems & SDK Targets
| Subsystem | Path | Language | Build System | Target / Requirements |
| --- | --- | --- | --- | --- |
| **Android Sender** | `byd-mirror/android/` | Kotlin + Jetpack Compose | Gradle 8.10.2 / AGP 8.7.2 | `minSdk = 26` (Android 8.0), `targetSdk = 34` (Android 14), flavors: `standard`, `fullTouch` |
| **iOS Sender** | `byd-mirror/ios/` | Swift 5.9 + SwiftUI | SwiftPM (`Package.swift`) + XcodeGen (`Project.yml`, `Project-free.yml`) | iOS `16.2+`; `Project.yml` (3 targets), `Project-free.yml` (1 App ID free sideload) |
| **BYD DiLink Receiver** | `byd-mirror/receiver-dilink/` | Kotlin + Jetpack Compose | Gradle 8.10.2 / AGP 8.7.2 | `minSdk = 26`, `targetSdk = 34`, `MediaCodec` + `SurfaceView` + `AudioTrack` |
| **Windows / Web Simulator** | `byd-mirror/simulator/` | Go 1.22 + HTML5/WebCodecs | `go build` (`GOOS=windows GOARCH=amd64`) | Zero external Go deps; Chrome/Edge WebCodecs (`VideoDecoder`, `AudioDecoder`) |
| **Protocol & Tests** | `byd-mirror/shared/`, `byd-mirror/tests/` | JSON + Python 3 | `python3 byd-mirror/tests/verify_protocol.py` | Cross-platform constants & framing test |

## 3. CI Workflow Audit (`.github/workflows/byd-mirror-build.yml`)
- **Triggers:** `workflow_dispatch`, `push` on `arena/**` touching `byd-mirror/**` or workflow file.
- **Permissions:** `contents: write` (used by `publish-release` job to update `byd-mirror-latest` pre-release).
- **Jobs:** `verify-protocol`, `build-android-phone`, `build-byd-receiver`, `build-ios-check`, `build-windows-simulator`, `publish-release`.
- **Audit Note (Section 14):** Because `publish-release` runs automatically on pushes and uses `contents: write`, no `git push` or release trigger was executed during this local audit session.

## 4. Baseline Known Bugs & Missing Implementations Found by Code Inspection
1. **CRITICAL — `ReceiverCryptoManager.kt` rejected iOS peers:** Only accepted X.509 SPKI (91-byte) public keys; failed with `InvalidKeySpecException` on 65-byte raw X9.63 keys sent by iOS `CryptoKit`. *(Fixed in this pass)*
2. **CRITICAL — `BydReceiverServer.kt` blocked `accept()` synchronously:** Single half-open TCP client blocked all reconnect attempts. *(Fixed in this pass)*
3. **CRITICAL — `VideoAudioDecoder.kt` cross-clock A/V PTS drop:** `isFrameTooLateForSync` compared sender `AudioRecord` `System.nanoTime()` against `MediaProjection` EGL PTS, risking silent frame drops; 3-byte Annex-B start codes (`00 00 01`) were also missed in `isCodecConfigOnly`. *(Fixed in this pass)*
4. **CRITICAL — `HardwareVideoEncoder.swift` (iOS) H.264 SPS/PPS bug:** `convertSampleBufferToAnnexB` read immutable `self.isHevc` instead of negotiated codec, preventing H.264 SPS/PPS injection; `UInt32` timestamp conversion could crash after ~71 min uptime. *(Fixed in this pass)*
5. **CRITICAL — `simulator/main.go` 0-byte encrypted decrypt bug:** `len(wire) <= encOverhead` rejected 28-byte encrypted empty control frames (`SESSION_STOP`, `KEYFRAME_REQUEST`). *(Fixed in this pass)*
6. **HIGH — Security gaps:** `BydReceiverServer.kt` and `simulator/main.go` could accept unencrypted UDP frames before `encryptionActive == true`; `BydProtocolWrapper.kt` and iOS lacked TCP `lastRxSeq` monotonic anti-replay check; `FrameReassembler` in `simulator/main.go` lacked bucket/fragment count caps. *(Fixed in this pass)*
7. **HIGH — iOS UDP->TCP fallback & TCP disconnect recovery:** `PeerTransportClient.swift` silently returned on TCP EOF without notifying `DirectMirrorSession`/`SampleHandler`, and iOS lacked automatic UDP->TCP fallback on firewalled Wi-Fi. *(Fixed in this pass)*
8. **HIGH — Android static-screen 0 FPS & lifecycle cleanup:** `ScreenCaptureEncoder.kt` lacked `KEY_REPEAT_PREVIOUS_FRAME_AFTER` and `H265 -> H264` encoder fallback; `MirrorForegroundService.onDestroy()` cancelled `serviceScope` before `disconnect()` ran; `NsdDiscoveryManager.kt` & `BonjourP2PDiscovery.swift` never expired stale UDP beacons. *(Fixed in this pass)*

## 5. Local Verification Status at Baseline vs After Fixes
- `python3 byd-mirror/tests/verify_protocol.py`: **PASS** (expanded with negative/fuzz framing + cross-platform parity & security gate checks).
- Web Simulator JS syntax (`node`): **PASS** (`16,715` chars parsed cleanly).
- Native compilation (`Gradle` / `Xcode` / `Go`) in this sandbox: **BLOCKED** (compilers not installed locally; requires Android SDK/JDK, Xcode on macOS, and Go 1.22+).
- Live BYD vehicle head unit validation: **BLOCKED** (no physical BYD vehicle attached in sandbox).
