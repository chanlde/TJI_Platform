package com.tji.device.product.speaker.core

import android.util.Log
import com.tji.device.BuildConfig

/**
 * 喊话器模块的轻量日志入口。
 *
 * Debug 日志仅进入调试构建；告警始终保留。这里不引入额外日志框架，
 * 只统一构建类型控制和 Android Log 调用位置。
 */
object SpeakerLogger {
    fun debug(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, throwable)
        }
    }
}
