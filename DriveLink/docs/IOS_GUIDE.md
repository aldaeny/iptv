# IOS GUIDE — BYD Wireless Mirror Sender

## 1. Two Build Variants (Honest iOS Constraints)
| Variant | Spec | Apple ID Cost | Capture Scope |
| --- | --- | --- | --- |
| **`BydWirelessMirror-FreeSideload.ipa`** | `Project-free.yml` | **$0** (Free Apple ID via SideStore / AltStore / Sideloadly) | **In-app** via `RPScreenRecorder` (`DirectMirrorSession.swift`). Uses 1 App ID and empty entitlements (`BydMirror-free.entitlements`). |
| **`BydWirelessMirror-unsigned.ipa`** | `Project.yml` | Paid `$99/yr` Developer account or TrollStore | **System-wide** across third-party apps via `BroadcastExtension` (`SampleHandler.swift`) + Live Activity `WidgetExtension`. |

## 2. Documented iOS Platform Limitations (vs Android)
- **No external reverse touch injection:** iOS sandboxing prohibits third-party apps from injecting touch events into other apps; `TOUCH_EVENT (0x14)` is not dispatched on iOS.
- **No global floating overlay (`TYPE_APPLICATION_OVERLAY`):** Replaced by in-app Picture-in-Picture (`PipOverlayController.swift`) and `WidgetExtension` Live Activity.
- **No Wi-Fi Direct (`WifiP2pManager`):** Uses `Network.framework` (`includePeerToPeer = true`) + Bonjour (`_byd-mirror._tcp`) + UDP `47813` beacon listener.
- **Free Apple ID 3-App-ID Cap & `wifi-info` Restriction:** `Project-free.yml` strips App Extensions, App Groups, and `com.apple.developer.networking.wifi-info` so free sideloading succeeds without paid entitlements.
- **DRM Protection:** Protected streaming apps (Netflix, Shahid, OS-protected video) black out capture frames per iOS OS policy.

## 3. Video & Audio Encoding
- `HardwareVideoEncoder.swift` uses `VideoToolbox` (`VTCompressionSession`) with inline Annex-B parameter sets (`SPS/PPS` for H.264, `VPS/SPS/PPS` for HEVC) matching the active negotiated codec.
- Both `DirectMirrorSession.swift` and `SampleHandler.swift` automatically switch from UDP `47811` to encrypted TCP `47810` if `rxFps == 0` after 15 sent video frames.
