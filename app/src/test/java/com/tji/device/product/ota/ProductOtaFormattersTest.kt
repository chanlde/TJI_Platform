package com.tji.device.product.ota

import com.tji.network.data.OtaLatestResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductOtaFormattersTest {

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
                info = ProductDeviceInfo(
                    hardwareVersion = "HW-A",
                    firmwareVersion = "1.0.4",
                    firmwareInnerVersion = 4
                ),
                latest = OtaLatestResponse(latestVersion = "1.0.5", hardwareVersion = "HW-A", innerVersion = 3)
            )
        )
        assertTrue(
            isDeviceAtLatest(
                info = ProductDeviceInfo(
                    hardwareVersion = "HW-A",
                    firmwareVersion = "1.0.4",
                    firmwareInnerVersion = null
                ),
                latest = OtaLatestResponse(latestVersion = "1.0.4", hardwareVersion = "HW-A", innerVersion = null)
            )
        )
        assertFalse(
            isDeviceAtLatest(
                info = ProductDeviceInfo(
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
        val status = ProductOtaStatus(status = "PENDING_REBOOT", progress = 88, message = "wait")

        val completed = status.toCompletedIfDeviceReachedLatest(deviceReachedLatest = true)

        assertEquals("SUCCESS", completed.status)
        assertEquals(100, completed.progress)
        assertNull(completed.message)
        assertEquals("等待重启", otaStatusText(status))
        assertEquals("升级成功", otaStatusText(completed))
    }

    @Test
    fun keepsDownloadingProgressEvenWhenPackageVersionIsNotNewer() {
        val status = ProductOtaStatus(status = "OTA_DOWNLOADING", progress = 36)

        val effective = status.toCompletedIfDeviceReachedLatest(deviceReachedLatest = true)

        assertEquals("OTA_DOWNLOADING", effective.status)
        assertEquals(36, effective.progress)
    }

    @Test
    fun filtersOpaqueOtaStatusMessages() {
        assertNull(otaUserMessage("OTA_DOWNLOADING"))
        assertNull(otaUserMessage("UNKNOWN"))
        assertEquals("flash write failed", otaUserMessage("flash write failed"))
    }

    @Test
    fun displaysOtaDownloadTestDoneAsSuccessfulDownloadOnly() {
        val status = ProductOtaStatus(status = "OTA_TEST_DONE", progress = 100)

        assertEquals("下载校验成功", otaStatusText(status))
        assertEquals("下载与校验成功，设备未升级", otaProgressTitle(status))
        assertFalse(status.isOtaBusy())
        assertTrue(status.shouldShowOtaProgress())
    }

    @Test
    fun derivesProgressFromBytesWhenPercentIsMissing() {
        val status = ProductOtaStatus(
            status = "OTA_DOWNLOADING",
            downloaded = 50,
            total = 200
        )

        assertEquals(25, status.displayProgressPercent())
    }

    @Test
    fun derivesProgressFromKnownOtaStageWhenNoPercentOrBytes() {
        assertEquals(0, ProductOtaStatus(status = "OTA_PREPARING").displayProgressPercent())
        assertEquals(90, ProductOtaStatus(status = "OTA_VERIFYING").displayProgressPercent())
        assertEquals(100, ProductOtaStatus(status = "OTA_PENDING_REBOOT").displayProgressPercent())
    }

    @Test
    fun clampsProgressFromExplicitPercentAndDownloadedBytes() {
        assertEquals(
            100,
            ProductOtaStatus(status = "OTA_DOWNLOADING", progress = 148).displayProgressPercent()
        )
        assertEquals(
            0,
            ProductOtaStatus(status = "OTA_DOWNLOADING", progress = -12).displayProgressPercent()
        )
        assertEquals(
            100,
            ProductOtaStatus(status = "OTA_DOWNLOADING", downloaded = 150, total = 100).displayProgressPercent()
        )
        assertEquals(
            0,
            ProductOtaStatus(status = "OTA_DOWNLOADING", downloaded = -20, total = 100).displayProgressPercent()
        )
        assertNull(
            ProductOtaStatus(status = "OTA_DOWNLOADING", downloaded = 20, total = 0).displayProgressPercent()
        )
    }

    @Test
    fun latestPackageIsStartableOnlyWhenRequiredFieldsExist() {
        assertTrue(
            OtaLatestResponse(
                latestVersion = "V1.0.1",
                downloadUrl = "https://example.com/fw.bin",
                fileSize = 1024,
                sha256 = "abc123"
            ).isStartable()
        )
        assertFalse(
            OtaLatestResponse(
                latestVersion = "",
                downloadUrl = "https://example.com/fw.bin",
                fileSize = 1024,
                sha256 = "abc123"
            ).isStartable()
        )
        assertFalse(
            OtaLatestResponse(
                latestVersion = "V1.0.1",
                downloadUrl = "",
                fileSize = 1024,
                sha256 = "abc123"
            ).isStartable()
        )
        assertFalse(
            OtaLatestResponse(
                latestVersion = "V1.0.1",
                downloadUrl = "https://example.com/fw.bin",
                fileSize = null,
                sha256 = "abc123"
            ).isStartable()
        )
        assertFalse(
            OtaLatestResponse(
                latestVersion = "V1.0.1",
                downloadUrl = "https://example.com/fw.bin",
                fileSize = 1024,
                sha256 = ""
            ).isStartable()
        )
    }
}
