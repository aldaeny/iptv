#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 3 || ( $1 != debug && $1 != release && $1 != unsigned ) ]]; then
  echo 'Usage: verify-android-build.sh {debug|release|unsigned} DRIVE_LINK_ROOT OUTPUT_DIR' >&2
  echo '  release  : requires DRIVELINK_SIGNER_SHA256 and a non-debug signer' >&2
  echo '  unsigned : release APKs built without a keystore; size/metadata only' >&2
  exit 2
fi
variant=$1
root=$2
out=$3
expected=${DRIVELINK_SIGNER_SHA256:-}
expected=${expected//:/}
expected=${expected,,}
if [[ $variant == release && ! $expected =~ ^[0-9a-f]{64}$ ]]; then
  echo 'A trusted release signing certificate SHA-256 is required.' >&2
  exit 2
fi
mkdir -p "$out"
ids=(app.drivelink.cast app.drivelink.cast.touch app.drivelink.receiver)
names=(DriveLink-Standard.apk DriveLink-FullTouch.apk DriveLink-Receiver.apk)
build_type=$variant
[[ $variant != unsigned ]] || build_type=release
dirs=("android/app/build/outputs/apk/standard/$build_type" "android/app/build/outputs/apk/fullTouch/$build_type" "receiver-dilink/app/build/outputs/apk/$build_type")
shopt -s nullglob
{
  printf '# Android %s — signing evidence\n\nCommit: `%s`\n\n' "$variant" "$(git rev-parse HEAD)"
  for i in "${!ids[@]}"; do
    files=("$root/${dirs[i]}"/*.apk)
    if [[ ${#files[@]} != 1 || ! -s ${files[0]} ]]; then
      echo "Expected exactly one nonempty APK in ${dirs[i]}" >&2
      exit 1
    fi
    apk=${files[0]}
    unzip -tq "$apk" >/dev/null
    for member in AndroidManifest.xml classes.dex resources.arsc; do
      [[ $(unzip -Z -l "$apk" "$member" | awk '$1 ~ /^-/ {print $4}') -gt 0 ]] || { echo "$apk: missing/empty $member" >&2; exit 1; }
    done
    badging=$(aapt dump badging "$apk")
    package=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$badging")
    [[ $package == "${ids[i]}" ]]
    grep -Fq "versionCode='3' versionName='1.4.1'" <<< "$badging"
    grep -Fxq "sdkVersion:'26'" <<< "$badging"
    if ! signed=$(apksigner verify --verbose --print-certs "$apk" 2>&1); then
      [[ $variant == unsigned ]] || { echo "$apk: signature verification failed" >&2; exit 1; }
    elif [[ $variant == unsigned ]]; then
      echo "$apk: expected unsigned release" >&2; exit 1
    fi
    if [[ $variant == release ]]; then
      [[ $badging != *application-debuggable* ]]
      actual=$(sed -nE 's/^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)$/\1/p' <<< "$signed")
      [[ ${actual,,} == "$expected" ]] || { echo "Release signer mismatch for $package" >&2; exit 1; }
      if grep -Ei '^Signer #[0-9]+ certificate DN:.*CN[[:space:]]*=[[:space:]]*Android Debug' <<< "$signed" >/dev/null; then
        echo "Debug certificate rejected for $package" >&2
        exit 1
      fi
    elif [[ $variant == unsigned ]]; then
      [[ $badging != *application-debuggable* ]] || { echo "Unsigned release build must not be debuggable ($package)" >&2; exit 1; }
    fi
    cp -- "$apk" "$out/${names[i]}"
    printf '## %s\n\nPackage: `%s`. Size: %s bytes.\n\n```text\n%s\n```\n\n' "${names[i]}" "$package" "$(wc -c < "$apk")" "$signed"
  done
} > "$out/SIGNING_REPORT.md"
(cd "$out"; sha256sum -- "${names[@]}" > SHA256SUMS.txt; sha256sum -c SHA256SUMS.txt)
