package com.tji.device.product.solarclean.runtime

import com.tji.device.product.solarclean.mqtt.SolarCleanMqttInbound
import com.tji.device.product.solarclean.repository.SolarCleanRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarCleanRuntimeControllerTest {
    @Test
    fun clearingRetiredProductCancelsOfflineTimerBeforeClearingDevices() = runBlocking {
        val repository = SolarCleanRepo()
        val inbound = SolarCleanMqttInbound(
            repository = repository,
            onlineTtlMillis = 20L
        )
        val controller = SolarCleanRuntimeController(repository, inbound::cleanup)
        inbound.handleEvent(SERIAL, "online", JSONObject())

        controller.clear()
        delay(60L)

        assertTrue(repository.devices.value.isEmpty())
        inbound.cleanup()
    }

    private companion object {
        const val SERIAL = "SOLAR-001"
    }
}
