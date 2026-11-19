# FINAL SECURITY REPORT — BYD Wireless Mirror

## 1. Cryptographic & Protocol Controls Verified by Executable Tests
- **Ephemeral ECDH P-256 + HKDF-SHA256 (`BYD-MIRROR-SESSION-KEY-V1`):** Executed via OpenSSL (`node:crypto`) across both Android X.509 SPKI (`91B`) and iOS raw X9.63 (`65B` + `P256_SPKI_PREFIX`), producing matching 256-bit AES keys and 6-digit visual pairing PINs.
- **AES-256-GCM with 16-Byte Big-Endian Header AAD:** Verified roundtrip including `0`-byte encrypted control payloads (`28B` wire size = `12B` nonce + `16B` tag), rejection of tampered AAD headers, and `0` nonce collisions across 200 fuzzed frames.
- **Unauthenticated UDP Media Gate:** Verified in live loopback UDP socket test and code (`BydReceiverServer.kt`, `simulator/main.go`) that packets arriving before `encryptionActive == true` or failing GCM authentication are silently dropped.
- **64-Bit Anti-Replay Window & Separated UDP/TCP Counters:** Verified 500-packet fuzzing in `crypto_fuzz_suite.mjs` (`AntiReplayWindow`) and separated `udpSequence` / `udpSequenceCounter` from TCP `sequence` in Android & iOS senders.
- **Fragmentation Bounds & Memory Exhaustion Protection:** Capped fragment totals (`<= 256`), bucket count (`<= 16`), and stale bucket age (`2s`) in `MediaReassembly.kt` and `simulator/main.go`.
- **Handshake Failure & PIN Timeout Hardening:** Added `1s–4s` backoff after `>= 3` failed handshakes, `goto done` disconnect on malformed `KEY_EXCHANGE_INIT` in `simulator/main.go`, and `60s` PIN confirmation window (`PAIRING_CONFIRM_TIMEOUT_MS`).

## 2. Privacy & Local Storage Controls
- Zero internet endpoints or telemetry servers; local LAN/P2P ports only (`47810`/`47811`/`47813`).
- `EncryptedSharedPreferences` (Android) / Keychain (iOS) for trusted vehicle fingerprints + one-tap "Clear Saved Vehicles" control.
