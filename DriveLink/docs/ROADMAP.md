# ROADMAP — DriveLink Cast

## Near-Term (Required for `GO — VERIFIED`)
1. Run full Gradle (`standard` & `fullTouch` + `receiver-dilink`), Swift/Xcode (`Project.yml` & `Project-free.yml`), and Go (`simulator`) builds & unit tests on the latest local commit.
2. Execute real-device tests: Android 14 ↔ Windows Simulator, iPhone ↔ Windows Simulator, Android ↔ BYD DiLink Receiver, and iPhone ↔ BYD DiLink Receiver.
3. Measure and log real end-to-end latency, FPS stability, thermal throttling, and 60-minute memory footprint in `REAL_HARDWARE_TESTS.md`.

## Mid-Term (Hardening & Ecosystem)
1. Add exponential backoff / temporary IP cooldown on repeated failed handshakes (`BydReceiverServer.kt`, `simulator/main.go`).
2. Add optional receiver-side "Confirm PIN / Always Trust" toggle on DiLink head unit UI.
3. Validate Track C deployment image on an external Android CarPlay AI Box for locked MG / Chery / Geely / Changan trims.
