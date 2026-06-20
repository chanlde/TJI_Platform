package com.tji.device.product.speaker.core

import com.tji.device.product.speaker.audio.SpeakerAdpcmPacketizer
import com.tji.device.product.speaker.audio.SpeakerHadpCodec
import com.tji.device.product.speaker.audio.SpeakerHadpFile
import com.tji.device.product.speaker.audio.SpeakerUdpStreamContext

object SpeakerCoreShadowVerifier {
    fun compareHadp(
        kotlinHadp: SpeakerHadpFile,
        pcm16le: ByteArray,
        recordId: String,
        codec: SpeakerHadpCodec = kotlinHadp.codec
    ): SpeakerCoreShadowResult {
        val native = SpeakerCoreNative.encodeHadpOrNull(
            pcm16le = pcm16le,
            recordId = recordId,
            codec = codec,
            sampleRate = kotlinHadp.sampleRate,
            channels = kotlinHadp.channels,
            packetMs = kotlinHadp.packetMs
        ) ?: return SpeakerCoreShadowResult.NativeUnavailable
        return compare("hadp:${codec.wireName}", kotlinHadp.data, native)
    }

    fun compareLegacyPacket(
        kotlinPacket: ByteArray,
        pcm16le: ByteArray,
        sequence: Int,
        timestampSamples: Int
    ): SpeakerCoreShadowResult {
        val native = SpeakerCoreNative.packetizeLegacyOrNull(
            pcm16le = pcm16le,
            sequence = sequence,
            timestampSamples = timestampSamples
        ) ?: return SpeakerCoreShadowResult.NativeUnavailable
        return compare("legacy-adpcm-packet", kotlinPacket, native)
    }

    fun compareV2Packet(
        kotlinPacket: ByteArray,
        pcm16le: ByteArray,
        sequence: Int,
        timestampMs: Int,
        context: SpeakerUdpStreamContext,
        isLastPacket: Boolean
    ): SpeakerCoreShadowResult {
        val native = SpeakerCoreNative.packetizeV2OrNull(
            pcm16le = pcm16le,
            sequence = sequence,
            timestampMs = timestampMs,
            deviceId = context.deviceId,
            taskId = context.taskId,
            talkId = context.talkId,
            streamType = context.type,
            isLastPacket = isLastPacket
        ) ?: return SpeakerCoreShadowResult.NativeUnavailable
        return compare("v2-adpcm-packet", kotlinPacket, native)
    }

    private fun compare(label: String, kotlinBytes: ByteArray, nativeBytes: ByteArray): SpeakerCoreShadowResult {
        if (kotlinBytes.contentEquals(nativeBytes)) {
            return SpeakerCoreShadowResult.Match(label, kotlinBytes.size)
        }
        val mismatchOffset = kotlinBytes.indices.firstOrNull { index ->
            index >= nativeBytes.size || kotlinBytes[index] != nativeBytes[index]
        } ?: minOf(kotlinBytes.size, nativeBytes.size)
        return SpeakerCoreShadowResult.Mismatch(
            label = label,
            kotlinSize = kotlinBytes.size,
            nativeSize = nativeBytes.size,
            mismatchOffset = mismatchOffset
        )
    }
}

sealed class SpeakerCoreShadowResult {
    data object NativeUnavailable : SpeakerCoreShadowResult()
    data class Match(val label: String, val byteCount: Int) : SpeakerCoreShadowResult()
    data class Mismatch(
        val label: String,
        val kotlinSize: Int,
        val nativeSize: Int,
        val mismatchOffset: Int
    ) : SpeakerCoreShadowResult()
}
