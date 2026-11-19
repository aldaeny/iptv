# Distribution inputs and evidence

Use the existing manual DriveLink CI workflow on an explicitly reviewed commit. Repository secrets must be configured in GitHub, never committed or posted in an issue. PRs do not receive release signing inputs. A diagnostic artifact is not a release.

Android requires the existing four `DRIVELINK_KEYSTORE_*` / `DRIVELINK_KEY_*` secrets and the repository variable `DRIVELINK_SIGNER_SHA256` (trusted release certificate digest). A manual run without the private key can still validate debug startup and unsigned release structure; it must **fail** distribution eligibility.

The macOS job first builds and validates the unsigned Full and Free IPAs. For a distribution export it additionally requires:

* Variables: `DRIVELINK_APPLE_TEAM_ID`, `DRIVELINK_IOS_SIGNER_SHA256` (approved leaf certificate SHA-256), `DRIVELINK_IOS_EXPORT_METHOD` (`release-testing` for registered devices or `app-store-connect` for Apple's distribution channel).
* Secrets: `DRIVELINK_IOS_P12_BASE64`, `DRIVELINK_IOS_P12_PASSWORD` and four individual base64 CMS profiles: `DRIVELINK_IOS_PROFILE_FULL`, `_BROADCAST`, `_WIDGET`, `_FREE` (each with the full `DRIVELINK_IOS_PROFILE_` prefix).
* Profiles must authorize the exact bundle IDs from `Project.yml` and `Project-free.yml`, selected team, distribution certificate and capabilities. No device registration, wildcard profile substitution, temporary certificate generation or Apple portal modification.

On the disposable Mac, `tools/sign-ios.py` imports the supplied identity into a private temporary keychain, selects a profile per Xcode target, archives and exports both variants. It checks the exported signature, embedded profile, entitlements, version and certificate of the app and every extension before either IPA may enter `drivelink-ios-release-<sha>`. The keychain/search list and installed profiles are cleaned up. Unsigned artifacts use a separate namespace.

Before publication, the authorized device tester must test the **signed bytes** on the intended physical devices, recording model/OS, installation/startup, pairing, controls, live audio/video and reconnection. Put evidence in a reviewed report in this repository. Only after review, set `DRIVELINK_DEVICE_REPORT_URL` to that report and `DRIVELINK_DEVICE_TESTED_DIGEST` to `cat SHA256SUMS.txt IOS_SHA256SUMS.txt | sha256sum` (digest field, Android manifest first). These variables are a human attestation bound to the files, not a substitute for running the tests. The separate publication workflow also requires a successful manual build of current master and both signed artifact sets. Do not merge or publish merely because a diagnostic PR check is green.
