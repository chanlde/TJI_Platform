package com.tji.device.product.radiodetection.map

enum class RadioDetectionMapProvider {
    Prototype,
    Gaode
}

data class RadioDetectionMapConfig(
    val provider: RadioDetectionMapProvider = RadioDetectionMapProvider.Prototype,
    val gaodeApiKey: String? = null,
    val centerLatitude: Double = 37.863209,
    val centerLongitude: Double = 116.293095,
    val defaultZoom: Float = 15.5f
)

object RadioDetectionMapContract {
    const val GAODE_MANIFEST_API_KEY = "com.amap.api.v2.apikey"
    const val GAODE_PRIVACY_ACCEPTED = "radio_detection_gaode_privacy_accepted"
}
