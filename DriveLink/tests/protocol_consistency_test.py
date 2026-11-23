#!/usr/bin/env python3
"""
Cross-Platform Protocol Consistency Test for BYD-WMP v1.1
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROTOCOL_JSON = ROOT / "shared/protocol/byd_mirror_protocol.json"

def load_json():
    with open(PROTOCOL_JSON) as f:
        return json.load(f)

def check_json_constants():
    proto = load_json()
    errors = []
    if proto.get("version") != "1.1.0":
        errors.append(f"Version mismatch: expected 1.1.0, got {proto.get('version')}")
    if proto.get("packetFraming", {}).get("magicBytes") != "0x4259":
        errors.append(f"Magic bytes mismatch: expected 0x4259, got {proto.get('packetFraming', {}).get('magicBytes')}")
    if proto.get("packetFraming", {}).get("headerSizeBytes") != 16:
        errors.append(f"Header size mismatch: expected 16, got {proto.get('packetFraming', {}).get('headerSizeBytes')}")
    if proto.get("packetFraming", {}).get("maxPayloadBytes") != 65536:
        errors.append(f"Max payload mismatch: expected 65536, got {proto.get('packetFraming', {}).get('maxPayloadBytes')}")
    if proto.get("security", {}).get("cipher") != "AES-256-GCM":
        errors.append(f"Cipher mismatch: expected AES-256-GCM, got {proto.get('security', {}).get('cipher')}")
    if proto.get("security", {}).get("nonceSizeBytes") != 12:
        errors.append(f"Nonce size mismatch: expected 12, got {proto.get('security', {}).get('nonceSizeBytes')}")
    if proto.get("security", {}).get("tagSizeBytes") != 16:
        errors.append(f"Tag size mismatch: expected 16, got {proto.get('security', {}).get('tagSizeBytes')}")
    return errors

def load_json():
    with open(PROTOCOL_JSON) as f:
        return json.load(f)

def main():
    print("=" * 60)
    print("BYD-WMP v1.1 Protocol Consistency Test")
    print("=" * 60)
    errors = check_json_constants()
    if errors:
        print(f"FAIL: {len(errors)} errors")
        for e in errors:
            print(f"  - {e}")
        return 1
    else:
        print("PASS: JSON protocol definition is consistent")
    return 0

if __name__ == "__main__":
    import sys
    sys.exit(main())
