package com.tji.device.product.ota

import com.tji.device.data.model.ProductType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductOtaRuntimeRepositoryTest {

    @Test
    fun ignoresOlderSeqForSameOtaCommand() {
        val repo = ProductOtaRuntimeRepo()

        repo.updateOtaStatus(
            productType = ProductType.Speaker,
            serialNumber = SERIAL,
            otaStatus = ProductOtaStatus(
                status = "OTA_PENDING_REBOOT",
                cmdId = "ota-1",
                seq = 12,
                progress = 100
            )
        )
        repo.updateOtaStatus(
            productType = ProductType.Speaker,
            serialNumber = SERIAL,
            otaStatus = ProductOtaStatus(
                status = "OTA_VERIFYING",
                cmdId = "ota-1",
                seq = 11,
                progress = 90
            )
        )

        val state = repo.states.value.single()
        assertEquals("OTA_PENDING_REBOOT", state.otaStatus?.status)
        assertEquals(100, state.otaStatus?.progress)
        assertEquals(12L, state.maxOtaSeqByCmdId["ota-1"])
    }

    @Test
    fun ignoresNonSeqProgressRegressionForLegacyFirmware() {
        val repo = ProductOtaRuntimeRepo()

        repo.updateOtaStatus(
            productType = ProductType.Speaker,
            serialNumber = SERIAL,
            otaStatus = ProductOtaStatus(
                status = "OTA_DOWNLOADING",
                cmdId = "ota-legacy",
                progress = 80,
                timestamp = 200
            )
        )
        repo.updateOtaStatus(
            productType = ProductType.Speaker,
            serialNumber = SERIAL,
            otaStatus = ProductOtaStatus(
                status = "OTA_DOWNLOADING",
                cmdId = "ota-legacy",
                progress = 65,
                timestamp = 201
            )
        )

        val state = repo.states.value.single()
        assertEquals(80, state.otaStatus?.progress)
    }

    @Test
    fun allowsNewCommandAfterTerminalState() {
        val repo = ProductOtaRuntimeRepo()

        repo.updateOtaStatus(
            productType = ProductType.Speaker,
            serialNumber = SERIAL,
            otaStatus = ProductOtaStatus(
                status = "OTA_TEST_DONE",
                cmdId = "ota-1",
                seq = 10,
                progress = 100
            )
        )
        repo.updateOtaStatus(
            productType = ProductType.Speaker,
            serialNumber = SERIAL,
            otaStatus = ProductOtaStatus(
                status = "OTA_PREPARING",
                cmdId = "ota-2",
                seq = 1,
                progress = 0
            )
        )

        val state = repo.states.value.single()
        assertEquals("OTA_PREPARING", state.otaStatus?.status)
        assertEquals("ota-2", state.otaStatus?.cmdId)
    }

    private companion object {
        const val SERIAL = "TEWNHZDBK"
    }
}
