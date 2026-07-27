package com.tji.device.product.speaker.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.tji.device.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.math.max

data class SpeakerFeedbackSession(
    val deviceId: String,
    val sessionId: String,
    val talkId: String
) {
    init {
        validateRouteId("deviceId", deviceId)
        validateRouteId("sessionId", sessionId)
        validateRouteId("talkId", talkId)
    }

    private fun validateRouteId(label: String, value: String) {
        require(value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size <= 32) {
            "$label 必须为 1-32 字节"
        }
        require(value.none(Char::isWhitespace)) { "$label 不能包含空白字符" }
    }
}

data class SpeakerFeedbackRuntimeStats(
    val packetsReceived: Long = 0,
    val packetsPlayed: Long = 0,
    val packetsConcealed: Long = 0,
    val packetsRejected: Long = 0,
    val duplicatePackets: Long = 0
)

data class SpeakerFeedbackTiming(
    val socketTimeoutMs: Int = 1_000,
    val registrationRetryMs: Long = 2_000L,
    val registrationTimeoutMs: Long = 8_000L,
    val registrationRefreshMs: Long = 10_000L,
    val firstAudioTimeoutMs: Long = 12_000L,
    val audioIdleTimeoutMs: Long = 8_000L
)

interface SpeakerFeedbackAudioSink : AutoCloseable {
    fun start()
    fun write(pcm: ByteArray)
}

interface SpeakerFeedbackReceiver {
    suspend fun listen(
        session: SpeakerFeedbackSession,
        onRegistered: suspend () -> Unit,
        onLeaseRefresh: suspend () -> Unit,
        onStats: (SpeakerFeedbackRuntimeStats) -> Unit
    )
}

/**
 * MCU 麦克风 UDP 接收器。
 *
 * 必须用同一个 UDP socket 完成 relay 注册和收包，否则 NAT 映射端口不同，
 * relay 发回的数据无法到达当前监听器。
 */
class SpeakerFeedbackClient(
    private val config: SpeakerRelayConfig = SpeakerRelayConfig(),
    private val token: String = BuildConfig.TJI_SPEAKER_RELAY_TOKEN,
    private val timing: SpeakerFeedbackTiming = SpeakerFeedbackTiming(),
    private val monotonicNowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val audioSinkFactory: () -> SpeakerFeedbackAudioSink = {
        AndroidSpeakerFeedbackAudioSink()
    }
) : SpeakerFeedbackReceiver {
    init {
        require(token.isNotBlank() && token.none(Char::isWhitespace)) {
            "UDP relay token 不能为空或包含空白字符"
        }
    }

    override suspend fun listen(
        session: SpeakerFeedbackSession,
        onRegistered: suspend () -> Unit,
        onLeaseRefresh: suspend () -> Unit,
        onStats: (SpeakerFeedbackRuntimeStats) -> Unit
    ) = withContext(Dispatchers.IO) {
        val relayAddress = InetAddress.getByName(config.host)
        val jitter = SpeakerFeedbackJitterBuffer()
        val audioSink = audioSinkFactory()
        var registered = false
        var stats = SpeakerFeedbackRuntimeStats()
        var lastRegistrationMs = 0L
        var registeredAtMs = 0L
        var lastAudioPacketMs = 0L
        val startedAtMs = monotonicNowMs()

        DatagramSocket().use { socket ->
            socket.soTimeout = timing.socketTimeoutMs
            socket.receiveBufferSize = max(socket.receiveBufferSize, UDP_RECEIVE_BUFFER_BYTES)
            try {
                sendRegistration(socket, relayAddress, session, enabled = true)
                lastRegistrationMs = monotonicNowMs()
                val receiveBuffer = ByteArray(SpeakerFeedbackProtocol.MAX_DATAGRAM_BYTES)

                while (currentCoroutineContext().isActive) {
                    val nowMs = monotonicNowMs()
                    if (!registered && nowMs - startedAtMs >= timing.registrationTimeoutMs) {
                        error("MCU 麦克风回传服务器注册超时")
                    }
                    if (registered && stats.packetsReceived == 0L &&
                        nowMs - registeredAtMs >= timing.firstAudioTimeoutMs
                    ) {
                        error("MCU 未返回麦克风音频")
                    }
                    if (lastAudioPacketMs != 0L &&
                        nowMs - lastAudioPacketMs >= timing.audioIdleTimeoutMs
                    ) {
                        error("MCU 麦克风音频已中断")
                    }

                    val refreshInterval = if (registered) {
                        timing.registrationRefreshMs
                    } else {
                        timing.registrationRetryMs
                    }
                    if (nowMs - lastRegistrationMs >= refreshInterval) {
                        sendRegistration(socket, relayAddress, session, enabled = true)
                        lastRegistrationMs = nowMs
                        if (registered) onLeaseRefresh()
                    }

                    val datagram = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    try {
                        socket.receive(datagram)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    if (datagram.address != relayAddress || datagram.port != config.port) continue

                    val expectedAck = registrationAck(session)
                    if (datagram.length == expectedAck.size &&
                        receiveBuffer.regionMatches(0, expectedAck)
                    ) {
                        if (!registered) {
                            registered = true
                            registeredAtMs = monotonicNowMs()
                            audioSink.start()
                            onRegistered()
                        }
                        continue
                    }
                    if (!registered) continue

                    val packet = SpeakerFeedbackProtocol.parse(receiveBuffer, datagram.length)
                    if (packet == null ||
                        packet.deviceId != session.deviceId ||
                        packet.sessionId != session.sessionId ||
                        packet.talkId != session.talkId
                    ) {
                        stats = stats.copy(packetsRejected = stats.packetsRejected + 1)
                        onStats(stats)
                        continue
                    }

                    stats = stats.copy(packetsReceived = stats.packetsReceived + 1)
                    lastAudioPacketMs = monotonicNowMs()
                    jitter.offer(packet).forEach { ready ->
                        val pcm = ready?.let(SpeakerFeedbackProtocol::decodePcm16le)
                            ?: ByteArray(SpeakerFeedbackProtocol.PCM_BYTES_PER_PACKET)
                        audioSink.write(pcm)
                        stats = stats.copy(packetsPlayed = stats.packetsPlayed + 1)
                    }
                    stats = stats.copy(
                        packetsConcealed = jitter.concealedPackets,
                        duplicatePackets = jitter.duplicatePackets
                    )
                    onStats(stats)
                }
            } finally {
                runCatching { sendRegistration(socket, relayAddress, session, enabled = false) }
                audioSink.close()
            }
        }
    }

    private fun sendRegistration(
        socket: DatagramSocket,
        address: InetAddress,
        session: SpeakerFeedbackSession,
        enabled: Boolean
    ) {
        val prefix = if (enabled) "HLAPP1" else "HLAPP0"
        val payload = "$prefix $token ${session.deviceId} ${session.sessionId} ${session.talkId}"
            .toByteArray(Charsets.UTF_8)
        socket.send(DatagramPacket(payload, payload.size, address, config.port))
    }

    private fun registrationAck(session: SpeakerFeedbackSession): ByteArray =
        "HLAPPACK1 REGISTERED ${session.deviceId} ${session.sessionId} ${session.talkId}"
            .toByteArray(Charsets.UTF_8)

    private fun ByteArray.regionMatches(offset: Int, expected: ByteArray): Boolean {
        if (offset < 0 || size - offset < expected.size) return false
        return expected.indices.all { this[offset + it] == expected[it] }
    }

    private companion object {
        const val UDP_RECEIVE_BUFFER_BYTES = 128 * 1_024
    }
}

private class AndroidSpeakerFeedbackAudioSink : SpeakerFeedbackAudioSink {
    private var track: AudioTrack? = null

    override fun start() {
        if (track != null) return
        val minBuffer = AudioTrack.getMinBufferSize(
            SpeakerFeedbackProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "手机不支持 16kHz 单声道 PCM16 播放" }
        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SpeakerFeedbackProtocol.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(
                max(minBuffer, SpeakerFeedbackProtocol.PCM_BYTES_PER_PACKET * AUDIO_BUFFER_PACKETS)
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        check(newTrack.state == AudioTrack.STATE_INITIALIZED) {
            newTrack.release()
            "初始化 MCU 麦克风播放器失败"
        }
        newTrack.play()
        track = newTrack
    }

    override fun write(pcm: ByteArray) {
        val audioTrack = track ?: return
        var offset = 0
        while (offset < pcm.size) {
            val written = audioTrack.write(
                pcm,
                offset,
                pcm.size - offset,
                AudioTrack.WRITE_BLOCKING
            )
            check(written > 0) { "MCU 麦克风音频播放失败: $written" }
            offset += written
        }
    }

    override fun close() {
        val audioTrack = track ?: return
        track = null
        runCatching { audioTrack.stop() }
        audioTrack.release()
    }

    private companion object {
        const val AUDIO_BUFFER_PACKETS = 6
    }
}
