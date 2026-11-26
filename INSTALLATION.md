# DriveLink Installation Guide

## Android (Sender)

### Prerequisites
- Android 13+ (API 33) for MediaProjection foreground service
- Wi-Fi Direct (P2P) capable device
- BYD vehicle with Wireless Mirroring support

### Install
1. Download the appropriate APK for your use case:
   - **Standard** (`DriveLink-Standard.apk`) — Basic mirroring sender
   - **FullTouch** (`DriveLink-FullTouch.apk`) — Mirroring + touch control overlay

2. Enable "Install unknown apps" for your browser/file manager

3. Install the APK

4. Grant permissions on first launch:
   - **Nearby devices** (Wi-Fi Direct discovery)
   - **Location** (coarse — required for Wi-Fi scanning on Android 12+)
   - **Screen recording** (MediaProjection)
   - **Display over other apps** (FullTouch only — touch overlay)

### Verify Integrity
```bash
# Verify SHA-256 after download
sha256sum DriveLink-Standard.apk
# Expected: 13c1336b9feb4f4c483a6fe976cd942c7f409ae90aa4f3b4c17cc4358ef0e7ce

sha256sum DriveLink-FullTouch.apk
# Expected: ca69fe1cec093b1cd2499fb57956b5a76a6cac0c2857e07ff6a199446ba19db0
```

## Android (Receiver — DiLink)

### Prerequisites
- Android 8.0+ (API 26) — BYD head unit
- Wi-Fi Direct (P2P) capable

### Install
1. Download `DriveLink-Receiver.apk`
2. Install on the BYD head unit (via ADB or USB transfer)
3. Grant permissions:
   - **Nearby devices**
   - **Location** (coarse)

### Verify Integrity
```bash
sha256sum DriveLink-Receiver.apk
# Expected: 0453e809b508960c5339e2ee5ab70bcba719f0683cf170270aaff01630d5c2a1
```

## Desktop Simulators (Development/Testing)

### Linux
```bash
chmod +x BydScreenSimulator-Linux
./BydScreenSimulator-Linux
```

### Windows
```cmd
BydScreenSimulator-Windows.exe
```

### Verify Integrity
```bash
# Linux
sha256sum BydScreenSimulator-Linux
# Expected: b7781fbac6d144b56fb82b86be8d46a94ffff1701860241b5aa66e31486a0afe

# Windows
certutil -hashfile BydScreenSimulator-Windows.exe SHA256
# Expected: 65588d56285b54ca2bd0fd3743e152c01472d532992aabdbd1820d68dc2f8430
```

## Connection Flow

1. **Start Receiver** on BYD head unit → shows "Waiting for connection..."
2. **Start Sender** on phone → tap "Connect to BYD"
3. **Accept pairing** on both devices (first time only)
4. **Grant MediaProjection** permission on phone
5. **Mirroring starts** — screen + audio stream to vehicle display

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "No devices found" | Ensure both devices have Wi-Fi Direct enabled; restart Wi-Fi |
| "Connection failed" | Forget previous pairings; re-pair; check firewall/VPN |
| "Black screen" | Grant screen recording permission; check HDCP settings |
| "Touch not working" | Use FullTouch variant; grant "Display over apps" permission |

## Security Notes
- **Unsigned APKs** in this release are diagnostic builds only
- For production/distribution, use APKs signed with the release keystore
- All traffic is encrypted with AES-GCM (256-bit) over DTLS 1.3
- No data leaves the local Wi-Fi Direct connection

## Support
- Issues: https://github.com/aldaeny/iptv/issues
- Protocol spec: `DriveLink/shared/protocol/byd_mirror_protocol.json`
