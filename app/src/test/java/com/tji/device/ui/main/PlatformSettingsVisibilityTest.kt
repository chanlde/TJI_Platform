package com.tji.device.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlatformSettingsVisibilityTest {
    @Test
    fun customerSettingsKeepVersionAndHideSessionAndDiagnosticActions() {
        val source = repoRoot()
            .resolve("app/src/main/java/com/tji/device/ui/main/PlatformSettingsSheet.kt")
            .readText()

        assertTrue(source.contains("当前版本"))
        assertFalse(source.contains("退出当前账号"))
        assertFalse(source.contains("导出诊断包"))
        assertFalse(source.contains("shareDiagnosticExport"))
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
