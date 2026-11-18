# TROUBLESHOOTING — BYD Wireless Mirror

## 1. Car / Simulator Not Appearing in Device List
- Confirm both phone and car screen (or Windows laptop) are on the **exact same Wi-Fi / hotspot** (avoid guest Wi-Fi with AP/Client Isolation enabled).
- Check that UDP port `47813` (beacon) and TCP `47810` are not blocked, or tap **Connect by IP** (`اتصال بـ IP يدوياً`) and type the IPv4 shown on the Receiver / Simulator screen.

## 2. Connected (`متصل`) but Screen Shows Black (`0 FPS`)
- Ensure you tapped **Start Now** (`البدء الآن`) on the Android screen-capture consent dialog.
- If using an older APK before this audit's fixes, update to a build containing `MediaProjection.Callback` registration (`API 34+`) and automatic UDP-to-TCP fallback (`BydProtocolWrapper.kt`).
- On Windows Simulator: if the browser shows black despite `LIVE`, ensure you are using Chrome or Microsoft Edge (WebCodecs `VideoDecoder` enabled) and not an older browser without H.264 WebCodecs.

## 3. Android Play Protect Blocks APK Sideloading
- Use **`BydMirror-Phone.apk`** (`standard` flavor, no Accessibility or Overlay permissions) instead of `BydMirror-Phone-ReverseTouch.apk`.
- Download directly in Chrome rather than sharing via messaging apps.
- On Android 14+ with Play Protect Enhanced Fraud Protection enabled, temporarily pause Play Protect in Play Store settings during sideloading or install via `adb install`.

## 4. iPhone Free Sideload Issues (`$0` Apple ID)
- Install **`BydWirelessMirror-FreeSideload.ipa`** (1 App ID, empty entitlements) via SideStore, AltStore, or Sideloadly.
- Note that free-sideloaded mode captures **in-app** content via `RPScreenRecorder`; system-wide capture across third-party apps requires `BydWirelessMirror-unsigned.ipa` with Extension signing / TrollStore.
