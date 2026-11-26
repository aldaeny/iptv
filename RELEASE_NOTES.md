# DriveLink v1.4.1

## What's New
- **Protocol v1.0** — Full BYD Wireless Mirroring Protocol implementation with 16-byte binary framing
- **Video Fragmentation (0x13)** — MTU-aware NALU fragmentation with out-of-order reassembly
- **Touch Back-Channel (0x14)** — Normalized touch coordinates with action codes
- **UDP → TCP Fallback** — Automatic media transport failover when UDP path degrades
- **HEVC/H.265 Support** — Dynamic codec selection with Annex-B detection on receiver

## Artifacts

### Android APKs (Unsigned — Diagnostic Only)
| Artifact | Package | Size | SHA-256 |
|----------|---------|------|---------|
| `DriveLink-Standard.apk` | `app.drivelink.cast` | 12.3 MB | `13c1336b9feb4f4c483a6fe976cd942c7f409ae90aa4f3b4c17cc4358ef0e7ce` |
| `DriveLink-FullTouch.apk` | `app.drivelink.cast.touch` | 12.3 MB | `ca69fe1cec093b1cd2499fb57956b5a76a6cac0c2857e07ff6a199446ba19db0` |
| `DriveLink-Receiver.apk` | `app.drivelink.receiver` | 11.4 MB | `0453e809b508960c5339e2ee5ab70bcba719f0683cf170270aaff01630d5c2a1` |

> ⚠️ **These APKs are unsigned diagnostic builds.** They are not for distribution. For a signed release, configure the release keystore secrets and re-run the workflow.

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
Built from commit `37d017324` on branch `arena/f1a53a8c-iptv`
