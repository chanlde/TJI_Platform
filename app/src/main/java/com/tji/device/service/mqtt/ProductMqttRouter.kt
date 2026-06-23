package com.tji.device.service.mqtt

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.network.MQTTConfig
import com.tji.network.MqttManager
import com.tji.network.MqttProfiles

object ProductMqttRouter {
    private const val TAG = "ProductMqttRouter"

    fun resetForAccount(
        account: String,
        platformClientId: String,
        radioDetectionClientId: String
    ) {
        Log.w(
            TAG,
            "TJI_MQTT_DIAG resetForAccount account=$account " +
                "platformClientId=$platformClientId radioDetectionClientId=$radioDetectionClientId"
        )
        MqttManager.reset(
            profileKey = MqttProfiles.PLATFORM,
            config = MQTTConfig.platform(
                username = account,
                clientId = platformClientId
            )
        )
        MqttManager.reset(
            profileKey = MqttProfiles.RADIO_DETECTION_LEGACY,
            config = MQTTConfig.radioDetectionLegacy(
                clientId = radioDetectionClientId
            )
        )
    }

    fun managerFor(productType: ProductType): MqttManager =
        MqttManager.getInstance(profileKeyFor(productType))

    fun platformManager(): MqttManager =
        MqttManager.getInstance(MqttProfiles.PLATFORM)

    fun profileKeyFor(productType: ProductType): String =
        when (productType) {
            ProductType.RadioDetection -> MqttProfiles.RADIO_DETECTION_LEGACY
            else -> MqttProfiles.PLATFORM
        }
}
