# TEST RESULTS — BYD Wireless Mirror (Current Workspace State)

> Every entry below reflects only checks actually executed in this sandbox environment. Any check whose toolchain or hardware is absent in this container is explicitly marked `BLOCKED`.

| Test / Check | Scope | Command / Method | Result | Evidence |
| --- | --- | --- | --- | --- |
| **Protocol, Manifest & Security Gate Verification** | JSON, 20 XML/Plist/Entitlements, cross-platform opcodes, 28B empty AEAD gate, API34 callback, X9.63/SPKI parity, negative malformed packet tests | `python3 DriveLink/tests/verify_protocol.py` | **PASS** | `ALL VERIFICATION CHECKS PASSED! (XML/Plist validated: 20)` |
| **Simulator Web JS Syntax Check** | `<script>` WebCodecs (`VideoDecoder`, `AudioDecoder`, SPS parser, demo mode, touch) | `node -e "...new Function(m[1])"` | **PASS** | `Simulator web JS syntax: OK (16715 chars)` |
| **Web Preview HTTP Serving** | Local simulator web preview on `0.0.0.0:47812` | `curl -i -s http://127.0.0.1:47812/` | **PASS** | `HTTP/1.0 200 OK` (`26,951` bytes) |
| **Android Unit Tests & APK Build** | `testStandardDebugUnitTest assembleStandardRelease assembleFullTouchRelease` | Gradle / JDK 17 | **BLOCKED** | `java`, `javac`, `gradle`, and `ANDROID_HOME` are not installed in this sandbox container. |
| **Receiver Unit Tests & APK Build** | `testDebugUnitTest assembleRelease` | Gradle / JDK 17 | **BLOCKED** | `java` / `gradle` / Android SDK not installed in this sandbox container. |
| **iOS Unit Tests & IPA Build** | `swift test`, `xcodebuild` (`Project.yml`, `Project-free.yml`) | Swift / Xcode on macOS | **BLOCKED** | `swift` / `xcodebuild` / macOS not available in this Linux sandbox. |
| **Go Simulator Unit Tests & `.exe` Build** | `go test -v ./...`, `GOOS=windows go build` | Go 1.22 | **BLOCKED** | `go` compiler not installed in this sandbox container. |
| **Physical BYD DiLink / Phone Hardware Tests** | Live 1080p@60 streaming, latency, memory leak, audio sync | Physical devices | **BLOCKED** | No physical phones or BYD vehicle available in sandbox. |
