# FINAL BUILD EVIDENCE — BYD Wireless Mirror

## 1. Environment & Toolchain Inventory (Verified in Sandbox)
| Component | Status | Exact Command / Probe | Result |
| --- | --- | --- | --- |
| `tree-sitter` C AST Parsers (`go 0.25.0`, `kotlin 1.1.0`, `swift 0.7.4`) | **INSTALLED & EXECUTED** (`/tmp/ast-tools`) | `PYTHONPATH=/tmp/ast-tools python3 ...` | **PASS** — `files=70, syntax_errors=0` across `.kt`, `.kts`, `.swift`, `.go` |
| `node v22.22.3` (`--check` + `node:crypto` OpenSSL) | **AVAILABLE & EXECUTED** | `node --check` on `web/index.html` JS + `node byd-mirror/tests/crypto_fuzz_suite.mjs` | **PASS** — `16715 chars` valid JS + OpenSSL crypto/loopback suite |
| `python3 3.11.2` + `xml.etree` + `plistlib` | **AVAILABLE & EXECUTED** | `python3 byd-mirror/tests/verify_protocol.py` | **PASS** — `20` XML/Plist/Entitlements validated |
| Android Sender (`Gradle` / `JDK 17` / `Android SDK`) | **BLOCKED** | `which java javac kotlinc gradle` | Missing in sandbox; `deb.debian.org`, `dl.google.com`, `release-assets.githubusercontent.com` blocked by egress firewall |
| BYD Receiver (`Gradle` / `JDK 17` / `Android SDK`) | **BLOCKED** | `which java javac kotlinc gradle` | Same sandbox toolchain/egress block |
| iOS Sender (`Swift 5.10` / `XcodeGen` / `Xcode`) | **BLOCKED** | `which swift swiftc xcodebuild` | Linux x86_64 sandbox; macOS/Xcode required |
| Windows Simulator (`Go 1.22`) | **BLOCKED** | `which go` | Missing in sandbox; `go.dev` & `deb.debian.org` blocked |

## 2. Exact Manual Build Commands (Ready on Workstation / GitHub Actions)
- **Android Sender (`standard` + `fullTouch`):**
  `cd byd-mirror/android && ./gradlew testStandardDebugUnitTest lintStandardDebug assembleStandardRelease assembleFullTouchRelease`
- **BYD DiLink Receiver:**
  `cd byd-mirror/receiver-dilink && ./gradlew testDebugUnitTest lintDebug assembleRelease`
- **iOS Sender (macOS):**
  `cd byd-mirror/ios && swift test && xcodegen generate --spec Project-free.yml && xcodebuild -project BydWirelessMirrorFree.xcodeproj -scheme BydWirelessMirror -configuration Release -sdk iphoneos CODE_SIGNING_ALLOWED=NO build`
- **Windows/Web Simulator:**
  `cd byd-mirror/simulator && go test -race -v ./... && GOOS=windows GOARCH=amd64 go build -o BydScreenSimulator-Windows.exe .`
