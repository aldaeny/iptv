// Host integration only. Run with the real Go simulator and API 35 android.jar on the classpath.
// Confirms encrypted transport/reassembly via receiver QoS, not physical display or decoding.
import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.protocol.BydProtocolWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket

fun main() = runBlocking<Unit> {
    val client = BydProtocolWrapper()
    val peer = BydDevice("loopback", "Receiver", "Simulator", "5.0", "127.0.0.1")
    val profile = DisplayProfile.DILINK_12_8_FHD
    val closedPort = ServerSocket(0).use { it.localPort }
    check(client.connectAndHandshake(peer.copy(controlPort = closedPort), profile).isFailure)
    check(client.connectAndHandshake(peer.copy(controlPort = -1), profile).isFailure)
    println("PASS unavailable endpoint and invalid port")
    // Accept but never answer HELLO. The production read timeout must release the connection.
    ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { silent ->
        check(client.connectAndHandshake(peer.copy(controlPort = silent.localPort), profile)
            .exceptionOrNull() is java.net.SocketTimeoutException)
    }
    println("PASS handshake read timeout")
    repeat(2) { round ->
        val hs = client.connectAndHandshake(peer, profile).getOrThrow()
        try {
            check(hs.pairingPin.length == 6 && !hs.supportsHevc)
            check(runCatching { client.startMediaSession(profile, "stale confirmation") }.isFailure)
            client.startMediaSession(profile, hs.fingerprint)
            val pong = CompletableDeferred<Unit>()
            val received = CompletableDeferred<Unit>()
            client.startControlReadLoop(this, {}, { fps, _ -> if (fps > 0) received.complete(Unit) }, { pong.complete(Unit) })
            client.sendHeartbeatPing()
            client.sendVideoFrame(ByteArray(3300), 1_000)
            withTimeout(5_000L) { pong.await(); received.await() }
            println("PASS encrypted TCP heartbeat and fragmented UDP receipt, session ${round + 1}")
        } finally {
            client.disconnect()
        }
    }
}
