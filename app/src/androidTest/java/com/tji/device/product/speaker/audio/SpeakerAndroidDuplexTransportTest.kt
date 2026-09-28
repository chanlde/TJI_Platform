package com.tji.device.product.speaker.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tji.device.product.speaker.core.SpeakerCoreAudioEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin

/**
 * 真机双向传输测试，不依赖手机麦克风、扬声器声学回路或 UI 自动化。
 *
 * MCU 需先通过 cmd=116 开启与 [FEEDBACK_SESSION_ID]/[FEEDBACK_TALK_ID]
 * 对应的麦克风回传。测试随后同时运行正式回传接收器和正式媒体发送器，确认
 * App->MCU Ogg Opus 传输期间 MCU->App Opus 包仍持续到达并被 AudioTrack 消费。
 */
@RunWith(AndroidJUnit4::class)
class SpeakerAndroidDuplexTransportTest {

    @Test
    fun quietMediaTransferDoesNotInterruptFeedback() = runBlocking {
        val receivedPackets = AtomicLong()
        val playedPackets = AtomicLong()
        val registered = CompletableDeferred<Unit>()
        val feedbackClient = SpeakerFeedbackClient().apply {
            // 保留完整解码和 AudioTrack 写入路径，但测试过程不从手机发声。
            setPlaybackGain(0f)
        }
        // 与正式 SpeakerAudioRelay 一致：所有媒体会话复用同一个 client/socket。
        val mediaClient = SpeakerMediaTransferClient()
        val feedbackJob = launch(Dispatchers.IO) {
            feedbackClient.listen(
                session = SpeakerFeedbackSession(
                    deviceId = DEVICE_ID,
                    sessionId = FEEDBACK_SESSION_ID,
                    talkId = FEEDBACK_TALK_ID
                ),
                onRegistered = { registered.complete(Unit) },
                onLeaseRefresh = {},
                onStats = { stats ->
                    receivedPackets.set(stats.packetsReceived)
                    playedPackets.set(stats.packetsPlayed)
                }
            )
        }

        try {
            withTimeout(REGISTRATION_TIMEOUT_MS) { registered.await() }
            awaitPacketCount(receivedPackets, FEEDBACK_WARMUP_PACKETS)

            TEST_DURATIONS_MS.indices.forEach { round ->
                val sequence = System.currentTimeMillis().toString(16).takeLast(8)
                val recordId = "LABAND_R${round}_$sequence"
                val durationMs = TEST_DURATIONS_MS[round]
                val pauseAndResume = round % 2 == 1
                val pcm = generateStressPcm(round, durationMs)
                val opus = SpeakerCoreAudioEngine.encodeOggOpus(
                    pcm = pcm,
                    recordId = recordId,
                    sampleRate = MEDIA_SAMPLE_RATE,
                    packetMs = SpeakerOpusFile.DEFAULT_PACKET_MS,
                    bitrate = MEDIA_BITRATE
                )
                val beforeReceived = receivedPackets.get()
                val beforePlayed = playedPackets.get()
                if (pauseAndResume) {
                    feedbackClient.pauseForPushToTalk()
                    delay(LOCAL_PAUSE_SETTLE_MS)
                }

                withTimeout(MEDIA_TRANSFER_TIMEOUT_MS) {
                    mediaClient.send(
                        SpeakerMediaTransferRequest(
                            deviceId = DEVICE_ID,
                            sessionId = "LABAND_M${round}_$sequence",
                            recordId = recordId,
                            name = "双向链路测试 ${round + 1}",
                            createdAt = CREATED_AT,
                            opusFile = opus,
                            mode = SpeakerMediaTransferMode.PlayTemporary,
                            volume = MCU_TEST_VOLUME,
                            recordType = SpeakerMediaTransferClient.RECORD_TYPE_TEST,
                            visible = false
                        )
                    )
                }

                val receivedDuringTransfer = receivedPackets.get() - beforeReceived
                val playedDuringTransfer = playedPackets.get() - beforePlayed
                if (pauseAndResume) {
                    withTimeout(FEEDBACK_RESUME_TIMEOUT_MS) {
                        feedbackClient.resumeAfterPushToTalk()
                    }
                } else {
                    assertTrue(
                        "第 ${round + 1} 轮媒体发送期间麦克风回传中断: " +
                            "received=$receivedDuringTransfer",
                        receivedDuringTransfer >= MIN_PACKETS_DURING_TRANSFER
                    )
                    assertTrue(
                        "第 ${round + 1} 轮媒体发送期间 AudioTrack 未持续消费: " +
                            "played=$playedDuringTransfer",
                        playedDuringTransfer >= MIN_PACKETS_DURING_TRANSFER
                    )
                }
                // send() 表示文件传输完成，不表示 MCU 的当前播放已结束。继续观察到
                // 播放尾部，既覆盖真正的双向并行区间，也避免下一轮被正常 BUSY 拒绝。
                delay(durationMs + PLAYBACK_SETTLE_MARGIN_MS)
                val receivedThroughPlayback = receivedPackets.get() - beforeReceived
                val playedThroughPlayback = playedPackets.get() - beforePlayed
                val minimumPacketsThroughPlayback =
                    durationMs * MIN_FEEDBACK_PACKETS_PER_SECOND / 1_000L
                assertTrue(
                    "第 ${round + 1} 轮 MCU 播放期间麦克风回传中断: " +
                        "received=$receivedThroughPlayback",
                    receivedThroughPlayback >= minimumPacketsThroughPlayback
                )
                assertTrue(
                    "第 ${round + 1} 轮 MCU 播放期间 AudioTrack 未持续消费: " +
                        "played=$playedThroughPlayback",
                    playedThroughPlayback >= minimumPacketsThroughPlayback
                )
                println(
                    "SPEAKER_DUPLEX round=${round + 1} " +
                        "mode=${if (pauseAndResume) "pause-resume" else "simultaneous"} " +
                        "durationMs=$durationMs " +
                        "opusBytes=${opus.data.size} " +
                        "duringTransfer=$receivedDuringTransfer/$playedDuringTransfer " +
                        "throughPlayback=$receivedThroughPlayback/$playedThroughPlayback"
                )
            }
        } finally {
            feedbackJob.cancelAndJoin()
        }
    }

    private suspend fun awaitPacketCount(counter: AtomicLong, minimum: Long) {
        withTimeout(FEEDBACK_AUDIO_TIMEOUT_MS) {
            while (counter.get() < minimum) delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * 生成确定性的高复杂度单声道 PCM：多频分量、缓慢语音包络和伪随机分量。
     * 数据本身足以让 Opus 接近配置码率；最终听觉音量由 MCU 1% 增益独立限制。
     */
    private fun generateStressPcm(round: Int, durationMs: Int): ByteArray {
        val sampleCount = MEDIA_SAMPLE_RATE * durationMs / 1_000
        val pcm = ByteArray(sampleCount * 2)
        var noiseState = 0x13579BDF xor (round * 0x10203)
        val baseHz = 170.0 + round * 23.0
        val fadeSamples = MEDIA_SAMPLE_RATE * TEST_FADE_MS / 1_000

        repeat(sampleCount) { index ->
            noiseState = noiseState xor (noiseState shl 13)
            noiseState = noiseState xor (noiseState ushr 17)
            noiseState = noiseState xor (noiseState shl 5)
            val noise = ((noiseState ushr 16) and 0xFFFF) / 32767.5 - 1.0
            val seconds = index.toDouble() / MEDIA_SAMPLE_RATE
            val speechEnvelope = 0.62 + 0.38 * sin(2.0 * PI * 3.7 * seconds)
            val mixed =
                0.50 * sin(2.0 * PI * baseHz * seconds) +
                    0.24 * sin(2.0 * PI * (baseHz * 2.13) * seconds) +
                    0.14 * sin(2.0 * PI * (baseHz * 4.71) * seconds) +
                    0.12 * noise
            val edgeGain = when {
                index < fadeSamples -> index.toDouble() / fadeSamples
                index >= sampleCount - fadeSamples ->
                    (sampleCount - 1 - index).coerceAtLeast(0).toDouble() / fadeSamples
                else -> 1.0
            }
            val sample = (mixed * speechEnvelope * edgeGain * PCM_STRESS_AMPLITUDE * 32767.0)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm[index * 2] = (sample and 0xFF).toByte()
            pcm[index * 2 + 1] = ((sample ushr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private companion object {
        const val DEVICE_ID = "T5TNBFM4Q"
        const val FEEDBACK_SESSION_ID = "LABAND_FB_01"
        const val FEEDBACK_TALK_ID = "LABAND_MON_01"
        const val CREATED_AT = "2026-08-03T00:00:00Z"

        const val MEDIA_SAMPLE_RATE = 48_000
        const val MEDIA_BITRATE = 32_000
        const val TEST_FADE_MS = 20
        const val PCM_STRESS_AMPLITUDE = 0.35
        const val MCU_TEST_VOLUME = 1
        val TEST_DURATIONS_MS = intArrayOf(2_000, 2_000, 3_000, 3_000, 5_000, 5_000, 8_000, 8_000)

        const val FEEDBACK_WARMUP_PACKETS = 24L
        const val MIN_PACKETS_DURING_TRANSFER = 20L
        const val MIN_FEEDBACK_PACKETS_PER_SECOND = 45L
        const val PLAYBACK_SETTLE_MARGIN_MS = 1_000L
        const val LOCAL_PAUSE_SETTLE_MS = 100L
        const val FEEDBACK_RESUME_TIMEOUT_MS = 6_000L
        const val REGISTRATION_TIMEOUT_MS = 15_000L
        const val FEEDBACK_AUDIO_TIMEOUT_MS = 15_000L
        const val MEDIA_TRANSFER_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 20L
    }
}
