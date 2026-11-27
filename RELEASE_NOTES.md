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
| `DriveLink-Standard.apk` | `app.drivelink.cast` | 12.3 MB | `366dd51e136ce75cac0cf2d69d694759d10ba2d73f6cea1dd0f824a8cc1d8134` |
| `DriveLink-FullTouch.apk` | `app.drivelink.cast.touch` | 12.3 MB | `c11e322a2ac164006fe04e728d8d427e5c3cb398946fc3b97bb746db8e66cd89` |
| `DriveLink-Receiver.apk` | `app.drivelink.receiver` | 11.4 MB | `9274811ef22ef247952cbfc27ad8e513c2fde6da1aa34d9fed2fde563c0a6b7a` |

> ✅ **These APKs are signed with the release keystore.** Ready for distribution.

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
Built from commit `6a18aec34` on branch `master`
