# Device Compatibility Matrix

> Evidence levels: **Implemented** < **Unit tested** < **CI built** < **Device tested** < **Vehicle tested**.
> Nothing here is `Device tested` or `Vehicle tested` unless a dated log is linked.

| Combination | Transport | Status | Evidence / blocker |
|---|---|---|---|
| Android phone → Android receiver | LAN/NSD | **CI built/tested** | Run `45060424962` at `87a72a2` (unsigned APKs); no device test |
| Android phone → Windows simulator | TCP/UDP | **CI built/tested** | Run `45060424962` (Go tests); no device/simulator session |
| iPhone → Android receiver | LAN/NSD | **CI built** | Unsigned IPA built in `45060424962`; no device test |
| Android phone → BYD DiLink 5.0 | LAN/NSD | Not tested | No vehicle |
| Android phone → BYD DiLink 4.0 | LAN/NSD | Not tested | No vehicle |
| Android phone → BYD DiLink 3.0 | LAN/NSD | Not claimed | Older stack may differ |
| iPhone → BYD DiLink 5.0 | LAN/NSD | Not tested | No vehicle |
| FullTouch reverse control | TCP `0x14` | Not verified | Capture test failed historically; device receipt pending |
| Miracast sink | Wi-Fi Display | Not verified | No vehicle |
| Wi-Fi Direct | P2P | Not verified | No two-device test |
| CarPlay | — | Unsupported | Apple entitlement/MFi absent |
| Android Auto | — | Unsupported | No projection category implementation |
| USB video | — | Unsupported | No implementation |
| Bluetooth video | — | Unsupported | No implementation |

## Known per-platform limits

- **iOS** cannot inject touches into other apps; reverse touch stays `UNSUPPORTED`.
- **iOS ReplayKit** requires the user to start a broadcast; background start is not available.
- **Android** `MediaProjection` requires per-session consent; Android 14+ requires a foreground
  service with `mediaProjection` type.
- **Receiver** advertises a fixed `1920x1080@60` capability today; per-head-unit display probing is
  a future improvement.
- **Older receivers** ignore `0x0A` and therefore always require manual approval.

## Acceptance criteria for upgrading a row

1. CI build passes on the exact commit.
2. Unit tests execute (not just compile).
3. A physical-device log with timestamp, model, Android/iOS version, and result is attached.
4. For vehicle rows, a BYD head-unit photo/log plus latency/FPS/thermal notes is attached.
