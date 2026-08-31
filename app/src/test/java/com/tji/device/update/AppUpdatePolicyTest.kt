package com.tji.device.update

import com.tji.network.data.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test
    fun acceptsNewerPlatformPackageWithExpectedIdentity() {
        val result = validateAppUpdateCandidate(
            response = candidate(),
            localVersionCode = 213,
            expectedPackageName = PACKAGE_NAME,
            expectedSignerSha256 = SIGNER,
            downloadBaseUrl = BASE_URL
        )

        assertTrue(result is AppUpdateValidation.Available)
        val available = result as AppUpdateValidation.Available
        assertEquals("https://www.tjinnovations.cloud/download/TJI_Platform.apk", available.url)
        assertEquals(214, available.versionCode)
        assertEquals(APK_SHA256, available.apkSha256)
    }

    @Test
    fun rejectsLegacyProductWrongIdentityAndUnsafeUrl() {
        assertUnavailable(candidate(productName = "水桶控制"), "产品")
        assertUnavailable(candidate(packageName = "com.tji.bucket"), "包名")
        assertUnavailable(candidate(signerSha256 = "0".repeat(64)), "签名")
        assertUnavailable(candidate(path = "http://www.tjinnovations.cloud/TJI_Platform.apk"), "HTTPS")
        assertUnavailable(candidate(path = "https://evil.example/TJI_Platform.apk"), "域名")
    }

    @Test
    fun rejectsMissingIntegrityMetadataAndNonNewerVersion() {
        assertUnavailable(candidate(innerVersion = 213), "版本")
        assertUnavailable(candidate(apkSha256 = null), "SHA256")
        assertUnavailable(candidate(apkSha256 = "abc123"), "SHA256")
        assertUnavailable(candidate(signerSha256 = null), "签名")
    }

    private fun assertUnavailable(response: AppVersion, messagePart: String) {
        val result = validateAppUpdateCandidate(
            response = response,
            localVersionCode = 213,
            expectedPackageName = PACKAGE_NAME,
            expectedSignerSha256 = SIGNER,
            downloadBaseUrl = BASE_URL
        )
        assertTrue(result is AppUpdateValidation.Unavailable)
        assertTrue((result as AppUpdateValidation.Unavailable).reason.contains(messagePart))
    }

    private fun candidate(
        innerVersion: Int = 214,
        path: String? = "/download/TJI_Platform.apk",
        productName: String? = "TJI Platform",
        packageName: String? = PACKAGE_NAME,
        signerSha256: String? = SIGNER,
        apkSha256: String? = APK_SHA256
    ) = AppVersion(
        version = "V2.0.14",
        innerVersion = innerVersion,
        path = path,
        productName = productName,
        type = 1,
        packageName = packageName,
        signerSha256 = signerSha256,
        sha256 = apkSha256,
        fileSize = 32_000_000
    )

    private companion object {
        const val BASE_URL = "https://www.tjinnovations.cloud/"
        const val PACKAGE_NAME = "com.tji.device"
        const val SIGNER = "0cfc0338819ac62f216737e20634c50bf702fdeb748d9a1a5f3713e22fb76567"
        const val APK_SHA256 = "c2fbaf5b0c9c10f7c4c6e79ec58f2af2f447f19d33c0ee900c0e1f785b1f8db4"
    }
}
