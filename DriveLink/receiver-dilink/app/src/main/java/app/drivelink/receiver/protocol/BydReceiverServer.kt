package app.drivelink.receiver.protocol

import android.content.Context
import android.media.MediaFormat
import android.view.Surface
import app.drivelink.receiver.crypto.ReceiverCryptoManager
import app.drivelink.receiver.decoder.VideoAudioDecoder
import app.drivelink.receiver.security.TrustedSender
import app.drivelink.receiver.security.TrustedSenderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

sealed interface ReceiverState {
    data object WaitingForPhone : ReceiverState
    data class PairingVerification(
        val phoneName: String,
        val pin: String,
        val fingerprint: String,
        val identityFingerprint: String? = null,
        val trusted: Boolean = false,
        val approved: Boolean = false,
        val warning: String? = null
    ) : ReceiverState
    data class MirroringActive(
        val phoneName: String,
        val fingerprint: String,
        val isLandscape: Boolean,
        val fps: Int
    ) : ReceiverState
}

/**
 * Runs on BYD DiLink head unit:
 * - TCP 47810: Async preempting control channel, ECDH P-256 key exchange, orientation & QoS telemetry
 * - UDP 47811: Low-latency encrypted VIDEO_NALU & AUDIO_AAC streaming
 * - UDP 47813: Zero-config LAN discovery beacon
 */
class BydReceiverServer(
    private val decoder: VideoAudioDecoder = VideoAudioDecoder(),
    private val crypto: ReceiverCryptoManager = ReceiverCryptoManager(),
    private val trustStore: TrustedSenderStore? = null
) {
    private val _state = MutableStateFlow<ReceiverState>(ReceiverState.WaitingForPhone)
    val state: StateFlow<ReceiverState> = _state.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _trustedSenders = MutableStateFlow<List<TrustedSender>>(emptyList())
    val trustedSenders: StateFlow<List<TrustedSender>> = _trustedSenders.asStateFlow()

    init {
        refreshTrustedSenders()
    }

    private var tcpServer: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    @Volatile
    private var activeClientSocket: Socket? = null
    @Volatile
    private var activeClientOut: DataOutputStream? = null
    private var targetSurface: Surface? = null
    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var udpJob: Job? = null
    private var beaconJob: Job? = null
    private var qosJob: Job? = null

    @Volatile
    private var encryptionActive = false
    private val outSequence = AtomicInteger(0)
    private val failedHandshakes = AtomicInteger(0)
    private val antiReplay = AntiReplayWindow()
    private val reassembler = UdpFrameReassembler()
    private var lastTcpSeq = 0
    private var lastVideoSeq = 0
    @Volatile private var activeMime: String? = null
    @Volatile private var activeLandscape: Boolean = true
    @Volatile private var activeBaseWidth: Int = 1920
    @Volatile private var activeBaseHeight: Int = 1080
    @Volatile private var sessionApproved = false
    @Volatile private var sessionActive = false
    @Volatile private var pendingSinceNs = 0L
    @Volatile private var pendingMime: String? = null
    @Volatile private var pendingLandscape: Boolean = true
    @Volatile private var pendingPhoneName: String = "BYD Phone"
    @Volatile private var verifiedIdentityFingerprint: String? = null
    @Volatile private var pendingIdentityTrusted = false
    private val receivedFramesWindow = AtomicInteger(0)
    private val droppedFrameCount = AtomicInteger(0)

    // Exponential backoff + IP cooldown for failed handshakes
    private val failedHandshakesPerIp = ConcurrentHashMap<String, AtomicInteger>()
    private val ipCooldownUntil = ConcurrentHashMap<String, AtomicLong>()
    private val maxBackoffMs = 60_000L // 60 seconds max
    private val baseBackoffMs = 1_000L // 1 second base
    private val maxFailedBeforeCooldown = 3

    fun attachSurface(surface: Surface?) {
        targetSurface = surface
        val mime = activeMime
        if (surface != null && mime != null) {
            val w = if (activeLandscape) maxOf(activeBaseWidth, activeBaseHeight) else minOf(activeBaseWidth, activeBaseHeight)
            val h = if (activeLandscape) minOf(activeBaseWidth, activeBaseHeight) else maxOf(activeBaseWidth, activeBaseHeight)
            decoder.configureVideo(surface, w, h, mime)
        }
    }

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        decoder.isMuted = next
    }

    fun disconnectActiveSession() {
        sessionApproved = false
        sessionActive = false
        pendingSinceNs = 0L
        val out = activeClientOut
        val sock = activeClientSocket
        if (out != null) {
            runCatching { sendControlFrame(out, OPCODE_SESSION_STOP, ByteArray(0)) }
        }
        runCatching { sock?.close() }
    }

    /**
     * Explicit user approval from the head-unit UI. Returns false when there is nothing waiting,
     * so a double tap cannot approve a different session that arrived in between.
     *
     * @param alwaysTrust If true, this sender will be auto-approved on future connections.
     */
    fun approvePendingSession(alwaysTrust: Boolean = false): Boolean {
        val pending = _state.value as? ReceiverState.PairingVerification ?: return false
        if (pending.approved) return true
        sessionApproved = true
        pendingSinceNs = 0L
        verifiedIdentityFingerprint?.let { fingerprint ->
            trustStore?.trust(fingerprint, pending.phoneName, alwaysTrust)
            refreshTrustedSenders()
        }
        _state.value = pending.copy(approved = true, trusted = true)
        val mime = pendingMime
        if (mime != null) {
            activateSession(mime, pendingLandscape)
        }
        return true
    }

    fun setTrustedSenderAlwaysTrust(fingerprint: String, alwaysTrust: Boolean) {
        trustStore?.setAlwaysTrust(fingerprint, alwaysTrust)
        refreshTrustedSenders()
    }

    fun removeTrustedSender(fingerprint: String) {
        trustStore?.remove(fingerprint)
        refreshTrustedSenders()
    }

    fun clearTrustedSenders() {
        trustStore?.clear()
        refreshTrustedSenders()
    }

    private fun refreshTrustedSenders() {
        _trustedSenders.value = trustStore?.list().orEmpty()
    }

    fun denyPendingSession() {
        sessionApproved = false
        sessionActive = false
        pendingSinceNs = 0L
        runCatching { activeClientSocket?.close() }
    }

    fun sendTouchEvent(xNorm: Float, yNorm: Float, action: Byte) {
        val out = activeClientOut ?: return
        if (!encryptionActive) return
        val payload = ByteBuffer.allocate(9)
            .order(ByteOrder.BIG_ENDIAN)
            .putFloat(xNorm.coerceIn(0f, 1f))
            .putFloat(yNorm.coerceIn(0f, 1f))
            .put(action)
            .array()
        runCatching { sendControlFrame(out, OPCODE_TOUCH_EVENT, payload) }
    }

    fun start(scope: CoroutineScope) {
        stop()
        serverJob = scope.launch(Dispatchers.IO) {
            val server = try { ServerSocket(CONTROL_PORT) } catch (e: java.io.IOException) {
                android.util.Log.e("DriveLink", "Control listener unavailable", e)
                return@launch
            }
            tcpServer = server
            startUdpMediaLoop(scope)
            startBeaconBroadcaster(scope)
            while (isActive) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                // Never let a second phone kill an active session: refuse the new socket instead.
                if (clientJob?.isActive == true) {
                    runCatching { client.close() }
                    continue
                }
                clientJob = launch(Dispatchers.IO) {
                    client.use {
                        try { handleClient(it, scope) } catch (e: java.io.IOException) {
                            android.util.Log.w("DriveLink", "Control connection closed during setup", e)
                        }
                    }
                }
            }
        }
    }

    private fun startBeaconBroadcaster(scope: CoroutineScope) {
        beaconJob = scope.launch(Dispatchers.IO) {
            val socket = runCatching { DatagramSocket().apply { broadcast = true } }.getOrNull() ?: return@launch
            val hevcSupported = decoder.supportsHevcDecoder()
            val payload = """{"proto":"byd-mirror-v1","name":"BYD DiLink Receiver","model":"BYD DiLink 5.0","port":$CONTROL_PORT,"mediaPort":$MEDIA_UDP_PORT,"hevc":$hevcSupported}"""
                .toByteArray(Charsets.UTF_8)
            socket.use {
                while (isActive) {
                    for (addr in resolveBroadcastAddresses()) {
                        runCatching { socket.send(DatagramPacket(payload, payload.size, addr, BEACON_UDP_PORT)) }
                    }
                    delay(1_000L)
                }
            }
        }
    }

    private fun resolveBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        runCatching { list.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val bcast = ifaceAddr.broadcast
                    if (bcast is Inet4Address) {
                        list.add(bcast)
                    }
                }
            }
        }
        return list.distinct()
    }

    @Synchronized
    private fun activateSession(mime: String, isLandscape: Boolean) {
        if (!encryptionActive) return
        sessionActive = true
        sessionApproved = true
        pendingSinceNs = 0L
        failedHandshakes.set(0)
        activeMime = mime
        activeLandscape = isLandscape
        activeClientSocket?.soTimeout = 0
        val w = if (isLandscape) activeBaseWidth else activeBaseHeight
        val h = if (isLandscape) activeBaseHeight else activeBaseWidth
        targetSurface?.let { surface -> decoder.configureVideo(surface, w, h, mime) }
        decoder.configureAudio()
        _state.value = ReceiverState.MirroringActive(
            phoneName = pendingPhoneName,
            fingerprint = crypto.sessionFingerprint,
            isLandscape = isLandscape,
            fps = 60
        )
        activeClientOut?.let { out ->
            runCatching { sendControlFrame(out, OPCODE_KEYFRAME_REQUEST, ByteArray(0)) }
        }
    }

    private fun startUdpMediaLoop(scope: CoroutineScope) {
        udpJob = scope.launch(Dispatchers.IO) {
            val socket = try { DatagramSocket(MEDIA_UDP_PORT) } catch (e: java.net.SocketException) {
                android.util.Log.w("DriveLink", "UDP unavailable; TCP media fallback remains active", e)
                return@launch
            }
            udpSocket = socket
            val buf = ByteArray(MAX_PACKET_BYTES)
            val packet = DatagramPacket(buf, buf.size)
            while (isActive) {
                runCatching {
                    packet.length = buf.size
                    socket.receive(packet)
                    val frameBytes = packet.data.copyOfRange(0, packet.length)
                    processIncomingFrameBytes(frameBytes)
                }
            }
        }
    }

    private suspend fun handleClient(client: Socket, scope: CoroutineScope) {
        val remoteIp = client.inetAddress.hostAddress
        val now = System.currentTimeMillis()

        // IP cooldown check
        val cooldownUntil = ipCooldownUntil.getOrDefault(remoteIp, AtomicLong(0)).get()
        if (now < cooldownUntil) {
            val waitMs = cooldownUntil - now
            android.util.Log.w("DriveLink", "Connection from $remoteIp rejected — IP cooldown active (${waitMs}ms remaining)")
            runCatching { client.close() }
            return
        }

        // Exponential backoff with jitter based on per-IP failure count
        val ipFailures = failedHandshakesPerIp.getOrDefault(remoteIp, AtomicInteger(0)).get()
        if (ipFailures >= maxFailedBeforeCooldown) {
            val backoffMs = minOf(
                (baseBackoffMs * (2.0.pow(ipFailures - maxFailedBeforeCooldown + 1))).toLong(),
                maxBackoffMs
            )
            val jitter = (Math.random() * 0.3 * backoffMs).toLong() // ±30% jitter
            val totalDelay = backoffMs + jitter
            android.util.Log.w("DriveLink", "Connection from $remoteIp delayed ${totalDelay}ms (failures=$ipFailures)")
            delay(totalDelay)
        }

        client.tcpNoDelay = true
        client.soTimeout = HANDSHAKE_TIMEOUT_MS
        activeClientSocket = client
        val input = DataInputStream(client.getInputStream())
        val output = DataOutputStream(client.getOutputStream())
        activeClientOut = output
        encryptionActive = false
        outSequence.set(0)
        lastTcpSeq = 0
        lastVideoSeq = 0
        antiReplay.reset()
        reassembler.clear()
        crypto.reset()
        sessionApproved = false
        sessionActive = false
        pendingSinceNs = 0L
        pendingMime = null
        verifiedIdentityFingerprint = null
        pendingIdentityTrusted = false

        var phoneName = "BYD Phone"
        var currentMime = MediaFormat.MIMETYPE_VIDEO_AVC
        var isLandscape = true

        startQosReporter(scope)

        runCatching {
            while (scope.isActive && !client.isClosed) {
                val header = ByteArray(HEADER_SIZE_BYTES)
                input.readFully(header)
                val parsed = parseHeader(header) ?: break
                if (parsed.sequence <= lastTcpSeq) break
                lastTcpSeq = parsed.sequence
                val wirePayload = ByteArray(parsed.payloadLength)
                input.readFully(wirePayload)

                val payload = if (encryptionActive) {
                    crypto.decrypt(wirePayload, header)
                } else {
                    wirePayload
                }

                if (!encryptionActive && parsed.opcode != OPCODE_HELLO_SYN && parsed.opcode != OPCODE_KEY_EXCHANGE_INIT) break
                if (encryptionActive && parsed.opcode in setOf(OPCODE_HELLO_SYN, OPCODE_KEY_EXCHANGE_INIT)) break
                when (parsed.opcode) {
                    OPCODE_HELLO_SYN -> {
                        phoneName = payload.decodeToString()
                        sendControlFrame(output, OPCODE_HELLO_ACK, "BYD-DILINK-5.0|15.6".toByteArray())
                    }
                    OPCODE_KEY_EXCHANGE_INIT -> {
                        val (salt, pubKey) = crypto.generateKeyExchangeResponse(payload)
                        sendControlFrame(output, OPCODE_KEY_EXCHANGE_RESP, salt + pubKey)
                        encryptionActive = true
                        pendingSinceNs = System.nanoTime()
                        _state.value = ReceiverState.PairingVerification(
                            phoneName = phoneName,
                            pin = crypto.pairingPin,
                            fingerprint = crypto.sessionFingerprint
                        )
                    }
                    OPCODE_DEVICE_IDENTITY -> {
                        val verified = ReceiverIdentityVerifier.verify(payload, crypto.sessionFingerprint)
                        if (verified != null) {
                            verifiedIdentityFingerprint = verified.fingerprint
                            pendingIdentityTrusted = trustStore?.isTrusted(verified.fingerprint) == true
                            sessionApproved = pendingIdentityTrusted
                            _state.value = ReceiverState.PairingVerification(
                                phoneName = phoneName,
                                pin = crypto.pairingPin,
                                fingerprint = crypto.sessionFingerprint,
                                identityFingerprint = verified.fingerprint,
                                trusted = pendingIdentityTrusted,
                                approved = pendingIdentityTrusted,
                                warning = if (pendingIdentityTrusted) null else {
                                    "جهاز جديد؛ وافِق يدوياً لمرة واحدة."
                                }
                            )
                        } else {
                            verifiedIdentityFingerprint = null
                            pendingIdentityTrusted = false
                            sessionApproved = false
                            _state.value = ReceiverState.PairingVerification(
                                phoneName = phoneName,
                                pin = crypto.pairingPin,
                                fingerprint = crypto.sessionFingerprint,
                                warning = "إثبات الهوية غير صالح؛ وافِق يدوياً بعد مطابقة الرمز."
                            )
                        }
                    }
                    OPCODE_CAPABILITIES_REQ -> {
                        val reqText = payload.decodeToString()
                        val dimsMatch = Regex("(\\d{3,4})x(\\d{3,4})").find(reqText)
                        if (dimsMatch != null) {
                            val w = dimsMatch.groupValues[1].toIntOrNull() ?: 1920
                            val h = dimsMatch.groupValues[2].toIntOrNull() ?: 1080
                            activeBaseWidth = maxOf(w, h)
                            activeBaseHeight = minOf(w, h)
                        }
                        val codecs = if (decoder.supportsHevcDecoder()) "H265,H264" else "H264"
                        sendControlFrame(output, OPCODE_CAPABILITIES_RESP, "1920x1080@60|$codecs".toByteArray())
                        client.soTimeout = PAIRING_CONFIRM_TIMEOUT_MS
                    }
                    OPCODE_SESSION_START -> {
                        currentMime = payload.decodeToString().ifBlank { MediaFormat.MIMETYPE_VIDEO_AVC }
                        currentMime = when (currentMime) {
                            "video/hevc", "video/HEVC" -> MediaFormat.MIMETYPE_VIDEO_HEVC
                            "video/avc", "video/AVC" -> MediaFormat.MIMETYPE_VIDEO_AVC
                            else -> MediaFormat.MIMETYPE_VIDEO_AVC
                        }
                        pendingMime = currentMime
                        pendingLandscape = isLandscape
                        pendingPhoneName = phoneName
                        pendingSinceNs = System.nanoTime()
                        if (sessionApproved) {
                            activateSession(currentMime, isLandscape)
                        } else {
                            _state.value = ReceiverState.PairingVerification(
                                phoneName = phoneName,
                                pin = crypto.pairingPin,
                                fingerprint = crypto.sessionFingerprint,
                                identityFingerprint = verifiedIdentityFingerprint,
                                trusted = pendingIdentityTrusted,
                                approved = false,
                                warning = if (verifiedIdentityFingerprint == null) {
                                    "لم يصل إثبات هوية صالح؛ وافِق يدوياً بعد مطابقة الرمز."
                                } else if (!pendingIdentityTrusted) {
                                    "جهاز جديد؛ وافِق يدوياً لمرة واحدة."
                                } else null
                            )
                        }
                    }
                    }
                    OPCODE_ORIENTATION_CHANGE -> {
                        if (!sessionActive) break
                        isLandscape = payload.firstOrNull() == 0x01.toByte()
                        activeLandscape = isLandscape
                        val w = if (isLandscape) activeBaseWidth else activeBaseHeight
                        val h = if (isLandscape) activeBaseHeight else activeBaseWidth
                        targetSurface?.let { decoder.configureVideo(it, w, h, currentMime) }
                        _state.value = ReceiverState.MirroringActive(
                            phoneName = phoneName,
                            fingerprint = crypto.sessionFingerprint,
                            isLandscape = isLandscape,
                            fps = 60
                        )
                    }
                    OPCODE_VIDEO_NALU -> {
                        if (!sessionActive) break
                        receivedFramesWindow.incrementAndGet()
                        decoder.decodeVideoFrame(payload, parsed.timestampUs.toLong())
                    }
                    OPCODE_VIDEO_NALU_FRAG -> {
                        if (!sessionActive) break
                        reassembler.pushFragment(payload)?.let { fullFrame ->
                            receivedFramesWindow.incrementAndGet()
                            decoder.decodeVideoFrame(fullFrame, parsed.timestampUs.toLong())
                        }
                    }
                    OPCODE_AUDIO_AAC -> {
                        if (!sessionActive) break
                        decoder.decodeAudioFrame(payload, parsed.timestampUs.toLong())
                    }
                    OPCODE_HEARTBEAT_PING -> {
                        if (!sessionActive && pendingSinceNs > 0L &&
                            System.nanoTime() - pendingSinceNs > PAIRING_CONFIRM_TIMEOUT_NS
                        ) {
                            android.util.Log.w("DriveLink", "Pairing approval deadline elapsed")
                            break
                        }
                        sendControlFrame(output, OPCODE_HEARTBEAT_PONG, payload)
                    }
                    OPCODE_SESSION_STOP -> break
                }
            }
        }

        if (!sessionActive) {
            failedHandshakes.incrementAndGet()
            val remoteIp = client.inetAddress.hostAddress
            val ipFails = failedHandshakesPerIp.computeIfAbsent(remoteIp) { AtomicInteger() }.incrementAndGet()
            if (ipFails >= maxFailedBeforeCooldown) {
                val cooldownMs = minOf(
                    (baseBackoffMs * (2.0.pow(ipFails - maxFailedBeforeCooldown + 1))).toLong(),
                    maxBackoffMs * 10 // up to 10 min cooldown
                )
                ipCooldownUntil.put(remoteIp, AtomicLong(System.currentTimeMillis() + cooldownMs))
                android.util.Log.w("DriveLink", "IP $remoteIp placed in cooldown for ${cooldownMs}ms (failures=$ipFails)")
            }
        }
        if (activeClientSocket === client) {
            qosJob?.cancel()
            activeMime = null
            sessionApproved = false
            sessionActive = false
            pendingSinceNs = 0L
            pendingMime = null
            verifiedIdentityFingerprint = null
            decoder.releaseAll()
            encryptionActive = false
            crypto.reset()
            activeClientOut = null
            activeClientSocket = null
            _state.value = ReceiverState.WaitingForPhone
        }
        runCatching { client.close() }
    }

    private fun processIncomingFrameBytes(bytes: ByteArray) {
        if (!encryptionActive) return // Security gate: ignore unauthenticated UDP media
        if (bytes.size < HEADER_SIZE_BYTES) return
        val header = bytes.copyOfRange(0, HEADER_SIZE_BYTES)
        val parsed = parseHeader(header) ?: return
        if (_state.value !is ReceiverState.MirroringActive || bytes.size != HEADER_SIZE_BYTES + parsed.payloadLength) return
        val wirePayload = bytes.copyOfRange(HEADER_SIZE_BYTES, HEADER_SIZE_BYTES + parsed.payloadLength)
        val payload = runCatching { crypto.decrypt(wirePayload, header) }.getOrNull() ?: return

        if (!antiReplay.accept(parsed.sequence)) return

        if (lastVideoSeq != 0 && parsed.sequence > lastVideoSeq + 1) {
            droppedFrameCount.addAndGet(parsed.sequence - lastVideoSeq - 1)
            activeClientOut?.let { out ->
                runCatching { sendControlFrame(out, OPCODE_KEYFRAME_REQUEST, ByteArray(0)) }
            }
        }
        lastVideoSeq = maxOf(lastVideoSeq, parsed.sequence)

        when (parsed.opcode) {
            OPCODE_VIDEO_NALU -> {
                receivedFramesWindow.incrementAndGet()
                decoder.decodeVideoFrame(payload, parsed.timestampUs.toLong())
            }
            OPCODE_VIDEO_NALU_FRAG -> {
                reassembler.pushFragment(payload)?.let { fullFrame ->
                    receivedFramesWindow.incrementAndGet()
                    decoder.decodeVideoFrame(fullFrame, parsed.timestampUs.toLong())
                }
            }
            OPCODE_AUDIO_AAC -> decoder.decodeAudioFrame(payload, parsed.timestampUs.toLong())
        }
    }

    private fun startQosReporter(scope: CoroutineScope) {
        qosJob?.cancel()
        qosJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1_000L)
                val fps = receivedFramesWindow.getAndSet(0)
                val current = _state.value
                if (current is ReceiverState.MirroringActive) {
                    _state.value = current.copy(fps = fps)
                    if (fps == 0) {
                        activeClientOut?.let { out ->
                            runCatching { sendControlFrame(out, OPCODE_KEYFRAME_REQUEST, ByteArray(0)) }
                        }
                    }
                }
                val out = activeClientOut ?: continue
                if (!encryptionActive) continue
                val telemetry = ByteBuffer.allocate(8)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(fps)
                    .putInt(droppedFrameCount.getAndSet(0))
                    .array()
                runCatching { sendControlFrame(out, OPCODE_QOS_TELEMETRY, telemetry) }
            }
        }
    }

    @Synchronized
    private fun sendControlFrame(out: DataOutputStream, opcode: Byte, rawPayload: ByteArray) {
        val seq = outSequence.incrementAndGet()
        val ts = (System.nanoTime() / 1_000L).toInt()
        val wireLen = if (encryptionActive) rawPayload.size + ENCRYPTION_OVERHEAD else rawPayload.size
        val header = ByteBuffer.allocate(HEADER_SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(MAGIC_BYTES)
            .put(PROTOCOL_VERSION)
            .put(opcode)
            .putInt(seq)
            .putInt(ts)
            .putInt(wireLen)
            .array()
        val payload = if (encryptionActive) crypto.encrypt(rawPayload, header) else rawPayload
        out.write(header)
        out.write(payload)
        out.flush()
    }

    private data class ParsedHeader(
        val opcode: Byte,
        val sequence: Int,
        val timestampUs: Int,
        val payloadLength: Int
    )

    private fun parseHeader(header: ByteArray): ParsedHeader? {
        val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        if (buf.short != MAGIC_BYTES) return null
        if (buf.get() != PROTOCOL_VERSION) return null
        val opcode = buf.get()
        val seq = buf.int
        val ts = buf.int
        val len = buf.int
        if (seq <= 0 || len < 0 || len > MAX_PACKET_BYTES) return null
        return ParsedHeader(opcode, seq, ts, len)
    }

    fun stop() {
        sessionApproved = false
        sessionActive = false
        pendingSinceNs = 0L
        pendingMime = null
        pendingLandscape = true
        verifiedIdentityFingerprint = null
        pendingIdentityTrusted = false
        activeMime = null
        _state.value = ReceiverState.WaitingForPhone
        qosJob?.cancel()
        beaconJob?.cancel()
        udpJob?.cancel()
        clientJob?.cancel()
        serverJob?.cancel()
        runCatching { activeClientSocket?.close() }
        runCatching { udpSocket?.close() }
        runCatching { tcpServer?.close() }
        decoder.releaseAll()
    }

    companion object {
        const val CONTROL_PORT = 47810
        const val MEDIA_UDP_PORT = 47811
        const val BEACON_UDP_PORT = 47813
        private const val HANDSHAKE_TIMEOUT_MS = 10_000
        private const val PAIRING_CONFIRM_TIMEOUT_MS = 60_000
        private const val PAIRING_CONFIRM_TIMEOUT_NS = 60_000_000_000L
        private const val MAGIC_BYTES: Short = 0x4259
        private const val PROTOCOL_VERSION: Byte = 0x01
        private const val HEADER_SIZE_BYTES = 16
        private const val ENCRYPTION_OVERHEAD = 28
        private const val MAX_PACKET_BYTES = 65_536

        private const val OPCODE_HELLO_SYN: Byte = 0x01
        private const val OPCODE_HELLO_ACK: Byte = 0x02
        private const val OPCODE_KEY_EXCHANGE_INIT: Byte = 0x03
        private const val OPCODE_KEY_EXCHANGE_RESP: Byte = 0x04
        private const val OPCODE_CAPABILITIES_REQ: Byte = 0x05
        private const val OPCODE_CAPABILITIES_RESP: Byte = 0x06
        private const val OPCODE_SESSION_START: Byte = 0x07
        private const val OPCODE_SESSION_STOP: Byte = 0x08
        private const val OPCODE_ORIENTATION_CHANGE: Byte = 0x09
        private const val OPCODE_DEVICE_IDENTITY: Byte = 0x0A
        private const val OPCODE_VIDEO_NALU: Byte = 0x10
        private const val OPCODE_AUDIO_AAC: Byte = 0x11
        private const val OPCODE_KEYFRAME_REQUEST: Byte = 0x12
        private const val OPCODE_VIDEO_NALU_FRAG: Byte = 0x13
        private const val OPCODE_TOUCH_EVENT: Byte = 0x14
        private const val OPCODE_HEARTBEAT_PING: Byte = 0x20
        private const val OPCODE_HEARTBEAT_PONG: Byte = 0x21
        private const val OPCODE_QOS_TELEMETRY: Byte = 0x22
    }
}
