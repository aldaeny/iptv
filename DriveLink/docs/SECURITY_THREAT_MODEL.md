# Security Threat Model

## Assets

- Screen/audio content (highest sensitivity).
- Session key `K` (AES-256-GCM).
- Installation identity private key (Android Keystore / iOS Keychain).
- Trust decisions (which sender may auto-approve).

## Adversaries

| Adversary | Capability | Mitigation |
|---|---|---|
| Passive LAN sniffer | Reads TCP/UDP | ECDH P-256 + HKDF + AES-256-GCM; AAD binds header |
| Active MITM | Replaces ECDH keys | PIN/SAS comparison on both screens; receiver approval |
| Replayer | Re-sends captured media/control | Monotonic TCP sequence; 64-packet UDP window; session-bound key |
| Malicious sender | Valid ECDH, no identity | Receiver requires manual approval; identity proof is optional |
| Identity thief | Replays a proof from another session | Signature covers `BYD-WMP-IDENTITY-V1|<sessionFingerprint>` |
| Network DoS | Opens many TCP sockets | Single active phone; new socket refused while active |
| Stale touch | Sends `TOUCH_EVENT` after session end | `injectionAllowed` gate + state check |
| Lost/stolen phone | Has Keystore identity | Revocation is local to the receiver trust store |

## Pairing flow

1. Both sides show a 6-digit PIN derived from `SHA-256(sessionKey)` (visual SAS).
2. Sender may send `DEVICE_IDENTITY (0x0A)` after key exchange.
3. Receiver verifies the signature over the current session fingerprint and looks up
   `SHA-256(publicKey SPKI)` in `TrustedSenderStore`.
4. Trusted + valid ⇒ auto-approve; otherwise the receiver shows Approve/Reject.
5. No decode before approval. `SESSION_START` is only a request.

## What is not claimed

- **No hardware attestation.** The receiver learns installation continuity, not device authenticity.
- **No protection on rooted/jailbroken devices.**
- **PIN is a 23-bit visual check, not a password.** It detects a mismatch; it is not a KDF.
- **iOS extensions may lack Keychain access**; then no proof is sent and manual approval is required.
  The code never invents a temporary identity.
- **No formal audit.** This is a design review, not a certification.

## Storage

- Receiver: public fingerprints in app-private `SharedPreferences`; no private keys, no PINs.
- Android sender: private key in Android Keystore, non-exportable; public SPKI sent per session.
- iOS sender: private key in Keychain, `AfterFirstUnlockThisDeviceOnly`; X9.63 public point sent.
- Trust stores are revocable and never contain private material.
