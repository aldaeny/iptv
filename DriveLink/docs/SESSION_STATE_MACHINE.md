# Session State Machine

`SessionStateMachine.kt` owns legal ordering. `ConnectionState` continues to carry UI details
(device, profile, fingerprint), but it cannot move to `Streaming` unless the machine allows it.

## Phases

```
Idle ──► Discovering ──► Connecting ──► Handshaking ──► AwaitingApproval ──► Streaming
  │           │              │              │                  │                 │
  │           └──────────────┴──────────────┴──────────────────┴──► Failed        ├─► Reconnecting
  │                                                                                 │
  └──────────────────────────── Stopping ◄──────────────────────────────────────────┘
                                   │
                                   └──► Idle
```

| From | Allowed next |
|---|---|
| `Idle` | `Discovering`, `Connecting`, `Failed` |
| `Discovering` | `Connecting`, `Idle`, `Failed` |
| `Connecting` | `Handshaking`, `Failed`, `Stopping` |
| `Handshaking` | `AwaitingApproval`, `Failed`, `Stopping` |
| `AwaitingApproval` | `Streaming`, `Failed`, `Stopping` |
| `Streaming` | `Reconnecting`, `Stopping`, `Failed` |
| `Reconnecting` | `Connecting`, `Failed`, `Stopping` |
| `Stopping` | `Idle`, `Failed` |
| `Failed` | `Idle`, `Discovering`, `Connecting` |

`Handshaking → Streaming` is deliberately absent. A handshake alone must never start decoding.

## Generation guard

Every accepted transition increments `generation`. Async work captures the generation it started
with; `isCurrent(generation)` is checked after every suspension point. This prevents a stale
connect/reconnect coroutine from resurrecting a session the user cancelled.

## Miracast exception

Miracast is a platform sink with no BYD-WMP handshake. `enterExternalStreaming()` is the only
explicit bypass; the normal path cannot call it. It is audited by
`verify_phase1_invariants.py`.

## Tests

`SessionStateMachineTest` covers: happy path, no streaming without approval, stale generation,
stopping cannot jump to streaming, generation changes, and connection acceptance.
