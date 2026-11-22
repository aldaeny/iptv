# CHANGELOG — DriveLink Cast

## 2026-11-22 — Master Execution Prompt Phase 1–11 (unreleased)

- **Receiver lifecycle:** boot restore, stateful foreground notification, trusted-sender list with per-device revoke, and a `MirrorTransport` contract with LAN/Wi-Fi Direct/Miracast endpoints.
- **CI:** run [45058256950](https://github.com/aldaeny/iptv/actions/runs/45058256950) at `1b5138b` is green: verify, Android (47 tests + both APKs), Receiver (20 tests), iOS (6 tests + unsigned IPAs), Go.

- **Session:** added `SessionStateMachine` with generation guard; `Handshaking → Streaming` is illegal, and `SESSION_START` is now a request rather than an implicit start.
- **Capabilities:** added evidence-backed Android/iOS registries; CarPlay/Android Auto/USB/Bluetooth remain explicitly `UNSUPPORTED`.
- **Encoder:** live bitrate/FPS application via `ScreenCaptureEncoder.applyProfile` + `FrameRateGovernor`; resolution/codec changes restart once.
- **Touch:** `TouchGeometry` rotation mapping, real stroke duration, 512-point paths, and a streaming-only injection gate.
- **Receiver:** foreground service + process-wide owner; explicit Approve/Reject; single active phone; `DEVICE_IDENTITY 0x0A` verification with manual fallback.
- **Security:** Android Keystore / iOS Keychain installation identity, session-bound ECDSA proof, trust by `SHA-256(SPKI)`; no attestation claimed.
- **Protocol:** document version `1.2.0`, wire version `0x01`, new `0x0A`, approval/identity fields.
- **CI:** `verify_phase1_invariants.py` gate; Android builds on `arena/**`; iOS unsigned build on push while signing stays dispatch-only.
- **Evidence:** source gates pass locally; Gradle/Xcode/Swift/Go, emulator, device, vehicle and signing remain **BLOCKED**.

## Unreleased (Current Audit & Stabilization Pass on `arena/9ae6c195-iptv`)
- **Cross-Platform Interop Fix:** Added raw 65-byte X9.63 public key auto-detection in `ReceiverCryptoManager.kt` so iOS senders (`CryptoKit`) can pair with `BydMirror-DiLink-Receiver.apk`.
- **Receiver Stability & Controls:** Converted `BydReceiverServer.kt` to asynchronous preempting client handling, gated UDP/TCP media behind `encryptionActive`, added multi-NIC UDP `47813` beacon broadcasting, fixed `VideoAudioDecoder.kt` cross-clock A/V PTS drop bug + 3-byte Annex-B start code detection + HEVC fallback, and added Mute/Disconnect/Reject controls in `ReceiverScreen.kt`.
- **Android Sender Stability & UX:** Added `KEY_REPEAT_PREVIOUS_FRAME_AFTER = 100_000L` and `H265 -> H264` hardware fallback in `ScreenCaptureEncoder.kt`, `AudioRecord.STATE_INITIALIZED` guard in `AudioCaptureEncoder.kt`, non-cancelled disconnect cleanup in `MirrorForegroundService.kt`, TCP monotonic `lastRxSeq` anti-replay in `BydProtocolWrapper.kt`, UDP beacon 6.5s TTL pruning in `NsdDiscoveryManager.kt`, API 31+ `FLAG_INCLUDE_LOCATION_INFO` in `AutoConnectMonitor.kt`, and Vehicle Compatibility / Troubleshooting / Privacy dialogs inside `MirrorDashboardScreen.kt`.
- **iOS Sender Fixes:** Fixed `HardwareVideoEncoder.swift` immutable `isHevc` bug for H.264 Annex-B parameter sets and `UInt32` timestamp overflow (>71m uptime), added TCP disconnect callback in `PeerTransportClient.swift`, added UDP-to-TCP media fallback and TCP anti-replay in `DirectMirrorSession.swift` & `SampleHandler.swift`, and added beacon TTL expiry in `BonjourP2PDiscovery.swift`.
- **Simulator Security & Bounds:** Fixed `< encOverhead` (28B empty control payload decryption) and added `fragTotal <= 256` / max-16 bucket bounds + unencrypted UDP rejection in `DriveLink/simulator/main.go`.
- **Engineering Audit & Docs:** Created `AUDIT_BASELINE.md`, `COMPATIBILITY_STRATEGY.md`, `PROTOCOL_COMPATIBILITY_MATRIX.md`, `SECURITY_AUDIT.md`, `REAL_HARDWARE_TESTS.md`, and complete `docs/` suite.
