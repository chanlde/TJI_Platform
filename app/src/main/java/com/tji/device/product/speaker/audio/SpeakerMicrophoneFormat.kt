package com.tji.device.product.speaker.audio

/**
 * 手机麦克风录音格式策略。
 *
 * 音质选择在开始录音时锁定，并贯穿采集、处理和 Ogg Opus 编码，禁止先录
 * 8 kHz 再上采样成 16/24/48 kHz。
 */
internal object SpeakerMicrophoneFormat {
    const val CHANNELS = 1
    const val PCM16_BYTES_PER_SAMPLE = 2
    val supportedSampleRates: Set<Int> = setOf(8_000, 16_000, 24_000, 48_000)

    fun frameBytes(sampleRate: Int, packetMs: Int = SpeakerOpusFile.DEFAULT_PACKET_MS): Int {
        require(sampleRate in supportedSampleRates) { "不支持的麦克风采样率: $sampleRate" }
        require(packetMs > 0) { "音频帧时长必须大于0" }
        return sampleRate * packetMs / 1_000 * CHANNELS * PCM16_BYTES_PER_SAMPLE
    }
}
