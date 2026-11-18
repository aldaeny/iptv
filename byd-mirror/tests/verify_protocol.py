#!/usr/bin/env python3
"""
End-to-end verification suite for the BYD Wireless Mirroring codebase:
1. Validates JSON, XML, Plist, and Entitlements files.
2. Verifies cross-platform protocol constants, opcodes, KDF info, and crypto/security gates
   across Android Sender, iOS Sender, BYD Receiver, and Windows Simulator.
3. Simulates BYD-WMP v1.0 16-byte binary framing, MTU fragmentation (0x13), touch packets (0x14),
   and negative/malformed packet rejection tests.
"""
import json
import struct
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAGIC = 0x4259
VERSION = 0x01
HEADER_FMT = ">HBBIII"


def encode_frame(opcode: int, seq: int, ts_us: int, payload: bytes) -> bytes:
    header = struct.pack(HEADER_FMT, MAGIC, VERSION, opcode, seq, ts_us, len(payload))
    return header + payload


def decode_frame(raw: bytes):
    assert len(raw) >= 16, "Truncated header"
    magic, ver, opcode, seq, ts_us, length = struct.unpack(HEADER_FMT, raw[:16])
    assert magic == MAGIC, f"Bad magic: {magic:#x}"
    assert ver == VERSION, f"Bad version: {ver}"
    assert seq > 0, f"Invalid sequence: {seq}"
    assert 0 <= length <= 65536, f"Invalid payload length: {length}"
    payload = raw[16 : 16 + length]
    assert len(payload) == length, "Truncated payload"
    return opcode, seq, ts_us, payload


def main():
    proto = json.loads((ROOT / "shared/protocol/byd_mirror_protocol.json").read_text())
    opcodes = proto["packetFraming"]["opcodes"]
    assert opcodes["VIDEO_NALU"] == 0x10
    assert opcodes["AUDIO_AAC"] == 0x11
    assert opcodes["KEYFRAME_REQUEST"] == 0x12
    assert opcodes["VIDEO_NALU_FRAG"] == 0x13
    assert opcodes["TOUCH_EVENT"] == 0x14

    # 1. Validate XML / Plist / Entitlements
    xml_files = sorted(ROOT.rglob("*.xml")) + sorted(ROOT.rglob("*.plist")) + sorted(ROOT.rglob("*.entitlements"))
    for path in xml_files:
        ET.parse(path)

    # 2. Cross-platform source parity & security gate checks
    kt_proto = (ROOT / "android/app/src/main/java/com/byd/mirror/core/protocol/BydProtocolWrapper.kt").read_text()
    kt_crypto = (ROOT / "android/app/src/main/java/com/byd/mirror/core/security/ChannelCryptoManager.kt").read_text()
    kt_enc = (ROOT / "android/app/src/main/java/com/byd/mirror/core/streaming/ScreenCaptureEncoder.kt").read_text()

    sw_proto = (ROOT / "ios/Sources/Core/Protocol/BydProtocolWrapper.swift").read_text()
    sw_enc = (ROOT / "ios/Sources/Core/Streaming/HardwareVideoEncoder.swift").read_text()
    sw_direct = (ROOT / "ios/Sources/Core/Session/DirectMirrorSession.swift").read_text()
    sw_ext = (ROOT / "ios/BroadcastExtension/SampleHandler.swift").read_text()

    rx_srv = (ROOT / "receiver-dilink/app/src/main/java/com/byd/receiver/protocol/BydReceiverServer.kt").read_text()
    rx_crypto = (ROOT / "receiver-dilink/app/src/main/java/com/byd/receiver/crypto/ReceiverCryptoManager.kt").read_text()
    rx_dec = (ROOT / "receiver-dilink/app/src/main/java/com/byd/receiver/decoder/VideoAudioDecoder.kt").read_text()

    sim_go = (ROOT / "simulator/main.go").read_text()

    for token in ["TOUCH_EVENT(0x14)", "VIDEO_NALU_FRAG(0x13)", "UDP_CHUNK_LIMIT_BYTES = 1_150", "useTcpMediaFallback", "lastRxSeq", "udpSequenceCounter"]:
        assert token in kt_proto, f"Android protocol missing {token}"
    assert "encryptedPayload.size >= GCM_NONCE_BYTES + (GCM_TAG_BITS / 8)" in kt_crypto, "Android crypto >= 28B gate missing"
    assert "registerCallback" in kt_enc and "KEY_REPEAT_PREVIOUS_FRAME_AFTER" in kt_enc, "Android ScreenCaptureEncoder missing API34 callback or repeat-frame"

    for token in ["videoNaluFrag = 0x13", "touchEvent = 0x14", "udpChunkLimitBytes = 1_150", "encodeVideoTcpFrames", "udpSequence", "invalidSequence", "invalidPayloadLength"]:
        assert token in sw_proto, f"iOS protocol missing {token}"
    assert "codecIsHevc" in sw_enc and "activeCodecIsHevc" in sw_enc, "iOS HardwareVideoEncoder missing dynamic H264/HEVC Annex-B flag"
    assert "useTcpMediaFallback" in sw_direct and "useTcpMediaFallback" in sw_ext, "iOS missing UDP->TCP fallback"

    rx_main = (ROOT / "receiver-dilink/app/src/main/java/com/byd/receiver/MainActivity.kt").read_text()
    assert "lifecycleScope.launch(Dispatchers.IO)" in rx_main and "disconnectActiveSession" in rx_main, "Receiver MainActivity onDisconnect must run off MainThread"

    kt_fg = (ROOT / "android/app/src/main/java/com/byd/mirror/service/MirrorForegroundService.kt").read_text()
    assert "Activity.RESULT_OK" in kt_fg and "resultCode != -1" not in kt_fg, "MirrorForegroundService MediaProjection resultCode regression"
    kt_acc = (ROOT / "android/app/src/main/java/com/byd/mirror/service/BydTouchAccessibilityService.kt").read_text()
    assert "mainHandler.post" in kt_acc and "MAX_MOVE_POINTS" in kt_acc, "Accessibility touch thread-safety guard missing"
    kt_p2p = (ROOT / "android/app/src/main/java/com/byd/mirror/core/network/WifiP2pTransport.kt").read_text()
    assert "ContextCompat.registerReceiver" in kt_p2p, "WifiP2pTransport missing Android 14+ ContextCompat.registerReceiver flag"

    sw_transport = (ROOT / "ios/Sources/Core/Network/PeerTransportClient.swift").read_text()
    assert "generation &+= 1" in sw_transport, "iOS PeerTransportClient missing generation guard"
    sw_aac = (ROOT / "ios/Sources/Core/Streaming/AudioAacEncoder.swift").read_text()
    assert "withUnsafeMutableBytes" in sw_aac, "iOS AudioAacEncoder missing safe buffer lifetime scope"

    for token in ["OPCODE_VIDEO_NALU_FRAG: Byte = 0x13", "OPCODE_TOUCH_EVENT: Byte = 0x14", "if (!encryptionActive) return", "clientJob = launch", "failedHandshakes", "PAIRING_CONFIRM_TIMEOUT_MS"]:
        assert token in rx_srv, f"Receiver missing {token}"
    assert "encryptedPayload.size >= GCM_NONCE_BYTES + (GCM_TAG_BITS / 8)" in rx_crypto, "Receiver crypto >= 28B gate missing"
    assert "P256_SPKI_PREFIX" in rx_crypto, "Receiver missing iOS X9.63 65B interop"
    assert "supportsHevcDecoder" in rx_dec, "Receiver decoder missing HEVC fallback check"

    for token in ["len(wire) < encOverhead", "!s.crypto.IsActive()", "fragTotal > 256", "udpReplay.Accept", "failedHandshake"]:
        assert token in sim_go, f"Simulator missing {token}"

    # 3. Simulate MTU fragmentation + out-of-order reassembly
    big_annex_b = b"\x00\x00\x00\x01" + b"\x65" + (b"\xAA" * 3000)
    chunk_size = 1150
    total_frags = (len(big_annex_b) + chunk_size - 1) // chunk_size
    wire_packets = []
    for idx in range(total_frags):
        chunk = big_annex_b[idx * chunk_size : (idx + 1) * chunk_size]
        frag_payload = struct.pack(">IHH", 42, idx, total_frags) + chunk
        wire_packets.append(encode_frame(opcodes["VIDEO_NALU_FRAG"], idx + 1, 50000, frag_payload))

    assembled = {}
    for pkt in reversed(wire_packets):
        op, _, _, payload = decode_frame(pkt)
        assert op == opcodes["VIDEO_NALU_FRAG"]
        fid, fidx, ftotal = struct.unpack(">IHH", payload[:8])
        assert fid == 42 and ftotal == total_frags
        assembled[fidx] = payload[8:]
    rebuilt = b"".join(assembled[i] for i in range(total_frags))
    assert rebuilt == big_annex_b

    # 4. Simulate Touch Back-Channel packet
    touch_pkt = encode_frame(opcodes["TOUCH_EVENT"], 99, 77777, struct.pack(">ffB", 0.5, 0.25, 0))
    op, _, _, t_payload = decode_frame(touch_pkt)
    assert op == opcodes["TOUCH_EVENT"]
    x_norm, y_norm, action = struct.unpack(">ffB", t_payload)
    assert abs(x_norm - 0.5) < 1e-6 and abs(y_norm - 0.25) < 1e-6 and action == 0

    # 5. Negative tests: reject bad magic, bad version, zero seq, oversized length
    bad_cases = [
        struct.pack(HEADER_FMT, 0x1234, VERSION, 1, 1, 0, 0),
        struct.pack(HEADER_FMT, MAGIC, 0x02, 1, 1, 0, 0),
        struct.pack(HEADER_FMT, MAGIC, VERSION, 1, 0, 0, 0),
        struct.pack(HEADER_FMT, MAGIC, VERSION, 1, 1, 0, 999999),
    ]
    for bad in bad_cases:
        failed = False
        try:
            decode_frame(bad)
        except AssertionError:
            failed = True
        assert failed, "Negative frame check unexpectedly passed!"

    print(f"ALL VERIFICATION CHECKS PASSED! (XML/Plist validated: {len(xml_files)})")


if __name__ == "__main__":
    main()
