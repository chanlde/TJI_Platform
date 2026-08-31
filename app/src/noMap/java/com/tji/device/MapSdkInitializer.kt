package com.tji.device

import android.app.Application
import android.content.Context

/** 无地图客户包没有 AMap 依赖，因此启动时无需地图 SDK 初始化。 */
object MapSdkInitializer {
    fun initialize(application: Application) = Unit
    fun isPrivacyPromptRequired(context: Context): Boolean = false
    fun isPrivacyAccepted(context: Context): Boolean = false
    fun acceptPrivacy(application: Application) = Unit
    fun declinePrivacy(application: Application) = Unit
}
