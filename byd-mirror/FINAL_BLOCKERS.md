# FINAL BLOCKERS — BYD Wireless Mirror

1. **Git Push / Remote CI Trigger Policy Constraint (Pending User Authorization):**
   - GitHub Actions workflow `BYD Mirror Build & Test` (`421582309`) is already configured and proved capable of building all 4 targets (`run 44223614822`), **but** rebuilding the latest local P0/P1 fixes requires pushing `arena/9ae6c195-iptv` to GitHub (prohibited without explicit user permission).
2. **Missing Local Native Compilers in Sandbox Container:**
   - `JDK 17` / `Android SDK` / `Gradle`, `Go 1.22`, and `macOS / Xcode` are absent in the container, and egress firewall blocks `deb.debian.org`, `dl.google.com`, `go.dev`, and `release-assets.githubusercontent.com`.
3. **No Android Emulator or Physical Devices (Android / iPhone / BYD DiLink Car):**
   - `REAL BYD HARDWARE COMPATIBILITY — NOT VERIFIED`. Physical media capture, speaker audio routing, rotating screen layout, and performance metrics remain `BLOCKED` until tested on real hardware.
