package com.tji.device.product.ota

import com.tji.device.data.model.ProductType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductOtaRuntimeRepositoryTest {

    @Test
    fun clearAllRemovesPreviousAccountRuntimeState() {
        val repo = ProductOtaRuntimeRepo()
        repo.updateDeviceInfo(
            productType = ProductType.Speaker,
            serialNumber = SERIAL,
            deviceInfo = ProductDeviceInfo(firmwareVersion = "1.0.0")
        )

        repo.clearAll()

        assertEquals(emptyList<ProductOtaRuntimeState>(), repo.states.value)
    }

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

    @Test
    fun keepsRebootWaitUnconfirmedWhenDeviceComesOnline() {
        var now = 200L
        val repo = ProductOtaRuntimeRepo(nowMillis = { now })

        repo.updateOtaStatus(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            otaStatus = ProductOtaStatus(
                status = "OTA_REBOOTING",
                cmdId = "ota-1",
                progress = 100,
                message = "waiting reboot"
            )
        )
        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "offline",
            timestamp = 250
        )
        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "online",
            timestamp = 300
        )

        val state = repo.states.value.single()
        assertEquals("BOOT_VERIFY", state.otaStatus?.status)
        assertEquals(99, state.otaStatus?.progress)
        assertEquals("设备已重启并上线，等待升级结果确认", state.otaStatus?.message)
        assertEquals(300L, state.otaStatus?.timestamp)
    }

    @Test
    fun retainedOrOlderOnlineDoesNotCompleteRebootWait() {
        var now = 200L
        val repo = ProductOtaRuntimeRepo(nowMillis = { now })
        repo.updateOtaStatus(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            otaStatus = ProductOtaStatus(
                status = "OTA_REBOOTING",
                cmdId = "ota-1",
                progress = 100
            )
        )

        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "online",
            timestamp = 300,
            isRetained = true
        )
        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "online",
            timestamp = 199
        )
        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "online",
            timestamp = 201
        )

        assertEquals("OTA_REBOOTING", repo.states.value.single().otaStatus?.status)
        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "offline",
            timestamp = 201
        )
        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "online",
            timestamp = 202
        )
        assertEquals("BOOT_VERIFY", repo.states.value.single().otaStatus?.status)
    }

    @Test
    fun olderOfflineCannotArmRebootCompletion() {
        var now = 200L
        val repo = ProductOtaRuntimeRepo(nowMillis = { now })
        repo.updateOtaStatus(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            otaStatus = ProductOtaStatus(
                status = "OTA_REBOOTING",
                cmdId = "ota-1",
                progress = 100
            )
        )

        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "offline",
            timestamp = 199
        )
        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "online",
            timestamp = 201
        )

        assertEquals("OTA_REBOOTING", repo.states.value.single().otaStatus?.status)

        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "offline",
            timestamp = 202
        )
        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "online",
            timestamp = 203
        )
        assertEquals("BOOT_VERIFY", repo.states.value.single().otaStatus?.status)
    }

    @Test
    fun lateStatusFromRetiredCommandCannotReplaceCurrentOtaTask() {
        var now = 100L
        val repo = ProductOtaRuntimeRepo(nowMillis = { now })
        repo.updateOtaStatus(
            ProductType.Speaker,
            SERIAL,
            ProductOtaStatus(status = "OTA_DOWNLOADING", cmdId = "ota-1", seq = 1, progress = 20)
        )
        now = 200L
        repo.updateOtaStatus(
            ProductType.Speaker,
            SERIAL,
            ProductOtaStatus(status = "OTA_PREPARING", cmdId = "ota-2", seq = 1, progress = 0)
        )
        now = 300L
        repo.updateOtaStatus(
            ProductType.Speaker,
            SERIAL,
            ProductOtaStatus(status = "OTA_VERIFYING", cmdId = "ota-1", seq = 2, progress = 90)
        )

        val status = repo.states.value.single().otaStatus
        assertEquals("ota-2", status?.cmdId)
        assertEquals("OTA_PREPARING", status?.status)
    }

    @Test
    fun lateAnonymousLegacyStatusCannotRegressNamedCurrentTask() {
        val repo = ProductOtaRuntimeRepo()
        repo.updateOtaStatus(
            ProductType.Speaker,
            SERIAL,
            ProductOtaStatus(
                status = "OTA_DOWNLOADING",
                cmdId = "ota-current",
                progress = 70,
                timestamp = 300
            )
        )

        repo.updateOtaStatus(
            ProductType.Speaker,
            SERIAL,
            ProductOtaStatus(
                status = "OTA_DOWNLOADING",
                cmdId = null,
                progress = 40,
                timestamp = 200
            )
        )

        val status = repo.states.value.single().otaStatus
        assertEquals("ota-current", status?.cmdId)
        assertEquals(70, status?.progress)
        assertEquals(300L, status?.timestamp)
    }

    @Test
    fun doesNotCompleteActiveDownloadWhenDeviceComesOnline() {
        val repo = ProductOtaRuntimeRepo()

        repo.updateOtaStatus(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            otaStatus = ProductOtaStatus(
                status = "OTA_DOWNLOADING",
                cmdId = "ota-1",
                progress = 42
            )
        )
        repo.updateLifecycle(
            productType = ProductType.SolarClean,
            serialNumber = SERIAL,
            eventType = "online"
        )

        val state = repo.states.value.single()
        assertEquals("OTA_DOWNLOADING", state.otaStatus?.status)
        assertEquals(42, state.otaStatus?.progress)
    }

    private companion object {
        const val SERIAL = "TEWNHZDBK"
    }
}
