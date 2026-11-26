#!/usr/bin/env python3
"""
Verify Phase 1 invariants for the release build.
Checks that critical configuration and version properties are correct.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def main():
    print("verify_phase1_invariants.py: Phase 1 invariant checks")

    # Check version consistency
    android_config = (ROOT / "android/app/build.gradle.kts").read_text()
    assert 'versionName = "1.4.1"' in android_config, "Android version mismatch"
    assert 'versionCode = 3' in android_config, "Android versionCode mismatch"

    receiver_config = (ROOT / "receiver-dilink/app/build.gradle.kts").read_text()
    assert 'versionName = "1.4.1"' in receiver_config, "Receiver version mismatch"
    assert 'versionCode = 3' in receiver_config, "Receiver versionCode mismatch"

    # Check protocol constants
    proto = json.loads((ROOT / "shared/protocol/byd_mirror_protocol.json").read_text())
    opcodes = proto["packetFraming"]["opcodes"]
    assert opcodes["VIDEO_NALU"] == 0x10
    assert opcodes["AUDIO_AAC"] == 0x11
    assert opcodes["KEYFRAME_REQUEST"] == 0x12
    assert opcodes["VIDEO_NALU_FRAG"] == 0x13
    assert opcodes["TOUCH_EVENT"] == 0x14

    # Check iOS Project.yml
    ios_project = (ROOT / "ios/Project.yml").read_text()
    assert "1.4.1" in ios_project or "MARKETING_VERSION" in ios_project

    print("PASS: verify_phase1_invariants.py")
    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except AssertionError as e:
        print(f"FAIL: {e}")
        sys.exit(1)