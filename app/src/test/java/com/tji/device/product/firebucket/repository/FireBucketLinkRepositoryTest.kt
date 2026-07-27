package com.tji.device.product.firebucket.repository

import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.model.FireBucketSwitchState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FireBucketLinkRepositoryTest {
    @Test
    fun repeatedAddReplacesExistingSwitchInsteadOfDuplicatingIt() = runBlocking {
        val repository = FireBucketLinkRepo()
        repository.updateLinkDevice(link())

        repository.addSubDevice(LINK_SERIAL, switch(angle = 10.0))
        repository.addSubDevice(LINK_SERIAL, switch(angle = 55.0))

        val switches = repository.links.value.single().subDevices
        assertEquals(1, switches.size)
        assertEquals(55.0, switches.single().currentAngle, 0.0)
    }

    @Test
    fun statusUpdateUpsertsPreviouslyUnknownSwitch() = runBlocking {
        val repository = FireBucketLinkRepo()
        repository.updateLinkDevice(link())

        repository.updateSubDevice(LINK_SERIAL, switch(angle = 35.0))

        assertEquals(35.0, repository.links.value.single().subDevices.single().currentAngle, 0.0)
    }

    @Test
    fun statusUpdateKeepsExistingSwitchOrder() = runBlocking {
        val repository = FireBucketLinkRepo()
        repository.updateLinkDevice(link())
        repository.addSubDevice(LINK_SERIAL, switch(serialNumber = "SWITCH-001", angle = 10.0))
        repository.addSubDevice(LINK_SERIAL, switch(serialNumber = "SWITCH-002", angle = 20.0))

        repository.updateSubDevice(
            LINK_SERIAL,
            switch(serialNumber = "SWITCH-001", angle = 55.0)
        )

        val switches = repository.links.value.single().subDevices
        assertEquals(listOf("SWITCH-001", "SWITCH-002"), switches.map { it.serialNumber })
        assertEquals(55.0, switches.first().currentAngle, 0.0)
    }

    private fun link() = FireBucketLinkDevice(
        event_type = "LinkDeviceStartup",
        serial_number = LINK_SERIAL,
        deviceName = "消防吊桶 Link",
        deviceType = "Link",
        manufacturer = "TJI",
        deviceModel = "FB",
        isOnline = true,
        hwVersion = "1",
        swVersion = "1",
        uptime = 1,
        deviceConfig = "",
        subDevices = emptyList(),
        timestamp = "1"
    )

    private fun switch(
        angle: Double,
        serialNumber: String = SWITCH_SERIAL
    ) = FireBucketSwitchState(
        serialNumber = serialNumber,
        deviceName = "消防吊桶",
        deviceType = "HydroSwitch",
        isOnline = true,
        currentAngle = angle,
        currentCurrent = 10.0,
        inputVoltage = 8.0,
        servoMinAngle = 0.0,
        servoMaxAngle = 90.0,
        uptime = 1
    )

    private companion object {
        const val LINK_SERIAL = "LINK-001"
        const val SWITCH_SERIAL = "SWITCH-001"
    }
}
