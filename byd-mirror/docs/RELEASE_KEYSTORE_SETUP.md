# Android Release Keystore Setup

Both `byd-mirror/android` and `byd-mirror/receiver-dilink` automatically sign release builds with your production keystore when the following environment variables are present, and safely fall back to `debug` signing when absent:

- `BYD_KEYSTORE_PATH`
- `BYD_KEYSTORE_PASSWORD`
- `BYD_KEY_ALIAS`
- `BYD_KEY_PASSWORD`

## 1. Generate a Keystore Locally (Never Commit to Git)

```bash
keytool -genkeypair -v \
  -keystore byd-release.jks \
  -alias bydmirror \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Encode it as Base64 for GitHub Actions Secrets:

```bash
base64 -w 0 byd-release.jks > byd-release.jks.b64
# On macOS: base64 -i byd-release.jks | tr -d '\n' > byd-release.jks.b64
```

## 2. Add Repository Secrets in GitHub

In **Settings → Secrets and variables → Actions**, add:

| Secret Name | Value |
| --- | --- |
| `BYD_RELEASE_KEYSTORE_BASE64` | Contents of `byd-release.jks.b64` |
| `BYD_KEYSTORE_PASSWORD` | Keystore password |
| `BYD_KEY_ALIAS` | `bydmirror` (or your chosen alias) |
| `BYD_KEY_PASSWORD` | Key password |

Once configured, `.github/workflows/byd-mirror-build.yml` automatically signs both Android Phone APKs (`standard` and `fullTouch`) and the BYD DiLink Receiver APK using your release keystore.
