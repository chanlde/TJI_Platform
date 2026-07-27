package com.tji.device.product.speaker.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresPermission
import com.tji.device.BuildConfig
import com.tji.device.product.speaker.core.SpeakerCoreAudioEngine
import com.tji.device.product.speaker.core.SpeakerCoreShadowVerifier
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.max

data class SpeakerRelayConfig(
    val host: String = SpeakerAudioConfig.Relay.HOST,
    val port: Int = SpeakerAudioConfig.Relay.PORT,
    val redundancy: Int = SpeakerAudioConfig.Relay.REDUNDANCY
)

class SpeakerAudioRelay(
    private val config: SpeakerRelayConfig = SpeakerRelayConfig()
) {
    suspend fun sendRecordedPcm(
        pcm: ByteArray,
        prebufferPackets: Int = 0,
        leadingSilenceMs: Int = 0,
        streamContext: SpeakerUdpStreamContext? = null,
        useNativePacketizer: Boolean = true,
        onPacketSent: (Int) -> Unit
    ) {
        val frameBytes = SpeakerAdpcmPacketizer.PCM_FRAME_BYTES
        val packetizer = SpeakerAdpcmPacketizer(streamContext, useNative = useNativePacketizer)
        val kotlinShadowPacketizer =
            if (BuildConfig.DEBUG) SpeakerAdpcmPacketizer(streamContext, useNative = false) else null
        val shadowSession =
            if (BuildConfig.DEBUG) SpeakerCoreShadowVerifier.createUdpPacketSessionOrNull() else null
        val shadowPath = if (streamContext == null) "recorded-legacy-udp" else "recorded-v2-udp"
        val streamPcm = SpeakerCoreAudioEngine
            .prependSilencePcm16(
                pcm16le = pcm,
                durationMs = leadingSilenceMs,
                sampleRate = SpeakerAdpcmPacketizer.SAMPLE_RATE
            )
            .let { SpeakerCoreAudioEngine.padPcm16ToFrame(it, frameBytes) }
        logUdpShadowAvailability(shadowSession, shadowPath)
        try {
            DatagramSocket().use { socket ->
                val address = InetAddress.getByName(config.host)
                var offset = 0
                var sentFrames = 0
                var nextPacedSendAtMs = 0L
                while (offset < streamPcm.size && currentCoroutineContext().isActive) {
                    val end = minOf(offset + frameBytes, streamPcm.size)
                    val frame = streamPcm.copyOfRange(offset, end)
                    val isLastPacket = end >= streamPcm.size
                    packetizer.packetize(frame, isLastPacket = isLastPacket)?.let { packet ->
                        kotlinShadowPacketizer?.packetize(frame, isLastPacket = isLastPacket)?.let { kotlinPacket ->
                            logUdpPacketShadowResult(
                                session = shadowSession,
                                path = shadowPath,
                                kotlinPacket = kotlinPacket,
                                pcm16le = frame,
                                sequence = sentFrames,
                                context = streamContext,
                                isLastPacket = isLastPacket
                            )
                        }
                        sendPacket(socket, address, packet)
                        onPacketSent(1)
                    }
                    offset = end
                    sentFrames += 1
                    if (sentFrames > prebufferPackets.coerceAtLeast(0)) {
                        nextPacedSendAtMs = if (nextPacedSendAtMs == 0L) {
                            SystemClock.elapsedRealtime() + SpeakerAdpcmPacketizer.PACKET_MS
                        } else {
                            nextPacedSendAtMs + SpeakerAdpcmPacketizer.PACKET_MS
                        }
                        val waitMs = nextPacedSendAtMs - SystemClock.elapsedRealtime()
                        if (waitMs > 0) delay(waitMs)
                    }
                }
            }
        } finally {
            packetizer.close()
            kotlinShadowPacketizer?.close()
            shadowSession?.close()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun captureMicrophoneFrames(
        sampleRate: Int,
        onFrame: suspend (ByteArray) -> Unit
    ) {
        captureMicrophone(sampleRate, onFrame)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun captureMicrophone(
        sampleRate: Int = SpeakerAdpcmPacketizer.SAMPLE_RATE,
        onFrame: suspend (ByteArray) -> Unit
    ) {
        require(sampleRate in SpeakerMicrophoneFormat.supportedSampleRates) {
            "不支持的麦克风采样率: $sampleRate"
        }
        val frameBytes = SpeakerMicrophoneFormat.frameBytes(sampleRate)
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "手机不支持 ${sampleRate / 1_000}kHz 单声道 PCM16 录音" }
        val bufferSize = max(minBuffer, frameBytes * 4)
        val recorder = AudioRecord(
            microphoneAudioSource(),
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "初始化 ${sampleRate / 1_000}kHz 麦克风失败"
        }
        val frame = ByteArray(frameBytes)
        var filled = 0
        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(frame, filled, frameBytes - filled)
                check(read >= 0) { "麦克风读取失败: $read" }
                if (read == 0) continue
                filled += read - (read % 2)
                if (filled >= frameBytes) {
                    onFrame(frame.copyOf())
                    filled = 0
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun microphoneAudioSource(): Int =
        MediaRecorder.AudioSource.VOICE_RECOGNITION

    private fun sendPacket(socket: DatagramSocket, address: InetAddress, packet: ByteArray) {
        repeat(config.redundancy.coerceAtLeast(1)) {
            socket.send(DatagramPacket(packet, packet.size, address, config.port))
        }
    }

    private fun logUdpShadowAvailability(
        session: SpeakerCoreShadowVerifier.UdpPacketSession?,
        path: String
    ) {
        if (session == null) {
            Log.d(
                SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
                "speakerCoreShadow status=nativeUnavailable path=$path"
            )
        }
    }

    private fun logUdpPacketShadowResult(
        session: SpeakerCoreShadowVerifier.UdpPacketSession?,
        path: String,
        kotlinPacket: ByteArray?,
        pcm16le: ByteArray,
        sequence: Int,
        context: SpeakerUdpStreamContext?,
        isLastPacket: Boolean
    ) {
        if (session == null) return
        if (kotlinPacket == null) return
        if (sequence >= SpeakerAudioConfig.Debug.AUDIO_DEBUG_FRAME_LIMIT && !isLastPacket) return
        val result = if (context == null) {
            session.compareLegacyPacket(
                kotlinPacket = kotlinPacket,
                pcm16le = pcm16le,
                sequence = sequence,
                timestampSamples = sequence * SpeakerAdpcmPacketizer.PCM_FRAME_BYTES / BYTES_PER_PCM16_SAMPLE
            )
        } else {
            session.compareV2Packet(
                kotlinPacket = kotlinPacket,
                pcm16le = pcm16le,
                sequence = sequence,
                timestampMs = sequence * SpeakerAdpcmPacketizer.PACKET_MS,
                context = context,
                isLastPacket = isLastPacket
            )
        }
        Log.d(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "${result.toLogLine()} path=$path sequence=$sequence " +
                "isLastPacket=$isLastPacket packetBytes=${kotlinPacket.size} streamType=${context?.type?.name ?: "Legacy"}"
        )
    }

    private companion object {
        const val BYTES_PER_PCM16_SAMPLE = 2
    }
}
