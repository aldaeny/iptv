# Reverse touch and release gates

Do not authorize production until all eight gates pass for the release candidate. Keep emulator, physical-device and release-signing evidence separate. A green compile, debug signature, or unsigned artifact is not distribution evidence. This checklist does not change GitHub branch protection or authorize publication.

| Gate | Required evidence and pass condition | Remaining scope to track |
|---|---|---|
| 1. FullTouch protocol | Encrypted receiver-to-phone input observed as actual phone motion events: tap, two consecutive taps, long press, four swipes, cancellation and stop. | v1 is single-pointer: no pointer IDs or pinch; must cancel rather than convert extra pointers into a tap. Verify app-level double-tap recognition separately from two injected taps. |
| 2. Coordinates | Normalized input arrives at expected phone position before and after rotation, with an independently verified decoded image; repeat on hardware with letterbox/insets and different aspect ratios. | v1 has no video-frame ID, source timestamp, or content rect. Exact frame synchronization and letterbox mapping are not demonstrated by normalized-axis tests. |
| 3. Network recovery | Repeat interruption/recovery with Wi-Fi off/on, receiver loss, network change, delay and packet loss; confirm one session and new pairing where required. Record recovery time and failure traces. | Local orderly reconnect is covered elsewhere; it is not equivalent to radio/network failure. |
| 4. Session lifecycle | Stop during a gesture; no new injected input after close. Restart capture with fresh consent, revoke consent in flight, restart apps and repeat with heap/codec/thread measurements. | Validate projection revocation and resource retention in repeated cycles. An Android gesture already dispatched to the OS cannot be recalled by clearing the queue. |
| 5. Android regression | Both debug flavors: capture, rotation, restart, touch scope as applicable; run Android release build, unit tests and lint and inspect merged manifests and APK signing reports. | Repeat for the exact release commit; retain XML and logcat. |
| 6. iOS build and signing | Build both variants, verify IDs/entitlements, approved signing fingerprint and provisioning for the app and extensions, export and validate on physical iOS. | An earlier unsigned build and 7 signing-policy tests are not device or distribution proof. |
| 7. BYD hardware | Execute the parked-vehicle plan below and retain device-specific evidence. | Not validated by an Android emulator. |
| 8. Release review | Gates 1–7 closed with same-candidate evidence; explicit approval before merge/publication. | Blocked while any required evidence is missing. |

## BYD 2025 hardware plan

Use the vehicle while parked. Record model, region, DiLink firmware, Android API, APK SHA-256s, phone OS, codec and connection topology. First reproduce the original start-broadcast crash with the previous receiver if possible; then install the candidate and repeat. Test AVC first, then HEVC only if advertised; cover native and negotiated resolution, portrait/landscape transitions, receiver surface recreation, Wi-Fi interruptions and three clean reconnects. Verify letterboxing and tap positions with a calibration grid; exercise swipes and long press only in FullTouch. Soak for 30 minutes, record rendered FPS, interruptions, visible stall/recovery, and codec failures. Retain timestamps, screen recordings and sanitized crash logs when available. Record **not tested** for unsupported modes and **failed** for any reproducible crash, uncommanded touch or persistent loss of control. Never substitute emulator results for this run.

## Signing and build review

- Android: review the Standard, FullTouch and receiver manifests/package IDs, flavor permissions, release non-debuggable status, TLS/local transport policy, dependency and toolchain pins, and signing report/checksums. Distribution requires the original JKS, alias and associated passwords in an approved signing environment, and an independently verified expected SHA-256 signer fingerprint. Build twice in equivalent clean environments before making a reproducibility claim.
- iOS: review Full and Free bundle IDs and entitlements, extensions, usage descriptions and local network settings, Xcode/Swift dependencies, and actual exported artifacts. Require the approved team/export method, genuine signing identity and password, matching profiles for Full, Free, Broadcast and Widget, and expected signer fingerprint. Build twice and compare appropriate normalized/package metadata and checksums; do not infer byte reproducibility from one build.
- Leave credentials outside the source tree. Do not alter existing verification or publish gates, or treat an unsigned IPA/APK or test key as a release credential.

## Recorded candidate evidence

Candidate `3ea9ded`: [Android 14 instrumentation](https://github.com/aldaeny/iptv/actions/runs/44940932432), [source verification](https://github.com/aldaeny/iptv/actions/runs/44940932365). Standard and FullTouch capture/rotation/restart passed (6/6). The FullTouch gesture test failed before touch injection because its first decoded frame did not arrive; Standard correctly skipped reverse injection. A [prior run](https://github.com/aldaeny/iptv/actions/runs/44939163063) passed the original FullTouch gesture scenario before the rotation extension. [Later runs](https://github.com/aldaeny/iptv/actions/runs/44940090782) reached the rotated gesture but did not observe its ACTION_UP. These results do not close gates 1 or 2.

- Gate 1: **failed on candidate**; earlier single-pointer behavior proven on one emulator run.
- Gate 2: **not passed**; pure coordinate math is tested; post-rotation injection and frame alignment are not proven.
- Gate 3: **not tested in full**; prior orderly loopback reconnect only.
- Gate 4: **partial**; fresh consent/restart pass; projection revocation, post-stop input on the current candidate and leak measurements outstanding.
- Gate 5: **partial**; 6/6 projection regressions and source verification pass, FullTouch acceptance fails.
- Gate 6: **blocked** on approved production signing and physical iOS evidence; local signing policy tests pass 7/7.
- Gate 7: **not tested** on hardware.
- Gate 8: **blocked**; no release authorization.

Release gates passed in full: **0/8**. This is a conservative release-evidence count, not a code completion percentage. No secrets, merge or publication changes were made.
