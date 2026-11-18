# PROTOCOL COMPATIBILITY MATRIX (`BYD-WMP v1.0`)

| Feature / Field | Canonical Spec (`byd_mirror_protocol.json`) | Android Sender (`byd-mirror/android`) | iOS Sender (`byd-mirror/ios`) | BYD Receiver (`byd-mirror/receiver-dilink`) | Windows Simulator (`byd-mirror/simulator`) | Parity Status |
| --- | --- | --- | --- | --- | --- | --- |
| **Magic & Version** | `0x4259`, `v0x01`, 16B BE | `0x4259`, `v0x01`, 16B BE | `0x4259`, `v0x01`, 16B BE | `0x4259`, `v0x01`, 16B BE | `0x4259`, `v0x01`, 16B BE | **PASS** |
| **Ports** | TCP `47810`, UDP `47811`, Beacon `47813` | TCP `47810`, UDP `47811`, Beacon `47813` | TCP `47810`, UDP `47811`, Beacon `47813` | TCP `47810`, UDP `47811`, Beacon `47813` | TCP `47810`, UDP `47811`, Beacon `47813`, Web `47812` | **PASS** |
| **Discovery** | `_byd-mirror._tcp.` + Beacon `47813` | NSD + UDP `47813` (TTL 6.5s) + Wi-Fi Direct | Bonjour + UDP `47813` (TTL 6.5s) | NSD + Multi-NIC UDP `47813` | mDNS + Multi-NIC UDP `47813` | **PASS** |
| **Key Exchange Format** | ECDH P-256 + 16B Salt + HKDF-SHA256 | X.509 SPKI (91B) | Raw X9.63 (65B `0x04..`) | Auto-detects SPKI & X9.63 | Auto-detects SPKI & X9.63 | **PASS** |
| **Cipher & 0B Payload** | AES-256-GCM (12B nonce, 16B tag = 28B min) | `size >= 28` (`ChannelCryptoManager.kt`) | `AES.GCM.SealedBox` | `size >= 28` (`ReceiverCryptoManager.kt`) | `len(wire) >= 28` (`main.go`) | **PASS** |
| **PIN Derivation** | `SHA256(key)[0..2] & 0x7FFFFF % 1_000_000` | Identical | Identical | Identical | Identical | **PASS** |
| **Video Codec & Annex-B** | H.264 / H.265 Annex-B | `MediaCodec` H.264 default + H.265 fallback, SPS/PPS prepended to IDR | `VideoToolbox` H.264 default + H.265, inline SPS/PPS/VPS | `MediaCodec` H.264 / H.265 (auto capability check) | WebCodecs dynamic SPS `avc1.PPCCLL` / `hvc1` | **PASS** |
| **MTU Fragmentation** | `0x13` (`<= 1150B` UDP / `32000B` TCP) | `VIDEO_NALU_FRAG` UDP + TCP | `encodeVideoUdpFrames` + `encodeVideoTcpFrames` | `UdpFrameReassembler` (bounded 12 frames / 256 frags) | `FrameReassembler` (bounded 16 frames / 256 frags) | **PASS** |
| **UDP -> TCP Fallback** | Seamless encrypted TCP media | Auto-switches if `rxFps == 0` after 15 frames | Auto-switches if `rxFps == 0` after 15 frames | Accepts TCP & UDP media (`encryptionActive` required) | Accepts TCP & UDP media (`IsActive()` required) | **PASS** |
| **Audio Stream** | AAC-LC 48kHz Stereo (`0x11`) | `AudioPlaybackCapture` / MIC `<API 29` | `AudioToolbox` `AudioConverter` | `MediaCodec` + `AudioTrack` (`csd-0 = 0x11, 0x90`) | WebCodecs `AudioDecoder` (`mp4a.40.2`) | **PASS** |
| **Reverse Touch (`0x14`)** | `[4B x][4B y][1B action]` | `fullTouch` flavor (`AccessibilityService`); omitted in `standard` | Not supported by iOS OS policy | Emits normalized touch from `SurfaceView` | Emits normalized touch from `<canvas>` | **PASS (with OS/flavor limits)** |
| **Anti-Replay** | Monotonic TCP `seq` + 64-pkt UDP window | `lastRxSeq` monotonic check | `lastRxSeq` monotonic check | `lastTcpSeq` + `AntiReplayWindow` (64-pkt) | `lastSeq` + UDP replay guard | **PASS** |
