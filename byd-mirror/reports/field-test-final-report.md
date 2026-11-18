# BYD Wireless Mirror — Field Test & Release Certification Report

**Repository:** `aldaeny/iptv` (`byd-mirror/` subsystem)  
**Branch:** `arena/9ae6c195-iptv`  
**Verified Commit:** `51f471dbe5709deedb0fd4a0897c4a426983c28c`  
**Authorized CI Run:** [`44230417667`](https://github.com/aldaeny/iptv/actions/runs/44230417667) (all 5 jobs `success`)  
**Existing Release Status:** `byd-mirror-latest` (`c9b43b47413e7f57a1baecc33888418086a3db6d`) confirmed untouched  
**Code Freeze Status:** Zero source/workflow modifications during this certification run  
**BYD VEHICLE COMPATIBILITY:** **`NOT VERIFIED`**  
**Final Completion Score:** **`88%`** (`CI + Native Builds + Local Security/Protocol Verification Complete`; real hardware & vehicle field tests pending)  
**Final Release Decision:** **`CONDITIONAL GO — INTERNAL BETA`** (Strictly **NO-GO** for public production release until physical Android, iPhone, and BYD DiLink vehicle tests pass)

---

## Executive Summary

All automated build, lint, unit-test, cryptographic, protocol, and non-destructive stress-test gates passed on commit `51f471dbe5709deedb0fd4a0897c4a426983c28c` (`Run 44230417667`). However, the execution environment has **no physical Android phone, no physical iPhone, no BYD DiLink head unit, no USB/ADB/Xcode tooling (`HARDWARE_TOOLS=NONE`), and a network egress restriction blocking `productionresultssa19.blob.core.windows.net`** (preventing sandbox download of the CI ZIP artifacts).

Per certification rules, **no blocked or unexecuted physical test is marked as passed**. Physical device installation, real wireless streaming, touch/audio behavior, and BYD DiLink head-unit compatibility remain `BLOCKED` / `NOT VERIFIED` and must be executed by the user on real hardware using the checklist at the bottom of this report.

---

## Phase 1 — Artifact & Integrity Verification

### 1.1 CI Job Verification (`Run 44230417667`, Commit `51f471dbe5709deedb0fd4a0897c4a426983c28c`)

| Job | Job ID | Status | Duration | Runner |
| --- | --- | --- | --- | --- |
| `verify-protocol` | `134553782020` | `success` | `6s` | `ubuntu-latest` |
| `build-android-phone` | `134553836239` | `success` | `3m42s` | `ubuntu-latest` |
| `build-byd-receiver` | `134553836218` | `success` | `2m48s` | `ubuntu-latest` |
| `build-windows-simulator` | `134553836194` | `success` | `42s` | `ubuntu-latest` |
| `build-ios-check` | `134553836114` | `success` | `2m34s` | `macos-latest` |

### 1.2 GitHub Artifact Registry Verification (via `gh api`)

| Artifact Name | Artifact ID | Size (Bytes) | Expired | Expires At | Recorded SHA-256 Digest | Sandbox Download |
| --- | --- | ---: | --- | --- | --- | --- |
| `byd-protocol-crypto-logs` | `14395266094` | `407` | `false` | `2027-02-16T03:26:19Z` | `51a1fff684f117fd06c3aad44c9b89aa4f7a8e6aae0aeb07435fe4beb6e08776` | `BLOCKED_BY_NETWORK` (`EOF` on `blob.core.windows.net`) |
| `byd-phone-release-apk` | `14394693460` | `22,082,787` | `false` | `2027-02-16T03:26:19Z` | `189b7b02bafc3e78572255e714c5c5112656229f75b263547b1aaac686c25301` | `BLOCKED_BY_NETWORK` (`EOF` on `blob.core.windows.net`) |
| `byd-dilink-receiver-release-apk` | `14394582822` | `10,227,315` | `false` | `2027-02-16T03:26:19Z` | `80c08a11402e27560eaf2b605018de61aff4de85137a470d7959d79870e5ae32` | `BLOCKED_BY_NETWORK` (`EOF` on `blob.core.windows.net`) |
| `byd-windows-simulator` | `14393884897` | `4,918,957` | `false` | `2027-02-16T03:26:19Z` | `0dc461af46185debc9715e3ebda7b15bdd750170f47b759ef9b3983cc44bb310` | `BLOCKED_BY_NETWORK` (`EOF` on `blob.core.windows.net`) |
| `byd-ios-unsigned-ipa` | `14394857068` | `1,071,327` | `false` | `2027-02-16T03:26:19Z` | `63bd3233fef2e059fd6f125f03e88cab3cdad54ef7cc831855aecd500a86b8bd` | `BLOCKED_BY_NETWORK` (`EOF` on `blob.core.windows.net`) |

**Phase 1 Assessment:** GitHub metadata confirms all 5 artifacts are active, non-empty, and SHA-256-indexed. Inner archive extraction and `apksigner` / `aapt2` / `Payload` inspection in the sandbox are `BLOCKED` by firewall egress rules (`productionresultssa19.blob.core.windows.net` returns `EOF`).  
**Safe Alternative:** Download directly from `https://github.com/aldaeny/iptv/actions/runs/44230417667` (or `gh run download 44230417667`) on an unrestricted host and verify the bundled `SHA256SUMS-*.txt` files before sideloading.

### 1.3 Source-Level Build Configuration Facts

- **Android Phone (`byd-mirror/android/app/build.gradle.kts`):** `applicationId = "com.byd.mirror"`, `minSdk = 26`, `targetSdk = 34`, `compileSdk = 35`, `versionCode = 2`, `versionName = "1.0.1"`. `standard` (`ENABLE_OVERLAY_AND_TOUCH = false`) and `fullTouch` (`applicationIdSuffix = ".touch"`, `ENABLE_OVERLAY_AND_TOUCH = true`) release APKs use `signingConfigs.debug` (`enableV1Signing = true`, `enableV2Signing = true`, `isDebuggable = false`). Production distribution requires a dedicated release keystore.
- **BYD DiLink Receiver (`byd-mirror/receiver-dilink/app/build.gradle.kts`):** `applicationId = "com.byd.receiver"`, `minSdk = 26`, `targetSdk = 34`, `compileSdk = 35`, `versionCode = 2`, `versionName = "1.0.1"`, debug-key signed release build (`isDebuggable = false`).
- **iOS Sender (`byd-mirror/ios/Project.yml` & `Project-free.yml`):**
  - `BydWirelessMirror-unsigned.ipa`: 3 targets (`com.byd.mirror.app`, `com.byd.mirror.app.broadcast`, `com.byd.mirror.app.liveactivity`) for paid Apple Developer signing (`ReplayKit` Broadcast Extension + App Groups).
  - `BydWirelessMirror-FreeSideload.ipa`: 1 target (`com.byd.mirror.app`, `BydMirror-free.entitlements`) preserving single-App-ID compatibility for free Apple IDs (in-app capture only).

---

## Phases 2–6 — Physical Device, Wireless & Vehicle Field Tests

**Hardware Probe Result:** `HARDWARE_TOOLS=NONE` (`adb`, `fastboot`, `emulator`, `xcrun`, `idevice_id` unavailable; no USB/ADB/iOS/BYD devices attached).

| Phase | Scope | Status | Blocking Cause & Required Evidence |
| --- | --- | --- | --- |
| **Phase 2** | Android Phone Field Validation (`standard` & `fullTouch` install, MediaProjection, rotation, background/lock, Wi-Fi Direct / mDNS, Play Protect behavior) | **`BLOCKED`** | No physical Android phone or `adb` in sandbox; Play Protect prompt on sideloaded Accessibility/Overlay APKs must be observed on hardware. |
| **Phase 3** | BYD Receiver APK Validation (`com.byd.receiver` install on Android/DiLink, `MediaCodec` H.264/HEVC decode, `AudioTrack`, NSD `_bydmirror._tcp.`, full-screen, disconnect recovery) | **`BLOCKED`** | No Android tablet/head unit available locally. |
| **Phase 4** | Real Wireless Streaming Validation (Phone <-> Receiver/Simulator over Wi-Fi/Hotspot: PIN confirm, latency, frame drops, A/V sync, 10–15 min stability, thermal, reconnect) | **`BLOCKED`** | Requires two physical endpoints on real RF network; loopback TCP/UDP verified in CI and local suite only. |
| **Phase 5** | iOS Field Validation (Sign `BydWirelessMirror-FreeSideload.ipa` via AltStore/Sideloadly/Xcode; test PIN, VideoToolbox H.264, AAC audio, Bonjour, background, paid ReplayKit IPA if available) | **`BLOCKED`** | No macOS/Xcode/iPhone attached locally; free-Apple-ID AltStore/Sideloadly re-signing must be performed by user. |
| **Phase 6** | BYD Real Vehicle Validation (Sideload on BYD DiLink, hotspot/Wi-Fi connect, safe parked-only test, DiLink screen aspect ratio, audio routing, reverse-camera / DiLink interruption) | **`NOT VERIFIED` (`BLOCKED`)** | No BYD vehicle or DiLink unit available. Must ONLY be tested while legally parked (`P`). |

---

## Phase 7 — Non-Destructive Security & Stability Stress Results

Executed read-only against repository verification tools (`verify_protocol.py`, `crypto_fuzz_suite.mjs`) and a transient `/tmp/phase7_security_stress.mjs` harness (deleted immediately after run; zero workspace files modified):

| Test Scenario | Result | Observed Evidence |
| --- | --- | --- |
| Protocol & Manifest Static Verification (`verify_protocol.py`) | **`PASS`** | 20 XML/Plist files validated; `ContextCompat.registerReceiver` + `ACCESS_COARSE_LOCATION` (`maxSdkVersion="32"`) + protocol constants match across all 4 targets. |
| Crypto, Fuzz & Live Loopback Suite (`crypto_fuzz_suite.mjs`) | **`PASS`** | OpenSSL ECDH P-256 + HKDF-SHA256 + AES-256-GCM AAD tag verification, 64-packet `AntiReplayWindow` fuzzing, WebCodecs NAL/SPS parsing, live TCP+UDP encrypted loopback passed. |
| Invalid Pairing PIN / MITM Public-Key Tamper | **`PASS`** | Independent Mallory ECDH keys produce distinct 6-digit confirmation PINs on Sender and Receiver. |
| Invalid / Truncated / Random H.264 & HEVC NALUs | **`PASS`** | `extractAvcCodecFromSps` & `isKeyFrame` handle 0-byte, 3-byte, truncated SPS, and 4 KB random/0xFF buffers cleanly (`null`/`boolean`, no exceptions). |
| Unbounded Buffer / Fragment Flood (`10,000` `frameId`s) | **`PASS`** | Reassembly bucket map strictly bounded to `<= 16`; invalid `fragTotal > 256`, `fragTotal == 0`, `idx >= total`, and mismatched `total` rejected. |
| Malformed Packets (Bad Magic, Bad Version, Zero Seq, Oversized `> 65536`, Duplicate TCP Seq) | **`PASS`** | Server immediately terminates malformed/replay TCP connections without crashing or corrupting state. |
| Mid-Encryption Disconnect, Rapid Session Replacement & 25-Cycle Reconnect Storm | **`PASS`** | All 25 rapid ECDH reconnects + post-handshake tampered-ciphertext injections closed cleanly with zero leaked state or hung sockets. |

---

## Certification Gates (A–L)

| Gate | Requirement | Status | Evidence |
| --- | --- | --- | --- |
| **Gate A** | Clean repo, verified commit (`51f471d`), CI green (`Run 44230417667`), `byd-mirror-latest` untouched | **`PASS`** | `git rev-parse HEAD`, `gh run view 44230417667`, `gh release view byd-mirror-latest` |
| **Gate B** | All 5 CI artifacts present, non-expired, SHA-256 recorded | **`PASS`** *(local download `BLOCKED`)* | `gh api .../runs/44230417667/artifacts` (5/5 `expired: false`); Azure blob egress blocked in sandbox |
| **Gate C** | Protocol, crypto, fuzz, and non-destructive stress tests | **`PASS`** | `verify_protocol.py`, `crypto_fuzz_suite.mjs`, Phase 7 stress suite (`18/18` checks) |
| **Gate D** | Android phone build (`standard` & `fullTouch` lint + unit tests + release APKs) | **`PASS`** | CI `build-android-phone` (`134553836239`, `3m42s`) |
| **Gate E** | Android phone physical field test | **`BLOCKED`** | No physical Android phone / `adb` |
| **Gate F** | BYD receiver build (`testDebugUnitTest`, `lintDebug`, `assembleRelease`) | **`PASS`** | CI `build-byd-receiver` (`134553836218`, `2m48s`) |
| **Gate G** | BYD receiver physical playback test | **`BLOCKED`** | No physical Android / DiLink receiver device |
| **Gate H** | Real wireless streaming over RF Wi-Fi/Hotspot | **`BLOCKED`** | Requires 2 physical devices on real wireless LAN/P2P |
| **Gate I** | iOS Swift tests & both unsigned IPAs (`FreeSideload` + full) | **`PASS`** | CI `build-ios-check` (`134553836114`, `2m34s`) |
| **Gate J** | iOS physical device sideload & live stream test | **`BLOCKED`** | No physical iPhone / user Apple ID signing in sandbox |
| **Gate K** | Real BYD DiLink vehicle validation (parked safety, aspect ratio, audio, interruptions) | **`BLOCKED` (`NOT VERIFIED`)** | No BYD vehicle / DiLink unit |
| **Gate L** | Non-destructive security & stability resilience | **`PASS`** | Phase 7 stress suite passed cleanly |

---

## Completion & Final Release Decision

- **Evidence-Based Completion:** **`88%`**
  - Architecture, protocol, crypto, Android (`standard` + `fullTouch`), BYD receiver, iOS (`FreeSideload` + full), Windows/Web simulator, CI workflow, and local stress/fuzz verification: **Complete**.
  - Remaining **12%**: Physical Android installation/streaming (**4%**), physical iPhone AltStore/Sideloadly installation/streaming (**4%**), and real BYD DiLink vehicle validation (**4%**).
- **Decision:** **`CONDITIONAL GO — INTERNAL BETA`**
  - **Approved for:** Internal/developer beta testing with the user's own devices in a parked vehicle.
  - **NOT approved (`NO-GO`) for:** Public release, `byd-mirror-latest` replacement, or production distribution until Gates **E, G, H, J, K** pass on real hardware and production release signing replaces debug signing on Android APKs.

---

## User Field-Test Checklist (Real Hardware Validation)

> **Safety Warning:** Perform all BYD DiLink tests **only while the vehicle is safely parked (`P`)**. Never operate or watch mirrored video while driving.

1. **Download & Verify Artifacts (PC/Mac):**
   - Open `https://github.com/aldaeny/iptv/actions/runs/44230417667` (or run `gh run download 44230417667`) and verify the downloaded `.sha256` checksums.
2. **Android Phone (`byd-phone-release-apk`):**
   - Install `app-standard-release.apk` first (recommended for daily use). If Google Play Protect warns about unknown publisher (debug-signed beta), choose **Install anyway** only for your own internal beta test.
   - Start Mirror -> accept Screen Capture (`MediaProjection`) + Notifications/Location permissions -> test portrait/landscape rotation and backgrounding.
   - *(Optional)* Test `app-fullTouch-release.apk` only if remote touch injection is required (requires Overlay + Accessibility opt-in).
3. **BYD DiLink / Receiver (`byd-dilink-receiver-release-apk`):**
   - Sideload `app-release.apk` onto BYD DiLink (or a spare Android tablet first). Note whether BYD's OEM app installer blocks third-party APKs or requires BYD APK sideload procedure.
   - Connect Phone and BYD DiLink to the same Wi-Fi hotspot (phone hotspot or DiLink hotspot).
   - Confirm 6-digit pairing PIN matches on both screens before approving; verify 16:9/DiLink widescreen scaling, audio routing (Bluetooth vs DiLink speakers), and recovery after shifting into Reverse (`R` camera) or pressing Home.
4. **iPhone (`byd-ios-unsigned-ipa`):**
   - Sideload `BydWirelessMirror-FreeSideload.ipa` using AltStore or Sideloadly with your free Apple ID (1 App ID). (Use `BydWirelessMirror-unsigned.ipa` only with a paid Apple Developer account for system-wide ReplayKit Broadcast.)
   - Trust developer profile in **Settings -> General -> VPN & Device Management**, pair via 6-digit PIN, and verify video/audio/latency over hotspot.
5. **Report Results:**
   - Record device models, OS versions, BYD DiLink software version, average latency, any crashes/disconnects, and whether Gates **E, G, H, J, K** passed before deciding on a public release.
