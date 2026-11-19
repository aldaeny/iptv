# RECEIVER & GO SIMULATOR BUILD EVIDENCE — BYD Wireless Mirror

## 1. BYD DiLink Android Receiver (`DriveLink/receiver-dilink/`)
- **Local Gradle Build & `testDebugUnitTest`:** **BLOCKED** — no `JDK 17` / `Android SDK` / `Gradle` in Linux container.
- **Local AST, Security & Fixes Executed:**
  - `tree-sitter-kotlin 1.1.0` parsed all Receiver `.kt` files: **`0` syntax errors**.
  - Fixed `MainActivity.kt` `onDisconnect` running `sendControlFrame(OPCODE_SESSION_STOP)` on the UI Main Thread (`NetworkOnMainThreadException`) -> now runs on `lifecycleScope.launch(Dispatchers.IO)`.
  - Fixed `BydReceiverServer.kt`: `PAIRING_CONFIRM_TIMEOUT_MS = 60_000` after `CAPABILITIES_RESP`, awaited `prevJob?.join()` on session preemption, and added handshake brute-force backoff.
- **Historical Remote CI Reference (`c9b43b4` ONLY — Pre-Fixes):** `build-byd-receiver` succeeded in `2m26s` -> `BydMirror-DiLink-Receiver.apk` (`10,676,816` bytes).

## 2. Windows / Web Go Simulator (`DriveLink/simulator/`)
- **Local `go test -race` & `go build`:** **BLOCKED** — `go` compiler unavailable (`go.dev` & `deb.debian.org` blocked by egress firewall).
- **Local Verification Executed:**
  - `tree-sitter-go 0.25.0` AST parse across `main.go` and `simulator_test.go`: **`0` syntax errors**.
  - Fixed `simulator/main.go`: added 64-bit `AntiReplayWindow`, `goto done` disconnect on invalid `opKeyExchangeInit`, `60s` PIN confirmation deadline, and failed-handshake backoff.
  - Web UI (`simulator/web/index.html`): `node --check` passed (`16715 chars`), `extractAvcCodecFromSps` & `isKeyFrame` unit-tested in `crypto_fuzz_suite.mjs`, and `http://127.0.0.1:47812/` returned `HTTP 200`.
- **Historical Remote CI Reference (`c9b43b4` ONLY — Pre-Fixes):** `build-windows-simulator` succeeded in `31s` -> `BydScreenSimulator-Windows.exe` (`5,761,024` bytes).
