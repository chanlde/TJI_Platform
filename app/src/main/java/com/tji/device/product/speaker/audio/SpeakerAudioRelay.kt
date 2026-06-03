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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.max
import kotlin.math.roundToInt

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
            prepareAudioStream(
                socket = socket,
                address = address,
                outputGain = minOf(outputGain, SpeakerAudioConfig.Gain.LIVE_SAFE_OUTPUT_GAIN),
                dspPreset = AudioDspPreset.Howling
            )
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
        onPacketSent: (Int) -> Unit
    ) {
        val packetizer = SpeakerAdpcmPacketizer()
        val streamPcm = pcm.withLeadingSilence(leadingSilenceMs)
        DatagramSocket().use { socket ->
            val address = InetAddress.getByName(config.host)
            prepareAudioStream(socket, address, outputGain, AudioDspPreset.Off)
            var offset = 0
            var sentFrames = 0
            var nextPacedSendAtMs = 0L
            while (offset < streamPcm.size && currentCoroutineContext().isActive) {
                val end = minOf(offset + SpeakerAdpcmPacketizer.PCM_FRAME_BYTES, streamPcm.size)
                val frame = streamPcm.copyOfRange(offset, end)
                packetizer.packetize(frame)?.let { packet ->
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

    suspend fun sendTone(
        frequencyHz: Int = SpeakerAudioConfig.Tone.FREQUENCY_HZ,
        amplitude: Float = SpeakerAudioConfig.Tone.AMPLITUDE,
        outputGain: Float
    ) {
        DatagramSocket().use { socket ->
            val address = InetAddress.getByName(config.host)
            sendDspControl(socket, address, AudioDspPreset.Off)
            sendGainControl(socket, address, outputGain)
            sendLocalToneControl(socket, address, frequencyHz, amplitude)
        }
    }

    suspend fun sendOutputGain(outputGain: Float) {
        DatagramSocket().use { socket ->
            val address = InetAddress.getByName(config.host)
            sendGainControl(socket, address, outputGain)
        }
    }

    suspend fun sendStreamReset() {
        DatagramSocket().use { socket ->
            val address = InetAddress.getByName(config.host)
            sendStreamReset(socket, address)
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

    private suspend fun prepareAudioStream(
        socket: DatagramSocket,
        address: InetAddress,
        outputGain: Float,
        dspPreset: AudioDspPreset
    ) {
        sendStreamReset(socket, address)
        delay(SpeakerAudioConfig.Timing.STREAM_RESET_DELAY_MS)
        sendDspControl(socket, address, dspPreset)
        sendGainControl(socket, address, outputGain)
    }

    private suspend fun sendGainControl(
        socket: DatagramSocket,
        address: InetAddress,
        outputGain: Float
    ) {
        val gainQ8 = (
            outputGain.coerceIn(0f, SpeakerAudioConfig.Gain.MAX_OUTPUT_GAIN) *
                SpeakerAudioConfig.Gain.CONTROL_GAIN_Q8_SCALE
            )
            .roundToInt()
            .coerceIn(0, SpeakerAudioConfig.Gain.CONTROL_GAIN_Q8_SCALE.toInt())
        val packet = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(AUDIO_CONTROL_MAGIC.toShort())
            .put(AUDIO_CONTROL_VERSION.toByte())
            .put(AUDIO_CONTROL_GAIN.toByte())
            .putShort(gainQ8.toShort())
            .putShort(0)
            .array()
        repeat(SpeakerAudioConfig.Timing.GAIN_CONTROL_REPEAT) {
            socket.send(DatagramPacket(packet, packet.size, address, config.port))
            delay(SpeakerAudioConfig.Timing.GAIN_CONTROL_REPEAT_DELAY_MS)
        }
    }

    private fun sendStreamReset(socket: DatagramSocket, address: InetAddress) {
        val packet = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(AUDIO_CONTROL_MAGIC.toShort())
            .put(AUDIO_CONTROL_VERSION.toByte())
            .put(AUDIO_CONTROL_RESET.toByte())
            .put(0)
            .put(0)
            .put(0)
            .put(0)
            .array()
        socket.send(DatagramPacket(packet, packet.size, address, config.port))
    }

    private suspend fun sendDspControl(
        socket: DatagramSocket,
        address: InetAddress,
        preset: AudioDspPreset
    ) {
        val packet = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(AUDIO_CONTROL_MAGIC.toShort())
            .put(AUDIO_CONTROL_VERSION.toByte())
            .put(AUDIO_CONTROL_DSP.toByte())
            .put(preset.mode.toByte())
            .put(preset.noise.toByte())
            .put(preset.voice.toByte())
            .put(preset.howling.toByte())
            .array()
        repeat(SpeakerAudioConfig.Timing.DSP_CONTROL_REPEAT) {
            socket.send(DatagramPacket(packet, packet.size, address, config.port))
            delay(SpeakerAudioConfig.Timing.DSP_CONTROL_REPEAT_DELAY_MS)
        }
    }

    private suspend fun sendLocalToneControl(
        socket: DatagramSocket,
        address: InetAddress,
        frequencyHz: Int,
        amplitude: Float
    ) {
        val clampedFrequency = frequencyHz.coerceIn(100, SpeakerAdpcmPacketizer.SAMPLE_RATE / 2 - 1)
        val amplitudeI16 = (amplitude.coerceIn(0.01f, 1f) * Short.MAX_VALUE)
            .toInt()
            .coerceIn(0, Short.MAX_VALUE.toInt())
        val packet = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(AUDIO_CONTROL_MAGIC.toShort())
            .put(AUDIO_CONTROL_VERSION.toByte())
            .put(AUDIO_CONTROL_LOCAL_TONE.toByte())
            .putShort(clampedFrequency.toShort())
            .putShort(amplitudeI16.toShort())
            .array()
        repeat(SpeakerAudioConfig.Timing.LOCAL_TONE_CONTROL_REPEAT) {
            socket.send(DatagramPacket(packet, packet.size, address, config.port))
            delay(SpeakerAudioConfig.Timing.LOCAL_TONE_CONTROL_REPEAT_DELAY_MS)
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

    private companion object {
        const val AUDIO_CONTROL_MAGIC = 0xA55B
        const val AUDIO_CONTROL_VERSION = 1
        const val AUDIO_CONTROL_DSP = 2
        const val AUDIO_CONTROL_RESET = 5
        const val AUDIO_CONTROL_GAIN = 6
        const val AUDIO_CONTROL_LOCAL_TONE = 7
        const val MILLIS_PER_SECOND = 1_000
        const val BYTES_PER_PCM16_SAMPLE = 2
    }
}

private enum class AudioDspPreset(
    val mode: Int,
    val noise: Int,
    val voice: Int,
    val howling: Int
) {
    Off(mode = 0, noise = 0, voice = 0, howling = 0),
    Howling(mode = 3, noise = 1, voice = 1, howling = 3)
}
