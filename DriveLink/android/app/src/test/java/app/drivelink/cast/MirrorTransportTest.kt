package app.drivelink.cast

import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.TransportType
import app.drivelink.cast.core.transport.LanNsdTransport
import app.drivelink.cast.core.transport.MiracastTransport
import app.drivelink.cast.core.transport.TransportKind
import app.drivelink.cast.core.transport.TransportSelector
import app.drivelink.cast.core.transport.WifiDirectTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorTransportTest {

    private fun device(type: TransportType, host: String = "192.168.49.1") = BydDevice(
        id = "byd-test",
        displayName = "BYD Test",
        modelName = "BYD DiLink",
        diLinkVersion = "5.0",
        hostAddress = host,
        transportType = type
    )

    @Test
    fun lanTransportUsesResolvedHost() {
        val endpoint = LanNsdTransport()
            .endpointFor(device(TransportType.LAN_NSD), "10.0.0.5")
            .getOrThrow()
        assertEquals("10.0.0.5", endpoint.host)
        assertEquals(TransportKind.LAN_NSD, endpoint.kind)
        assertFalse(endpoint.providesSurface)
    }

    @Test
    fun wifiDirectFailsWithoutGroupOwnerOrHost() {
        assertTrue(
            WifiDirectTransport()
                .endpointFor(device(TransportType.WIFI_DIRECT, host = ""), null)
                .isFailure
        )
    }

    @Test
    fun miracastProvidesItsOwnSurface() {
        val endpoint = MiracastTransport()
            .endpointFor(device(TransportType.MIRACAST_SINK))
            .getOrThrow()
        assertTrue(endpoint.providesSurface)
        assertEquals(TransportKind.MIRACAST, endpoint.kind)
    }

    @Test
    fun selectorMapsEveryTransportType() {
        assertEquals(TransportKind.LAN_NSD, TransportSelector.forDevice(device(TransportType.LAN_NSD)).kind)
        assertEquals(TransportKind.WIFI_DIRECT, TransportSelector.forDevice(device(TransportType.WIFI_DIRECT)).kind)
        assertEquals(TransportKind.MIRACAST, TransportSelector.forDevice(device(TransportType.MIRACAST_SINK)).kind)
    }
}
