# Capability Registry

Every capability has exactly one state and a non-empty evidence string. A capability cannot be
constructed as `AVAILABLE` while its evidence contains `BLOCKED`.

## States

| State | Meaning | May be called usable? |
|---|---|---|
| `AVAILABLE` | Working and evidenced in this build/platform | Yes |
| `REQUIRES_PERMISSION` | Code exists; user/OS consent missing | No (until granted/tested) |
| `DEGRADED` | Works with a documented limitation | Yes, with limitation |
| `UNSUPPORTED` | Platform/hardware cannot do it, or no implementation | No |
| `NOT_VERIFIED` | Code exists but no executable evidence | No |
| `BLOCKED_BY_PLATFORM` | OS policy forbids it (e.g. iOS touch injection) | No |
| `BLOCKED_BY_HARDWARE` | Device lacks the required hardware | No |
| `FAILED` | Was expected to work; current evidence is failure | No |

Android: `CapabilityRegistry.kt` (11 capabilities).
iOS: `CapabilityRegistry.swift` (same 11 ids, platform-specific states).

## Current truth

| Capability | Android | iOS | Receiver |
|---|---|---|---|
| BYD-WMP transport | Available (CI `45056352610`) | Available (CI) | Available (CI) |
| Wi-Fi Direct | Not verified | Not verified | N/A |
| Miracast | Not verified | Not verified | N/A |
| Screen mirroring | Available / Requires permission (flavor) | Requires permission (ReplayKit) | N/A |
| Reverse touch | Requires permission (fullTouch) / Unsupported (standard) | Unsupported (platform) | N/A |
| Audio streaming | Not verified | Not verified | Not verified |
| HEVC | Not verified | Not verified | Not verified |
| CarPlay | Unsupported | Unsupported | Unsupported |
| Android Auto | Unsupported | Unsupported | Unsupported |
| USB video | Unsupported | Unsupported | Unsupported |
| Bluetooth video | Unsupported | Unsupported | Unsupported |

`CI_BASELINE_EVIDENCE` refers to GitHub Actions run `45056352610` on commit `7c2aaea`: native
builds and unit tests passed. It is **not** a device/vehicle test claim.

## Rules for adding a capability

1. Add the id in both registries (and the receiver if it advertises it).
2. Provide an evidence string naming the exact test/run/device — never "works".
3. If only code exists, use `NOT_VERIFIED`, not `AVAILABLE`.
4. If the platform forbids it, use `UNSUPPORTED`/`BLOCKED_BY_PLATFORM` and document the reason.
5. UI renders the registry; do not hard-code a second support matrix.
