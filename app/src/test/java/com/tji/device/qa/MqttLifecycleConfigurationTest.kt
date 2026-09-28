package com.tji.device.qa

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MqttLifecycleConfigurationTest {
    @Test
    fun foregroundControlAppDoesNotDeclareIneffectiveMqttService() {
        val root = repoRoot()
        val manifest = root.resolve("app/src/main/AndroidManifest.xml").readText()
        val activity = root.resolve(
            "app/src/main/java/com/tji/device/ui/main/MainActivity.kt"
        ).readText()

        assertFalse(manifest.contains(".service.MqttService"))
        assertFalse(activity.contains("MqttService"))
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
