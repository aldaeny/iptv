# RELEASE CHECKLIST — DriveLink Cast

- [x] **Gate A — Source Integrity, AST & Backup:** All edits strictly inside `DriveLink/`; 70 `.kt/.kts/.swift/.go` files AST-parsed cleanly via `tree-sitter` (`0` syntax errors); 20 XML/Plist valid; `0` TODOs/secrets; backup verified (`/tmp/byd-mirror-final-backup-20261118T025840Z.tar.gz`).
- [x] **Gate G — Security Verification:** Ephemeral ECDH P-256 + HKDF-SHA256 + AES-256-GCM `>=28B` + 64-bit `AntiReplayWindow` + handshake backoff verified (`crypto_fuzz_suite.mjs`).
- [x] **Gate H — Protocol & Live Loopback Interoperability:** Full TCP+UDP loopback handshake, SPKI + 65B X9.63 interop, out-of-order NALU reassembly, forged-packet drop, and WebCodecs SPS/IDR parser verified (`verify_protocol.py`, `crypto_fuzz_suite.mjs`).
- [x] **Gates B, E, F — Native Builds:** **PASS (unsigned)** in CI run `45058936142` at `9f4df72` (Android Standard/FullTouch/Receiver APKs, iOS Full/Free IPAs, Windows/Linux simulator).
- [x] **Gate F — Native Unit Tests:** **PASS** in the same CI run: 47 Android JVM tests, 20 receiver tests, 6 XCTest cases, Go `-race`; Python & Node suites pass locally.
- [ ] **Gates C, D, I, J, K, L — Install/Runtime/Real Hardware:** `BLOCKED` (no adb device, emulator, physical phone or BYD vehicle; signing inputs absent).

**Final Release Decision:** `CONDITIONAL GO` (`INTERNAL BETA / PROTOTYPE BUILD CANDIDATE ONLY`) — see `../FINAL_RELEASE_DECISION.md`.
