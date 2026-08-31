package com.tji.device.product.speaker.error

import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerUserVisibleErrorTest {
    @Test
    fun audioBufferFailureUsesSpeakerSpecificMessage() {
        assertEquals(
            "语音播放失败，请重试",
            IllegalStateException("PCM buffer exhausted")
                .toSpeakerUserVisibleMessage()
        )
    }

    @Test
    fun wrappedNetworkFailureStillUsesSharedNetworkMapping() {
        val failure = IllegalStateException(
            "preview failed",
            SocketTimeoutException("read timed out")
        )

        assertEquals(
            "等待时间过长，请稍后重试",
            failure.toSpeakerUserVisibleMessage()
        )
    }

    @Test
    fun deviceProtocolDetailIsNotExposedToUi() {
        assertEquals(
            "语音文件处理失败，请重试",
            "Ogg Opus CRC mismatch".toSpeakerDeviceMessage()
        )
    }
}
