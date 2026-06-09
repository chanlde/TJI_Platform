package com.tji.device.product.solarclean.ui.control

import com.tji.device.product.solarclean.model.SolarCleanDeviceInfo
import com.tji.device.product.solarclean.model.SolarCleanOtaStatus
import com.tji.network.data.OtaLatestResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarCleanOtaFormattersTest {

    @Test
    fun normalizesDeviceOtaStatesForDisplay() {
        assertEquals("升级成功", otaStateText("OTA_SUCCESS"))
        assertEquals("正在升级", otaStateText("installing"))
        assertEquals("--", otaStateText("UNKNOWN"))
        assertEquals("CUSTOM_STATE", otaStateText("CUSTOM_STATE"))
    }

    @Test
    fun detectsLatestFirmwareByInnerVersionOrVersionName() {
        assertTrue(
            isDeviceAtLatest(
                info = SolarCleanDeviceInfo(
                    hardwareVersion = "HW-A",
                    firmwareVersion = "1.0.4",
                    firmwareInnerVersion = 4
                ),
                latest = OtaLatestResponse(latestVersion = "1.0.5", hardwareVersion = "HW-A", innerVersion = 3)
            )
        )
        assertTrue(
            isDeviceAtLatest(
                info = SolarCleanDeviceInfo(
                    hardwareVersion = "HW-A",
                    firmwareVersion = "1.0.4",
                    firmwareInnerVersion = null
                ),
                latest = OtaLatestResponse(latestVersion = "1.0.4", hardwareVersion = "HW-A", innerVersion = null)
            )
        )
        assertFalse(
            isDeviceAtLatest(
                info = SolarCleanDeviceInfo(
                    hardwareVersion = "HW-A",
                    firmwareVersion = "1.0.3",
                    firmwareInnerVersion = 3
                ),
                latest = OtaLatestResponse(latestVersion = "1.0.4", hardwareVersion = "HW-A", innerVersion = 4)
            )
        )
    }

    @Test
    fun treatsPendingRebootAsCompleteWhenDeviceReportsLatest() {
        val status = SolarCleanOtaStatus(status = "PENDING_REBOOT", progress = 88, message = "wait")

        val completed = status.toCompletedIfDeviceReachedLatest(deviceReachedLatest = true)

        assertEquals("SUCCESS", completed.status)
        assertEquals(100, completed.progress)
        assertNull(completed.message)
        assertEquals("等待重启", otaStatusText(status))
        assertEquals("升级成功", otaStatusText(completed))
    }

    @Test
    fun filtersOpaqueOtaStatusMessages() {
        assertNull(otaUserMessage("OTA_DOWNLOADING"))
        assertNull(otaUserMessage("UNKNOWN"))
        assertEquals("flash write failed", otaUserMessage("flash write failed"))
    }
}
