package com.tji.device.product.speaker.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresPermission
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
        val voiceProcessor = SpeakerVoiceProcessor()
        var frameCount = 0
        var debugFrameCount = 0
        DatagramSocket().use { socket ->
            val address = InetAddress.getByName(config.host)
            captureMicrophone(
                onFrame = { frame ->
                    val processedFrame = if (frameCount < SpeakerAudioConfig.Timing.LIVE_STARTUP_MUTE_FRAMES) {
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
                        sendPacket(socket, address, packet)
                        onPacketSent(1)
                    }
                }
            )
        }
    }

    suspend fun sendRecordedPcm(
        pcm: ByteArray,
        outputGain: Float,
        prebufferPackets: Int = 0,
        leadingSilenceMs: Int = 0,
        streamContext: SpeakerUdpStreamContext? = null,
        onPacketSent: (Int) -> Unit
    ) {
        val frameBytes = SpeakerAdpcmPacketizer.PCM_FRAME_BYTES
        val packetizer = SpeakerAdpcmPacketizer(streamContext)
        val streamPcm = pcm
            .withLeadingSilence(leadingSilenceMs)
            .withTrailingFramePadding(frameBytes)
        DatagramSocket().use { socket ->
            val address = InetAddress.getByName(config.host)
            var offset = 0
            var sentFrames = 0
            var nextPacedSendAtMs = 0L
            while (offset < streamPcm.size && currentCoroutineContext().isActive) {
                val end = minOf(offset + frameBytes, streamPcm.size)
                val frame = streamPcm.copyOfRange(offset, end)
                packetizer.packetize(frame, isLastPacket = end >= streamPcm.size)?.let { packet ->
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

    private fun ByteArray.withLeadingSilence(leadingSilenceMs: Int): ByteArray {
        val silenceBytes = SpeakerAdpcmPacketizer.SAMPLE_RATE *
            leadingSilenceMs.coerceAtLeast(0) /
            MILLIS_PER_SECOND *
            BYTES_PER_PCM16_SAMPLE
        if (silenceBytes <= 0) return this
        return ByteArray(silenceBytes) + this
    }

    private fun ByteArray.withTrailingFramePadding(frameBytes: Int): ByteArray {
        val remainder = size % frameBytes
        if (remainder == 0) return this
        return copyOf(size + frameBytes - remainder)
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

    private companion object {
        const val MILLIS_PER_SECOND = 1_000
        const val BYTES_PER_PCM16_SAMPLE = 2
    }
}
