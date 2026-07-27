package com.tji.device.product.speaker.audio

/**
 * 手机麦克风录音格式策略。
 *
 * 音质选择在开始录音时锁定，并贯穿采集、处理和 HADP 编码，禁止先录 8 kHz
 * 再上采样成 16/24 kHz。MCU 仅支持 8 kHz HADP ADPCM，因此中高音质使用 PCM16。
 */
internal object SpeakerMicrophoneFormat {
    const val CHANNELS = 1
    const val PCM16_BYTES_PER_SAMPLE = 2
    val supportedSampleRates: Set<Int> = setOf(8_000, 16_000, 24_000)

    fun frameBytes(sampleRate: Int, packetMs: Int = SpeakerAdpcmPacketizer.PACKET_MS): Int {
        require(sampleRate in supportedSampleRates) { "不支持的麦克风采样率: $sampleRate" }
        require(packetMs > 0) { "音频帧时长必须大于0" }
        return sampleRate * packetMs / 1_000 * CHANNELS * PCM16_BYTES_PER_SAMPLE
    }

    fun hadpCodec(quality: SpeakerAudioQuality): SpeakerHadpCodec =
        if (quality == SpeakerAudioQuality.Low) {
            SpeakerHadpCodec.ImaAdpcm
        } else {
            SpeakerHadpCodec.Pcm16
        }
}
