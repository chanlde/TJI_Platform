package com.tji.device.product.solarclean.ui.control

import com.tji.device.product.solarclean.model.SolarCleanDeviceState
import org.junit.Assert.assertEquals
import org.junit.Test

class SolarCleanDisplayFormattersTest {

    @Test
    fun mapsKnownWaterLevelsToCustomerText() {
        assertEquals("低", waterLevelText(0))
        assertEquals("正常", waterLevelText(1))
        assertEquals("高", waterLevelText(2))
    }

    @Test
    fun keepsUnknownWaterLevelValueVisibleForDiagnosis() {
        assertEquals("9", waterLevelText(9))
    }

    @Test
    fun formatsMqttStatusForOnlineOfflineAndUnknownStates() {
        assertEquals("正常", mqttStatusText(SolarCleanDeviceState(serialNumber = SERIAL, mqttConnected = true)))
        assertEquals("断开", mqttStatusText(SolarCleanDeviceState(serialNumber = SERIAL, mqttConnected = false)))
        assertEquals(
            "错误 7",
            mqttStatusText(SolarCleanDeviceState(serialNumber = SERIAL, mqttConnected = false, mqttLastError = 7))
        )
        assertEquals("--", mqttStatusText(SolarCleanDeviceState(serialNumber = SERIAL)))
        assertEquals("--", mqttStatusText(null))
    }

    private companion object {
        const val SERIAL = "SOLAR-001"
    }
}
