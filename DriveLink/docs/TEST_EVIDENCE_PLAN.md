# Test Evidence Plan

## Levels

| Level | Definition | May be claimed only with |
|---|---|---|
| L0 Syntax | Parser accepts every file | tree-sitter/PyYAML output |
| L1 Source gates | Invariants/contracts pass | script output |
| L2 Native build | Gradle/Xcode/Swift/Go compile | CI run URL + artifact |
| L3 Unit tests | JVM/Swift/Go tests execute | test report |
| L4 Integration | Phone ↔ receiver/simulator over network | timestamped log |
| L5 Device | Real Android/iPhone + head unit | model/version/log/photo |
| L6 Vehicle | BYD DiLink in a car | test report + telemetry |
| L7 Distribution | Signed APK/IPA installed | signature verification |

## Current evidence

| Level | Android app | Receiver | iOS | Simulator |
|---|---|---|---|---|
| L0 | Yes (tree-sitter) | Yes | Yes | Go/Node parse via CI |
| L1 | Yes | Yes | Yes | Yes |
| L2 | **Blocked** (no JDK/SDK locally; no CI on this tree) | **Blocked** | **Blocked** | **Blocked** (no Go) |
| L3 | **Blocked** (44 tests written, not run) | **Blocked** (20 tests) | **Blocked** (6 tests) | Node suites pass locally |
| L4 | **Not run** | — | **Not run** | Local loopback passes |
| L5 | **Not run** | **Not run** | **Not run** | — |
| L6 | **Not run** | **Not run** | — | — |
| L7 | **Blocked** (signing inputs missing) | **Blocked** | **Blocked** | N/A |

## Commands

```bash
python3 DriveLink/tests/verify_protocol.py
python3 DriveLink/tests/verify_isolation.py
python3 DriveLink/tests/verify_release.py
python3 DriveLink/tests/verify_phase1_invariants.py
python3 DriveLink/tests/test_ios_signing.py
node DriveLink/tests/crypto_fuzz_suite.mjs
node DriveLink/tests/simulator_ui_wiring.mjs
```

Native (CI only in this environment):

```bash
cd DriveLink/android && ./gradlew :app:testStandardDebugUnitTest :app:testFullTouchDebugUnitTest
cd DriveLink/receiver-dilink && ./gradlew :app:testDebugUnitTest
cd DriveLink/ios && swift test
cd DriveLink/simulator && go test -race ./...
```

## Rules

- A skipped CI job is not a pass.
- `assemble` success does not mean tests ran.
- Do not convert `BLOCKED` to `PASS` because the code "looks correct".
- Every release claim must name the level and the artifact/log that proves it.
