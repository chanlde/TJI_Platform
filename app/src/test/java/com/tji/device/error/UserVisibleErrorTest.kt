package com.tji.device.error

import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class UserVisibleErrorTest {
    @Test
    fun wrappedNetworkCauseUsesRootExceptionType() {
        val failure = IllegalStateException(
            "repository failed",
            UnknownHostException("host lookup failed")
        )

        assertEquals(
            "网络不可用，请检查手机网络",
            failure.toUserVisibleMessage()
        )
    }

    @Test
    fun nonSpeakerBufferFailureDoesNotProduceAudioMessage() {
        val failure = IllegalStateException("database buffer exhausted")

        assertEquals(
            "保存失败",
            failure.toUserVisibleMessage("保存失败")
        )
    }

    @Test
    fun safeChineseServerMessageCanPassThrough() {
        assertEquals(
            "设备名称已存在",
            "设备名称已存在".toUserVisibleServerMessage()
        )
    }
}
