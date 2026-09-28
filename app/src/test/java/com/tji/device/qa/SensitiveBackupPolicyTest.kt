package com.tji.device.qa

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SensitiveBackupPolicyTest {
    @Test
    fun manifestDisablesBackupAndRulesExcludeSensitivePreferences() {
        val root = repoRoot()
        val manifest = root.resolve("app/src/main/AndroidManifest.xml").readText()
        val legacyRules = root.resolve("app/src/main/res/xml/backup_rules.xml").readText()
        val extractionRules = root.resolve(
            "app/src/main/res/xml/data_extraction_rules.xml"
        ).readText()

        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        SENSITIVE_PREFERENCES.forEach { fileName ->
            assertTrue(legacyRules.contains("path=\"$fileName\""))
            assertTrue(extractionRules.countOccurrences("path=\"$fileName\"") >= 2)
        }
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length).count { it == value }

    private fun repoRoot(): File {
        var current: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile
        }
        error("Cannot locate repository root")
    }

    private companion object {
        val SENSITIVE_PREFERENCES = listOf(
            "user_preferences.xml",
            "user_preferences_secure.xml",
            "radio_detection_replay.xml",
            "map_privacy_preferences.xml"
        )
    }
}
