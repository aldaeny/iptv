# Android Release Keystore Setup

Both `DriveLink/android` and `DriveLink/receiver-dilink` sign the `release` variant **only** when a production
keystore is supplied through the `DRIVELINK_*` environment variables. Without them the release variant is left
unsigned instead of silently falling back to the debug key.

- `DRIVELINK_KEYSTORE_PATH`
- `DRIVELINK_KEYSTORE_PASSWORD`
- `DRIVELINK_KEY_ALIAS`
- `DRIVELINK_KEY_PASSWORD`

## 1. Generate a Keystore Locally (Never Commit to Git)

```bash
keytool -genkeypair -v \
  -keystore drivelink-release.jks \
  -alias drivelink \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Encode it as Base64 for GitHub Actions Secrets:

```bash
base64 -w 0 drivelink-release.jks > drivelink-release.jks.b64
# On macOS: base64 -i drivelink-release.jks | tr -d '\n' > drivelink-release.jks.b64
```

## 2. Add Repository Secrets and Variables in GitHub

In **Settings → Secrets and variables → Actions**, add:

| Secret / Variable | Value |
| --- | --- |
| `DRIVELINK_KEYSTORE_BASE64` (secret) | Contents of `drivelink-release.jks.b64` |
| `DRIVELINK_KEYSTORE_PASSWORD` (secret) | Keystore password |
| `DRIVELINK_KEY_ALIAS` (secret) | `drivelink` (or your chosen alias) |
| `DRIVELINK_KEY_PASSWORD` (secret) | Key password |
| `DRIVELINK_SIGNER_SHA256` (variable) | SHA-256 fingerprint of the signing certificate (`apksigner verify --print-certs`) |

Once configured, the `android` job of `.github/workflows/drivelink-android-release.yml` decodes the keystore to a
temporary file, exports `DRIVELINK_KEYSTORE_PATH`, and builds/signs both Android Phone APKs (`standard` and
`fullTouch`) plus the DriveLink Receiver APK, then verifies signatures and SHA-256 via
`DriveLink/tools/verify-android-build.sh`.
