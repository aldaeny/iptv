#!/usr/bin/env bash
# Designated Linux test host: Kotlin 2.0.21, Java 17, Go 1.22 and Android SDK 35.
# Starts the actual simulator; its documented TCP/UDP ports must be free.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
: "${KOTLIN_HOME:?Path to kotlinc distribution}"
api=${ANDROID_JAR:-${ANDROID_HOME:?Set ANDROID_HOME or ANDROID_JAR}/platforms/android-35/android.jar}
out=$(mktemp -d)
pid=
trap '[[ -z $pid ]] || kill "$pid" 2>/dev/null || true; rm -rf "$out"' EXIT
core="$root/android/app/src/main/java/app/drivelink/cast/core"
cp="$api:$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
"$KOTLIN_HOME/bin/kotlinc" -jvm-target 17 -cp "$cp" "$core"/{model,protocol}/*.kt \
  "$core/security/ChannelCryptoManager.kt" "$root/tests/Loopback.kt" -d "$out/client.jar"
(cd "$root/simulator" && go build -o "$out/simulator" .)
"$out/simulator" >"$out/simulator.log" 2>&1 & pid=$!
java -cp "$out/client.jar:$cp:$KOTLIN_HOME/lib/kotlin-stdlib.jar" LoopbackKt
kill -0 "$pid"
