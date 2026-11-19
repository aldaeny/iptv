# DriveLink 1.4.1 — evidence, 19 November 2026

These are logs of this execution, not the historical BYD Mirror build. All unit and loopback results are host-only. `kotlin-host.log`: actual Kotlin sender protocol, model, crypto and quality classes and receiver protocol/crypto/decoder classes compiled with Kotlin 2.0.21 targeting Java 17; six JUnit classes (11 tests), followed by sender ↔ Go simulator live transport. `tests/run-host-loopback.sh` repeats the transport test with a local SDK and Kotlin distribution. No MediaCodec hardware, Android Activity or iOS UI is exercised there.

## Build map

| Product | Effective build inputs | Native gate |
|---|---|---|
| Standard | `android/settings.gradle.kts` includes `:app`; root plugin versions + `app/build.gradle.kts`; `src/main/{java,res,AndroidManifest.xml}`. Compose dashboard calls the session coordinator, projection service and streaming pipeline. Standard sets `ENABLE_OVERLAY_AND_TOUCH=false`. | `:app:assembleStandardRelease` |
| FullTouch | Same source and resources + `src/fullTouch/AndroidManifest.xml`, `.touch` ID suffix and enabled build flag. Overlay and accessibility components are registered only here. | `:app:assembleFullTouchRelease` |
| Receiver | Independent `receiver-dilink` wrapper/settings/build, `:app/src/main`; NSD registrar, TCP/UDP receiver, replay filter/reassembly and MediaCodec/AudioTrack → Surface. | `:app:assembleRelease` |
| iOS Full | XcodeGen `ios/Project.yml`: app Sources + assets; BroadcastExtension shares selected Core sources; WidgetExtension shares Activity attributes. Target plists/entitlements used directly. | SwiftPM tests, then Xcode release build; distribution additionally requires signing all three IDs and App Group provisioning. |
| iOS Free | `Project-free.yml`, single app + assets, `app.drivelink.cast.app.free`, no extensions or App Group entitlement. | Separate unsigned Xcode build; unsigned IPA is not distributable. |

Android: AGP 8.7.2, Kotlin/Compose plugin 2.0.21, Gradle 8.10.2, Java 17, SDK 35/build tools 35.0.0, target 34, min 26. IDs/versions checked in source; only verified `aapt` output is binary evidence. `_byd-mirror._tcp`, KDF context and wire opcodes deliberately retained for interoperability. Simulator and historical reports do not feed Gradle or Xcode packaging.

Manual device checks still required: Wi-Fi loss and network switch, real receiver discovery, MediaProjection consent/revocation, orientation/codec reconfiguration, audio/video decode and sync, FullTouch gestures, iOS ReplayKit and restart/recovery on target hardware. None can be inferred from host framing tests.
