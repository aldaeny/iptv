# DriveLink v1.4.1

## What's New
- **Protocol v1.0** — Full BYD Wireless Mirroring Protocol implementation with 16-byte binary framing
- **Video Fragmentation (0x13)** — MTU-aware NALU fragmentation with out-of-order reassembly
- **Touch Back-Channel (0x14)** — Normalized touch coordinates with action codes
- **UDP → TCP Fallback** — Automatic media transport failover when UDP path degrades
- **HEVC/H.265 Support** — Dynamic codec selection with Annex-B detection on receiver

## Artifacts

### Android APKs (Signed Release)
| Artifact | Package | Size | SHA-256 |
|----------|---------|------|---------|
| `DriveLink-Standard.apk` | `app.drivelink.cast` | 11.7 MB | `32a9e01032e8279fd2bf536950c30811a064799897dcdeae867f58de0f8ab3bf` |
| `DriveLink-FullTouch.apk` | `app.drivelink.cast.touch` | 11.7 MB | `70609a9fa368d16e406b2635de81119cf8c47b0ef12181f664e5d8edde710100` |
| `DriveLink-Receiver.apk` | `app.drivelink.receiver` | 10.9 MB | `3e26e0c49c2b472a5fb46901122b902a95539d21b87ac28a90fd3429a1ad4724` |

> ✅ **These APKs are signed with the release keystore.** Ready for distribution.

### iOS IPA (Unsigned - CI Build)
| Artifact | Platform | Size | SHA-256 |
|----------|----------|------|---------|
| `BydWirelessMirror-Full.ipa` | iOS (Standard with Extensions) | 3.7 MB | `eea150ab31bc629115e398b9a702ceec7d3bbb8d92ec0eeee2be238ab7cc9ee3` |

> ⚠️ **This IPA is unsigned.** For distribution, configure Apple Developer certificates and re-run the workflow on `master` branch.

### Simulators
| Artifact | Platform | Size | SHA-256 |
|----------|----------|------|---------|
| `BydScreenSimulator-Linux` | Linux x86_64 | 7.5 MB | `b7781fbac6d144b56fb82b86be8d46a94ffff1701860241b5aa66e31486a0afe` |
| `BydScreenSimulator-Windows.exe` | Windows x86_64 | 7.7 MB | `65588d56285b54ca2bd0fd3743e152c01472d532992aabdbd1820d68dc2f8430` |

## Verification
All verification suites pass:
- ✅ Protocol parity (Android / iOS / Receiver / Simulator)
- ✅ Crypto interop & fuzz suite
- ✅ Isolation & package identity checks
- ✅ Go simulator unit tests (race detector)
- ✅ Android unit tests & lint (Standard / FullTouch / Receiver)

## Installation
See [INSTALLATION.md](INSTALLATION.md) for device setup instructions.

## Source
Built from commit `f06eb83af` on branch `master`
