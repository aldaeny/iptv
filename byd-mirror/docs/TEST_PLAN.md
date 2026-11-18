# TEST PLAN — BYD Wireless Mirror

## 1. Automated Verification Layers
1. **Cross-Platform Protocol & Security Gate Suite (`byd-mirror/tests/verify_protocol.py`):**
   - XML, AndroidManifest, Info.plist, and `.entitlements` syntax checks.
   - Parity checks across `byd_mirror_protocol.json`, Android, iOS, Receiver, and Simulator.
   - Binary frame roundtrip, out-of-order `VIDEO_NALU_FRAG (0x13)`, `TOUCH_EVENT (0x14)`, and negative/malformed packet rejection.
2. **Android Unit Tests (`byd-mirror/android`):**
   - `ChannelCryptoManagerTest.kt`: ECDH P-256 + HKDF-SHA256 + AES-256-GCM + 28B zero-payload decrypt.
   - `BydProtocolWrapperTest.kt`: 16-byte Big-Endian headers, MTU fragmentation (`0x13`), touch (`0x14`), replay rejection.
   - `QualityNegotiatorTest.kt`: 15.6"/12.8"/10.1" profile selection and thermal throttling.
3. **BYD Receiver Unit Tests (`byd-mirror/receiver-dilink`):**
   - `ReceiverCryptoTest.kt`: SPKI (Android) & 65B X9.63 (iOS) ECDH interoperability + 28B zero-payload decrypt.
   - `BydReceiverServerTest.kt`: 64-packet `AntiReplayWindow` + out-of-order `UdpFrameReassembler`.
4. **iOS Core Tests (`byd-mirror/ios`):**
   - `BydMirrorCoreTests.swift`: X9.63 key exchange, AES-GCM roundtrip, thermal throttling, MTU fragmentation.
5. **Simulator Go Tests (`byd-mirror/simulator`):**
   - `simulator_test.go`: Both X9.63 & SPKI key exchange, PIN parity, empty-payload AES-GCM, out-of-order reassembly.
6. **Hardware & Stress Gate (`REAL_HARDWARE_TESTS.md`):**
   - 30/60/120 min streaming stability, 720p/1080p @ 30/60 FPS, rotation, call/Wi-Fi interruption recovery, and physical BYD DiLink testing.
