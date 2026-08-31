package com.tji.device.qa

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseSensitiveLogGuardTest {
    @Test
    fun warningAndErrorLogsDoNotContainKnownSensitiveFields() {
        val root = repoRoot()
        val sourceRoots = listOf(
            root.resolve("app/src/main/java"),
            root.resolve("NetWork/src/main/java")
        )
        val violations = sourceRoots
            .flatMap { sourceRoot -> sourceRoot.walkTopDown().filter { it.extension == "kt" }.toList() }
            .flatMap { source ->
                val lines = source.readLines()
                lines.indices.mapNotNull { index ->
                    val line = lines[index]
                    if (RELEASE_LOG_CALLS.none(line::contains)) return@mapNotNull null
                    val callWindow = lines
                        .subList(index, minOf(index + LOG_CALL_WINDOW_LINES, lines.size))
                        .joinToString("\n")
                    val marker = SENSITIVE_MARKERS.firstOrNull(callWindow::contains)
                        ?: return@mapNotNull null
                    "${source.relativeTo(root)}:${index + 1} contains $marker"
                }
            }

        assertTrue(
            "Release-capable logs expose sensitive fields:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    private fun repoRoot(): File {
        var current: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile
        }
        error("Cannot locate repository root")
    }

    private companion object {
        const val LOG_CALL_WINDOW_LINES = 3
        val RELEASE_LOG_CALLS = listOf("Log.w(", "Log.e(", "SpeakerLogger.warn(")
        val SENSITIVE_MARKERS = listOf(
            "account=$",
            "clientId=$",
            "topic=$",
            "sn=$",
            "deviceId=$",
            "serial=$",
            "json.toString()"
        )
    }
}
