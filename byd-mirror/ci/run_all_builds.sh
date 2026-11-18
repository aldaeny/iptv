#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="/tmp/byd-mirror-build-logs"
mkdir -p "$LOG_DIR"
echo "=== BYD Wireless Mirror — Full Local Verification & Build ==="

python3 "$ROOT/tests/verify_protocol.py"
node "$ROOT/tests/crypto_fuzz_suite.mjs"

if command -v go >/dev/null 2>&1; then
  (
    set -o pipefail
    cd "$ROOT/simulator"
    go test -race -v ./... 2>&1 | tee "$LOG_DIR/go-test.log"
    GOOS=windows GOARCH=amd64 go build -ldflags="-s -w" -o BydScreenSimulator-Windows.exe .
    sha256sum BydScreenSimulator-Windows.exe
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
    "$GRADLE_BIN" testStandardDebugUnitTest lintStandardDebug assembleStandardRelease assembleFullTouchRelease 2>&1 | tee "$LOG_DIR/gradle-android.log"
    sha256sum app/build/outputs/apk/standard/release/app-standard-release.apk
    sha256sum app/build/outputs/apk/fullTouch/release/app-fullTouch-release.apk
  )
  (
    set -o pipefail
    cd "$ROOT/receiver-dilink"
    "$GRADLE_BIN" testDebugUnitTest lintDebug assembleRelease 2>&1 | tee "$LOG_DIR/gradle-receiver.log"
    sha256sum app/build/outputs/apk/release/app-release.apk
  )
else
  echo "SKIP: JDK / Gradle / Android SDK not installed in current environment"
fi

if [ "$(uname -s)" = "Darwin" ] && command -v xcodebuild >/dev/null 2>&1; then
  (
    set -o pipefail
    cd "$ROOT/ios"
    swift test 2>&1 | tee "$LOG_DIR/swift-test.log"
    xcodegen generate --spec Project.yml
    xcodebuild -project BydWirelessMirror.xcodeproj -scheme BydWirelessMirror -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build 2>&1 | tee "$LOG_DIR/xcodebuild-full.log"
    xcodegen generate --spec Project-free.yml
    xcodebuild -project BydWirelessMirrorFree.xcodeproj -scheme BydWirelessMirror -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build 2>&1 | tee "$LOG_DIR/xcodebuild-free.log"
  )
else
  echo "SKIP: macOS / Xcode not installed in current environment"
fi
