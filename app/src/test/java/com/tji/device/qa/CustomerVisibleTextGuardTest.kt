package com.tji.device.qa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CustomerVisibleTextGuardTest {

    @Test
    fun productionSourceDoesNotExposeTestDeviceLabels() {
        val sourceRoot = repoRoot().resolve("app/src/main/java")
        val offenders = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if ("测试设备" in line) "${file.relativeTo(repoRoot())}:${index + 1}:$line" else null
                }
            }
            .toList()

        assertTrue(
            "Production UI/source must not expose test-device labels:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun releaseBuildDisablesLocalDemoDevices() {
        val releaseBlock = releaseBuildTypeBlock()

        assertTrue(
            "Release build must explicitly disable local demo devices",
            releaseBlock.contains("""buildConfigField("boolean", "TJI_ENABLE_LOCAL_DEMO_DEVICES", "false")""")
        )
        assertFalse(
            "Release build must not enable local demo devices",
            releaseBlock.contains("""buildConfigField("boolean", "TJI_ENABLE_LOCAL_DEMO_DEVICES", "true")""")
        )
    }

    @Test
    fun releaseBuildDisablesOtaTestEntry() {
        val releaseBlock = releaseBuildTypeBlock()

        assertTrue(
            "Release build must explicitly disable OTA test entry",
            releaseBlock.contains("""buildConfigField("boolean", "TJI_ENABLE_OTA_TEST_ENTRY", "false")""")
        )
        assertFalse(
            "Release build must not enable OTA test entry",
            releaseBlock.contains("""buildConfigField("boolean", "TJI_ENABLE_OTA_TEST_ENTRY", "true")""")
        )
    }

    private fun releaseBuildTypeBlock(): String {
        val buildGradle = repoRoot().resolve("app/build.gradle.kts").readText()
        val buildTypesBlock = extractGradleBlock(buildGradle, "buildTypes")
        return extractGradleBlock(buildTypesBlock, "release")
    }

    private fun extractGradleBlock(source: String, blockName: String): String {
        val match = Regex("""(?m)^\s*$blockName\s*\{""").find(source) ?: return ""
        val openingBrace = source.indexOf('{', match.range.first)
        var depth = 0
        source.forEachIndexed { index, character ->
            if (index < openingBrace) return@forEachIndexed
            when (character) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return source.substring(openingBrace + 1, index)
                    }
                }
            }
        }
        return ""
    }

    private fun repoRoot(): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is unavailable" }
        var current: File? = File(userDir).absoluteFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile
        }
        error("Cannot locate repository root")
    }
}
