package com.tji.device.diagnostics

import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import com.tji.device.BuildConfig
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppDiagnostics {
    @Volatile
    private var store: DiagnosticEventStore? = null

    fun initialize(context: Context) {
        if (store != null) return
        synchronized(this) {
            if (store != null) return
            store = DiagnosticEventStore(File(context.filesDir, "diagnostics"))
            installCrashHandler()
            record(
                "app_start",
                mapOf(
                    "version" to BuildConfig.VERSION_NAME,
                    "versionCode" to BuildConfig.VERSION_CODE,
                    "flavor" to BuildConfig.FLAVOR,
                    "buildType" to BuildConfig.BUILD_TYPE,
                    "android" to Build.VERSION.SDK_INT,
                    "device" to "${Build.MANUFACTURER} ${Build.MODEL}"
                )
            )
        }
    }

    fun record(event: String, fields: Map<String, Any?> = emptyMap()) {
        store?.record(event, fields)
    }

    fun deviceRef(serialNumber: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(serialNumber.trim().encodeToByteArray())
            .take(6)
            .joinToString("") { "%02x".format(it) }

    fun export(context: Context): android.net.Uri {
        val source = requireNotNull(store) { "Diagnostics not initialized" }
        val exportDirectory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(exportDirectory, "tji-diagnostics-$timestamp.jsonl")
        source.exportTo(target)
        return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", target)
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is DiagnosticCrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(DiagnosticCrashHandler(previous))
    }

    private class DiagnosticCrashHandler(
        private val delegate: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            record(
                "uncaught_exception",
                mapOf(
                    "thread" to thread.name,
                    "type" to throwable.javaClass.name,
                    "message" to throwable.message,
                    "stack" to throwable.stackTraceToString().take(MAX_STACK_CHARS)
                )
            )
            delegate?.uncaughtException(thread, throwable)
        }
    }

    private const val MAX_STACK_CHARS = 16_000
}

internal class DiagnosticEventStore(
    directory: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxBytes: Int = 256 * 1024
) {
    private val file = File(directory.apply { mkdirs() }, "events.jsonl")
    private val lock = Any()

    fun record(event: String, fields: Map<String, Any?>) = synchronized(lock) {
        val json = JSONObject()
            .put("timestamp", nowMillis())
            .put("event", sanitize(event))
        fields.forEach { (key, value) ->
            json.put(sanitize(key), sanitizeValue(value))
        }
        file.appendText(json.toString() + "\n", Charsets.UTF_8)
        trimIfNeeded()
    }

    fun exportTo(target: File) = synchronized(lock) {
        target.parentFile?.mkdirs()
        target.writeText(
            JSONObject(
                mapOf(
                    "format" to "TJI_LOCAL_DIAGNOSTICS_V1",
                    "exportedAt" to nowMillis(),
                    "redacted" to true
                )
            ).toString() + "\n",
            Charsets.UTF_8
        )
        if (file.exists()) target.appendBytes(file.readBytes())
    }

    private fun trimIfNeeded() {
        if (file.length() <= maxBytes) return
        val bytes = file.readBytes()
        val keepFrom = (bytes.size - maxBytes / 2).coerceAtLeast(0)
        var newline = -1
        for (index in keepFrom until bytes.size) {
            if (bytes[index] == 10.toByte()) {
                newline = index
                break
            }
        }
        file.writeBytes(bytes.copyOfRange(if (newline >= 0) newline + 1 else keepFrom, bytes.size))
    }

    private fun sanitizeValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Number, is Boolean -> value
        else -> sanitize(value.toString())
    }

    private fun sanitize(value: String): String = value
        .replace(ACCOUNT_PATTERN, "account=<redacted>")
        .replace(TOKEN_PATTERN, "$1=<redacted>")
        .take(MAX_FIELD_CHARS)

    private companion object {
        const val MAX_FIELD_CHARS = 16_000
        val ACCOUNT_PATTERN = Regex("(?i)account\\s*[=:]\\s*[^\\s,;]+")
        val TOKEN_PATTERN = Regex("(?i)(token|password|authorization)\\s*[=:]\\s*[^\\s,;]+")
    }
}
