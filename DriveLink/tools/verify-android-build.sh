#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 3 || ( $1 != debug && $1 != release && $1 != unsigned ) ]]; then
  echo 'Usage: verify-android-build.sh {debug|release|unsigned} DRIVE_LINK_ROOT OUTPUT_DIR' >&2
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
# For unsigned variant, skip signer verification
if [[ $variant == unsigned ]]; then
  variant=release  # APKs are built as release variants
fi
mkdir -p "$out"
ids=(app.drivelink.cast app.drivelink.cast.touch app.drivelink.receiver)
names=(DriveLink-Standard.apk DriveLink-FullTouch.apk DriveLink-Receiver.apk)
dirs=("android/app/build/outputs/apk/standard/$variant" "android/app/build/outputs/apk/fullTouch/$variant" "receiver-dilink/app/build/outputs/apk/$variant")
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
    badging=$(aapt dump badging "$apk")
    package=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$badging")
    [[ $package == "${ids[i]}" ]]
    grep -Fq "versionCode='3' versionName='1.4.1'" <<< "$badging"
    grep -Fxq "sdkVersion:'26'" <<< "$badging"

    if [[ $1 == unsigned ]]; then
      # Unsigned APK: skip apksigner verify, just copy and record SHA256
      cp -- "$apk" "$out/${names[i]}"
      sha256=$(sha256sum "$apk" | cut -d' ' -f1)
      printf '## %s (UNSIGNED)\n\nPackage: `%s`. Size: %s bytes. SHA-256: %s\n\n' "${names[i]}" "$package" "$(wc -c < "$apk")" "$sha256"
    else
      signed=$(apksigner verify --verbose --print-certs "$apk")
      if [[ $variant == release ]]; then
        [[ $badging != *application-debuggable* ]]
        actual=$(sed -nE 's/^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)$/\1/p' <<< "$signed")
        [[ ${actual,,} == "$expected" ]] || { echo "Release signer mismatch for $package" >&2; exit 1; }
        if grep -Ei '^Signer #[0-9]+ certificate DN:.*CN[[:space:]]*=[[:space:]]*Android Debug' <<< "$signed" >/dev/null; then
          echo "Debug certificate rejected for $package" >&2
          exit 1
        fi
      fi
      cp -- "$apk" "$out/${names[i]}"
      printf '## %s\n\nPackage: `%s`. Size: %s bytes.\n\n```text\n%s\n```\n\n' "${names[i]}" "$package" "$(wc -c < "$apk")" "$signed"
    fi
  done
} > "$out/SIGNING_REPORT.md"
(cd "$out"; sha256sum -- "${names[@]}" > SHA256SUMS.txt; sha256sum -c SHA256SUMS.txt)
