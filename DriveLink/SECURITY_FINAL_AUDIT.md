# SECURITY FINAL AUDIT (INDEPENDENT REVIEW) — BYD Wireless Mirror

## 1. Scope of Independent Audit
Audited every cryptographic, transport, parsing, reassembly, storage, and permission boundary across Android Sender (`ChannelCryptoManager.kt`, `TrustedVehiclesStore.kt`, `BydProtocolWrapper.kt`, `MirrorForegroundService.kt`, `BydTouchAccessibilityService.kt`), BYD Receiver (`ReceiverCryptoManager.kt`, `BydReceiverServer.kt`, `MediaReassembly.kt`, `MainActivity.kt`), iOS Sender (`BydProtocolWrapper.swift`, `PeerTransportClient.swift`, `SampleHandler.swift`, `DirectMirrorSession.swift`, `KeychainTrustedVehiclesStore.swift`), and Simulator (`main.go`).

## 2. Newly Uncovered & Fixed Security/Stability Findings (This Execution)
1. **Unbounded/Main-Thread `SESSION_STOP` Drop (`receiver-dilink/.../MainActivity.kt`):**
   - *Finding:* `onDisconnect = { server.disconnectActiveSession() }` ran TCP `sendControlFrame` on the UI Main Thread, triggering `NetworkOnMainThreadException` (swallowed by `runCatching`), preventing clean `SESSION_STOP` transmission.
   - *Fix:* Wrapped in `lifecycleScope.launch(Dispatchers.IO)`. Verified via `verify_protocol.py`.
2. **Missing Sequence & Length Bounds on iOS Frame Decoder (`BydProtocolWrapper.swift`):**
   - *Finding:* `decodeFrame` accepted `seq == 0` and unbounded `payloadLen` if invoked outside `PeerTransportClient`.
   - *Fix:* Added `seq > 0 && seq <= UInt32(Int32.max)` (`invalidSequence`) and `payloadLen <= 65_536` (`invalidPayloadLength`). Verified via `verify_protocol.py`.
3. **Malformed `KEY_EXCHANGE_INIT` Left TCP Loop Open (`simulator/main.go`):**
   - *Finding:* `case opKeyExchangeInit:` used `break` instead of `goto done` on handshake error.
   - *Fix:* Changed to `goto done` so invalid keys immediately close the socket and increment `failedHandshake`.
4. **Thread-Unsafe `Path` Mutation on Reverse Touch (`BydTouchAccessibilityService.kt`):**
   - *Finding:* `Dispatchers.IO` mutated `android.graphics.Path` directly without main-looper serialization or move cap.
   - *Fix:* Serialized on `Handler(Looper.getMainLooper())` with `MAX_MOVE_POINTS = 128`.

## 3. Verified Security Controls (Active & Tested)
- **ECDH P-256 + HKDF-SHA256 + AES-256-GCM (16B Header AAD, `>=28B` empty payload):** Verified in OpenSSL (`crypto_fuzz_suite.mjs`).
- **64-bit `AntiReplayWindow` & Separated UDP/TCP Sequence Counters:** Verified over 500 fuzzed packets (`crypto_fuzz_suite.mjs`).
- **Unauthenticated UDP Drop + Memory Bounds (`<=256` frags, `<=16` buckets):** Verified in live loopback UDP socket test (`crypto_fuzz_suite.mjs`).
- **Zero Hardcoded Secrets / Zero External Servers:** Confirmed across all files (`DriveLink/` only).
