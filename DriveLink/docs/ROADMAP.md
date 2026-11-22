# ROADMAP — DriveLink Cast

## Near-Term (Required for `GO — VERIFIED`)
1. Run full Gradle (`standard` & `fullTouch` + `receiver-dilink`), Swift/Xcode (`Project.yml` & `Project-free.yml`), and Go (`simulator`) builds & unit tests on the latest local commit.
2. Execute real-device tests: Android 14 ↔ Windows Simulator, iPhone ↔ Windows Simulator, Android ↔ BYD DiLink Receiver, and iPhone ↔ BYD DiLink Receiver.
3. Measure and log real end-to-end latency, FPS stability, thermal throttling, and 60-minute memory footprint in `REAL_HARDWARE_TESTS.md`.

## Mid-Term (Hardening & Ecosystem)
1. Add exponential backoff / temporary IP cooldown on repeated failed handshakes (`BydReceiverServer.kt`, `simulator/main.go`).
2. Add optional receiver-side "Confirm PIN / Always Trust" toggle on DiLink head unit UI.
3. Validate Track C deployment image on an external Android CarPlay AI Box for locked MG / Chery / Geely / Changan trims.


## Phase 1–11 execution status (2026-11-22)

| Phase | Source | Native build/test | Device/vehicle |
| --- | --- | --- | --- |
| 1 Baseline | Done | Baseline CI `44966996220` only | — |
| 2 Unified shell | Done | **PASS** (CI `45056352610`) | — |
| 3 Session/capabilities | Done | **PASS** (43 Android tests) | — |
| 4 Encoder | Done (live apply + governor) | **PASS** (unit tests) | — |
| 5 FullTouch | Done (geometry/duration/gate) | **PASS** (unit tests) | BLOCKED |
| 6 Receiver lifecycle | Done (foreground service + approval) | **PASS** (20 tests) | BLOCKED |
| 7 Identity/security | Done (session-bound proof, no attestation) | **PASS** (unit tests) | BLOCKED |
| 8 iOS | Done (identity + capability registry) | **PASS** (6 tests, unsigned IPA) | BLOCKED |
| 9 Protocol/docs | Done (`1.2.0`, wire `0x01`) | — | — |
| 10 CI | Done (invariants; arena/iOS push builds) | **PASS** (`45056352610`) | — |
| 11 Docs | Done | — | — |

> CI build/test evidence now exists. The next real milestone is physical device/vehicle tests and
> signing; do not upgrade "CI built" to "Device tested / Vehicle tested" without L5/L6 evidence.
