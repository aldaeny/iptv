package app.drivelink.cast.core.model

enum class TransportType {
    WIFI_DIRECT,
    LAN_NSD,
    MIRACAST_SINK
}

data class BydDevice(
    val id: String,
    val displayName: String,
    val modelName: String,
    val diLinkVersion: String,
    val hostAddress: String,
    val controlPort: Int = 47810,
    val mediaPort: Int = 47811,
    val transportType: TransportType = TransportType.WIFI_DIRECT,
    val signalStrengthDbm: Int = -50,
    val supportsRotatingScreen: Boolean = true,
    val supportsHevc: Boolean = true
)
