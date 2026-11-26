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
python3 "$ROOT/tests/publish_guard_selftest.py"
node "$ROOT/tests/crypto_fuzz_suite.mjs"
node "$ROOT/tests/simulator_ui_wiring.mjs"

if command -v go >/dev/null 2>&1; then
  (
    set -o pipefail
    cd "$ROOT/simulator"
    go vet ./...
    go test -race -v ./... 2>&1 | tee "$LOG_DIR/go-test.log"
    GOOS=windows GOARCH=amd64 go build -trimpath -ldflags="-s -w -buildid=" -o "$LOG_DIR/DriveLink-Simulator-Windows.exe" .
    GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w -buildid=" -o "$LOG_DIR/DriveLink-Simulator-Linux" .
    sha256sum "$LOG_DIR"/DriveLink-Simulator-*
  )
else
  echo "SKIP: go compiler not installed in current environment"
fi

HAS_ANDROID_SDK=0
if [ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ] || [ -f "$ROOT/android/local.properties" ]; then
  HAS_ANDROID_SDK=1
fi

if command -v java >/dev/null 2>&1 && [ "$HAS_ANDROID_SDK" -eq 1 ]; then
  failed=0
  (cd "$ROOT/android" && ./gradlew --continue --no-daemon --stacktrace testStandardDebugUnitTest testFullTouchDebugUnitTest \
    lintStandardDebug lintFullTouchDebug assembleDebug assembleStandardRelease assembleFullTouchRelease) 2>&1 | tee "$LOG_DIR/gradle-android.log" || failed=1
  (cd "$ROOT/receiver-dilink" && ./gradlew --continue --no-daemon --stacktrace testDebugUnitTest lintDebug assembleDebug assembleRelease) 2>&1 | tee "$LOG_DIR/gradle-receiver.log" || failed=1
  [[ $failed == 0 ]] || exit 1
  mode=unsigned
  if [[ -n ${DRIVELINK_KEYSTORE_PATH:-} ]]; then mode=release; fi
  if [[ -n ${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}} ]]; then
    export PATH="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/build-tools/35.0.0:$PATH"
  fi
  bash "$ROOT/tools/verify-android-build.sh" "$mode" "$ROOT" "$LOG_DIR/$mode"

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
