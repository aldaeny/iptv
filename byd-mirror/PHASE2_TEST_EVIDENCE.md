# PHASE 2 TEST EVIDENCE — BYD Wireless Mirror

> Only tests actually executed in this sandbox have `PASS` status. Every unexecuted compiler or hardware step is marked `BLOCKED`.

| # | Check / Test Suite | Exact Command | Outcome | Verifiable Output / Log Excerpt |
| --- | --- | --- | --- | --- |
| 1 | **Baseline Backup Creation** | `tar -czf /tmp/byd-mirror-phase2-backup-20261118T025449Z.tar.gz byd-mirror/` | **PASS** | `126K`, `206` files verified via `tar -tzf` |
| 2 | **Cross-Platform Protocol, Manifest & Negative Frame Suite** | `python3 byd-mirror/tests/verify_protocol.py` | **PASS** | `ALL VERIFICATION CHECKS PASSED! (XML/Plist validated: 20)` |
| 3 | **OpenSSL Cryptographic, Interop, Fuzz & WebCodecs Suite** | `node byd-mirror/tests/crypto_fuzz_suite.mjs` | **PASS** | `CRYPTO, INTEROP, FUZZ & WEBCODECS PARSER SUITE: ALL PASSED!` |
| 4 | **Source Structural Sanity Scan** | `python3` brace/paren balance across 70 `.kt/.swift/.go/.kts` files | **PASS** | `Checked 70 source files (.kt/.swift/.go/.kts)` (0 mismatches) |
| 5 | **Scope & Cleanliness Audit** | `git status --porcelain` + `TODO/FIXME` + secret scan | **PASS** | `Scope check: PASS (all changes strictly inside byd-mirror/)`, 0 TODOs, 0 secrets |
| 6 | **Web Simulator HTTP Preview** | `curl -s -o /dev/null -w "HTTP %{http_code}\n" http://127.0.0.1:47812/` | **PASS** | `HTTP 200` |
| 7 | **Android Sender Gradle Build & Unit Tests** | `./gradlew testStandardDebugUnitTest assembleStandardRelease assembleFullTouchRelease` | **BLOCKED** | `java`, `gradle`, `ANDROID_HOME` unavailable in sandbox |
| 8 | **BYD Receiver Gradle Build & Unit Tests** | `./gradlew testDebugUnitTest assembleRelease` | **BLOCKED** | `java`, `gradle`, `ANDROID_HOME` unavailable in sandbox |
| 9 | **iOS SwiftPM & Xcode Builds** | `swift test`, `xcodebuild ...` | **BLOCKED** | `swift`, `xcodebuild`, macOS unavailable in sandbox |
| 10 | **Go Simulator Tests & Windows `.exe` Build** | `go test -v ./...`, `GOOS=windows GOARCH=amd64 go build` | **BLOCKED** | `go` compiler unavailable in sandbox |
| 11 | **Physical Device & BYD Vehicle Field Tests** | Android / iPhone / Receiver / BYD DiLink | **BLOCKED** | No physical phones or BYD vehicle connected in sandbox |
