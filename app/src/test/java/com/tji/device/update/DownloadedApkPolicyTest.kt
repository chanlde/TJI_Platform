package com.tji.device.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadedApkPolicyTest {
    @Test
    fun acceptsFileOnlyWhenActualApkIdentityMatchesCandidate() {
        val result = validateDownloadedApkIdentity(candidate(), identity())

        assertTrue(result is DownloadedApkValidation.Verified)
    }

    @Test
    fun rejectsTamperedBytesSizePackageVersionAndSigner() {
        assertRejected(identity(apkSha256 = "0".repeat(64)), "SHA256")
        assertRejected(identity(fileSize = 31_999_999), "大小")
        assertRejected(identity(packageName = "com.tji.bucket"), "包名")
        assertRejected(identity(versionCode = 215), "版本")
        assertRejected(identity(signerSha256 = "0".repeat(64)), "签名")
    }

    @Test
    fun fileDigestUsesFullDownloadedBytes() {
        val file = File.createTempFile("tji-update-policy", ".apk")
        try {
            file.writeText("verified apk fixture")

            assertEquals(
                "baafdc89e7664cd0fce8b52175d8afc9a93e5510d828969b17d34fcca580e407",
                sha256Hex(file)
            )
        } finally {
            file.delete()
        }
    }

    private fun assertRejected(identity: DownloadedApkIdentity, messagePart: String) {
        val result = validateDownloadedApkIdentity(candidate(), identity)
        assertTrue(result is DownloadedApkValidation.Rejected)
        assertTrue((result as DownloadedApkValidation.Rejected).reason.contains(messagePart))
    }

    private fun candidate() = AppUpdateValidation.Available(
        url = "https://www.tjinnovations.cloud/download/TJI_Platform.apk",
        versionCode = 214,
        apkSha256 = APK_SHA256,
        fileSize = 32_000_000,
        packageName = PACKAGE_NAME,
        signerSha256 = SIGNER
    )

    private fun identity(
        fileSize: Long = 32_000_000,
        apkSha256: String = APK_SHA256,
        packageName: String = PACKAGE_NAME,
        versionCode: Long = 214,
        signerSha256: String = SIGNER
    ) = DownloadedApkIdentity(
        fileSize = fileSize,
        apkSha256 = apkSha256,
        packageName = packageName,
        versionCode = versionCode,
        signerSha256Digests = setOf(signerSha256)
    )

    private companion object {
        const val PACKAGE_NAME = "com.tji.device"
        const val SIGNER = "0cfc0338819ac62f216737e20634c50bf702fdeb748d9a1a5f3713e22fb76567"
        const val APK_SHA256 = "c2fbaf5b0c9c10f7c4c6e79ec58f2af2f447f19d33c0ee900c0e1f785b1f8db4"
    }
}
