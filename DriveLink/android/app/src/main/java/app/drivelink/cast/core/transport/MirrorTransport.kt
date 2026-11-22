package app.drivelink.cast.core.transport

import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.TransportType

enum class TransportKind {
    LAN_NSD,
    WIFI_DIRECT,
    MIRACAST
}

data class TransportEndpoint(
    val host: String,
    val controlPort: Int,
    val mediaPort: Int,
    val kind: TransportKind,
    val providesSurface: Boolean = false
)

/**
 * Transport contract. A transport only resolves/addressing; it must never move the session state
 * machine or start encoding. Miracast is the only kind that provides its own surface.
 */
interface MirrorTransport {
    val kind: TransportKind
    fun endpointFor(device: BydDevice, resolvedHost: String? = null): Result<TransportEndpoint>
}

class LanNsdTransport : MirrorTransport {
    override val kind = TransportKind.LAN_NSD

    override fun endpointFor(device: BydDevice, resolvedHost: String?): Result<TransportEndpoint> =
        runCatching {
            val host = resolvedHost?.takeIf { it.isNotBlank() } ?: device.hostAddress
            require(host.isNotBlank()) { "Missing LAN host" }
            require(device.controlPort in 1..65535 && device.mediaPort in 1..65535) {
                "Invalid BYD-WMP ports"
            }
            TransportEndpoint(host, device.controlPort, device.mediaPort, kind)
        }
}

class WifiDirectTransport : MirrorTransport {
    override val kind = TransportKind.WIFI_DIRECT

    override fun endpointFor(device: BydDevice, resolvedHost: String?): Result<TransportEndpoint> =
        runCatching {
            // A failed group-owner negotiation must not silently fall back to a hard-coded gateway.
            val host = resolvedHost?.takeIf { it.isNotBlank() }
                ?: device.hostAddress.takeIf { it.isNotBlank() }
                ?: error("Wi-Fi Direct group owner did not provide a host")
            TransportEndpoint(host, device.controlPort, device.mediaPort, kind)
        }
}

class MiracastTransport : MirrorTransport {
    override val kind = TransportKind.MIRACAST

    override fun endpointFor(device: BydDevice, resolvedHost: String?): Result<TransportEndpoint> =
        Result.success(
            TransportEndpoint(
                host = resolvedHost ?: device.hostAddress,
                controlPort = device.controlPort,
                mediaPort = device.mediaPort,
                kind = kind,
                providesSurface = true
            )
        )
}

object TransportSelector {
    fun forDevice(device: BydDevice): MirrorTransport = when (device.transportType) {
        TransportType.LAN_NSD -> LanNsdTransport()
        TransportType.WIFI_DIRECT -> WifiDirectTransport()
        TransportType.MIRACAST_SINK -> MiracastTransport()
    }
}
