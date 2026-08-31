package com.tji.device.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DiagnosticEventStoreTest {
    @Test
    fun exportIncludesMetadataAndRedactsCommonSecrets() {
        val directory = Files.createTempDirectory("tji-diagnostics-test").toFile()
        try {
            val store = DiagnosticEventStore(directory, nowMillis = { 123L })
            store.record(
                "mqtt",
                mapOf("detail" to "account=demo token=secret password=hunter2")
            )
            val target = directory.resolve("export.jsonl")

            store.exportTo(target)

            val exported = target.readText()
            assertTrue(exported.contains("TJI_LOCAL_DIAGNOSTICS_V1"))
            assertTrue(exported.contains("account=<redacted>"))
            assertTrue(exported.contains("token=<redacted>"))
            assertTrue(exported.contains("password=<redacted>"))
            assertFalse(exported.contains("secret"))
            assertFalse(exported.contains("hunter2"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun eventFileStaysBounded() {
        val directory = Files.createTempDirectory("tji-diagnostics-bounds").toFile()
        try {
            val store = DiagnosticEventStore(directory, maxBytes = 1_024)
            repeat(100) { index ->
                store.record("event", mapOf("index" to index, "payload" to "x".repeat(80)))
            }

            val target = directory.resolve("export.jsonl")
            store.exportTo(target)

            assertTrue(target.length() < 1_024)
            assertTrue(target.readText().contains("\"index\":99"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
