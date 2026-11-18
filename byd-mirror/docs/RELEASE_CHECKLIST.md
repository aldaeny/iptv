# RELEASE CHECKLIST — BYD Wireless Mirror

- [x] **Gate A — Source Integrity, AST & Backup:** All edits strictly inside `byd-mirror/`; 70 `.kt/.kts/.swift/.go` files AST-parsed cleanly via `tree-sitter` (`0` syntax errors); 20 XML/Plist valid; `0` TODOs/secrets; backup verified (`/tmp/byd-mirror-final-backup-20261118T025840Z.tar.gz`).
- [x] **Gate G — Security Verification:** Ephemeral ECDH P-256 + HKDF-SHA256 + AES-256-GCM `>=28B` + 64-bit `AntiReplayWindow` + handshake backoff verified (`crypto_fuzz_suite.mjs`).
- [x] **Gate H — Protocol & Live Loopback Interoperability:** Full TCP+UDP loopback handshake, SPKI + 65B X9.63 interop, out-of-order NALU reassembly, forged-packet drop, and WebCodecs SPS/IDR parser verified (`verify_protocol.py`, `crypto_fuzz_suite.mjs`).
- [ ] **Gates B, C, D, E — Native Builds:** `BLOCKED` in sandbox (`Gradle/JDK`, `Xcode/macOS`, `Go` absent).
- [ ] **Gate F — Native Unit Tests:** `BLOCKED` for JUnit/XCTest/`go test`; Python & Node suites **PASS**.
- [ ] **Gates I, J, K, L — Real Phones & BYD DiLink Vehicle:** `BLOCKED` (no physical hardware connected).

**Final Release Decision:** `CONDITIONAL GO` (`INTERNAL BETA / PROTOTYPE BUILD CANDIDATE ONLY`) — see `../FINAL_RELEASE_DECISION.md`.
