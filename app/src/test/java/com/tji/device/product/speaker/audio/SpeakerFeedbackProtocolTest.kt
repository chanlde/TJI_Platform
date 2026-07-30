package com.tji.device.product.speaker.audio

import com.tji.device.product.speaker.core.SpeakerCommandJson
import com.tji.device.product.speaker.model.SpeakerCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SpeakerFeedbackProtocolTest {
    @Test
    fun parsesMcuOpusPacket() {
        val parsed = SpeakerFeedbackProtocol.parse(feedbackPacket(sequence = 7))

        assertNotNull(parsed)
        assertEquals(7, parsed?.sequence)
        assertEquals(16_000, parsed?.sampleRate)
        assertEquals(320, parsed?.sampleCount)
        assertEquals("TEWNHZDBK", parsed?.deviceId)
        assertEquals("FB_1", parsed?.sessionId)
        assertEquals("MON_1", parsed?.talkId)
        assertEquals(SpeakerFeedbackProtocol.CODEC_OPUS, parsed?.codec)
        assertTrue(requireNotNull(parsed).payload.isNotEmpty())
    }

    @Test
    fun rejectsWrongFlagsLengthsAndMalformedRoutingIds() {
        assertNull(SpeakerFeedbackProtocol.parse(feedbackPacket(flags = 0x0004)))
        assertNull(SpeakerFeedbackProtocol.parse(feedbackPacket(codec = 1)))
        assertNull(SpeakerFeedbackProtocol.parse(feedbackPacket().copyOf(40)))

        val malformedUtf8 = feedbackPacket()
        malformedUtf8[28] = 0xC3.toByte()
        malformedUtf8[29] = 0x28
        assertNull(SpeakerFeedbackProtocol.parse(malformedUtf8))
    }

    @Test
    fun jitterBufferReordersStartupPacketsAndConcealsLateLoss() {
        val reordered = SpeakerFeedbackJitterBuffer(
            prebufferPackets = 3,
            maxReorderPackets = 2
        )
        assertTrue(reordered.offer(parsedPacket(2)).isEmpty())
        assertTrue(reordered.offer(parsedPacket(0)).isEmpty())
        assertEquals(
            listOf(0, 1, 2),
            reordered.offer(parsedPacket(1)).map { it?.sequence }
        )

        val lossy = SpeakerFeedbackJitterBuffer(
            prebufferPackets = 1,
            maxReorderPackets = 2
        )
        assertEquals(listOf(0), lossy.offer(parsedPacket(0)).map { it?.sequence })
        assertTrue(lossy.offer(parsedPacket(2)).isEmpty())
        assertEquals(listOf(null, 2, 3), lossy.offer(parsedPacket(3)).map { it?.sequence })
        assertEquals(1L, lossy.concealedPackets)
        assertTrue(lossy.offer(parsedPacket(2)).isEmpty())
        assertEquals(1L, lossy.duplicatePackets)
    }

    @Test
    fun feedbackCommandUsesSingleCmd116ForEnableAndDisable() {
        val enabled = SpeakerCommandJson.encode(
            SpeakerCommand.SetMcuMicrophoneFeedback(
                msgId = "feedback-on",
                enabled = true,
                sessionId = "FB_1",
                talkId = "MON_1"
            ),
            deviceId = "TEWNHZDBK",
            timestampMs = 10L
        )
        val disabled = SpeakerCommandJson.encode(
            SpeakerCommand.SetMcuMicrophoneFeedback(
                msgId = "feedback-off",
                enabled = false
            ),
            deviceId = "TEWNHZDBK",
            timestampMs = 20L
        )

        assertEquals(116, enabled.getInt("cmd"))
        assertEquals(1, enabled.getInt("enabled"))
        assertEquals(1, enabled.getJSONObject("params").getInt("enabled"))
        assertEquals(16_000, enabled.getJSONObject("params").getInt("sampleRate"))
        assertEquals(20, enabled.getJSONObject("params").getInt("packetMs"))
        assertEquals(30_000L, enabled.getJSONObject("params").getLong("ttlMs"))
        assertEquals(116, disabled.getInt("cmd"))
        assertEquals(0, disabled.getInt("enabled"))
        assertFalse(disabled.getJSONObject("params").has("sessionId"))
    }

    @Test
    fun clientRegistersRefreshesDecodesAndUnregistersOnSameSocket() = runBlocking {
        val relay = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        relay.soTimeout = 100
        val running = AtomicBoolean(true)
        val registrations = AtomicInteger()
        val unregisters = AtomicInteger()
        val relayFailure = AtomicReference<Throwable?>(null)
        val relayThread = Thread {
            val buffer = ByteArray(2_048)
            while (running.get()) {
                try {
                    val datagram = DatagramPacket(buffer, buffer.size)
                    relay.receive(datagram)
                    val text = buffer.copyOf(datagram.length).toString(Charsets.UTF_8)
                    when {
                        text == "HLAPP1 hydrolink TEWNHZDBK FB_1 MON_1" -> {
                            val count = registrations.incrementAndGet()
                            val ack = "HLAPPACK1 REGISTERED TEWNHZDBK FB_1 MON_1"
                                .toByteArray()
                            relay.send(DatagramPacket(ack, ack.size, datagram.address, datagram.port))
                            if (count == 1) {
                                repeat(4) { sequence ->
                                    val audio = feedbackPacket(sequence = sequence)
                                    relay.send(
                                        DatagramPacket(audio, audio.size, datagram.address, datagram.port)
                                    )
                                }
                            }
                        }
                        text == "HLAPP0 hydrolink TEWNHZDBK FB_1 MON_1" -> {
                            unregisters.incrementAndGet()
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Poll running.
                } catch (_: SocketException) {
                    if (running.get()) relayFailure.set(AssertionError("relay socket closed early"))
                } catch (throwable: Throwable) {
                    relayFailure.set(throwable)
                    return@Thread
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        val sink = FakeFeedbackAudioSink()
        val registered = AtomicInteger()
        val leaseRefreshes = AtomicInteger()
        val client = SpeakerFeedbackClient(
            config = SpeakerRelayConfig(host = "127.0.0.1", port = relay.localPort),
            token = "hydrolink",
            timing = SpeakerFeedbackTiming(
                socketTimeoutMs = 20,
                registrationRetryMs = 50,
                registrationTimeoutMs = 1_000,
                registrationRefreshMs = 80,
                firstAudioTimeoutMs = 1_000,
                audioIdleTimeoutMs = 1_000
            ),
            audioSinkFactory = { sink },
            opusDecoderFactory = { _, _ -> FakeFeedbackOpusDecoder() }
        )
        client.setPlaybackGain(0.6f)
        val clientJob = launch(Dispatchers.IO) {
            client.listen(
                session = SpeakerFeedbackSession("TEWNHZDBK", "FB_1", "MON_1"),
                onRegistered = { registered.incrementAndGet() },
                onLeaseRefresh = { leaseRefreshes.incrementAndGet() },
                onStats = {}
            )
        }

        try {
            withTimeout(2_000) {
                while (sink.writes.size < 4 || leaseRefreshes.get() < 1) delay(10)
            }
            client.setPlaybackGain(0.8f)
        } finally {
            clientJob.cancelAndJoin()
            withTimeout(500) {
                while (unregisters.get() < 1) delay(10)
            }
            running.set(false)
            relay.close()
            relayThread.join(1_000)
        }

        assertEquals(1, registered.get())
        assertTrue(registrations.get() >= 2)
        assertEquals(1, unregisters.get())
        assertTrue(sink.started)
        assertTrue(sink.closed)
        assertEquals(listOf(0.6f, 0.8f), sink.volumes)
        assertEquals(4, sink.writes.size)
        assertTrue(sink.writes.all { it.size == SpeakerFeedbackProtocol.PCM_BYTES_PER_PACKET })
        assertNull(relayFailure.get())
    }

    private fun parsedPacket(sequence: Int): SpeakerFeedbackPacket =
        requireNotNull(SpeakerFeedbackProtocol.parse(feedbackPacket(sequence)))

    private fun feedbackPacket(
        sequence: Int = 0,
        flags: Int = 0x0008,
        deviceId: String = "TEWNHZDBK",
        sessionId: String = "FB_1",
        talkId: String = "MON_1",
        codec: Int = SpeakerFeedbackProtocol.CODEC_OPUS
    ): ByteArray {
        val device = deviceId.toByteArray()
        val session = sessionId.toByteArray()
        val talk = talkId.toByteArray()
        val payload = ByteArray(16) { 0xF8.toByte() }
        val headerBytes = 28 + device.size + session.size + talk.size
        return ByteBuffer.allocate(headerBytes + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0xA55A.toShort())
            .put(2)
            .put(codec.toByte())
            .putShort(headerBytes.toShort())
            .putShort(flags.toShort())
            .putInt(sequence)
            .putInt(sequence * SpeakerFeedbackProtocol.SAMPLES_PER_PACKET)
            .putShort(SpeakerFeedbackProtocol.SAMPLE_RATE.toShort())
            .put(SpeakerFeedbackProtocol.CHANNELS.toByte())
            .put(SpeakerFeedbackProtocol.PACKET_MS.toByte())
            .putShort(payload.size.toShort())
            .putShort(SpeakerFeedbackProtocol.SAMPLES_PER_PACKET.toShort())
            .put(device.size.toByte())
            .put(session.size.toByte())
            .put(talk.size.toByte())
            .put(0)
            .put(device)
            .put(session)
            .put(talk)
            .put(payload)
            .array()
    }

    private class FakeFeedbackAudioSink : SpeakerFeedbackAudioSink {
        @Volatile
        var started = false

        @Volatile
        var closed = false

        val writes: MutableList<ByteArray> =
            Collections.synchronizedList(mutableListOf())
        val volumes: MutableList<Float> =
            Collections.synchronizedList(mutableListOf())

        override fun start() {
            started = true
        }

        override fun write(pcm: ByteArray) {
            writes += pcm.copyOf()
        }

        override fun setVolume(gain: Float) {
            volumes += gain
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeFeedbackOpusDecoder : SpeakerFeedbackOpusDecoder {
        override fun decode(payload: ByteArray): ByteArray? =
            ByteArray(SpeakerFeedbackProtocol.PCM_BYTES_PER_PACKET)

        override fun conceal(): ByteArray? =
            ByteArray(SpeakerFeedbackProtocol.PCM_BYTES_PER_PACKET)

        override fun close() = Unit
    }
}
