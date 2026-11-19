# EXTERNAL BUILD EXECUTION — BYD Wireless Mirror

## 1. Repository, Branch & Backup State
- **Repository:** `aldaeny/iptv`
- **Branch:** `arena/9ae6c195-iptv`
- **Base Commit (`HEAD`):** `c9b43b47413e7f57a1baecc33888418086a3db6d`
- **Fresh Verified Backup:** `/tmp/byd-mirror-ext-backup-20261118T030602Z.tar.gz` (`142 KB`, `220` entries verified via `tar -tzf`)
- **Workspace Isolation:** `100%` of modified and untracked files are inside `DriveLink/` (`git reset` / `git checkout` / `git clean` / `git push` / `git merge` NEVER executed).

## 2. Sandbox vs External CI Environment Verification
| Environment | Capabilities Verified | Status for Current Unpushed Code |
| --- | --- | --- |
| **Local Linux Container** | `python3 3.11.2`, `node v22.22.3`, `gcc/g++`, `tree-sitter` C AST parsers (`go 0.25.0`, `kotlin 1.1.0`, `swift 0.7.4`), `gh` CLI authenticated as `aldaeny` | Executed AST, XML/Plist, OpenSSL crypto, fuzz, and TCP+UDP loopback tests; **lacks JDK 17 / Android SDK / Go 1.22 / macOS Xcode** (`deb.debian.org`, `dl.google.com`, `go.dev`, `release-assets.githubusercontent.com` blocked by egress firewall) |
| **GitHub Actions CI (`BYD Mirror Build & Test`, workflow `421582309`)** | Verified via `gh run view 44223614822` (`ubuntu-latest` + `macos-latest`): all 5 build jobs (`verify-protocol`, `build-android-phone`, `build-windows-simulator`, `build-ios-check`, `build-byd-receiver`) succeeded at baseline `c9b43b4` | **Awaiting Explicit User Authorization to `git push`** — CI runs against remote `github.sha`; local P0/P1 fixes (including `MirrorForegroundService.kt` `Activity.RESULT_OK` & `MainActivity.kt` `Dispatchers.IO` disconnect) are NOT yet pushed |
| **New Self-Contained Build Assets Inside `DriveLink/`** | Created `DriveLink/ci/run_drivelink_builds.sh` (toolchain-aware local driver) | Verified locally (`bash DriveLink/ci/run_drivelink_builds.sh`) — runs protocol + OpenSSL/loopback suites and skips absent native compilers cleanly |
