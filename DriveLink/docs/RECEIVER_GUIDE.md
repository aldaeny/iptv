# RECEIVER GUIDE — BYD DiLink Head-Unit Receiver (`DriveLink/receiver-dilink`)

## 1. Purpose
Runs directly on BYD DiLink (`3.0 / 4.0 / 5.0`) Android infotainment screens or external Android AI/HDMI boxes (`Track A` / `Track C`).

## 2. Core Features
- Advertises `_byd-mirror._tcp.` via NSD and broadcasts UDP `47813` discovery beacons across all active IPv4 broadcast interfaces every 1 second.
- Accepts both **Android (X.509 SPKI)** and **iOS (65-byte X9.63)** ECDH P-256 peers.
- Displays local LAN IPv4 addresses, 6-digit PIN + fingerprint verification card (with **Reject** button), and live fullscreen HUD (`FPS`, **Mute/Unmute**, **Disconnect**).
- Hardware `MediaCodec` video decoding (`H.264` / `H.265` with automatic capability fallback) onto `SurfaceView` + `AudioTrack` AAC-LC 48kHz stereo output.
- Emits normalized `TOUCH_EVENT (0x14)` coordinates from `SurfaceView` back to Android `fullTouch` senders.
