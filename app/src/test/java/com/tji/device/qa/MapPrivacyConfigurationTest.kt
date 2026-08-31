package com.tji.device.qa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapPrivacyConfigurationTest {
    @Test
    fun appStartupDoesNotAutoConsentOrRequestLocation() {
        val root = repoRoot()
        val mapInitializer = root.resolve("app/src/map/java/com/tji/device/MapSdkInitializer.kt").readText()
        val activity = root.resolve(
            "app/src/main/java/com/tji/device/ui/main/MainActivity.kt"
        ).readText()
        val onCreateSection = activity.substring(
            startIndex = activity.indexOf("override fun onCreate"),
            endIndex = activity.indexOf("@Composable\n    private fun AppNavigation")
        )

        assertTrue(mapInitializer.contains("if (!isPrivacyAccepted(application)) return"))
        assertFalse(onCreateSection.contains("requestLocationPermission()"))
    }

    @Test
    fun mapScreenOffersExplicitAcceptAndDeclineBeforeUsingSdk() {
        val source = repoRoot().resolve(
            "app/src/main/java/com/tji/device/product/radiodetection/ui/control/RadioDetectionMonitorMap.kt"
        ).readText()

        assertTrue(source.contains("MapSdkInitializer.acceptPrivacy(application)"))
        assertTrue(source.contains("MapSdkInitializer.declinePrivacy(application)"))
        assertTrue(source.contains("mapBuildCanUseGaode && privacyAccepted"))
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
