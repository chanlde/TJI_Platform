package com.tji.device.service.mqtt

import com.tji.device.data.model.ProductType
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttTopics
import org.junit.Assert.assertEquals
import org.junit.Test

class MqttTopicLayoutTest {

    @Test
    fun mapsPlatformProductsToCanonicalDeviceTopics() {
        val deviceId = "T0000001"

        listOf(
            ProductType.FireBucket to "FireBucket",
            ProductType.SolarClean to "SolarClean",
            ProductType.DropperSixStage to "SixStageDropper",
            ProductType.Speaker to "Speaker",
            ProductType.BreakWindowProjectile to "GlassBreaker",
            ProductType.Searchlight to "Searchlight"
        ).forEach { (productType, productCode) ->
            val topics = mqttTopicsFor(productType)

            assertEquals(
                "$productCode/devices/$deviceId/lifecycle",
                topics.lifecycleTopic(deviceId)
            )
            assertEquals(
                "$productCode/devices/$deviceId/status",
                topics.statusTopic(deviceId)
            )
            assertEquals(
                "$productCode/devices/$deviceId/control",
                topics.controlTopic(deviceId)
            )
        }
    }

    @Test
    fun keepsRadioDetectionLegacyRidStatusTopicSeparateFromRgbAckTopic() {
        val deviceId = "RID-001"
        val topics = mqttTopicsFor(ProductType.RadioDetection)

        assertEquals(
            "RadioDetection/devices/$deviceId/lifecycle",
            topics.lifecycleTopic(deviceId)
        )
        assertEquals(
            "spectrum-detection-client/$deviceId",
            topics.statusTopic(deviceId)
        )
        assertEquals(
            "RadioDetection/devices/$deviceId/control",
            topics.controlTopic(deviceId)
        )
        assertEquals(
            "RadioDetection/devices/$deviceId/status",
            RadioDetectionMqttTopics.rgbAckTopic(deviceId)
        )
    }
}
