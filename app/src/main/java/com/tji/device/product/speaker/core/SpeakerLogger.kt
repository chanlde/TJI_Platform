package com.tji.device.product.speaker.core

import android.util.Log

/**
 * 喊话器模块的轻量日志入口。
 *
 * 只保留需要现场关注的异常告警，不输出正常业务过程和设备标识。
 */
object SpeakerLogger {
    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
    }
}
