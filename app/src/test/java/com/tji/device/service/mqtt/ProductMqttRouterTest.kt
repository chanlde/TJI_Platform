package com.tji.device.service.mqtt

import com.tji.device.data.model.ProductType
import com.tji.network.MqttProfiles
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductMqttRouterTest {

    @Test
    fun routesOnlyRadioDetectionToLegacyMqttProfile() {
        ProductType.entries.forEach { productType ->
            val expectedProfile = if (productType == ProductType.RadioDetection) {
                MqttProfiles.RADIO_DETECTION_LEGACY
            } else {
                MqttProfiles.PLATFORM
            }

            assertEquals(expectedProfile, ProductMqttRouter.profileKeyFor(productType))
        }
    }
}
