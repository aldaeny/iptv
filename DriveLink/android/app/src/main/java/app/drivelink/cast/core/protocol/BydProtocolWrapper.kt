package app.drivelink.cast.core.protocol

import app.drivelink.cast.core.model.BydDevice
import app.drivelink.cast.core.model.DisplayOrientation
import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.security.ChannelCryptoManager
import app.drivelink.cast.core.security.DeviceIdentitySigner
import app.drivelink.cast.core.security.IdentityProofCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

enum class ProtocolOpcode(val code: Byte) {
    HELLO_SYN(0x01),
    HELLO_ACK(0x02),
    KEY_EXCHANGE_INIT(0x03),
    KEY_EXCHANGE_RESP(0x04),
    CAPABILITIES_REQ(0x05),
    CAPABILITIES_RESP(0x06),
    SESSION_START(0x07),
    SESSION_STOP(0x08),
    ORIENTATION_CHANGE(0x09),
    DEVICE_IDENTITY(0x0A),
    VIDEO_NALU(0x10),
    AUDIO_AAC(0x11),
    KEYFRAME_REQUEST(0x12),
    VIDEO_NALU_FRAG(0x13),
    TOUCH_EVENT(0x14),
    HEARTBEAT_PING(0x20),
    HEARTBEAT_PONG(0x21),
    QOS_TELEMETRY(0x22),
    THERMAL_THROTTLE(0x23);

    companion object {
        fun fromByte(value: Byte): ProtocolOpcode? = entries.firstOrNull { it.code == value }
    }
}

data class BydPacket(
    val opcode: ProtocolOpcode,
    val sequence: Int,
    val timestampUs: Int,
    val payload: ByteArray
)

data class HandshakeResult(
    val fingerprint: String,
    val pairingPin: String,
    val supportsHevc: Boolean
)

/**
 * API wrapper simulating & presenting BYD DiLink infotainment screen as a standard
 * wireless external display sink using BYD-WMP v1.0 over TCP (control) + UDP (low-latency media).
 */
class BydProtocolWrapper(
    private val cryptoManager: ChannelCryptoManager = ChannelCryptoManager()
) {
    /** Optional installation identity; absent means the receiver must fall back to manual approval. */
    var identitySigner: DeviceIdentitySigner? = null
    private val sequenceCounter = AtomicInteger(0)
    private val udpSequenceCounter = AtomicInteger(0)
    private val frameIdCounter = AtomicInteger(0)
    private var controlSocket: Socket? = null
    private var udpMediaSocket: DatagramSocket? = null
    private var targetMediaAddr: InetAddress? = null
    private var targetMediaPort: Int = 47811
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null
    private var controlReaderJob: Job? = null
    @Volatile private var mediaStarted = false
    @Volatile private var encryptionActive = false
    private val sentVideoFrames = AtomicInteger(0)
    @Volatile
    private var lastRxSeq = 0
    @Volatile
    private var useTcpMediaFallback = false

    suspend fun connectAndHandshake(
        device: BydDevice,
        preferredProfile: DisplayProfile
    ): Result<HandshakeResult> = withContext(Dispatchers.IO) {
        runCatching {
            mediaStarted = false
            runCatching { udpMediaSocket?.close() }
            runCatching { controlSocket?.close() }
            sequenceCounter.set(0)
            udpSequenceCounter.set(0)
            sentVideoFrames.set(0)
            lastRxSeq = 0
            useTcpMediaFallback = false
            encryptionActive = false
            cryptoManager.reset()

            val socket = Socket().also { controlSocket = it }
            socket.tcpNoDelay = true
            socket.soTimeout = HANDSHAKE_READ_TIMEOUT_MS
            socket.connect(InetSocketAddress(device.hostAddress, device.controlPort), CONNECT_TIMEOUT_MS)
            outputStream = DataOutputStream(socket.getOutputStream())
            inputStream = DataInputStream(socket.getInputStream())

            udpMediaSocket = DatagramSocket()
            targetMediaAddr = InetAddress.getByName(device.hostAddress)
            targetMediaPort = device.mediaPort

            // Step 1: HELLO_SYN -> HELLO_ACK
            val helloPayload = "BYD-PHONE-1.0|${android.os.Build.MODEL}".toByteArray(Charsets.UTF_8)
            sendFrame(ProtocolOpcode.HELLO_SYN, helloPayload, encrypt = false)
            val helloAck = readFrame(decrypt = false)
            require(helloAck.opcode == ProtocolOpcode.HELLO_ACK) {
                "Unexpected handshake reply: ${helloAck.opcode}"
            }

            // Step 2: ECDH P-256 Key Exchange
            val clientPubKey = cryptoManager.generateEphemeralKeyPair()
            sendFrame(ProtocolOpcode.KEY_EXCHANGE_INIT, clientPubKey, encrypt = false)
            val keyResp = readFrame(decrypt = false)
            require(keyResp.opcode == ProtocolOpcode.KEY_EXCHANGE_RESP) {
                "Expected KEY_EXCHANGE_RESP, got ${keyResp.opcode}"
            }

            require(keyResp.payload.size > 16) { "Invalid KEY_EXCHANGE_RESP payload" }
            val sessionSalt = keyResp.payload.copyOfRange(0, 16)
            val peerPubKey = keyResp.payload.copyOfRange(16, keyResp.payload.size)
            cryptoManager.deriveSessionKey(peerPubKey, sessionSalt)
            encryptionActive = true

            // Step 2b: prove this installation without revealing the private key. The proof is
            // bound to this ECDH session, so it cannot be replayed into another one.
            identitySigner?.let { signer ->
                IdentityProofCodec.create(cryptoManager.sessionFingerprint, signer)?.let { proof ->
                    sendFrame(ProtocolOpcode.DEVICE_IDENTITY, proof, encrypt = true)
                }
            }

            // Step 3: Negotiate Display Capabilities (SESSION_START waits for PIN confirmation if untrusted)
            val capPayload = "${preferredProfile.id}|${preferredProfile.baseWidth}x${preferredProfile.baseHeight}@${preferredProfile.targetFps}"
                .toByteArray(Charsets.UTF_8)
            sendFrame(ProtocolOpcode.CAPABILITIES_REQ, capPayload, encrypt = true)
            val capAck = readFrame(decrypt = true)
            require(capAck.opcode == ProtocolOpcode.CAPABILITIES_RESP) {
                "Capabilities negotiation failed"
            }

            socket.soTimeout = 0
            HandshakeResult(
                fingerprint = cryptoManager.sessionFingerprint,
                supportsHevc = capAck.payload.decodeToString().substringAfter('|', "").split(',').any { it.trim() == "H265" },
                pairingPin = cryptoManager.pairingPin
            )
        }.onFailure {
            encryptionActive = false
            cryptoManager.reset()
            runCatching { udpMediaSocket?.close() }
            runCatching { controlSocket?.close() }
            udpMediaSocket = null
            controlSocket = null
            outputStream = null
            inputStream = null
        }
    }

    suspend fun startMediaSession(profile: DisplayProfile, fingerprint: String) = withContext(Dispatchers.IO) {
        check(encryptionActive && cryptoManager.sessionFingerprint == fingerprint) { "Session changed; confirm the new PIN" }
        sendFrame(
            ProtocolOpcode.SESSION_START,
            profile.codec.mimeType.toByteArray(Charsets.UTF_8),
            encrypt = true
        )
        mediaStarted = true
    }

    fun startControlReadLoop(
        scope: CoroutineScope,
        onKeyframeRequested: () -> Unit,
        onQosReport: (fps: Int, droppedPackets: Int) -> Unit,
        onHeartbeatRtt: (rttMs: Int) -> Unit,
        onTouchEvent: (xNorm: Float, yNorm: Float, action: Byte) -> Unit = { _, _, _ -> },
        onUnexpectedDisconnect: () -> Unit = {}
    ) {
        controlReaderJob?.cancel()
        controlReaderJob = scope.launch(Dispatchers.IO) {
            var gracefulStop = false
            while (isActive && controlSocket?.isClosed == false) {
                val packet = runCatching { readFrame(decrypt = true) }.getOrNull() ?: break
                if (packet.sequence <= lastRxSeq) break
                lastRxSeq = packet.sequence
                when (packet.opcode) {
                    ProtocolOpcode.KEYFRAME_REQUEST -> onKeyframeRequested()
                    ProtocolOpcode.QOS_TELEMETRY -> {
                        if (packet.payload.size >= 8) {
                            val buf = ByteBuffer.wrap(packet.payload).order(ByteOrder.BIG_ENDIAN)
                            val rxFps = buf.int
                            val dropped = buf.int
                            if (rxFps == 0 && !useTcpMediaFallback && sentVideoFrames.get() >= 15) {
                                useTcpMediaFallback = true
                                onKeyframeRequested()
                            }
                            onQosReport(rxFps, dropped)
                        }
                    }
                    ProtocolOpcode.HEARTBEAT_PONG -> {
                        if (packet.payload.size >= 8) {
                            val sentNs = ByteBuffer.wrap(packet.payload).order(ByteOrder.BIG_ENDIAN).long
                            val rttMs = ((System.nanoTime() - sentNs) / 1_000_000L).toInt().coerceAtLeast(1)
                            onHeartbeatRtt(rttMs)
                        }
                    }
                    ProtocolOpcode.TOUCH_EVENT -> {
                        if (packet.payload.size >= 9) {
                            val buf = ByteBuffer.wrap(packet.payload).order(ByteOrder.BIG_ENDIAN)
                            val xNorm = buf.float
                            val yNorm = buf.float
                            val action = buf.get()
                            onTouchEvent(xNorm, yNorm, action)
                        }
                    }
                    ProtocolOpcode.SESSION_STOP -> {
                        gracefulStop = true
                        break
                    }
                    else -> Unit
                }
            }
            if (!gracefulStop && isActive) {
                onUnexpectedDisconnect()
            }
        }
    }

    suspend fun sendVideoFrame(nalu: ByteArray, timestampUs: Int) = withContext(Dispatchers.IO) {
        sendMediaUdp(ProtocolOpcode.VIDEO_NALU, nalu, timestampUs)
    }

    suspend fun sendAudioFrame(aacFrame: ByteArray, timestampUs: Int) = withContext(Dispatchers.IO) {
        sendMediaUdp(ProtocolOpcode.AUDIO_AAC, aacFrame, timestampUs)
    }

    suspend fun sendHeartbeatPing() = withContext(Dispatchers.IO) {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(System.nanoTime()).array()
        sendFrame(ProtocolOpcode.HEARTBEAT_PING, payload, encrypt = true)
    }

    suspend fun sendOrientationChange(orientation: DisplayOrientation) = withContext(Dispatchers.IO) {
        val flag = byteArrayOf(if (orientation == DisplayOrientation.LANDSCAPE) 0x01 else 0x00)
        sendFrame(ProtocolOpcode.ORIENTATION_CHANGE, flag, encrypt = true)
    }

    suspend fun sendThermalThrottleNotice(targetFps: Int, targetBitrateMbps: Int) = withContext(Dispatchers.IO) {
        val payload = ByteBuffer.allocate(8)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(targetFps)
            .putInt(targetBitrateMbps)
            .array()
        sendFrame(ProtocolOpcode.THERMAL_THROTTLE, payload, encrypt = true)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mediaStarted = false
        controlReaderJob?.cancel()
        controlReaderJob = null
        runCatching {
            if (controlSocket?.isConnected == true) {
                sendFrame(ProtocolOpcode.SESSION_STOP, ByteArray(0), encrypt = true)
            }
        }
        encryptionActive = false
        cryptoManager.reset()
        runCatching { udpMediaSocket?.close() }
        runCatching { controlSocket?.close() }
        udpMediaSocket = null
        controlSocket = null
        outputStream = null
        inputStream = null
    }

    private fun sendMediaUdp(opcode: ProtocolOpcode, rawPayload: ByteArray, timestampUs: Int) {
        if (!mediaStarted) return
        if (opcode == ProtocolOpcode.VIDEO_NALU) {
            sentVideoFrames.incrementAndGet()
        }
        val udp = udpMediaSocket
        val addr = targetMediaAddr
        if (useTcpMediaFallback || udp == null || addr == null) {
            sendMediaTcp(opcode, rawPayload, timestampUs)
            return
        }

        if (opcode == ProtocolOpcode.VIDEO_NALU && rawPayload.size > UDP_CHUNK_LIMIT_BYTES) {
            val frameId = frameIdCounter.incrementAndGet()
            val totalFrags = (rawPayload.size + UDP_CHUNK_LIMIT_BYTES - 1) / UDP_CHUNK_LIMIT_BYTES
            for (fragIdx in 0 until totalFrags) {
                val start = fragIdx * UDP_CHUNK_LIMIT_BYTES
                val end = minOf(rawPayload.size, start + UDP_CHUNK_LIMIT_BYTES)
                val chunkLen = end - start
                val fragPayload = ByteBuffer.allocate(8 + chunkLen)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(frameId)
                    .putShort(fragIdx.toShort())
                    .putShort(totalFrags.toShort())
                    .put(rawPayload, start, chunkLen)
                    .array()
                dispatchUdpPacket(udp, addr, ProtocolOpcode.VIDEO_NALU_FRAG, fragPayload, timestampUs)
            }
            return
        }

        dispatchUdpPacket(udp, addr, opcode, rawPayload, timestampUs)
    }

    private fun sendMediaTcp(opcode: ProtocolOpcode, rawPayload: ByteArray, timestampUs: Int) {
        if (opcode == ProtocolOpcode.VIDEO_NALU && rawPayload.size > TCP_CHUNK_LIMIT_BYTES) {
            val frameId = frameIdCounter.incrementAndGet()
            val totalFrags = (rawPayload.size + TCP_CHUNK_LIMIT_BYTES - 1) / TCP_CHUNK_LIMIT_BYTES
            for (fragIdx in 0 until totalFrags) {
                val start = fragIdx * TCP_CHUNK_LIMIT_BYTES
                val end = minOf(rawPayload.size, start + TCP_CHUNK_LIMIT_BYTES)
                val chunkLen = end - start
                val fragPayload = ByteBuffer.allocate(8 + chunkLen)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(frameId)
                    .putShort(fragIdx.toShort())
                    .putShort(totalFrags.toShort())
                    .put(rawPayload, start, chunkLen)
                    .array()
                sendFrame(ProtocolOpcode.VIDEO_NALU_FRAG, fragPayload, timestampUs, encrypt = true)
            }
            return
        }
        sendFrame(opcode, rawPayload, timestampUs, encrypt = true)
    }

    private fun dispatchUdpPacket(
        udp: DatagramSocket,
        addr: InetAddress,
        opcode: ProtocolOpcode,
        rawPayload: ByteArray,
        timestampUs: Int
    ) {
        val seq = udpSequenceCounter.incrementAndGet()
        val wireSize = rawPayload.size + ENCRYPTION_OVERHEAD_BYTES
        val header = buildHeader(opcode, seq, timestampUs, wireSize)
        val payload = cryptoManager.encrypt(rawPayload, header)
        val packetBytes = header + payload
        udp.send(DatagramPacket(packetBytes, packetBytes.size, addr, targetMediaPort))
    }

    @Synchronized
    private fun sendFrame(
        opcode: ProtocolOpcode,
        rawPayload: ByteArray,
        timestampUs: Int = (System.nanoTime() / 1_000L).toInt(),
        encrypt: Boolean
    ) {
        val out = outputStream ?: return
        val seq = sequenceCounter.incrementAndGet()

        val wirePayloadSize = if (encrypt) {
            rawPayload.size + ENCRYPTION_OVERHEAD_BYTES
        } else {
            rawPayload.size
        }
        val wireHeader = buildHeader(opcode, seq, timestampUs, wirePayloadSize)
        val wirePayload = if (encrypt) {
            cryptoManager.encrypt(rawPayload, wireHeader)
        } else {
            rawPayload
        }

        out.write(wireHeader)
        out.write(wirePayload)
        out.flush()
    }

    private fun readFrame(decrypt: Boolean): BydPacket {
        val input = requireNotNull(inputStream) { "Control channel not open" }
        val header = ByteArray(HEADER_SIZE_BYTES)
        input.readFully(header)

        val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val magic = buf.short
        require(magic == MAGIC_BYTES) { "Invalid BYD protocol magic: $magic" }
        val version = buf.get()
        require(version == PROTOCOL_VERSION) { "Unsupported protocol version: $version" }
        val opcodeByte = buf.get()
        val opcode = requireNotNull(ProtocolOpcode.fromByte(opcodeByte)) {
            "Unknown opcode: $opcodeByte"
        }
        val seq = buf.int
        require(seq > 0) { "Invalid sequence number: $seq" }
        val timestampUs = buf.int
        val payloadLen = buf.int
        require(payloadLen in 0..MAX_PAYLOAD_BYTES) { "Invalid payload length: $payloadLen" }

        val wirePayload = ByteArray(payloadLen)
        input.readFully(wirePayload)

        val payload = if (decrypt) {
            val aadHeader = buildHeader(opcode, seq, timestampUs, payloadLen)
            cryptoManager.decrypt(wirePayload, aadHeader)
        } else {
            wirePayload
        }

        return BydPacket(opcode, seq, timestampUs, payload)
    }

    fun buildHeader(
        opcode: ProtocolOpcode,
        sequence: Int,
        timestampUs: Int,
        payloadLength: Int
    ): ByteArray {
        return ByteBuffer.allocate(HEADER_SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(MAGIC_BYTES)
            .put(PROTOCOL_VERSION)
            .put(opcode.code)
            .putInt(sequence)
            .putInt(timestampUs)
            .putInt(payloadLength)
            .array()
    }

    companion object {
        const val MAGIC_BYTES: Short = 0x4259 // "BY"
        const val PROTOCOL_VERSION: Byte = 0x01
        const val HEADER_SIZE_BYTES = 16
        const val ENCRYPTION_OVERHEAD_BYTES = 28 // 12-byte GCM nonce + 16-byte auth tag
        const val UDP_CHUNK_LIMIT_BYTES = 1_150 // Keeps encrypted UDP packet safely below 1500B MTU
        const val TCP_CHUNK_LIMIT_BYTES = 32_000
        const val MAX_PAYLOAD_BYTES = 65_536
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val HANDSHAKE_READ_TIMEOUT_MS = 6_000
    }
}
