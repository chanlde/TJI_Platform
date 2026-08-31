package com.tji.device.product.ota

import com.tji.device.data.model.ProductType
import com.tji.network.data.OtaLatestResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductOtaPackagePolicyTest {
    @Test
    fun validPackagePreservesConfirmedMcuFields() {
        val result = validateProductOtaPackage(
            productType = ProductType.Speaker,
            deviceInfo = deviceInfo(),
            latest = latest(),
            otaBaseUrl = BASE_URL
        )

        assertTrue(result is ProductOtaPackageValidation.Valid)
        val packageInfo = (result as ProductOtaPackageValidation.Valid).packageInfo
        assertEquals("V1.0.2", packageInfo.targetVersion)
        assertEquals(12, packageInfo.targetInnerVersion)
        assertEquals("HW-A", packageInfo.hardwareVersion)
        assertEquals("https://www.tjinnovations.cloud/download/speaker.bin", packageInfo.downloadUrl)
        assertEquals(216_256L, packageInfo.fileSize)
        assertEquals(SHA256, packageInfo.sha256)
        assertEquals("signed-manifest", packageInfo.signature)
    }

    @Test
    fun rejectsUnsafeUrlAndWrongProductOrHardware() {
        assertInvalid(latest(downloadUrl = "http://www.tjinnovations.cloud/download/speaker.bin"), "HTTPS")
        assertInvalid(latest(downloadUrl = "https://evil.example/speaker.bin"), "域名")
        assertInvalid(latest(productName = "破窗弹"), "产品")
        assertInvalid(latest(hardwareVersion = "HW-B"), "硬件")
    }

    @Test
    fun rejectsDowngradeInvalidSizeHashAndLowBattery() {
        assertInvalid(latest(innerVersion = 11), "版本")
        assertInvalid(latest(innerVersion = null), "内部版本")
        assertInvalid(latest(fileSize = 0), "大小")
        assertInvalid(latest(fileSize = 70L * 1024 * 1024), "大小")
        assertInvalid(latest(sha256 = "abc123"), "SHA256")
        assertInvalid(latest(minBattery = 50), "电量", deviceInfo = deviceInfo(batteryPercent = 49))
    }

    @Test
    fun signatureIsOptionalUntilBootloaderCapabilityIsConfirmed() {
        assertTrue(
            validateProductOtaPackage(
                productType = ProductType.Speaker,
                deviceInfo = deviceInfo(),
                latest = latest(signature = null),
                otaBaseUrl = BASE_URL,
                requireSignature = false
            ) is ProductOtaPackageValidation.Valid
        )
        assertInvalid(latest(signature = null), "签名", requireSignature = true)
    }

    private fun assertInvalid(
        latest: OtaLatestResponse,
        expectedMessagePart: String,
        deviceInfo: ProductDeviceInfo = deviceInfo(),
        requireSignature: Boolean = false
    ) {
        val result = validateProductOtaPackage(
            productType = ProductType.Speaker,
            deviceInfo = deviceInfo,
            latest = latest,
            otaBaseUrl = BASE_URL,
            requireSignature = requireSignature
        )
        assertTrue(result is ProductOtaPackageValidation.Invalid)
        assertTrue((result as ProductOtaPackageValidation.Invalid).message.contains(expectedMessagePart))
    }

    private fun deviceInfo(batteryPercent: Int = 80) = ProductDeviceInfo(
        hardwareVersion = "HW-A",
        firmwareVersion = "V1.0.1",
        firmwareInnerVersion = 11,
        batteryPercent = batteryPercent
    )

    private fun latest(
        productName: String = "无人机喊话器",
        hardwareVersion: String? = "HW-A",
        fileSize: Long? = 216_256,
        sha256: String? = SHA256,
        signature: String? = "signed-manifest",
        minBattery: Int? = 30,
        downloadUrl: String? = "/download/speaker.bin",
        innerVersion: Int? = 12
    ) = OtaLatestResponse(
        hasUpdate = true,
        latestVersion = "V1.0.2",
        hardwareVersion = hardwareVersion,
        fileSize = fileSize,
        sha256 = sha256,
        signature = signature,
        minBattery = minBattery,
        downloadUrl = downloadUrl,
        productName = productName,
        innerVersion = innerVersion,
        type = 2
    )

    private companion object {
        const val BASE_URL = "https://www.tjinnovations.cloud/"
        const val SHA256 = "f51563a1db560764eda95a6f2f0c4fddfbcf7870efc6bcd5457025ee157b3509"
    }
}
