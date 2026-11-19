# SECURITY AUDIT & THREAT MODEL — BYD Wireless Mirror

## 1. Threat Model & Attack Surface
- **Network Boundary:** Local car hotspot (`BYD-DiLink-*`), Wi-Fi Direct group (`DIRECT-BYD-*`), or shared home/office Wi-Fi LAN.
- **Exposed Ports:** TCP `47810` (control/handshake), UDP `47811` (media), UDP `47813` (discovery beacon), TCP `47812` (local simulator Web UI — simulator only).
- **Assets Protected:** Phone screen pixels, internal app audio, session keys, saved vehicle trust list, and accessibility touch injection (Android `fullTouch` only).

## 2. Cryptographic & Protocol Controls
| Control | Implementation | Verification |
| --- | --- | --- |
| **Key Exchange & Forward Secrecy** | Ephemeral **ECDH P-256** (`secp256r1`) generated per session; never reuses static ECDH keys. | Verified in `ChannelCryptoManager.kt`, `ChannelCryptoManager.swift`, `ReceiverCryptoManager.kt`, `simulator/main.go`. |
| **Key Derivation** | **HKDF-SHA256** with 16-byte `SecureRandom`/`SecRandom` salt and domain info `"BYD-MIRROR-SESSION-KEY-V1"`. | Cross-platform PIN & key match verified in `simulator_test.go`, `ReceiverCryptoTest.kt`, `BydMirrorCoreTests.swift`. |
| **Authenticated Encryption** | **AES-256-GCM** with fresh random 12-byte nonce per frame and full 16-byte header passed as **AAD** (prevents header/opcode tampering). | Verified in all 4 implementations. |
| **Zero-Payload Auth Tag Fix** | Fixed `<` vs `<=` (`>= 28` bytes) across Android, Receiver, and Simulator so 0-byte control frames (`KEYFRAME_REQUEST`, `SESSION_STOP`) authenticate cleanly. | Verified by unit tests + `verify_protocol.py`. |
| **PIN Authentication & No Static PIN** | Dynamic 6-digit PIN derived from `SHA-256(sessionKey)` (`23-bit` masked mod `1,000,000`), displayed on both sides before `SESSION_START`. | No hardcoded PINs anywhere in codebase. |
| **Trusted Device Storage** | Android: `EncryptedSharedPreferences` (`AES256_SIV` / `AES256_GCM` Keystore `MasterKey`); iOS: Keychain `kSecClassGenericPassword`. | Fallback to `MODE_PRIVATE` on Android only if hardware Keystore throws on legacy ROMs. |

## 3. Security Findings Fixed During This Audit
1. **HIGH — Unauthenticated UDP Media Before Handshake (`BydReceiverServer.kt`, `simulator/main.go`):**
   - *Before:* Both accepted unencrypted UDP `47811` media packets if `encryptionActive == false`.
   - *Fix:* Strictly drop all UDP `47811` and TCP media opcodes (`0x10`, `0x11`, `0x13`) whenever `!encryptionActive`.
2. **HIGH — Missing TCP Sequence Anti-Replay on Senders (`BydProtocolWrapper.kt`, `DirectMirrorSession.swift`, `SampleHandler.swift`):**
   - *Before:* Senders accepted repeated or out-of-order TCP control frames.
   - *Fix:* Enforced strict monotonic `packet.sequence > lastRxSeq` and `seq > 0` validation.
3. **HIGH — Blocking `accept()` & Half-Open Handshake Hang (`BydReceiverServer.kt`, `simulator/main.go`):**
   - *Before:* Slow/malformed TCP client blocked `server.accept()` indefinitely (Denial of Service).
   - *Fix:* Asynchronous preempting client handler + `10_000 ms` handshake `soTimeout` / read deadline.
4. **MEDIUM — Unbounded Fragment Reassembly Buffers (`simulator/main.go`):**
   - *Before:* `FrameReassembler` did not cap `fragTotal` or concurrent `buckets` map size.
   - *Fix:* Bounded `fragTotal <= 256` and max `16` concurrent pending frame buckets with oldest eviction.
5. **MEDIUM — Android 14+ Sideload Attack Surface (`DriveLink/android`):**
   - *Mitigation:* Split `standard` flavor (`BydMirror-Phone.apk`, no `SYSTEM_ALERT_WINDOW` or `BIND_ACCESSIBILITY_SERVICE`) from `fullTouch` (`BydMirror-Phone-ReverseTouch.apk`) and set `android:allowBackup="false"`.

## 4. Remaining Risks & Non-Bypassable Limitations
- **MITM Risk on First Pairing if User Skips PIN Check:** Just like Bluetooth/CarPlay numeric comparison, security of the first pairing depends on the user visually confirming the 6-digit PIN matches on both phone and car screen before tapping **Trust Vehicle**.
- **20-bit PIN Entropy vs Active Guessing:** A 6-digit PIN (`10^6` space) protects a single ephemeral ECDH session (because each reconnect generates a brand-new ECDH keypair and new PIN — offline brute force is impossible). Adding receiver-side exponential backoff after failed handshakes is tracked in `ROADMAP.md`.
- **Android Keystore Fallback:** If an older Android 8 ROM has a broken Keystore TEE, `TrustedVehiclesStore` falls back to app-private `MODE_PRIVATE` storage rather than crashing.
- **No Sensitive Logging:** Audited codebase for `Log.` / `print` of PINs, session keys, or private keys — none present.
