package com.tji.device

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.amap.api.maps.MapsInitializer

/** 仅地图产品包初始化高德 SDK；避免 noMap 包产生对 AMap 类的引用。 */
object MapSdkInitializer {
    fun initialize(application: Application) {
        if (!isPrivacyAccepted(application)) return
        configureAcceptedPrivacy(application)
    }

    fun isPrivacyPromptRequired(context: Context): Boolean =
        !preferences(context).contains(KEY_PRIVACY_ACCEPTED)

    fun isPrivacyAccepted(context: Context): Boolean =
        preferences(context).getBoolean(KEY_PRIVACY_ACCEPTED, false)

    fun acceptPrivacy(application: Application) {
        preferences(application).edit(commit = true) {
            putBoolean(KEY_PRIVACY_ACCEPTED, true)
        }
        configureAcceptedPrivacy(application)
    }

    fun declinePrivacy(application: Application) {
        preferences(application).edit(commit = true) {
            putBoolean(KEY_PRIVACY_ACCEPTED, false)
        }
        MapsInitializer.updatePrivacyShow(application, true, true)
        MapsInitializer.updatePrivacyAgree(application, false)
    }

    private fun configureAcceptedPrivacy(application: Application) {
        MapsInitializer.updatePrivacyShow(application, true, true)
        MapsInitializer.updatePrivacyAgree(application, true)
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private const val PREFERENCES = "map_privacy_preferences"
    private const val KEY_PRIVACY_ACCEPTED = "gaode_privacy_accepted"
}
