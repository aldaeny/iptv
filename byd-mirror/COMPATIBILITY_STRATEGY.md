# COMPATIBILITY STRATEGY — Wireless Screen Mirroring for Chinese Vehicles

> **Rule:** Never assume all Chinese vehicles speak one protocol. Each track below is strictly separated by whether a receiver app can be installed on the vehicle infotainment unit.

## 1. Compatibility Paths Overview

| Method | Receiver Required? | Phone Support | Vehicle Support | Limitations |
| --- | --- | --- | --- | --- |
| **Track A — BYD-WMP App-to-App** (`BydMirror-Phone` + `BydMirror-DiLink-Receiver`) | **Yes** (`BydMirror-DiLink-Receiver.apk` installed on car head unit) | Android 8.0+ (`minSdk 26`), iOS 16.2+ (`FreeSideload` or full `BroadcastExtension`) | Open Android-based head units: **BYD DiLink 3.0 / 4.0 / 5.0**, open Android aftermarket units, Chery/Geely/Changan/MG units **only if** ADB/USB APK sideloading is unlocked | Requires APK installation on head unit; Play Protect / OEM installer restrictions on locked dealer firmware; iOS forbids external reverse touch |
| **Track B — Native Phone-Only (OEM Protocol, No Receiver)** | **No** | Android (Miracast / WFD / Wireless Android Auto), iOS (AirPlay / Wireless CarPlay) | Only cars whose factory OS natively enables Miracast sink (`MiracastDisplayAdapter`), Wireless CarPlay, Wireless Android Auto, or OEM projection (e.g. CarLife/HiCar/ICCOA) | **Closed OEM protocols (CarLife, HiCar, ICCOA, locked CarPlay/AA) cannot be legally/technically impersonated without OEM SDK keys.** Android `MiracastDisplayAdapter` works only on head units exposing a standard Wi-Fi Display sink (many BYD/Chery factory builds disable Miracast sink or restrict it). |
| **Track C — External Android/HDMI/Carlinkit Box Receiver** | **External Box** (install `BydMirror-DiLink-Receiver.apk` on Android AI Box / HDMI stick) | Android 8.0+, iOS 16.2+ | **All** BYD, MG, Chery/Jetour, Geely/Zeekr, Changan/Deepal/Avatr with **CarPlay/Android Auto USB port** (using Android AI Box) or **HDMI input** | Requires external hardware (`~$30–$90` Android AI Box or HDMI-to-Android stick); reverse touch depends on CarPlay/AA HID bridge of the box |
| **Track D — Existing Open Standard Fallback (Miracast / DLNA / WebRTC Browser)** | **Browser or Built-in Sink** | Android (`MiracastDisplayAdapter`), Web/Browser (`BydScreenSimulator` / browser URL) | Any Chinese EV head unit with an unlocked **built-in Chromium browser** or **Miracast Sink** | Chromium on car head units often throttles WebCodecs/WebSockets while driving (`driving safety lock`); Miracast does not support BYD-WMP reverse touch or PIN pairing |

## 2. Priority Matrix by Brand (Honest Technical Assessment)

| Brand | Infotainment Platform | Recommended Track | Can Install Receiver APK? | Real Hardware Tested? |
| --- | --- | --- | --- | --- |
| **BYD (DiLink 3.0 / 4.0 / 5.0)** | Android-based DiLink (rotating 10.1" / 12.8" / 15.6") | **Track A** (primary), **Track C** (if dealer-locked) | Usually Yes via USB APK or ADB (varies by region/OTA firmware) | **NO** (Only Windows Simulator tested in user photos `image-5..8`; real BYD car = **BLOCKED**) |
| **MG (iSmart / Venus OS)** | Linux/QNX or locked Android depending on trim/year | **Track C** (AI Box) or **Track B** (CarPlay/AA) | Usually Locked on global/GCC trims | **NO** (`BLOCKED`) |
| **Chery / Jetour / Exeed** | Lion OS / Snapdragon Android | **Track A** (where APK unlock exists) or **Track C** | Trim-dependent | **NO** (`BLOCKED`) |
| **Geely / Zeekr** | GKUI / Zeekr OS / Flyme Auto | **Track A** (open GKUI) or **Track C** | Restricted on Flyme Auto / Zeekr OS | **NO** (`BLOCKED`) |
| **Changan / Deepal / Avatr** | HarmonyOS / Deepal OS / Android | **Track C** or **Track A** (open Android trims) | Restricted on HarmonyOS / newer trims | **NO** (`BLOCKED`) |

## 3. Practical Engineering Conclusion
- **Track A** (`byd-mirror/android` + `byd-mirror/ios` ↔ `byd-mirror/receiver-dilink`) is fully implemented and verified at the protocol/code level, and works against `byd-mirror/simulator/` over real Wi-Fi LAN.
- **Track B** (`MiracastDisplayAdapter.kt`) is implemented for Android sinks that advertise Wi-Fi Display (`WIFI_DISPLAY_SERVICE` / `MediaRouter`), without claiming support for proprietary closed protocols (CarPlay/HiCar/CarLife) that require licensed MFi/OEM keys.
- **Track C** requires no protocol changes: installing `BydMirror-DiLink-Receiver.apk` onto any plug-in Android CarPlay AI Box brings BYD-WMP wireless mirroring to locked MG/Chery/Geely/Changan head units.
