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
        assertEquals("结果待核对", otaStateText("UNKNOWN"))
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
    fun keepsPendingRebootUnconfirmedEvenWhenDeviceReportsLatest() {
        val status = ProductOtaStatus(status = "PENDING_REBOOT", progress = 88, message = "wait")
        assertEquals("等待重启", otaStatusText(status))
        assertEquals(88, status.displayProgressPercent())
        assertTrue(status.isOtaBusy())
        assertEquals("升级结果待核对", otaCandidateSummaryText(false, status))
    }

    @Test
    fun keepsRollbackBusyUntilDeviceConfirmsItFinished() {
        val rollingBack = ProductOtaStatus(status = "OTA_ROLLBACK", cmdId = "ota-1", progress = 100)
        val rolledBack = ProductOtaStatus(status = "OTA_ROLLED_BACK", cmdId = "ota-1", progress = 100)

        assertEquals("正在回滚", otaStatusText(rollingBack))
        assertEquals(99, rollingBack.displayProgressPercent())
        assertTrue(rollingBack.isOtaBusy())
        assertEquals("已回滚", otaStatusText(rolledBack))
        assertFalse(rolledBack.isOtaBusy())
        assertEquals(100, rolledBack.displayProgressPercent())
    }

    @Test
    fun keepsUnknownNamedTaskOccupiedUntilItIsReconciled() {
        assertTrue(ProductOtaStatus(status = "UNKNOWN", cmdId = "ota-1").isOtaBusy())
        assertFalse(ProductOtaStatus(status = "UNKNOWN").isOtaBusy())
        assertEquals("结果待核对", otaStateText("UNKNOWN"))
    }

    @Test
    fun filtersOpaqueOtaStatusMessages() {
        assertNull(otaUserMessage("OTA_DOWNLOADING"))
        assertNull(otaUserMessage("UNKNOWN"))
        assertEquals(
            "固件文件大小与发布信息不一致，请联系管理员",
            otaUserMessage("Firmware size mismatch: expected 53, got 1")
        )
        assertEquals("固件写入失败，请联系管理员", otaUserMessage("flash write failed"))
        assertEquals("电量不足，已停止升级", otaUserMessage("电量不足，已停止升级"))
        assertEquals("设备升级失败，请查看后台升级记录", otaUserMessage("ERROR_42"))
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
        assertEquals(99, ProductOtaStatus(status = "OTA_PENDING_REBOOT").displayProgressPercent())
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
                sha256 = VALID_SHA256
            ).isStartable()
        )
        assertFalse(
            OtaLatestResponse(
                latestVersion = "",
                downloadUrl = "https://example.com/fw.bin",
                fileSize = 1024,
                sha256 = VALID_SHA256
            ).isStartable()
        )
        assertFalse(
            OtaLatestResponse(
                latestVersion = "V1.0.1",
                downloadUrl = "",
                fileSize = 1024,
                sha256 = VALID_SHA256
            ).isStartable()
        )
        assertFalse(
            OtaLatestResponse(
                latestVersion = "V1.0.1",
                downloadUrl = "https://example.com/fw.bin",
                fileSize = null,
                sha256 = VALID_SHA256
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

    @Test
    fun resolvesFirmwareDownloadUrlsAndRejectsBlankValues() {
        val baseUrl = "https://ota.example.com/releases/"

        assertNull(resolveProductOtaDownloadUrl(null, baseUrl))
        assertNull(resolveProductOtaDownloadUrl("  ", baseUrl))
        assertNull(resolveProductOtaDownloadUrl(" https://cdn.example.com/fw.bin ", baseUrl))
        assertEquals(
            "https://ota.example.com/fw.bin",
            resolveProductOtaDownloadUrl("/fw.bin", baseUrl)
        )
        assertEquals(
            "https://ota.example.com/releases/fw.bin",
            resolveProductOtaDownloadUrl("fw.bin", baseUrl)
        )
    }

    private companion object {
        const val VALID_SHA256 = "f51563a1db560764eda95a6f2f0c4fddfbcf7870efc6bcd5457025ee157b3509"
    }
}
