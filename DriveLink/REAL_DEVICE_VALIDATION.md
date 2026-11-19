# REAL DEVICE VALIDATION — BYD Wireless Mirror

## 1. Strict Separation of Execution Tiers
| Tier | Environment | Current Status | Notes |
| --- | --- | --- | --- |
| **AST, Protocol, Crypto & Loopback** | Sandbox Linux (`tree-sitter`, `python3`, `node:crypto`) | **PASS** | Executed after final code edits (`verify_protocol.py`, `crypto_fuzz_suite.mjs`) |
| **Native Build** | Local container / Unpushed CI | **BLOCKED** | Local compilers missing; CI requires `git push` authorization |
| **Android Emulator** | AVD / `adb` | **BLOCKED** | No emulator / KVM / Android SDK in container |
| **Physical Android Phone** | Real device via USB/Wi-Fi | **BLOCKED** | No physical phone connected |
| **Physical iPhone** | Real iOS device | **BLOCKED** | No physical iPhone / macOS connected |
| **Physical BYD DiLink Vehicle** | Actual BYD head unit | **BLOCKED** | `REAL BYD HARDWARE COMPATIBILITY — NOT VERIFIED` |

## 2. Performance Measurements
- **FPS / Avg Latency / P95 Latency / Frame Drops / Reconnection Time / CPU / RAM / Battery / Temp / Resolution / Wi-Fi Stability:** **NO LIVE DATA COLLECTED** (`BLOCKED` — zero fabricated or estimated numbers).

## 3. Required External Verification Checklist (After Rebuilding Latest Fixes)
1. Push `arena/9ae6c195-iptv` (when authorized) to trigger `BYD Mirror Build & Test` and download freshly built APKs/IPAs/EXE containing the `MirrorForegroundService` `Activity.RESULT_OK` fix and `MainActivity` `Dispatchers.IO` fix.
2. Execute `DriveLink/REAL_HARDWARE_TESTS.md` on a physical Android phone, physical iPhone, and real BYD DiLink vehicle.
