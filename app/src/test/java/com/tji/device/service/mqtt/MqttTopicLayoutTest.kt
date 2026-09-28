package com.tji.device.service.mqtt

import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttTopics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MqttTopicLayoutTest {

    @Test
    fun everyEnabledCatalogProductHasConcreteTopics() {
        ProductCatalog.enabledTypes.forEach { productType ->
            mqttTopicsFor(productType)
        }
    }

    @Test
    fun mapsPlatformProductsToCanonicalDeviceTopics() {
        val deviceId = "T0000001"

        listOf(
            ProductType.FireBucket to "FireBucket",
            ProductType.SolarClean to "SolarClean",
            ProductType.DropperSixStage to "FC100_FireDrop",
            ProductType.Speaker to "Speaker",
            ProductType.BreakWindowProjectile to "GlassBreaker"
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
    fun fireGunUsesControlEventStatusAndLifecycleTopics() {
        val deviceId = "E465B062174A5124"
        val topics = mqttTopicsFor(ProductType.FireGun)

        assertEquals("FireBucket/devices/$deviceId/control", topics.controlTopic(deviceId))
        assertEquals(
            listOf("FireBucket/devices/$deviceId/lifecycle"),
            topics.lifecycleTopics(deviceId)
        )
        assertEquals(
            listOf(
                "FireBucket/devices/$deviceId/event",
                "FireBucket/devices/$deviceId/status"
            ),
            topics.statusTopics(deviceId)
        )
    }

    @Test
    fun disabledProductCannotCreatePlaceholderTopics() {
        assertThrows(IllegalArgumentException::class.java) {
            mqttTopicsFor(ProductType.Searchlight)
        }
    }

    @Test
    fun sixStageDropperSubscribesToDeviceAndCompatibilityTopics() {
        val deviceId = "D29D5405F"
        val topics = mqttTopicsFor(ProductType.DropperSixStage)

        assertEquals(
            listOf(
                "FC100_FireDrop/devices/$deviceId/lifecycle",
                "SixStageDropper/devices/$deviceId/lifecycle"
            ),
            topics.lifecycleTopics(deviceId)
        )
        assertEquals(
            listOf(
                "FC100_FireDrop/devices/$deviceId/status",
                "SixStageDropper/devices/$deviceId/status"
            ),
            topics.statusTopics(deviceId)
        )
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
