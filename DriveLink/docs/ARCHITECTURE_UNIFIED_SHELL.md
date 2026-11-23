# Architecture — Unified Shell (Phase 1–11)

> Status: design implemented in source; native builds and JVM/XCTest/Go tests **PASS** in CI. Device/vehicle tests and distribution signing remain **BLOCKED**.
> This document describes the target layering and the ownership rules now enforced in code.

## 1. Layers

```
UI (Compose / SwiftUI)
  └─ ViewModel / ObservableObject
      └─ MirrorSessionCoordinator (single session owner)
          ├─ SessionStateMachine      legal ordering, single-flight, generation guard
          ├─ CapabilityRegistry       explicit evidence-backed capabilities
          ├─ Transport                LAN/NSD, Wi-Fi Direct, Miracast (contract)
          ├─ BydProtocolWrapper       framing, crypto, identity, control loop
          ├─ ScreenCaptureEncoder     MediaCodec + VirtualDisplay + governor
          └─ TouchGeometry            normalized → rotated screen coordinates
Receiver (Android head unit)
  ├─ DriveLinkReceiverApp             process-wide server owner
  ├─ DriveLinkReceiverService         foreground + NSD lifecycle
  ├─ BydReceiverServer                approval gate, identity verify, single phone
  └─ TrustedSenderStore               public-key fingerprints only
```

## 2. Ownership rules

1. **One session owner.** `MirrorSessionCoordinator` is the only component allowed to move
   `ConnectionState`. UI reads flows; it never opens sockets.
2. **No media before approval.** `SESSION_START (0x07)` is a request. The receiver sets
   `sessionActive` and configures the decoder only inside `activateSession`, reached after
   `approvePendingSession()` or a trusted `DEVICE_IDENTITY (0x0A)` proof.
3. **Encoding follows the approved profile.** `ScreenCaptureEncoder.applyProfile()` applies
   bitrate/FPS live; resolution/codec changes restart exactly once.
4. **Touch is a gated side effect.** `BydTouchAccessibilityService` refuses every event unless
   `injectionAllowed` is true, which the coordinator sets only while `Streaming`.
5. **Receiver survives the UI.** `DriveLinkReceiverService` owns sockets and NSD; the activity is
   only a view/approval surface and may be destroyed without dropping the stream.

## 3. Sequence (normal, new sender)

```
Phone                         Receiver
  |-- HELLO_SYN -------------->|
  |<------------- HELLO_ACK ---|
  |-- KEY_EXCHANGE_INIT ------>|
  |<------------ KEY_EXCHANGE_RESP (salt + pub)
  |-- DEVICE_IDENTITY -------->| verify; trust lookup
  |-- CAPABILITIES_REQ ------->|
  |<--------- CAPABILITIES_RESP|
  |-- SESSION_START ---------->| request only; show approval
  |                            | user taps Approve
  |<--------- KEYFRAME_REQUEST-| activate decoder
  |-- VIDEO_NALU / AUDIO ----->| decode
```

Old senders that omit `0x0A` still work, but always require manual approval.

## 4. Explicit non-goals

- CarPlay: no entitlement/MFi; reported `UNSUPPORTED`.
- Android Auto: no projection category or desktop-head-unit implementation.
- Hardware attestation: not implemented and not claimed.
- USB/Bluetooth video: not implemented.

## 5. Remaining architectural gaps

- `MirrorTransport` is a contract, not yet a full adapter registry; LAN/NSD remains the primary
  path, Wi-Fi Direct and Miracast are selected in the ViewModel.
- `MirrorSessionCoordinator` is still an object singleton; dependency injection is a later refactor.
- Reconnect cancellation is best-effort generation-guarded, not a separate supervisor service.
