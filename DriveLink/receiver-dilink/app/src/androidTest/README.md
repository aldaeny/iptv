# Receiver decoder device test

With an Android emulator booted, run `./gradlew :app:connectedDebugAndroidTest` from `DriveLink/receiver-dilink`. The manual DriveLink CI workflow also runs this on its Android emulator when testing debug APKs.

The test feeds a synthetic H.264 IDR to the production decoder and waits for a frame on an Android surface. It repeats setup callbacks for the same surface, then repeats playback with a replacement surface. It does not emulate the BYD firmware, test the phone-to-receiver network path, or validate HEVC and audio.

The synthetic fixture can be regenerated with FFmpeg and libx264:

```sh
ffmpeg -f lavfi -i color=c=blue:s=320x240:r=30 -frames:v 1 -threads 1 \
  -c:v libx264 -profile:v baseline -tune zerolatency \
  -bsf:v filter_units=remove_types=6 -f h264 assets/blue-idr.h264
```
