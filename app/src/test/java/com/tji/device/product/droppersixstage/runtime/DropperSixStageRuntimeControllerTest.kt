package com.tji.device.product.droppersixstage.runtime

import com.tji.device.product.droppersixstage.model.DropperSixStageState
import com.tji.device.product.droppersixstage.model.DropperStageState
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DropperSixStageRuntimeControllerTest {
    @Test
    fun `six control channels are not exposed as child devices`() = runBlocking {
        val repository = DropperSixStageRepo()
        val controller = DropperSixStageRuntimeController(repository)
        repository.updateState(
            DropperSixStageState(
                serialNumber = "D29D5405F",
                isOnline = true,
                stages = DropperStageState.defaults()
            )
        )

        val devices = controller.devices.first()

        assertEquals(1, devices.size)
        assertEquals("D29D5405F", devices.single().serialNumber)
        assertNull(devices.single().childCount)
        assertEquals(6, (devices.single().payload as DropperSixStageState).stages.size)
    }
}
