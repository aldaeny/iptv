# PRIVACY POLICY & DATA HANDLING — BYD Wireless Mirror

1. **Zero Cloud Transmission:** BYD Wireless Mirror does not operate any remote servers, analytics, telemetry endpoints, or crash-reporting uploads.
2. **Strictly Local Wi-Fi / P2P Scope:** Screen pixels, device audio, touch events, and pairing metadata travel solely over your local Wi-Fi network, hotspot, or direct peer-to-peer link between your phone and vehicle display.
3. **End-to-End Encryption:** Every session generates fresh ephemeral ECDH P-256 keys and encrypts all media and control payloads with AES-256-GCM.
4. **Local Storage & Instant Deletion:** Only user-trusted vehicle IDs, display names, and 4-byte fingerprints are stored locally (Android `EncryptedSharedPreferences` backed by Keystore; iOS Keychain). Users can delete individual vehicles or clear all saved vehicles at any time inside **Advanced Settings → Saved Vehicles / Privacy**.
5. **No Sensitive Logging:** Pairing PINs, session keys, and raw frame contents are never written to system logs.
