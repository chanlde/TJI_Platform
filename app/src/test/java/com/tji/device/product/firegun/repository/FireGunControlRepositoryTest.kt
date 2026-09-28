package com.tji.device.product.firegun.repository

import com.tji.device.product.firegun.protocol.FireGunAction
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FireGunControlRepositoryTest {
    @Test
    fun linkSerialRoutesTopicAndEsp32SerialTargetsPayload() = runTest {
        var publishedTopic: String? = null
        var publishedPayload: String? = null
        val repository = FireGunControlRepo { topic, payload ->
            publishedTopic = topic
            publishedPayload = payload
        }

        repository.sendActuator(
            linkSerial = "E465B062174A5124",
            targetSerial = "9CCC0139EF88727D",
            requestId = "actuator-000001",
            action = FireGunAction.OPEN
        )

        assertEquals(
            "FireBucket/devices/E465B062174A5124/control",
            publishedTopic
        )
        val json = JSONObject(publishedPayload!!)
        assertEquals("9CCC0139EF88727D", json.getString("serial_number"))
        assertEquals("actuator-000001", json.getString("request_id"))
        assertEquals("open", json.getString("action"))
    }
}
