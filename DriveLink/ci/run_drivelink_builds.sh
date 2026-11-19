#!/usr/bin/env bash
# DriveLink local verification & build driver.
# Runs every check that the sandbox supports and skips the toolchains it lacks,
# so the same command works on a bare Linux box, a CI runner and a macOS build host.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${DRIVELINK_LOG_DIR:-/tmp/drivelink-build-logs}"
mkdir -p "$LOG_DIR"
echo "=== DriveLink Cast — local verification & build (tree: $ROOT) ==="

python3 "$ROOT/tests/verify_isolation.py"
python3 "$ROOT/tests/verify_protocol.py"
python3 "$ROOT/tests/verify_release.py"
node "$ROOT/tests/crypto_fuzz_suite.mjs"
node "$ROOT/tests/simulator_ui_wiring.mjs"

if command -v go >/dev/null 2>&1; then
  (
    set -o pipefail
    cd "$ROOT/simulator"
    go vet ./...
    go test -race -v ./... 2>&1 | tee "$LOG_DIR/go-test.log"
    GOOS=windows GOARCH=amd64 go build -trimpath -ldflags="-s -w -buildid=" -o BydScreenSimulator-Windows.exe .
    GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w -buildid=" -o BydScreenSimulator-Linux .
    sha256sum BydScreenSimulator-Windows.exe BydScreenSimulator-Linux
  )
else
  echo "SKIP: go compiler not installed in current environment"
fi

HAS_ANDROID_SDK=0
if [ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ] || [ -f "$ROOT/android/local.properties" ]; then
  HAS_ANDROID_SDK=1
fi

if command -v java >/dev/null 2>&1 && [ "$HAS_ANDROID_SDK" -eq 1 ] && { command -v gradle >/dev/null 2>&1 || [ -x "$ROOT/android/gradlew" ]; }; then
  GRADLE_BIN="$(command -v gradle || echo "./gradlew")"
  (
    set -o pipefail
    cd "$ROOT/android"
    "$GRADLE_BIN" --no-daemon testStandardDebugUnitTest testFullTouchDebugUnitTest \
      lintStandardDebug lintFullTouchDebug assembleDebug 2>&1 | tee "$LOG_DIR/gradle-android.log"
  )
  (
    set -o pipefail
    cd "$ROOT/receiver-dilink"
    "$GRADLE_BIN" --no-daemon testDebugUnitTest lintDebug assembleDebug 2>&1 | tee "$LOG_DIR/gradle-receiver.log"
  )
  if [ -n "${DRIVELINK_KEYSTORE_PATH:-}" ]; then
    (
      set -o pipefail
      cd "$ROOT/android"
      "$GRADLE_BIN" --no-daemon assembleStandardRelease assembleFullTouchRelease 2>&1 | tee "$LOG_DIR/gradle-android-release.log"
    )
    (
      set -o pipefail
      cd "$ROOT/receiver-dilink"
      "$GRADLE_BIN" --no-daemon assembleRelease 2>&1 | tee "$LOG_DIR/gradle-receiver-release.log"
    )
    bash "$ROOT/tools/verify-android-build.sh" release "$ROOT" "$ROOT/releases/current"
  else
    echo "SKIP: release APKs need DRIVELINK_KEYSTORE_* environment variables (see docs/RELEASE_KEYSTORE_SETUP.md)"
  fi
else
  echo "SKIP: JDK / Gradle / Android SDK not installed in current environment"
fi

if [ "$(uname -s)" = "Darwin" ] && command -v xcodebuild >/dev/null 2>&1; then
  (
    set -o pipefail
    cd "$ROOT/ios"
    swift test 2>&1 | tee "$LOG_DIR/swift-test.log"
    xcodegen generate --spec Project.yml
    xcodebuild -project BydWirelessMirror.xcodeproj -scheme BydWirelessMirror -configuration Release \
      -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build 2>&1 | tee "$LOG_DIR/xcodebuild-full.log"
    xcodegen generate --spec Project-free.yml
    xcodebuild -project BydWirelessMirrorFree.xcodeproj -scheme BydWirelessMirror -configuration Release \
      -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build 2>&1 | tee "$LOG_DIR/xcodebuild-free.log"
  )
else
  echo "SKIP: macOS / Xcode not installed in current environment"
fi

echo "=== done — logs in $LOG_DIR ==="
