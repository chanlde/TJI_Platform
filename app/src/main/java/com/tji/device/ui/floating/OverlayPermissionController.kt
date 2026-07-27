package com.tji.device.ui.floating

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * 悬浮窗系统权限的查询与设置页跳转入口。
 */
object OverlayPermissionController {
    fun isGranted(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun openSettingsIfNeeded(activity: Activity) {
        if (isGranted(activity)) return
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${activity.packageName}".toUri()
            )
        )
    }
}
