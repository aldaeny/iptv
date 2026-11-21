# Receiver decoder device test

With an Android emulator booted, run `./gradlew :app:connectedDebugAndroidTest` from `DriveLink/receiver-dilink`. The manual DriveLink CI workflow also runs this on its Android emulator when testing debug APKs.

The test feeds a synthetic H.264 IDR to the production decoder and waits for a frame on an Android surface. It repeats setup callbacks for the same surface, then repeats playback with a replacement surface.
A separate loopback test runs the production Android phone transport and DiLink receiver in the emulator. It negotiates a session, checks the pairing PIN, waits for a keyframe request, sends the fixture over encrypted UDP, and requires a frame on the receiver surface in two successive sessions, waiting for the receiver to return to idle between them. It does not cover MediaProjection, a real Wi-Fi network, BYD firmware, HEVC, audio, or touch.

The synthetic fixture can be regenerated with FFmpeg and libx264:

```sh
ffmpeg -f lavfi -i color=c=blue:s=320x240:r=30 -frames:v 1 -threads 1 \
  -c:v libx264 -profile:v baseline -tune zerolatency \
  -bsf:v filter_units=remove_types=6 -f h264 assets/blue-idr.h264
```
