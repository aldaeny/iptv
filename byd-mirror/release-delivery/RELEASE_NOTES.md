# BYD Wireless Mirror — Release Notes (v1.0.1 / Branch `arena/b9db39ae-iptv`)

## Highlights

- **Dark Luxury Automotive UI/UX Redesign:**
  - Unified `#050914` Deep Navy, `#00C8FF` Electric Cyan, and `#00D084` Emerald Green theme across Android Phone, BYD DiLink Receiver, iOS, and Windows/Web Simulator.
  - Custom App Icon (`Phone -> Wireless Waves -> EV`) exported across Android adaptive/monochrome/legacy mipmaps (`mdpi`–`xxxhdpi`), iOS `Assets.xcassets`, and Simulator web icons.
  - Separate landscape automotive HMI in `byd-mirror/receiver-dilink` with persistent `SurfaceView`, left sidebar navigation, large touch targets, audio mute toggle, and ECDH 6-digit PIN overlay.
  - Strictly authentic state & telemetry exposure (real `ConnectionState`, real `FPS`/`RTT`/`Bitrate`/`Loss`, real `BuildConfig` version/edition, zero fabricated devices or progress percentages).
- **Verified Code Fixes:**
  - Fixed Android `MirrorDashboardScreen.kt` modifier syntax and simplified layout hierarchy.
  - Added scroll protection to `ConnectingScreen.kt` and `ReceiverScreen.kt` for smaller displays.
  - Added iOS timer cleanup on disappear and active device card parity in `Devices` tab.
  - Added WebSocket auto-reconnect, WebCodecs availability check, and pointercancel cleanup in `simulator/web/index.html`.
- **Protocol & Security Preserved:**
  - `BYD-WMP v1.0` (`TCP 47810`, `UDP 47811`, `UDP 47813`), ECDH P-256 + HKDF-SHA256 + AES-256-GCM untouched and verified passing.
