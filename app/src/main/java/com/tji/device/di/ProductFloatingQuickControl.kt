package com.tji.device.di

import android.util.Log

/**
 * 悬浮窗「快捷拨动开关」按产品线的实现；无能力的产品线使用 [NoOpProductFloatingQuickControl]。
 */
fun interface ProductFloatingQuickControl {
    suspend fun toggleSwitch(linkSerial: String, switchSerial: String, targetAngle: Int)
}

object NoOpProductFloatingQuickControl : ProductFloatingQuickControl {
    private const val TAG = "NoOpFloatControl"

    override suspend fun toggleSwitch(linkSerial: String, switchSerial: String, targetAngle: Int) {
        Log.d(TAG, "ignored toggle link=$linkSerial switch=$switchSerial angle=$targetAngle")
    }
}
