package com.tji.device.product.speaker.audio

import com.tji.device.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

enum class SpeakerMediaTransferMode(val wireValue: Int) {
    PlayTemporary(1),
    Store(2)
}

data class SpeakerMediaTransferRequest(
    val deviceId: String,
    val sessionId: String,
    val recordId: String,
    val name: String,
    val createdAt: String,
    val opusFile: SpeakerOpusFile,
    val mode: SpeakerMediaTransferMode,
    val volume: Int = 100,
    val recordType: Int = 0,
    val visible: Boolean = true
)

/**
 * 手机到 MCU 的统一 Ogg Opus 可靠 UDP 发送器。
 *
 * 喊话和 TTS 使用 [SpeakerMediaTransferMode.PlayTemporary]，保存录音使用
 * [SpeakerMediaTransferMode.Store]。三种业务只改变会话元数据，均发送完全相同
 * 的 Ogg Opus 文件字节、使用相同 ACK、重试、背压和 CRC 规则。
 */
class SpeakerMediaTransferClient(
    private val config: SpeakerRelayConfig = SpeakerRelayConfig(),
    private val token: String = BuildConfig.TJI_SPEAKER_RELAY_TOKEN,
    private val monotonicNowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    /**
     * 同一 App 生命周期复用媒体 UDP 端口，避免每段喊话重新建立公网 NAT 映射。
     * sendMutex 同时保证不同业务不会在同一个 socket 上交叉消费 ACK。
     */
    private val sendMutex = Mutex()
    private var sharedSocket: DatagramSocket? = null

    suspend fun send(request: SpeakerMediaTransferRequest) = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && token.none(Char::isWhitespace)) {
            "语音传输服务未配置"
        }
        require(request.opusFile.packetMs == PACKET_MS) { "仅支持 20 ms Opus 包" }
        require(request.opusFile.sampleRate in SUPPORTED_SAMPLE_RATES) {
            "不支持的 Opus 采样率: ${request.opusFile.sampleRate}"
        }
        require(request.opusFile.data.isNotEmpty() &&
            request.opusFile.data.size <= MAX_FILE_BYTES
        ) { "Ogg Opus 文件大小无效" }
        require(request.name.encodeToByteArray().size in 1..MAX_NAME_BYTES) {
            "录音名称必须为 1-$MAX_NAME_BYTES 字节"
        }
        require(request.createdAt.encodeToByteArray().size in 1..MAX_TIMESTAMP_BYTES) {
            "创建时间必须为 1-$MAX_TIMESTAMP_BYTES 字节"
        }
        val chunks = buildChunks(request)
        val relay = InetAddress.getByName(config.host)
        sendMutex.lock()
        try {
            val routeLease = SpeakerSharedUdpTransport.acquireMediaRoute(
                request.deviceId,
                request.sessionId,
                request.recordId,
                relay,
                config.port
            )
            val ownsSocket = routeLease == null
            val socket = routeLease?.socket ?: mediaSocket()
            try {
                registerAndAwaitAck(
                    socket, relay, request.deviceId, request.sessionId, request.recordId,
                    routeLease?.packets
                )
                try {
                    sendReliably(
                        socket, relay,
                        request.deviceId, request.sessionId, request.recordId, chunks,
                        routeLease?.packets
                    )
                } finally {
                    runCatching {
                        sendListenerCommand(
                            socket, relay, enabled = false,
                            deviceId = request.deviceId,
                            sessionId = request.sessionId,
                            recordId = request.recordId
                        )
                    }
                }
            } catch (throwable: Throwable) {
                if (ownsSocket) {
                    // 后备端口失败说明 NAT 映射不可用；下一次发送重新建端口。
                    socket.close()
                    if (sharedSocket === socket) sharedSocket = null
                }
                throw throwable
            } finally {
                routeLease?.close()
            }
        } finally {
            sendMutex.unlock()
        }
    }

    private fun mediaSocket(): DatagramSocket =
        sharedSocket?.takeUnless { it.isClosed } ?: DatagramSocket().also { socket ->
            socket.soTimeout = SOCKET_POLL_MS
            socket.sendBufferSize = maxOf(socket.sendBufferSize, SOCKET_BUFFER_BYTES)
            socket.receiveBufferSize = maxOf(socket.receiveBufferSize, SOCKET_BUFFER_BYTES)
            sharedSocket = socket
        }

    private suspend fun registerAndAwaitAck(
        socket: DatagramSocket,
        relay: InetAddress,
        deviceId: String,
        sessionId: String,
        recordId: String,
        sharedPackets: ReceiveChannel<ByteArray>?
    ) {
        val expected =
            "HLAPPACK1 REGISTERED $deviceId $sessionId $recordId".encodeToByteArray()
        val deadline = monotonicNowMs() + REGISTRATION_TIMEOUT_MS
        var lastSend = 0L
        while (monotonicNowMs() < deadline) {
            val now = monotonicNowMs()
            if (lastSend == 0L || now - lastSend >= REGISTRATION_RETRY_MS) {
                sendListenerCommand(
                    socket, relay, true, deviceId, sessionId, recordId
                )
                lastSend = now
            }
            val received = receiveFromRelay(socket, relay, sharedPackets) ?: continue
            if (received.size == expected.size &&
                received.regionMatches(0, expected)
            ) return
        }
        error("音频 UDP 注册超时")
    }

    private suspend fun sendReliably(
        socket: DatagramSocket,
        relay: InetAddress,
        deviceId: String,
        sessionId: String,
        recordId: String,
        chunks: List<ByteArray>,
        sharedPackets: ReceiveChannel<ByteArray>?
    ) {
        val acked = BooleanArray(chunks.size)
        val attempts = IntArray(chunks.size)
        val sentAt = LongArray(chunks.size)
        var ackedCount = 0
        var fastRetransmitChunk = -1
        var lastRegistration = monotonicNowMs()
        val deadline = monotonicNowMs() +
            maxOf(MIN_TRANSFER_TIMEOUT_MS, chunks.size * 1_000L)

        while (ackedCount < chunks.size &&
            currentCoroutineContext().isActive &&
            monotonicNowMs() < deadline
        ) {
            val now = monotonicNowMs()
            if (now - lastRegistration >= REGISTRATION_REFRESH_MS) {
                sendListenerCommand(
                    socket, relay, true, deviceId, sessionId, recordId
                )
                lastRegistration = now
            }
            var outstanding = acked.indices.count {
                !acked[it] && sentAt[it] != 0L &&
                    now - sentAt[it] < RETRY_TIMEOUT_MS
            }
            /* 0 号块先握手建会话，避免公网乱序令 MCU 丢弃整个首窗口。 */
            val sendEnd = if (acked[0]) chunks.size else 1
            for (index in 0 until sendEnd) {
                if (acked[index]) continue
                val due = sentAt[index] == 0L ||
                    now - sentAt[index] >= RETRY_TIMEOUT_MS
                if (!due || outstanding >= SEND_WINDOW) continue
                check(attempts[index] < MAX_SEND_ATTEMPTS) {
                    "音频分块重试超限: ${index + 1}/${chunks.size}"
                }
                socket.send(
                    DatagramPacket(chunks[index], chunks[index].size, relay, config.port)
                )
                attempts[index]++
                sentAt[index] = monotonicNowMs()
                outstanding++
                delay(SEND_PACING_MS)
            }

            val received = receiveFromRelay(socket, relay, sharedPackets) ?: continue
            val ack = parseAck(
                received, received.size, deviceId, sessionId, recordId
            ) ?: continue
            when (ack.status) {
                ACK_OK -> {
                    /* expectedChunk 是 MCU 已连续接收的累计边界，ACK 丢失无需逐块重传。 */
                    val cumulativeEnd = ack.expectedChunk.coerceIn(0, acked.size)
                    val previousAckedCount = ackedCount
                    for (index in 0 until cumulativeEnd) {
                        if (!acked[index]) {
                            acked[index] = true
                            ackedCount++
                        }
                    }
                    if (ackedCount != previousAckedCount) fastRetransmitChunk = -1
                }
                ACK_BUSY -> error("设备音频或存储服务正忙")
                ACK_BAD_PACKET -> error("设备拒绝了 Ogg Opus 数据")
                ACK_GAP -> {
                    if (ack.expectedChunk in sentAt.indices &&
                        !acked[ack.expectedChunk] &&
                        fastRetransmitChunk != ack.expectedChunk
                    ) {
                        /* 同一窗口的多个 GAP 只触发一次快速重传，避免 ACK 风暴。 */
                        sentAt[ack.expectedChunk] = 0L
                        fastRetransmitChunk = ack.expectedChunk
                    }
                }
            }
        }
        check(ackedCount == chunks.size) {
            "音频传输超时: $ackedCount/${chunks.size}"
        }
    }

    private suspend fun receiveFromRelay(
        socket: DatagramSocket,
        relay: InetAddress,
        sharedPackets: ReceiveChannel<ByteArray>?
    ): ByteArray? {
        if (sharedPackets != null) {
            return withTimeoutOrNull(SOCKET_POLL_MS.toLong()) {
                sharedPackets.receiveCatching().getOrNull()
            }
        }
        val receive = ByteArray(MAX_DATAGRAM_BYTES)
        val datagram = DatagramPacket(receive, receive.size)
        try {
            socket.receive(datagram)
        } catch (_: SocketTimeoutException) {
            return null
        }
        if (datagram.address != relay || datagram.port != config.port) return null
        return receive.copyOf(datagram.length)
    }

    private fun sendListenerCommand(
        socket: DatagramSocket,
        relay: InetAddress,
        enabled: Boolean,
        deviceId: String,
        sessionId: String,
        recordId: String
    ) {
        val prefix = if (enabled) "HLAPP1" else "HLAPP0"
        val payload =
            "$prefix $token $deviceId $sessionId $recordId".encodeToByteArray()
        socket.send(DatagramPacket(payload, payload.size, relay, config.port))
    }

    private data class MediaAck(
        val status: Int,
        val chunkIndex: Int,
        val expectedChunk: Int
    )

    private fun parseAck(
        packet: ByteArray,
        length: Int,
        deviceId: String,
        sessionId: String,
        recordId: String
    ): MediaAck? {
        if (length < UDP_FIXED_HEADER_BYTES ||
            packet.readU16(0) != UDP_MAGIC ||
            packet[2].toInt() and 0xFF != UDP_VERSION ||
            packet[3].toInt() and 0xFF != CODEC_OPUS
        ) return null
        val headerBytes = packet.readU16(4)
        val flags = packet.readU16(6)
        val payloadBytes = packet.readU16(20)
        val deviceBytes = packet[24].toInt() and 0xFF
        val sessionBytes = packet[25].toInt() and 0xFF
        val recordBytes = packet[26].toInt() and 0xFF
        if (flags and (FLAG_FEEDBACK or FLAG_ACK) !=
            (FLAG_FEEDBACK or FLAG_ACK) ||
            headerBytes != UDP_FIXED_HEADER_BYTES +
                deviceBytes + sessionBytes + recordBytes ||
            length != headerBytes + payloadBytes ||
            payloadBytes != ACK_PAYLOAD_BYTES
        ) return null
        var offset = UDP_FIXED_HEADER_BYTES
        val routeDevice = packet.decodeUtf8(offset, deviceBytes)
        offset += deviceBytes
        val routeSession = packet.decodeUtf8(offset, sessionBytes)
        offset += sessionBytes
        val routeRecord = packet.decodeUtf8(offset, recordBytes)
        if (routeDevice != deviceId ||
            routeSession != sessionId ||
            routeRecord != recordId
        ) return null
        if (packet.readU32(headerBytes) != ACK_MAGIC ||
            packet[headerBytes + 4].toInt() and 0xFF != PAYLOAD_VERSION
        ) return null
        return MediaAck(
            status = packet[headerBytes + 5].toInt() and 0xFF,
            chunkIndex = packet.readU32(headerBytes + 8).toInt(),
            expectedChunk = packet.readU32(headerBytes + 12).toInt()
        )
    }

    internal fun buildChunks(request: SpeakerMediaTransferRequest): List<ByteArray> {
        val device = routeId("deviceId", request.deviceId)
        val session = routeId("sessionId", request.sessionId)
        val record = routeId("recordId", request.recordId)
        val name = request.name.encodeToByteArray()
        val createdAt = request.createdAt.encodeToByteArray()
        val file = request.opusFile.data
        val chunkData = groupOggPages(file)
        val chunkCount = chunkData.size
        require(chunkCount in 1..0xFFFF) { "Ogg Opus 分块数量超限" }
        val fileCrc = CRC32().apply { update(file) }.value.toInt()

        return List(chunkCount) { index ->
            val data = chunkData[index]
            val first = index == 0
            val metadataBytes = if (first) name.size + createdAt.size else 0
            val payload = ByteBuffer.allocate(
                CHUNK_HEADER_BYTES + metadataBytes + data.size
            ).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(CHUNK_MAGIC)
                .put(PAYLOAD_VERSION.toByte())
                .put(request.mode.wireValue.toByte())
                .put(request.volume.coerceIn(0, 100).toByte())
                .put(request.recordType.coerceIn(0, 2).toByte())
                .putShort(index.toShort())
                .putShort(chunkCount.toShort())
                .putInt(file.size)
                .putInt(fileCrc)
                .putInt(request.opusFile.durationMs.coerceAtLeast(PACKET_MS))
                .putInt(request.opusFile.bitrate)
                .putInt(CRC32().apply { update(data) }.value.toInt())
                .put(if (first) name.size.toByte() else 0)
                .put(if (first) createdAt.size.toByte() else 0)
                .put(if (request.visible) 1 else 0)
                .put(0)
                .apply {
                    if (first) {
                        put(name)
                        put(createdAt)
                    }
                    put(data)
                }
                .array()
            val flags = FLAG_PLAYBACK or
                (if (first) FLAG_START else 0) or
                (if (index + 1 == chunkCount) FLAG_LAST else 0)
            buildUdpPacket(
                device, session, record, flags, index,
                request.opusFile.sampleRate, payload
            )
        }
    }

    /**
     * Ogg 页是接收端增量解析的自然事务边界。每块最多八页，保证 48 kHz
     * 播放时一次 ACK 前最多产生约 16 KiB PCM，给 32 KiB 播放环留出余量。
     */
    private fun groupOggPages(file: ByteArray): List<ByteArray> {
        val groups = mutableListOf<ByteArray>()
        var current = ByteArrayOutputStream(MAX_CHUNK_DATA_BYTES)
        var pageCount = 0
        var offset = 0
        while (offset < file.size) {
            require(offset + 27 <= file.size &&
                file[offset] == 'O'.code.toByte() &&
                file[offset + 1] == 'g'.code.toByte() &&
                file[offset + 2] == 'g'.code.toByte() &&
                file[offset + 3] == 'S'.code.toByte()
            ) { "Ogg Page 损坏" }
            val segments = file[offset + 26].toInt() and 0xFF
            require(offset + 27 + segments <= file.size) { "Ogg lacing 损坏" }
            var payloadBytes = 0
            repeat(segments) {
                payloadBytes += file[offset + 27 + it].toInt() and 0xFF
            }
            val pageBytes = 27 + segments + payloadBytes
            require(pageBytes <= MAX_CHUNK_DATA_BYTES &&
                offset + pageBytes <= file.size
            ) { "Ogg Page 超过单个 UDP 分块" }
            if (current.size() != 0 &&
                (pageCount >= MAX_PAGES_PER_CHUNK ||
                    current.size() + pageBytes > MAX_CHUNK_DATA_BYTES)
            ) {
                groups += current.toByteArray()
                current = ByteArrayOutputStream(MAX_CHUNK_DATA_BYTES)
                pageCount = 0
            }
            current.write(file, offset, pageBytes)
            pageCount++
            offset += pageBytes
        }
        if (current.size() != 0) groups += current.toByteArray()
        return groups
    }

    private fun buildUdpPacket(
        deviceId: ByteArray,
        sessionId: ByteArray,
        recordId: ByteArray,
        flags: Int,
        sequence: Int,
        sampleRate: Int,
        payload: ByteArray
    ): ByteArray {
        val headerBytes =
            UDP_FIXED_HEADER_BYTES + deviceId.size + sessionId.size + recordId.size
        return ByteBuffer.allocate(headerBytes + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(UDP_MAGIC.toShort())
            .put(UDP_VERSION.toByte())
            .put(CODEC_OPUS.toByte())
            .putShort(headerBytes.toShort())
            .putShort(flags.toShort())
            .putInt(sequence)
            .putInt(0)
            .putShort(sampleRate.toShort())
            .put(CHANNELS.toByte())
            .put(PACKET_MS.toByte())
            .putShort(payload.size.toShort())
            .putShort(0)
            .put(deviceId.size.toByte())
            .put(sessionId.size.toByte())
            .put(recordId.size.toByte())
            .put(0)
            .put(deviceId)
            .put(sessionId)
            .put(recordId)
            .put(payload)
            .array()
            .also {
                require(it.size <= MAX_DATAGRAM_BYTES) {
                    "音频 UDP 包超过 MTU: ${it.size}"
                }
            }
    }

    private fun routeId(label: String, value: String): ByteArray =
        value.encodeToByteArray().also {
            require(it.isNotEmpty() && it.size <= MAX_ROUTE_ID_BYTES &&
                value.none(Char::isWhitespace)
            ) { "$label 必须为 1-32 字节且不能包含空白" }
        }

    private fun ByteArray.readU16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.readU32(offset: Int): Long =
        (readU16(offset).toLong() or
            (readU16(offset + 2).toLong() shl 16)) and 0xFFFF_FFFFL

    private fun ByteArray.decodeUtf8(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).toString(Charsets.UTF_8)

    private fun ByteArray.regionMatches(offset: Int, expected: ByteArray): Boolean =
        offset >= 0 && size - offset >= expected.size &&
            expected.indices.all { this[offset + it] == expected[it] }

    companion object {
        const val RECORD_TYPE_RECORD = 0
        const val RECORD_TYPE_TTS = 1
        const val RECORD_TYPE_TEST = 2

        private const val UDP_MAGIC = 0xA55A
        private const val UDP_VERSION = 2
        private const val CODEC_OPUS = 2
        private const val UDP_FIXED_HEADER_BYTES = 28
        private const val MAX_DATAGRAM_BYTES = 1472
        private const val MAX_ROUTE_ID_BYTES = 32
        private const val MAX_NAME_BYTES = 64
        private const val MAX_TIMESTAMP_BYTES = 32
        private const val FLAG_LAST = 0x0001
        private const val FLAG_PLAYBACK = 0x0004
        private const val FLAG_FEEDBACK = 0x0008
        private const val FLAG_START = 0x0010
        private const val FLAG_ACK = 0x0020
        private const val CHUNK_MAGIC = 0x3152544D
        private const val ACK_MAGIC = 0x3141524DL
        private const val PAYLOAD_VERSION = 1
        private const val CHUNK_HEADER_BYTES = 36
        private const val ACK_PAYLOAD_BYTES = 16
        private const val MAX_CHUNK_DATA_BYTES = 1_100
        private const val MAX_PAGES_PER_CHUNK = 8
        private const val MAX_FILE_BYTES = 4 * 1024 * 1024
        private val SUPPORTED_SAMPLE_RATES = setOf(8_000, 12_000, 16_000, 24_000, 48_000)
        private const val CHANNELS = 1
        private const val PACKET_MS = 20
        private const val ACK_OK = 0
        private const val ACK_BUSY = 1
        private const val ACK_BAD_PACKET = 2
        private const val ACK_GAP = 3
        /* 与 MCU 8 包乱序深度一致，避免大窗口制造无效 GAP 和突发重传。 */
        private const val SEND_WINDOW = 8
        private const val MAX_SEND_ATTEMPTS = 20
        private const val RETRY_TIMEOUT_MS = 750L
        private const val SEND_PACING_MS = 20L
        private const val SOCKET_POLL_MS = 100
        private const val SOCKET_BUFFER_BYTES = 128 * 1_024
        private const val REGISTRATION_RETRY_MS = 1_000L
        private const val REGISTRATION_TIMEOUT_MS = 8_000L
        private const val REGISTRATION_REFRESH_MS = 10_000L
        private const val MIN_TRANSFER_TIMEOUT_MS = 15_000L
    }
}
