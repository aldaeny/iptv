# Transport Abstraction

## Contract

The intended contract is:

```kotlin
interface MirrorTransport {
    val kind: TransportKind
    suspend fun connect(device: BydDevice): Result<TransportEndpoint>
    fun disconnect()
}
```

`TransportEndpoint` carries `host`, `controlPort`, `mediaPort`, `kind`, and whether the transport
provides its own surface (Miracast) or needs the BYD-WMP codec path.

## Current reality

| Transport | Discovery | Connection | Status |
|---|---|---|---|
| LAN / NSD | `NsdDiscoveryManager` + UDP 47813 | TCP 47810 / UDP 47811 | Primary, implemented |
| Wi-Fi Direct | `WifiP2pTransport` | group-owner host resolution, then LAN | Implemented, not device-verified |
| Miracast | `MiracastDisplayAdapter` | platform sink selection | Implemented, not vehicle-verified |

The ViewModel still branches on `TransportType` directly. A registry/factory is a follow-up
refactor; this document does **not** claim it exists.

## Invariants

1. Transport selection never bypasses `SessionStateMachine`.
2. A transport that supplies its own surface (Miracast) does not enter the BYD-WMP approval path.
3. If Wi-Fi Direct group-owner negotiation fails, the original device host is used rather than a
   hard-coded `192.168.49.1` fallback.
4. Media sockets are closed on `Stopping`/`Failed`; the next connection starts with fresh sequence
   and crypto state.

## Known gaps

- No automatic transport scoring/fallback (LAN → P2P → Miracast).
- TCP media fallback is one-way; returning to UDP is not implemented.
- No USB/Bluetooth transport.
