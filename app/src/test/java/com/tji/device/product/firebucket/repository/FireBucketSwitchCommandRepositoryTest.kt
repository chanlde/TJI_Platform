package com.tji.device.product.firebucket.repository

import com.tji.device.product.firebucket.model.ControlMode
import com.tji.device.product.firebucket.model.FireBucketSwitchControlParams
import com.tji.device.product.firebucket.transport.FireBucketControlTransport
import com.tji.device.product.firebucket.transport.FireBucketConnectionMode
import com.tji.device.product.firebucket.transport.FireBucketConnectionModeStore
import com.tji.device.product.firebucket.transport.FireBucketSetServoCommand
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FireBucketSwitchCommandRepositoryTest {
    @Test
    fun existingUiParametersBecomeOneTransportIndependentCommand() = runTest {
        var captured: FireBucketSetServoCommand? = null
        val transport = FireBucketControlTransport { captured = it }
        val repository = FireBucketSwitchCommandRepository { transport }

        repository.setAngle(
            linkSn = "LINK-001",
            params = FireBucketSwitchControlParams(
                sn = "FB-001",
                angle = 90,
                speed = 100,
                mode = ControlMode.ABSOLUTE
            )
        )

        assertEquals(
            FireBucketSetServoCommand(
                linkId = "LINK-001",
                deviceId = "FB-001",
                angleDegrees = 90,
                speed = 100,
                mode = ControlMode.ABSOLUTE
            ),
            captured
        )
    }

    @Test
    fun switchingModeRoutesOnlyTheNextControlCommand() = runTest {
        val modeStore = FireBucketConnectionModeStore()
        val cloudCommands = mutableListOf<FireBucketSetServoCommand>()
        val directCommands = mutableListOf<FireBucketSetServoCommand>()
        val cloud = FireBucketControlTransport(cloudCommands::add)
        val direct = FireBucketControlTransport(directCommands::add)
        val repository = FireBucketSwitchCommandRepository {
            when (modeStore.current) {
                FireBucketConnectionMode.CLOUD -> cloud
                FireBucketConnectionMode.DIRECT_LINK -> direct
            }
        }
        val params = FireBucketSwitchControlParams(
            sn = "FB-001",
            angle = 90,
            speed = 100,
            mode = ControlMode.ABSOLUTE
        )

        repository.setAngle("LINK-001", params)
        modeStore.useDirectLink()
        repository.setAngle("LINK-001", params.copy(angle = 0))
        modeStore.useCloud()
        repository.setAngle("LINK-001", params.copy(angle = 180))

        assertEquals(listOf(90, 180), cloudCommands.map { it.angleDegrees })
        assertEquals(listOf(0), directCommands.map { it.angleDegrees })
    }
}
