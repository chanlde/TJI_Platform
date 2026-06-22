package com.tji.device.product.speaker.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresPermission
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
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun streamMicrophone(
        outputGain: Float,
        toneSettings: SpeakerToneSettings = SpeakerToneSettings(),
        onPacketSent: (Int) -> Unit
    ) {
        val packetizer = SpeakerAdpcmPacketizer()
        val kotlinShadowPacketizer = SpeakerAdpcmPacketizer(useNative = false)
        val shadowSession = SpeakerCoreShadowVerifier.createUdpPacketSessionOrNull()
        val voiceProcessor = SpeakerCoreAudioEngine.createLiveVoiceProcessor()
        var frameCount = 0
        var debugFrameCount = 0
        logUdpShadowAvailability(shadowSession, "live-legacy-udp")
        try {
            DatagramSocket().use { socket ->
                val address = InetAddress.getByName(config.host)
                captureMicrophone(
                    onFrame = { frame ->
                        val sequence = frameCount
                        val processedFrame = if (sequence < SpeakerAudioConfig.Timing.LIVE_STARTUP_MUTE_FRAMES) {
                            ByteArray(frame.size)
                        } else {
                            voiceProcessor.processFrame(frame, toneSettings)
                        }
                        frameCount += 1
                        if (debugFrameCount < SpeakerAudioConfig.Debug.AUDIO_DEBUG_FRAME_LIMIT) {
                            logAudioStats("live frame#$debugFrameCount", frame, processedFrame)
                            debugFrameCount += 1
                        }
                        packetizer.packetize(processedFrame)?.let { packet ->
                            val kotlinPacket = kotlinShadowPacketizer.packetize(processedFrame)
                            logUdpPacketShadowResult(
                                session = shadowSession,
                                path = "live-legacy-udp",
                                kotlinPacket = kotlinPacket,
                                pcm16le = processedFrame,
                                sequence = sequence,
                                context = null,
                                isLastPacket = false
                            )
                            sendPacket(socket, address, packet)
                            onPacketSent(1)
                        }
                    }
                )
            }
        } finally {
            packetizer.close()
            kotlinShadowPacketizer.close()
            voiceProcessor.close()
            shadowSession?.close()
        }
    }

    suspend fun sendRecordedPcm(
        pcm: ByteArray,
        outputGain: Float,
        prebufferPackets: Int = 0,
        leadingSilenceMs: Int = 0,
        streamContext: SpeakerUdpStreamContext? = null,
        useNativePacketizer: Boolean = true,
        onPacketSent: (Int) -> Unit
    ) {
        val frameBytes = SpeakerAdpcmPacketizer.PCM_FRAME_BYTES
        val packetizer = SpeakerAdpcmPacketizer(streamContext, useNative = useNativePacketizer)
        val kotlinShadowPacketizer = SpeakerAdpcmPacketizer(streamContext, useNative = false)
        val shadowSession = SpeakerCoreShadowVerifier.createUdpPacketSessionOrNull()
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
                        val kotlinPacket = kotlinShadowPacketizer.packetize(frame, isLastPacket = isLastPacket)
                        logUdpPacketShadowResult(
                            session = shadowSession,
                            path = shadowPath,
                            kotlinPacket = kotlinPacket,
                            pcm16le = frame,
                            sequence = sentFrames,
                            context = streamContext,
                            isLastPacket = isLastPacket
                        )
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
            kotlinShadowPacketizer.close()
            shadowSession?.close()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun captureMicrophoneFrames(onFrame: suspend (ByteArray) -> Unit) {
        captureMicrophone(onFrame)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun captureMicrophone(onFrame: suspend (ByteArray) -> Unit) {
        val frameBytes = SpeakerAdpcmPacketizer.PCM_FRAME_BYTES
        val minBuffer = AudioRecord.getMinBufferSize(
            SpeakerAdpcmPacketizer.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBuffer, frameBytes * 4)
        val recorder = AudioRecord(
            microphoneAudioSource(),
            SpeakerAdpcmPacketizer.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        val frame = ByteArray(frameBytes)
        var filled = 0
        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(frame, filled, frameBytes - filled)
                if (read <= 0) continue
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            MediaRecorder.AudioSource.MIC
        }

    private fun sendPacket(socket: DatagramSocket, address: InetAddress, packet: ByteArray) {
        repeat(config.redundancy.coerceAtLeast(1)) {
            socket.send(DatagramPacket(packet, packet.size, address, config.port))
        }
    }

    private fun logAudioStats(label: String, raw: ByteArray, processed: ByteArray) {
        val rawStats = SpeakerVoiceProcessor.measurePcm(raw)
        val processedStats = SpeakerVoiceProcessor.measurePcm(processed)
        val ratio = if (rawStats.rms > 0f) processedStats.rms / rawStats.rms else 0f
        Log.d(
            SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG,
            "$label rawRms=${rawStats.rms} rawPeak=${rawStats.peak} " +
                "processedRms=${processedStats.rms} processedPeak=${processedStats.peak} " +
                "rmsRatio=$ratio samples=${processedStats.samples}"
        )
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
