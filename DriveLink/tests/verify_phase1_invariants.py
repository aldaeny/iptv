#!/usr/bin/env python3
"""Phase 1-11 source invariants that do not require a JDK/Xcode/device.

These checks protect the *design* that the native builds verify:
- no streaming before receiver approval;
- capability states are explicit and evidence-backed;
- encoder reconfiguration is live for bitrate/FPS;
- reverse touch is geometry-checked and gated;
- DEVICE_IDENTITY is session-bound and optional (manual fallback).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    path = ROOT / rel
    assert path.is_file(), f"missing file: {rel}"
    return path.read_text(encoding="utf-8")


def require(haystack: str, needle: str, label: str) -> None:
    assert needle in haystack, f"{label}: missing {needle!r}"


def main() -> int:
    capability = read("android/app/src/main/java/app/drivelink/cast/core/capability/CapabilityRegistry.kt")
    for token in (
        "enum class CapabilityState",
        "BYD_WMP_TRANSPORT",
        "CARPLAY",
        "ANDROID_AUTO",
        "UNSUPPORTED",
        "require(evidence.isNotBlank())",
        "CI_BASELINE_EVIDENCE",
    ):
        require(capability, token, "Android capability registry")
    assert "assertsUsable" in capability

    session = read("android/app/src/main/java/app/drivelink/cast/core/session/SessionStateMachine.kt")
    for phase in (
        "Idle", "Discovering", "Connecting", "Handshaking",
        "AwaitingApproval", "Streaming", "Reconnecting", "Stopping", "Failed",
    ):
        require(session, phase, "session state machine")
    require(session, "SessionPhase.Handshaking to setOf(", "session transitions")
    assert "SessionPhase.AwaitingApproval" in session
    assert "enterExternalStreaming" in session

    encoder = read("android/app/src/main/java/app/drivelink/cast/core/streaming/ScreenCaptureEncoder.kt")
    require(encoder, "fun applyProfile(", "encoder")
    require(encoder, "frameGovernor.shouldEmit", "encoder")
    require(encoder, "EncoderReconfiguration.plan", "encoder")
    reconfig = read("android/app/src/main/java/app/drivelink/cast/core/streaming/EncoderReconfiguration.kt")
    require(reconfig, "requiresRestart", "encoder plan")
    require(reconfig, "class FrameRateGovernor", "frame governor")

    touch_geometry = read("android/app/src/main/java/app/drivelink/cast/core/touch/TouchGeometry.kt")
    require(touch_geometry, "rotationDegrees", "touch geometry")
    require(touch_geometry, "strokeDurationMs", "touch geometry")
    touch_service = read("android/app/src/main/java/app/drivelink/cast/service/BydTouchAccessibilityService.kt")
    for token in ("injectionAllowed", "currentWindowMetrics", "TouchGeometry.toScreen", "MAX_MOVE_POINTS = 512"):
        require(touch_service, token, "touch service")

    receiver = read("receiver-dilink/app/src/main/java/app/drivelink/receiver/protocol/BydReceiverServer.kt")
    for token in (
        "fun approvePendingSession(): Boolean",
        "sessionApproved",
        "sessionActive",
        "OPCODE_DEVICE_IDENTITY: Byte = 0x0A",
        "ReceiverIdentityVerifier.verify",
        "if (clientJob?.isActive == true)",
        "PAIRING_CONFIRM_TIMEOUT_NS",
        "activateSession",
        "sessionApproved = pendingIdentityTrusted",
    ):
        require(receiver, token, "receiver server")
    assert "handshakeCompleted" not in receiver, "receiver must use explicit approval/session flags"
    # SESSION_START must not activate unconditionally.
    start = receiver.index("OPCODE_SESSION_START ->")
    end = receiver.index("OPCODE_ORIENTATION_CHANGE ->", start)
    start_block = receiver[start:end]
    require(start_block, "if (sessionApproved)", "receiver SESSION_START gate")
    require(start_block, "else {", "receiver SESSION_START approval branch")

    verifier = read("receiver-dilink/app/src/main/java/app/drivelink/receiver/protocol/ReceiverIdentityVerifier.kt")
    for token in ("P256_SPKI_PREFIX", "SHA256withECDSA", "BYD-WMP-IDENTITY-V1|", "canonicalSpki"):
        require(verifier, token, "receiver identity verifier")

    app = read("receiver-dilink/app/src/main/java/app/drivelink/receiver/DriveLinkReceiverApp.kt")
    service = read("receiver-dilink/app/src/main/java/app/drivelink/receiver/DriveLinkReceiverService.kt")
    manifest = read("receiver-dilink/app/src/main/AndroidManifest.xml")
    require(app, "BydReceiverServer(trustStore = trustStore)", "receiver app owner")
    require(service, "startForeground(", "receiver service")
    require(service, "server.start(scope)", "receiver service")
    require(manifest, "foregroundServiceType=\"mediaPlayback\"", "receiver manifest")
    require(manifest, 'android:name=".DriveLinkReceiverApp"', "receiver manifest")

    proto = json.loads(read("shared/protocol/byd_mirror_protocol.json"))
    assert proto["version"] == "1.2.0", proto["version"]
    assert proto["wireVersion"] == 1
    assert proto["packetFraming"]["opcodes"]["DEVICE_IDENTITY"] == 0x0A
    assert proto["security"]["approvalRequired"] is True
    assert proto["security"]["deviceIdentity"]["attestation"] is False
    assert proto["sessionLifecycle"]["decodeBeforeApproval"] is False

    ios_identity = read("ios/Sources/Core/Security/DeviceIdentityManager.swift")
    for token in ("P256.Signing.PrivateKey", "BYD-WMP-IDENTITY-V1|", "x963Representation", "SecItemAdd"):
        require(ios_identity, token, "iOS identity")
    ios_capability = read("ios/Sources/Core/Capability/CapabilityRegistry.swift")
    require(ios_capability, "case carplay", "iOS capability")
    require(ios_capability, "case reverseTouch", "iOS capability")
    ios_protocol = read("ios/Sources/Core/Protocol/BydProtocolWrapper.swift")
    require(ios_protocol, "case deviceIdentity = 0x0A", "iOS protocol")
    ios_app = read("ios/Sources/Core/Session/DirectMirrorSession.swift")
    ios_ext = read("ios/BroadcastExtension/SampleHandler.swift")
    require(ios_app, "opcode: .deviceIdentity", "iOS app identity send")
    require(ios_ext, "opcode: .deviceIdentity", "iOS extension identity send")

    for doc in (
        "docs/ARCHITECTURE_UNIFIED_SHELL.md",
        "docs/CAPABILITY_REGISTRY.md",
        "docs/SESSION_STATE_MACHINE.md",
        "docs/TRANSPORT_ABSTRACTION.md",
        "docs/SECURITY_THREAT_MODEL.md",
        "docs/DEVICE_COMPATIBILITY_MATRIX.md",
        "docs/TEST_EVIDENCE_PLAN.md",
    ):
        assert (ROOT / doc).is_file(), f"missing document: {doc}"

    print("PHASE 1-11 INVARIANTS PASSED")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"INVARIANT FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
