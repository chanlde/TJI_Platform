package com.tji.device.product.speaker.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.tji.device.product.speaker.core.SpeakerLogger
import com.tji.device.BuildConfig
import com.tji.device.product.speaker.core.SpeakerCoreNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
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
    fun setVolume(gain: Float) = Unit
    fun beginDebugCapture(): Boolean = false
    fun endDebugCapture() = Unit
}

/** MCU 实时 Opus 解码器；空包解码用于 Opus PLC 丢包补偿。 */
interface SpeakerFeedbackOpusDecoder : AutoCloseable {
    fun decode(payload: ByteArray): ByteArray?
    fun conceal(): ByteArray?
}

interface SpeakerFeedbackReceiver {
    fun setPlaybackGain(gain: Float) = Unit
    fun startDebugCapture(): Boolean = false
    fun stopDebugCapture() = Unit

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
    private val debugCaptureDirectory: File? = null,
    private val audioSinkFactory: () -> SpeakerFeedbackAudioSink = {
        AndroidSpeakerFeedbackAudioSink(debugCaptureDirectory)
    },
    private val opusDecoderFactory: (Int, Int) -> SpeakerFeedbackOpusDecoder? =
        { sampleRate, packetMs ->
            SpeakerCoreNative.createRealtimeOpusDecoderOrNull(sampleRate, packetMs)
                ?.let(::NativeSpeakerFeedbackOpusDecoder)
        }
) : SpeakerFeedbackReceiver {
    @Volatile
    private var playbackGain = SpeakerAudioConfig.Gain.MCU_MONITOR_OUTPUT_GAIN

    @Volatile
    private var activeAudioSink: SpeakerFeedbackAudioSink? = null

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
        audioSink.setVolume(playbackGain)
        val opusDecoder = opusDecoderFactory(
            SpeakerFeedbackProtocol.SAMPLE_RATE,
            SpeakerFeedbackProtocol.PACKET_MS
        ) ?: error("初始化 MCU 麦克风 Opus 解码器失败")
        activeAudioSink = audioSink
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
                        val pcm = if (ready != null) {
                            opusDecoder.decode(ready.payload)
                        } else {
                            opusDecoder.conceal()
                        } ?: error("MCU 麦克风 Opus 解码失败")
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
                if (activeAudioSink === audioSink) activeAudioSink = null
                audioSink.close()
                opusDecoder.close()
            }
        }
    }

    override fun setPlaybackGain(gain: Float) {
        playbackGain = gain.coerceIn(0f, 1f)
        activeAudioSink?.setVolume(playbackGain)
    }

    override fun startDebugCapture(): Boolean =
        activeAudioSink?.beginDebugCapture() == true

    override fun stopDebugCapture() {
        activeAudioSink?.endDebugCapture()
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

private class NativeSpeakerFeedbackOpusDecoder(
    private val delegate: SpeakerCoreNative.RealtimeOpusDecoder
) : SpeakerFeedbackOpusDecoder {
    override fun decode(payload: ByteArray): ByteArray? = delegate.decodeOrNull(payload)
    override fun conceal(): ByteArray? = delegate.concealOrNull()
    override fun close() = delegate.close()
}

private class AndroidSpeakerFeedbackAudioSink(
    private val debugCaptureDirectory: File?
) : SpeakerFeedbackAudioSink {
    private var track: AudioTrack? = null
    private var writtenPackets = 0L
    private var playbackGain = SpeakerAudioConfig.Gain.MCU_MONITOR_OUTPUT_GAIN
    private val captureLock = Any()
    private var captureFile: File? = null
    private var captureOutput: RandomAccessFile? = null
    private var capturedPcmBytes = 0L

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
                    // MCU monitoring is a user-facing loudspeaker stream.  Keeping it on
                    // VOICE_COMMUNICATION routes it to the handset earpiece on some MIUI
                    // devices, making an otherwise healthy stream appear silent.
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
        newTrack.setVolume(playbackGain)
        newTrack.play()
        if (BuildConfig.DEBUG) {
            SpeakerLogger.debug(
                SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                "mcu monitor AudioTrack started session=${newTrack.audioSessionId} " +
                    "sampleRate=${newTrack.sampleRate} gain=$playbackGain"
            )
        }
        track = newTrack
    }

    override fun setVolume(gain: Float) {
        playbackGain = gain.coerceIn(0f, 1f)
        track?.setVolume(playbackGain)
    }

    override fun beginDebugCapture(): Boolean {
        return synchronized(captureLock) {
            if (track == null || captureOutput != null) return@synchronized false
            startDebugCapture()
        }
    }

    override fun endDebugCapture() {
        synchronized(captureLock) {
            finishDebugCapture()
        }
    }

    override fun write(pcm: ByteArray) {
        val audioTrack = track ?: return
        capturePcm(pcm)
        writtenPackets++
        if (BuildConfig.DEBUG && writtenPackets % AUDIO_LEVEL_LOG_INTERVAL_PACKETS == 1L) {
            var sumSquares = 0.0
            var peak = 0
            var samples = 0
            var index = 0
            while (index + 1 < pcm.size) {
                val sample = (
                    (pcm[index].toInt() and 0xFF) or
                        (pcm[index + 1].toInt() shl 8)
                    ).toShort().toInt()
                val magnitude = kotlin.math.abs(sample)
                if (magnitude > peak) peak = magnitude
                sumSquares += sample.toDouble() * sample.toDouble()
                samples++
                index += 2
            }
            val rms = if (samples == 0) 0.0 else kotlin.math.sqrt(sumSquares / samples)
            SpeakerLogger.debug(
                SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                "mcu monitor pcm packet=$writtenPackets bytes=${pcm.size} " +
                    "rms=${rms.toInt()} peak=$peak"
            )
        }
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
        synchronized(captureLock) {
            finishDebugCapture()
        }
        runCatching { audioTrack.stop() }
        audioTrack.release()
    }

    private fun capturePcm(pcm: ByteArray) {
        synchronized(captureLock) {
            val output = captureOutput ?: return
            runCatching {
                output.write(pcm)
                capturedPcmBytes += pcm.size.toLong()
            }.onFailure {
                SpeakerLogger.warn(
                    SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                    "mcu monitor capture write failed",
                    it
                )
                finishDebugCapture()
            }
        }
    }

    private fun startDebugCapture(): Boolean {
        if (!BuildConfig.DEBUG) return false
        val directory = debugCaptureDirectory ?: return false
        return runCatching {
            check(directory.mkdirs() || directory.isDirectory)
            val outputFile = File(
                directory,
                "mcu_monitor_${System.currentTimeMillis()}.wav"
            )
            val output = RandomAccessFile(outputFile, "rw")
            output.setLength(0L)
            output.write(ByteArray(WAV_HEADER_BYTES))
            captureFile = outputFile
            captureOutput = output
            capturedPcmBytes = 0L
            SpeakerLogger.debug(
                SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                "mcu monitor capture started file=${outputFile.absolutePath}"
            )
            true
        }.onFailure {
            SpeakerLogger.warn(
                SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                "mcu monitor capture start failed",
                it
            )
        }.getOrDefault(false)
    }

    private fun finishDebugCapture() {
        val output = captureOutput ?: return
        val outputFile = captureFile
        captureOutput = null
        captureFile = null
        runCatching {
            writeWavHeader(output, capturedPcmBytes)
            output.close()
            SpeakerLogger.debug(
                SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                "mcu monitor capture saved file=${outputFile?.absolutePath} " +
                    "pcmBytes=$capturedPcmBytes"
            )
        }.onFailure {
            runCatching { output.close() }
            SpeakerLogger.warn(
                SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                "mcu monitor capture save failed",
                it
            )
        }
        capturedPcmBytes = 0L
    }

    private fun writeWavHeader(output: RandomAccessFile, pcmBytes: Long) {
        val dataBytes = pcmBytes.coerceAtMost(0xFFFF_FFFFL)
        output.seek(0L)
        output.writeBytes("RIFF")
        output.writeLittleEndian32(36L + dataBytes)
        output.writeBytes("WAVEfmt ")
        output.writeLittleEndian32(16L)
        output.writeLittleEndian16(1)
        output.writeLittleEndian16(SpeakerFeedbackProtocol.CHANNELS)
        output.writeLittleEndian32(SpeakerFeedbackProtocol.SAMPLE_RATE.toLong())
        output.writeLittleEndian32(
            SpeakerFeedbackProtocol.SAMPLE_RATE.toLong() *
                SpeakerFeedbackProtocol.CHANNELS *
                PCM16_BYTES_PER_SAMPLE
        )
        output.writeLittleEndian16(
            SpeakerFeedbackProtocol.CHANNELS * PCM16_BYTES_PER_SAMPLE
        )
        output.writeLittleEndian16(PCM16_BITS_PER_SAMPLE)
        output.writeBytes("data")
        output.writeLittleEndian32(dataBytes)
    }

    private fun RandomAccessFile.writeLittleEndian16(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun RandomAccessFile.writeLittleEndian32(value: Long) {
        write((value and 0xFF).toInt())
        write(((value ushr 8) and 0xFF).toInt())
        write(((value ushr 16) and 0xFF).toInt())
        write(((value ushr 24) and 0xFF).toInt())
    }

    private companion object {
        const val AUDIO_BUFFER_PACKETS = 6
        const val AUDIO_LEVEL_LOG_INTERVAL_PACKETS = 50L
        const val WAV_HEADER_BYTES = 44
        const val PCM16_BYTES_PER_SAMPLE = 2
        const val PCM16_BITS_PER_SAMPLE = 16
    }
}
