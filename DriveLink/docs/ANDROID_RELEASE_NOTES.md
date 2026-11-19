# DriveLink 1.4.1 (3) — Android

Install Standard or FullTouch on the phone and Receiver on the display (Android 8.0/API 26 or later). FullTouch adds optional overlay and accessibility controls. Connect both devices to the same trusted local network and compare the pairing PIN before granting screen-capture consent. No Internet connection is required for local mirroring. Control: TCP 47810; media: UDP 47811; discovery beacon: UDP 47813.

Download the APKs and SHA256SUMS.txt into one directory and run `sha256sum -c SHA256SUMS.txt`. Check the signing certificate with `apksigner verify --print-certs` against the approved DriveLink fingerprint. On a designated test device use `adb install -r DriveLink-Standard.apk` (or substitute the desired filename). Do not uninstall an existing app to bypass a signing mismatch. The signing report and install/startup log accompany the Android release. Startup testing does not certify mirroring on every vehicle. No iOS IPA is included.
