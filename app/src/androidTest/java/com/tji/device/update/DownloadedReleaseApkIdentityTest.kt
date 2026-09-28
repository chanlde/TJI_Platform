package com.tji.device.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tji.device.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DownloadedReleaseApkIdentityTest {
    @Test
    fun packageManagerReadsInstalledApkIdentityAndSigner() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installedApk = File(context.applicationInfo.sourceDir)

        val identity = readDownloadedApkIdentity(context, installedApk)

        assertNotNull(identity)
        val actual = requireNotNull(identity)
        assertEquals(BuildConfig.APPLICATION_ID, actual.packageName)
        assertEquals(BuildConfig.VERSION_CODE.toLong(), actual.versionCode)
        assertEquals(installedApk.length(), actual.fileSize)
        assertTrue(actual.apkSha256.matches(Regex("^[0-9a-f]{64}$")))
        if (!BuildConfig.DEBUG) {
            assertTrue(
                actual.signerSha256Digests.contains(
                    BuildConfig.TJI_RELEASE_SIGNER_SHA256.lowercase()
                )
            )
        }
    }
}
