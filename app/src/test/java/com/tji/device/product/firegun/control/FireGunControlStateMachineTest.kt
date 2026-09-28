package com.tji.device.product.firegun.control

import com.tji.device.product.firegun.model.FireGunDeviceStatus
import com.tji.device.product.firegun.protocol.FireGunAction
import com.tji.device.product.firegun.protocol.FireGunResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FireGunControlStateMachineTest {
    private val machine = FireGunControlStateMachine()

    @Test
    fun onlyMatchingRequestAndLinkCanCompleteACommand() {
        val pending = machine.commandStarted(
            FireGunControlState(),
            FireGunPendingCommand.Actuator(
                "actuator-1", "LINK-001", "SUB-001", FireGunAction.OPEN
            )
        )
        val wrongTarget = machine.responseReceived(pending, actuatorResponse("actuator-1", "OTHER"))
        val completed = machine.responseReceived(pending, actuatorResponse("actuator-1", "SUB-001"))

        assertEquals(pending, wrongTarget)
        assertNull(completed.pendingActuator)
        assertEquals("opening", completed.actuatorState)
        assertEquals("展开指令已接受", completed.actuatorFeedback)
    }

    @Test
    fun retainedResponseIsIgnored() {
        val pending = machine.commandStarted(
            FireGunControlState(),
            FireGunPendingCommand.Unlock("lock-1", "LINK-001", "SUB-001")
        )
        val response = FireGunResponse.Lock(
            "SUB-001", "lock-1", true, "unlock", "unlocking", 500, null
        )

        assertEquals(pending, machine.responseReceived(pending, response, retained = true))
    }

    @Test
    fun realtimeStatusUpdatesDisplayedLockAndPosition() {
        val status = FireGunDeviceStatus(
            linkSerial = "LINK-001",
            serialNumber = "SUB-001",
            deviceName = "HydroActuator_SUB",
            deviceType = "HydroActuator",
            isOnline = true,
            currentAngle = 0.0,
            currentCurrent = 0.0,
            inputVoltage = 11.14,
            servoMinAngle = 0.0,
            servoMaxAngle = 90.0,
            uptime = 501,
            timestamp = 946656371,
            rssi = 0,
            lockState = "locked",
            actuatorState = "closed"
        )

        val state = machine.statusReceived(FireGunControlState(), status)

        assertEquals("locked", state.lockState)
        assertEquals("closed", state.actuatorState)
    }

    private fun actuatorResponse(requestId: String, serial: String) =
        FireGunResponse.Actuator(
            serialNumber = serial,
            requestId = requestId,
            success = true,
            action = FireGunAction.OPEN,
            state = "opening",
            maxRunMs = 90_000,
            error = null
        )
}
