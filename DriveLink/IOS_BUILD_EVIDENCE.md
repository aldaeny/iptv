# iOS BUILD EVIDENCE — BYD Wireless Mirror

## 1. Local Sandbox Verification (Linux x86_64)
- **Native Build / `swift test` / `xcodebuild`:** **BLOCKED** — `swift`, `swiftc`, `xcodebuild`, `iOS SDK`, and `macOS` are unavailable in this Linux container. No fake Xcode build was attempted.
- **Compiler-Grade AST & Security Review Executed Locally:**
  - `tree-sitter-swift 0.7.4` parsed all Swift sources (`Sources/`, `Tests/`, `BroadcastExtension/`): **`0` syntax errors**.
  - `verify_protocol.py` validated `Info.plist` and all `.entitlements` XML files (`Project.yml` + `Project-free.yml`).
  - Fixed 3 real Swift bugs in this session:
    1. `PeerTransportClient.swift`: added `generation` guard to stop double `onDisconnected`/`onFailed` reconnect loops.
    2. `AudioAacEncoder.swift`: scoped `outputBuffer` inside `withUnsafeMutableBytes` for Swift 5.10 exclusivity safety.
    3. `BydProtocolWrapper.swift`: added `seq > 0` (`invalidSequence`) and `payloadLen <= 65_536` (`invalidPayloadLength`) checks + separated `udpSequence`.
    4. `BonjourP2PDiscovery.swift`: deduplicated unresolved `.local` Bonjour duplicates against resolved UDP beacon hosts.

## 2. Historical Remote macOS CI Reference (Baseline Commit `c9b43b4` ONLY — Pre-Fixes)
- **Workflow Run:** `https://github.com/aldaeny/iptv/actions/runs/44223614822` (`build-ios-check` on `macos-latest` succeeded in `2m44s`).
- **Remote Assets Produced at `c9b43b4` (Pre-Fixes):**
  - `BydWirelessMirror-FreeSideload.ipa`: `327,284` bytes (`Project-free.yml`, single App ID, in-app ReplayKit `RPScreenRecorder`)
  - `BydWirelessMirror-unsigned.ipa`: `674,002` bytes (`Project.yml`, main app + `BroadcastExtension` + App Group)

## 3. Exact macOS Build Commands Required for Current Working Tree
```bash
cd DriveLink/ios
swift test
xcodegen generate --spec Project.yml
xcodebuild -project BydWirelessMirror.xcodeproj -scheme BydWirelessMirror -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
xcodegen generate --spec Project-free.yml
xcodebuild -project BydWirelessMirrorFree.xcodeproj -scheme BydWirelessMirror -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```
