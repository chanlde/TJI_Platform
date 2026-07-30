package com.tji.device.product.speaker.audio

import com.tji.device.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * 松手后可靠 Opus 喊话发送器。
 *
 * App 在同一 UDP socket 上注册 ACK 路由，以最多四个分块的滑动窗口发送
 * 48 kHz Opus。MCU 环形缓冲不足时不确认，发送端定时重发形成自然背压。
 */
class SpeakerDirectPttClient(
    private val config: SpeakerRelayConfig = SpeakerRelayConfig(),
    private val token: String = BuildConfig.TJI_SPEAKER_RELAY_TOKEN,
    private val monotonicNowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private fun requireConfiguredToken() {
        require(token.isNotBlank() && token.none(Char::isWhitespace)) {
            "UDP relay token 未配置或包含空白字符"
        }
    }

    suspend fun send(
        deviceId: String,
        sessionId: String,
        talkId: String,
        opusFile: SpeakerOpusFile,
        volume: Int
    ) = withContext(Dispatchers.IO) {
        requireConfiguredToken()
        require(opusFile.sampleRate == SAMPLE_RATE && opusFile.packetMs == PACKET_MS) {
            "直接喊话必须是 48 kHz/20 ms Opus"
        }
        val packets = extractOggOpusAudioPackets(opusFile.data)
        require(packets.isNotEmpty()) { "Opus 文件没有音频包" }
        val chunks = buildChunks(
            deviceId = deviceId,
            sessionId = sessionId,
            talkId = talkId,
            frames = packets,
            durationMs = opusFile.durationMs.coerceAtLeast(PACKET_MS),
            bitrate = opusFile.bitrate,
            volume = volume.coerceIn(0, 100)
        )
        val relay = InetAddress.getByName(config.host)
        DatagramSocket().use { socket ->
            socket.soTimeout = SOCKET_POLL_MS
            socket.sendBufferSize = maxOf(socket.sendBufferSize, SOCKET_BUFFER_BYTES)
            socket.receiveBufferSize = maxOf(socket.receiveBufferSize, SOCKET_BUFFER_BYTES)
            registerAndAwaitAck(socket, relay, deviceId, sessionId, talkId)
            try {
                sendReliably(socket, relay, deviceId, sessionId, talkId, chunks)
            } finally {
                runCatching {
                    sendListenerCommand(
                        socket, relay, enabled = false,
                        deviceId = deviceId, sessionId = sessionId, talkId = talkId
                    )
                }
            }
        }
    }

    private fun registerAndAwaitAck(
        socket: DatagramSocket,
        relay: InetAddress,
        deviceId: String,
        sessionId: String,
        talkId: String
    ) {
        val expected =
            "HLAPPACK1 REGISTERED $deviceId $sessionId $talkId".encodeToByteArray()
        val deadline = monotonicNowMs() + REGISTRATION_TIMEOUT_MS
        var lastSend = 0L
        val receive = ByteArray(MAX_DATAGRAM_BYTES)
        while (monotonicNowMs() < deadline) {
            val now = monotonicNowMs()
            if (lastSend == 0L || now - lastSend >= REGISTRATION_RETRY_MS) {
                sendListenerCommand(
                    socket, relay, enabled = true,
                    deviceId = deviceId, sessionId = sessionId, talkId = talkId
                )
                lastSend = now
            }
            val datagram = DatagramPacket(receive, receive.size)
            try {
                socket.receive(datagram)
            } catch (_: SocketTimeoutException) {
                continue
            }
            if (datagram.address == relay &&
                datagram.port == config.port &&
                datagram.length == expected.size &&
                receive.regionMatches(0, expected)
            ) {
                return
            }
        }
        error("直接喊话 UDP 注册超时")
    }

    private suspend fun sendReliably(
        socket: DatagramSocket,
        relay: InetAddress,
        deviceId: String,
        sessionId: String,
        talkId: String,
        chunks: List<ByteArray>
    ) {
        val acked = BooleanArray(chunks.size)
        val attempts = IntArray(chunks.size)
        val sentAt = LongArray(chunks.size)
        val receive = ByteArray(MAX_DATAGRAM_BYTES)
        var ackedCount = 0
        var lastRegistration = monotonicNowMs()
        val deadline =
            monotonicNowMs() + maxOf(MIN_TRANSFER_TIMEOUT_MS, chunks.size * 1_000L)

        while (ackedCount < chunks.size &&
            currentCoroutineContext().isActive &&
            monotonicNowMs() < deadline
        ) {
            val now = monotonicNowMs()
            if (now - lastRegistration >= REGISTRATION_REFRESH_MS) {
                sendListenerCommand(
                    socket, relay, enabled = true,
                    deviceId = deviceId, sessionId = sessionId, talkId = talkId
                )
                lastRegistration = now
            }

            var outstanding = acked.indices.count {
                !acked[it] && sentAt[it] != 0L &&
                    now - sentAt[it] < RETRY_TIMEOUT_MS
            }
            for (index in chunks.indices) {
                if (acked[index]) continue
                val due = sentAt[index] == 0L ||
                    now - sentAt[index] >= RETRY_TIMEOUT_MS
                if (!due || outstanding >= SEND_WINDOW) continue
                check(attempts[index] < MAX_SEND_ATTEMPTS) {
                    "直接喊话分块重试超限: ${index + 1}/${chunks.size}"
                }
                socket.send(
                    DatagramPacket(chunks[index], chunks[index].size, relay, config.port)
                )
                attempts[index]++
                sentAt[index] = now
                outstanding++
            }

            val datagram = DatagramPacket(receive, receive.size)
            try {
                socket.receive(datagram)
            } catch (_: SocketTimeoutException) {
                continue
            }
            if (datagram.address != relay || datagram.port != config.port) continue
            val ack = parseAck(
                receive, datagram.length, deviceId, sessionId, talkId
            ) ?: continue
            when (ack.status) {
                ACK_OK -> {
                    if (ack.chunkIndex in acked.indices &&
                        !acked[ack.chunkIndex]
                    ) {
                        acked[ack.chunkIndex] = true
                        ackedCount++
                    }
                }
                ACK_BUSY -> error("设备扬声器正在播放其他内容")
                ACK_BAD_PACKET -> error("设备拒绝了直接喊话数据")
                ACK_GAP -> {
                    val expected = ack.expectedChunk
                    if (expected in sentAt.indices && !acked[expected]) {
                        sentAt[expected] = 0L
                    }
                }
            }
        }
        check(ackedCount == chunks.size) {
            "直接喊话传输超时: $ackedCount/${chunks.size}"
        }
    }

    private fun sendListenerCommand(
        socket: DatagramSocket,
        relay: InetAddress,
        enabled: Boolean,
        deviceId: String,
        sessionId: String,
        talkId: String
    ) {
        val prefix = if (enabled) "HLAPP1" else "HLAPP0"
        val payload = "$prefix $token $deviceId $sessionId $talkId".encodeToByteArray()
        socket.send(DatagramPacket(payload, payload.size, relay, config.port))
    }

    private data class DirectAck(
        val status: Int,
        val chunkIndex: Int,
        val expectedChunk: Int
    )

    private fun parseAck(
        packet: ByteArray,
        length: Int,
        deviceId: String,
        sessionId: String,
        talkId: String
    ): DirectAck? {
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
        val talkBytes = packet[26].toInt() and 0xFF
        if (flags and (FLAG_FEEDBACK or FLAG_ACK) !=
            (FLAG_FEEDBACK or FLAG_ACK) ||
            headerBytes != UDP_FIXED_HEADER_BYTES +
                deviceBytes + sessionBytes + talkBytes ||
            length != headerBytes + payloadBytes ||
            payloadBytes != ACK_PAYLOAD_BYTES
        ) return null
        var offset = UDP_FIXED_HEADER_BYTES
        val routeDevice = packet.decodeUtf8(offset, deviceBytes)
        offset += deviceBytes
        val routeSession = packet.decodeUtf8(offset, sessionBytes)
        offset += sessionBytes
        val routeTalk = packet.decodeUtf8(offset, talkBytes)
        if (routeDevice != deviceId ||
            routeSession != sessionId ||
            routeTalk != talkId
        ) return null
        val payload = headerBytes
        if (packet.readU32(payload) != ACK_MAGIC ||
            packet[payload + 4].toInt() and 0xFF != PAYLOAD_VERSION
        ) return null
        return DirectAck(
            status = packet[payload + 5].toInt() and 0xFF,
            chunkIndex = packet.readU32(payload + 8).toInt(),
            expectedChunk = packet.readU32(payload + 12).toInt()
        )
    }

    internal fun buildChunks(
        deviceId: String,
        sessionId: String,
        talkId: String,
        frames: List<ByteArray>,
        durationMs: Int,
        bitrate: Int,
        volume: Int
    ): List<ByteArray> {
        val device = routeId("deviceId", deviceId)
        val session = routeId("sessionId", sessionId)
        val talk = routeId("talkId", talkId)
        val groups = mutableListOf<List<ByteArray>>()
        var current = mutableListOf<ByteArray>()
        var currentBytes = 0
        frames.forEach { frame ->
            require(frame.isNotEmpty() && frame.size <= MAX_OPUS_PACKET_BYTES) {
                "Opus 包长度无效: ${frame.size}"
            }
            val framedBytes = 2 + frame.size
            if (current.isNotEmpty() &&
                (current.size >= MAX_FRAMES_PER_CHUNK ||
                    currentBytes + framedBytes > MAX_CHUNK_FRAME_BYTES)
            ) {
                groups += current
                current = mutableListOf()
                currentBytes = 0
            }
            current += frame
            currentBytes += framedBytes
        }
        if (current.isNotEmpty()) groups += current

        return groups.mapIndexed { index, group ->
            val frameData = ByteArrayOutputStream().apply {
                group.forEach { frame ->
                    writeLe16(frame.size)
                    write(frame)
                }
            }.toByteArray()
            val payload = ByteBuffer.allocate(CHUNK_HEADER_BYTES + frameData.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(CHUNK_MAGIC)
                .put(PAYLOAD_VERSION.toByte())
                .put(group.size.toByte())
                .put(volume.toByte())
                .put(0)
                .putShort(index.toShort())
                .putShort(groups.size.toShort())
                .putInt(frames.size)
                .putInt(durationMs)
                .putInt(bitrate)
                .putInt(CRC32().apply { update(frameData) }.value.toInt())
                .put(frameData)
                .array()
            val flags = FLAG_PLAYBACK or
                (if (index == 0) FLAG_START else 0) or
                (if (index + 1 == groups.size) FLAG_LAST else 0)
            buildUdpPacket(
                device, session, talk, flags, index, group.size, payload
            )
        }
    }

    private fun buildUdpPacket(
        deviceId: ByteArray,
        sessionId: ByteArray,
        talkId: ByteArray,
        flags: Int,
        sequence: Int,
        frameCount: Int,
        payload: ByteArray
    ): ByteArray {
        val headerBytes =
            UDP_FIXED_HEADER_BYTES + deviceId.size + sessionId.size + talkId.size
        val packet = ByteBuffer.allocate(headerBytes + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        packet.putShort(UDP_MAGIC.toShort())
        packet.put(UDP_VERSION.toByte())
        packet.put(CODEC_OPUS.toByte())
        packet.putShort(headerBytes.toShort())
        packet.putShort(flags.toShort())
        packet.putInt(sequence)
        packet.putInt(sequence * MAX_FRAMES_PER_CHUNK * PACKET_MS)
        packet.putShort(SAMPLE_RATE.toShort())
        packet.put(CHANNELS.toByte())
        packet.put(PACKET_MS.toByte())
        packet.putShort(payload.size.toShort())
        packet.putShort((frameCount * SAMPLES_PER_FRAME).toShort())
        packet.put(deviceId.size.toByte())
        packet.put(sessionId.size.toByte())
        packet.put(talkId.size.toByte())
        packet.put(0)
        packet.put(deviceId)
        packet.put(sessionId)
        packet.put(talkId)
        packet.put(payload)
        return packet.array().also {
            require(it.size <= MAX_DATAGRAM_BYTES) {
                "直接喊话 UDP 包超过 MTU: ${it.size}"
            }
        }
    }

    private fun extractOggOpusAudioPackets(data: ByteArray): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            require(offset + 27 <= data.size &&
                data[offset] == 'O'.code.toByte() &&
                data[offset + 1] == 'g'.code.toByte() &&
                data[offset + 2] == 'g'.code.toByte() &&
                data[offset + 3] == 'S'.code.toByte()
            ) { "Ogg Page 损坏" }
            val segmentCount = data[offset + 26].toInt() and 0xFF
            val segmentTable = offset + 27
            require(segmentTable + segmentCount <= data.size) { "Ogg lacing 损坏" }
            var payloadBytes = 0
            repeat(segmentCount) {
                payloadBytes += data[segmentTable + it].toInt() and 0xFF
            }
            val payloadOffset = segmentTable + segmentCount
            require(payloadOffset + payloadBytes <= data.size) { "Ogg payload 损坏" }
            val payload = data.copyOfRange(payloadOffset, payloadOffset + payloadBytes)
            if (!payload.startsWithAscii("OpusHead") &&
                !payload.startsWithAscii("OpusTags")
            ) {
                packets += payload
            }
            offset = payloadOffset + payloadBytes
        }
        require(offset == data.size) { "Ogg 尾部长度错误" }
        return packets
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
        (readU16(offset).toLong() or (readU16(offset + 2).toLong() shl 16)) and
            0xFFFF_FFFFL

    private fun ByteArray.decodeUtf8(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).toString(Charsets.UTF_8)

    private fun ByteArray.regionMatches(offset: Int, expected: ByteArray): Boolean =
        offset >= 0 && size - offset >= expected.size &&
            expected.indices.all { this[offset + it] == expected[it] }

    private fun ByteArray.startsWithAscii(text: String): Boolean {
        val expected = text.encodeToByteArray()
        return size >= expected.size && expected.indices.all { this[it] == expected[it] }
    }

    private fun ByteArrayOutputStream.writeLe16(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private companion object {
        const val UDP_MAGIC = 0xA55A
        const val UDP_VERSION = 2
        const val CODEC_OPUS = 2
        const val UDP_FIXED_HEADER_BYTES = 28
        const val MAX_DATAGRAM_BYTES = 1472
        const val MAX_ROUTE_ID_BYTES = 32
        const val FLAG_LAST = 0x0001
        const val FLAG_PLAYBACK = 0x0004
        const val FLAG_FEEDBACK = 0x0008
        const val FLAG_START = 0x0010
        const val FLAG_ACK = 0x0020
        const val CHUNK_MAGIC = 0x31545044
        const val ACK_MAGIC = 0x31415044L
        const val PAYLOAD_VERSION = 1
        const val CHUNK_HEADER_BYTES = 28
        const val ACK_PAYLOAD_BYTES = 16
        const val MAX_FRAMES_PER_CHUNK = 20
        const val MAX_CHUNK_FRAME_BYTES = 1_160
        const val MAX_OPUS_PACKET_BYTES = 1_275
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 1
        const val PACKET_MS = 20
        const val SAMPLES_PER_FRAME = SAMPLE_RATE * PACKET_MS / 1_000
        const val ACK_OK = 0
        const val ACK_BUSY = 1
        const val ACK_BAD_PACKET = 2
        const val ACK_GAP = 3
        const val SEND_WINDOW = 4
        const val MAX_SEND_ATTEMPTS = 20
        const val RETRY_TIMEOUT_MS = 300L
        const val SOCKET_POLL_MS = 100
        const val SOCKET_BUFFER_BYTES = 128 * 1_024
        const val REGISTRATION_RETRY_MS = 1_000L
        const val REGISTRATION_TIMEOUT_MS = 8_000L
        const val REGISTRATION_REFRESH_MS = 10_000L
        const val MIN_TRANSFER_TIMEOUT_MS = 15_000L
    }
}
