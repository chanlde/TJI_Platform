package com.tji.device.service.mqtt

import com.tji.device.data.model.ProductType
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttTopics
import org.junit.Assert.assertEquals
import org.junit.Test

class MqttTopicLayoutTest {

    @Test
    fun mapsPlatformProductsToCanonicalDeviceTopics() {
        val serialNumber = "SN-001"

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
                "$productCode/devices/$serialNumber/lifecycle",
                topics.lifecycleTopic(serialNumber)
            )
            assertEquals(
                "$productCode/devices/$serialNumber/status",
                topics.statusTopic(serialNumber)
            )
            assertEquals(
                "$productCode/devices/$serialNumber/control",
                topics.controlTopic(serialNumber)
            )
        }
    }

    @Test
    fun keepsRadioDetectionLegacyRidStatusTopicSeparateFromRgbAckTopic() {
        val serialNumber = "RID-001"
        val topics = mqttTopicsFor(ProductType.RadioDetection)

        assertEquals(
            "RadioDetection/devices/$serialNumber/lifecycle",
            topics.lifecycleTopic(serialNumber)
        )
        assertEquals(
            "spectrum-detection-client/$serialNumber",
            topics.statusTopic(serialNumber)
        )
        assertEquals(
            "RadioDetection/devices/$serialNumber/control",
            topics.controlTopic(serialNumber)
        )
        assertEquals(
            "RadioDetection/devices/$serialNumber/status",
            RadioDetectionMqttTopics.rgbAckTopic(serialNumber)
        )
    }
}
