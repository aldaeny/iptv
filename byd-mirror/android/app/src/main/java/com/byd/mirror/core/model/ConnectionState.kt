package com.byd.mirror.core.model

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Discovering : ConnectionState
    data class Handshaking(val device: BydDevice) : ConnectionState
    data class KeyExchange(val device: BydDevice) : ConnectionState
    data class NegotiatingQuality(val device: BydDevice) : ConnectionState
    data class Streaming(
        val device: BydDevice,
        val profile: DisplayProfile,
        val orientation: DisplayOrientation,
        val encryptedChannelFingerprint: String
    ) : ConnectionState
    data class Reconnecting(val device: BydDevice, val attempt: Int) : ConnectionState
    data class Error(val message: String, val recoverable: Boolean = true) : ConnectionState
}
