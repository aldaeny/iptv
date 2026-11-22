# TEST RESULTS — BYD Wireless Mirror (Current Workspace State)

> Every entry below reflects a check actually executed, locally or on GitHub Actions. Hardware-only checks (real phone/vehicle) and distribution signing are explicitly marked `BLOCKED`; nothing is inferred from source inspection.

| Test / Check | Scope | Command / Method | Result | Evidence |
| --- | --- | --- | --- | --- |
| **Protocol, Manifest & Security Gate Verification** | JSON, 28 XML/Plist/Entitlements, cross-platform opcodes, 28B empty AEAD gate, API34 callback, X9.63/SPKI parity, negative malformed packet tests | `python3 DriveLink/tests/verify_protocol.py` | **PASS** | `ALL VERIFICATION CHECKS PASSED! (XML/Plist validated: 28)` |
| **Phase 1–11 Invariants** | Session ordering, approval gate, capability states, encoder live reconfig, touch gate, identity proof, protocol 1.2.0 | `python3 DriveLink/tests/verify_phase1_invariants.py` | **PASS** | `PHASE 1-11 INVARIANTS PASSED` |
| **Session / Encoder / Touch / Identity Unit Tests** | 6 session + 6 encoder + 4 touch + 3 identity + 4 capability tests (47 Android total) | Gradle `testStandardDebugUnitTest` / `testFullTouchDebugUnitTest` | **PASS** | Run [45060424962](https://github.com/aldaeny/iptv/actions/runs/45060424962) — both flavor test tasks succeeded |
| **Receiver Identity / Approval Unit Tests** | 5 verifier tests; approval gate/trust revoke source-checked | Gradle `testDebugUnitTest` | **PASS** | Run [45060424962](https://github.com/aldaeny/iptv/actions/runs/45060424962) |
| **iOS Capability / Shared Store Tests** | 2 XCTest cases added (7 total) | `swift test` | **PASS** | Run [45060424962](https://github.com/aldaeny/iptv/actions/runs/45060424962) |
| **Simulator Web JS Syntax Check** | `<script>` WebCodecs (`VideoDecoder`, `AudioDecoder`, SPS parser, demo mode, touch) | `node -e "...new Function(m[1])"` | **PASS** | `Simulator web JS syntax: OK (16715 chars)` |
| **Web Preview HTTP Serving** | Local simulator web preview on `0.0.0.0:47812` | `curl -i -s http://127.0.0.1:47812/` | **PASS** | `HTTP/1.0 200 OK` (`26,951` bytes) |
| **Android Unit Tests & APK Build** | 47 tests, `assembleStandardRelease`, `assembleFullTouchRelease`, lint | CI Gradle / JDK 17 | **PASS** | Run [45060424962](https://github.com/aldaeny/iptv/actions/runs/45060424962) — standard 12,294,357 B SHA-256 `20ac8fe0…`, fullTouch 12,294,581 B SHA-256 `f6afeed7…` |
| **Receiver Unit Tests & APK Build** | 20 tests, `assembleRelease`, lint | CI Gradle / JDK 17 | **PASS** | Run [45060424962](https://github.com/aldaeny/iptv/actions/runs/45060424962) — receiver 11,421,542 B SHA-256 `5762bf09…` |
| **iOS Unit Tests & IPA Build** | 7 XCTest cases, unsigned Full + Free IPA | CI macOS / Xcode | **PASS (unsigned only)** | Run [45060424962](https://github.com/aldaeny/iptv/actions/runs/45060424962) — `swift test` and both `xcodebuild` targets succeeded; signed IPA still BLOCKED |
| **Go Simulator Unit Tests & `.exe` Build** | `go vet`, `go test -race ./...`, Windows/Linux builds | CI Go 1.22 | **PASS** | Run [45060424962](https://github.com/aldaeny/iptv/actions/runs/45060424962) verify job |
| **CI native build & unit tests** | verify + Android + Receiver + iOS + Go on the exact commit | GitHub Actions | **PASS** | Run [45060424962](https://github.com/aldaeny/iptv/actions/runs/45060424962) at `87a72a2` |
| **Physical BYD DiLink / Phone Hardware Tests** | Live 1080p@60 streaming, latency, memory leak, audio sync | Physical devices | **BLOCKED** | No physical phones or BYD vehicle available in sandbox. |
