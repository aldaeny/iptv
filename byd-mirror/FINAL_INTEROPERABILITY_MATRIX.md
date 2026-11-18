# FINAL INTEROPERABILITY MATRIX — BYD Wireless Mirror

| Sender | Receiver | AST / Protocol / Crypto Status | Build Status | Live Connection / Video / Audio / FPS / Latency |
| --- | --- | --- | --- | --- |
| **Android** | **BYD DiLink Receiver** | **PASS** (`tree-sitter` 0 errs, `verify_protocol.py`, `crypto_fuzz_suite.mjs` SPKI+AES-GCM+loopback) | **BLOCKED** (no local JDK/SDK) | **BLOCKED** (requires rebuilt APK + physical devices/car) |
| **iPhone** | **BYD DiLink Receiver** | **PASS** (`tree-sitter` 0 errs, `verify_protocol.py`, `crypto_fuzz_suite.mjs` 65B X9.63↔SPKI) | **BLOCKED** (no macOS/Xcode/JDK) | **BLOCKED** (requires rebuilt IPA/APK + physical iPhone/car) |
| **Android** | **Windows Simulator** | **PASS** (`tree-sitter` 0 errs, WebCodecs SPS/IDR parser + loopback UDP frag passed) | **BLOCKED** (no local JDK/Go) | Pre-fix run `e0affbf` reached `LIVE`/`139ms` (`image-7/8`); post-`MirrorForegroundService` fix **BLOCKED** pending rebuild |
| **iPhone** | **Windows Simulator** | **PASS** (`tree-sitter` 0 errs, 65B X9.63 ECDH + Annex-B `codecIsHevc` verified) | **BLOCKED** (no Xcode/Go) | **BLOCKED** (requires rebuilt IPA + `.exe` + physical iPhone) |
| **Android** | **Android Receiver (Generic)** | **PASS** (same `BydMirror-DiLink-Receiver.apk` + MediaCodec HEVC fallback) | **BLOCKED** (no local JDK/SDK) | **BLOCKED** (requires 2 physical Android devices) |
