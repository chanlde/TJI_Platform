package com.tji.device.qa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdateConfigurationTest {
    @Test
    fun updateFlowDoesNotUseFixedHttpApkUrl() {
        val root = repoRoot()
        val networkBuild = root.resolve("NetWork/build.gradle.kts").readText()
        val appBuild = root.resolve("app/build.gradle.kts").readText()
        val application = root.resolve(
            "app/src/main/java/com/tji/device/MyApplication.kt"
        ).readText()
        val activity = root.resolve(
            "app/src/main/java/com/tji/device/ui/main/MainActivity.kt"
        ).readText()
        val securityConfig = root.resolve(
            "app/src/main/res/xml/network_security_config.xml"
        ).readText()
        val manifest = root.resolve("app/src/main/AndroidManifest.xml").readText()
        val installer = root.resolve(
            "app/src/main/java/com/tji/device/update/VerifiedAppUpdateInstaller.kt"
        ).readText()
        val filePaths = root.resolve(
            "app/src/main/res/xml/app_update_file_paths.xml"
        ).readText()

        assertFalse(networkBuild.contains("TJI_UPDATE_URL"))
        assertFalse(activity.contains("NetworkEndpoints.appUpdateUrl"))
        assertFalse(activity.contains("fun openUrl("))
        assertTrue(application.contains("validateAppUpdateCandidate"))
        assertTrue(application.contains("BuildConfig.TJI_APP_UPDATE_PRODUCT_ID"))
        assertTrue(appBuild.contains("configString(\"TJI_APP_UPDATE_PRODUCT_ID\", \"-1\")"))
        assertFalse(securityConfig.contains(">api.tjinnovations.cloud<"))
        assertTrue(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(filePaths.contains("path=\"verified-app-updates/\""))
        assertTrue(installer.contains("connection.instanceFollowRedirects = false"))
        assertTrue(installer.contains("validateDownloadedApkIdentity(candidate, identity)"))
        assertTrue(installer.contains("getPackageArchiveInfo"))
        assertTrue(installer.contains("FileProvider.getUriForFile"))
        assertTrue(activity.contains("downloadVerifyAndOpen(candidate)"))
    }

    private fun repoRoot(): File {
        var current: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile
        }
        error("Cannot locate repository root")
    }
}
