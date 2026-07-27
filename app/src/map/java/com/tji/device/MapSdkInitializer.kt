package com.tji.device

import android.app.Application
import com.amap.api.maps.MapsInitializer

/** 仅地图产品包初始化高德 SDK；避免 noMap 包产生对 AMap 类的引用。 */
object MapSdkInitializer {
    fun initialize(application: Application) {
        MapsInitializer.updatePrivacyShow(application, true, true)
        MapsInitializer.updatePrivacyAgree(application, true)
    }
}
