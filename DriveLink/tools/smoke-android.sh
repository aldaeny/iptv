#!/usr/bin/env bash
# Run only on a designated test device/emulator. Never uninstall or clear app data.
set -euo pipefail
: "${ANDROID_SERIAL:?Select a test device with ANDROID_SERIAL}"
dir=${1:?Expected verified APK directory}
for spec in Standard:app.drivelink.cast FullTouch:app.drivelink.cast.touch Receiver:app.drivelink.receiver; do
  name=${spec%:*}; pkg=${spec#*:}
  adb install -r "$dir/DriveLink-$name.apk"
  activity=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$pkg" | tail -1 | tr -d '\r')
  [[ $activity == "$pkg/"* ]]
  result=$(adb shell am start -W -n "$activity")
  printf '%s\n' "$result"
  grep -Eq '^Status: ok[[:space:]]*$' <<< "$result"
  sleep 3
  adb shell pidof "$pkg"
  echo "PASS install and launch: $pkg"
done
