package com.tji.device.product.firebucket.runtime

import com.tji.device.product.firebucket.mqtt.FireBucketMqttInbound
import com.tji.device.product.firebucket.repository.FireBucketLinkRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class FireBucketRuntimeControllerTest {
    @Test
    fun clearingRetiredProductCancelsHeartbeatTimerBeforeClearingLinks() = runBlocking {
        val repository = FireBucketLinkRepo()
        val inbound = FireBucketMqttInbound(
            linkDeviceRepo = repository,
            heartbeatTimeoutMillis = 20L
        )
        val controller = FireBucketRuntimeController(repository, inbound::cleanup)
        inbound.handleEvent(SERIAL, "LinkDeviceStartup", startupPayload())

        controller.clear()
        delay(60L)

        assertTrue(repository.links.value.isEmpty())
        inbound.cleanup()
    }

    private fun startupPayload() = JSONObject(
        """
        {
          "event_type": "LinkDeviceStartup",
          "serial_number": "$SERIAL",
          "deviceName": "消防吊桶 Link",
          "deviceType": "Link",
          "manufacturer": "TJI",
          "deviceModel": "FB",
          "isOnline": true,
          "hwVersion": "1",
          "swVersion": "1",
          "uptime": 1,
          "deviceConfig": "",
          "timestamp": "1",
          "subDevices": []
        }
        """.trimIndent()
    )

    private companion object {
        const val SERIAL = "LINK-001"
    }
}
