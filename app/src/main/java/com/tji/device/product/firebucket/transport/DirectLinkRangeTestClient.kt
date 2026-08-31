package com.tji.device.product.firebucket.transport

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.FileProvider
import com.tji.device.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress

/** ESP32 本地拉距记录的快照；仅使用手机当前连接 ESP 的 Wi-Fi，不经过互联网。 */
data class DirectLinkRangeTestStatus(
    val storageReady: Boolean,
    val active: Boolean,
    val runId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val events: Int,
    val droppedEvents: Int,
    val appTx: Int,
    val retries: Int,
    val acknowledgements: Int,
    val timeouts: Int,
    val coalesces: Int,
    val tcpConnects: Int,
    val tcpDisconnects: Int,
    val uartCrcErrors: Int,
    val ackP50Ms: Long,
    val ackP95Ms: Long,
    val ackMaxMs: Long,
    val uartValidFrames: Int,
    val uartDiscardedBytes: Int
)

data class DirectLinkRangeTestEvent(
    val index: Int,
    val atMs: Long,
    val name: String,
    val sequence: Int,
    val relatedSequence: Int,
    val attempt: Int,
    val ackLatencyMs: Long,
    val uartCrcErrors: Int,
    val tcpDisconnects: Int
)

data class DirectLinkRangeTestEvents(
    val runId: String,
    val active: Boolean,
    val nextCursor: Int,
    val truncated: Boolean,
    val events: List<DirectLinkRangeTestEvent>
)

/**
 * 拉距测试的旁路 HTTP 客户端。
 *
 * 控制帧继续独占 TCP 19010；诊断用 HTTP 与其分离，避免把调试协议混入透明串口字节流。
 */
class DirectLinkRangeTestClient(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
        as ConnectivityManager

    suspend fun status(): DirectLinkRangeTestStatus =
        decodeStatus(requestJson(method = "GET", path = STATUS_PATH))

    suspend fun start(): DirectLinkRangeTestStatus =
        decodeStatus(requestJson(method = "POST", path = START_PATH))

    suspend fun stop(): DirectLinkRangeTestStatus =
        decodeStatus(requestJson(method = "POST", path = STOP_PATH))

    suspend fun events(after: Int): DirectLinkRangeTestEvents {
        val json = requestJson(method = "GET", path = "$EVENTS_PATH?after=${after.coerceAtLeast(0)}")
        val items = json.optJSONArray("events") ?: JSONArray()
        return DirectLinkRangeTestEvents(
            runId = json.optString("runId"),
            active = json.optBoolean("active"),
            nextCursor = json.optInt("nextCursor"),
            truncated = json.optBoolean("truncated"),
            events = buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    add(
                        DirectLinkRangeTestEvent(
                            index = item.optInt("index"),
                            atMs = item.optLong("atMs"),
                            name = item.optString("event", "UNKNOWN"),
                            sequence = item.optInt("seq"),
                            relatedSequence = item.optInt("relatedSeq"),
                            attempt = item.optInt("attempt"),
                            ackLatencyMs = item.optLong("ackLatencyMs"),
                            uartCrcErrors = item.optInt("uartCrcErrors"),
                            tcpDisconnects = item.optInt("tcpDisconnects")
                        )
                    )
                }
            }
        )
    }

    /** 停止测试后把 ESP 生成的原始 CSV 缓存到 App，再交给系统分享/保存。 */
    suspend fun downloadReport(runId: String): android.net.Uri = withContext(Dispatchers.IO) {
        val target = resolveDirectLinkWifiTarget(connectivityManager)
        val response = request(target, REPORT_PATH, "GET")
        if (response.status !in 200..299) throw IOException(parseError(response.body.decodeToString(), response.status))
        val exportDirectory = File(appContext.cacheDir, "range-tests").apply { mkdirs() }
        val safeRunId = runId.ifBlank { "unknown" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(exportDirectory, "esp-range-$safeRunId.csv")
        destination.outputStream().use { it.write(response.body) }
        FileProvider.getUriForFile(
            appContext,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            destination
        )
    }

    private suspend fun requestJson(method: String, path: String): JSONObject = withContext(Dispatchers.IO) {
        val target = resolveDirectLinkWifiTarget(connectivityManager)
        val response = request(target, path, method)
        val body = response.body.decodeToString()
        if (response.status !in 200..299) throw IOException(parseError(body, response.status))
        JSONObject(body)
    }

    /**
     * HttpURLConnection 会对 IP 明文请求执行平台 cleartext 策略。本地 ESP API 走绑定 Wi-Fi
     * Network 的原生 Socket，和 19010 控制链路一样不会被蜂窝默认路由或全局 HTTP 策略影响。
     */
    private fun request(
        target: DirectLinkWifiTarget,
        path: String,
        method: String
    ): LocalHttpResponse {
        val socket = target.network.socketFactory.createSocket()
        try {
            socket.connect(InetSocketAddress(target.gateway, HTTP_PORT), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            // 不能 close Writer：关闭 Socket 输出流会同时关闭输入流，导致收不到 HTTP 响应。
            val writer = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
            writer.write("$method $path HTTP/1.1\r\n")
            writer.write("Host: ${target.gateway.hostAddress}\r\n")
            writer.write("Accept: application/json\r\n")
            writer.write("Connection: close\r\n\r\n")
            writer.flush()
            return socket.getInputStream().readLocalHttpResponse()
        } finally {
            socket.close()
        }
    }

    private fun decodeStatus(json: JSONObject): DirectLinkRangeTestStatus = DirectLinkRangeTestStatus(
        storageReady = json.optBoolean("storageReady"),
        active = json.optBoolean("active"),
        runId = json.optString("runId"),
        startedAtMs = json.optLong("startedAtMs"),
        endedAtMs = json.optLong("endedAtMs"),
        events = json.optInt("events"),
        droppedEvents = json.optInt("droppedEvents"),
        appTx = json.optInt("appTx"),
        retries = json.optInt("retries"),
        acknowledgements = json.optInt("acks"),
        timeouts = json.optInt("timeouts"),
        coalesces = json.optInt("coalesces"),
        tcpConnects = json.optInt("tcpConnects"),
        tcpDisconnects = json.optInt("tcpDisconnects"),
        uartCrcErrors = json.optInt("uartCrcErrors"),
        ackP50Ms = json.optLong("ackP50Ms"),
        ackP95Ms = json.optLong("ackP95Ms"),
        ackMaxMs = json.optLong("ackMaxMs"),
        uartValidFrames = json.optInt("uartValidFrames"),
        uartDiscardedBytes = json.optInt("uartDiscardedBytes")
    )

    private fun parseError(body: String, status: Int): String = runCatching {
        JSONObject(body).optString("error").takeIf(String::isNotBlank)
    }.getOrNull() ?: "ESP 调试接口请求失败（HTTP $status）"

    private fun InputStream.readLocalHttpResponse(): LocalHttpResponse {
        val header = ByteArrayOutputStream()
        var matchedLineEndBytes = 0
        while (header.size() < MAX_HEADER_BYTES) {
            val value = read()
            if (value < 0) throw IOException("ESP 在返回 HTTP 响应头前断开连接")
            header.write(value)
            matchedLineEndBytes = when {
                matchedLineEndBytes == 0 && value == '\r'.code -> 1
                matchedLineEndBytes == 1 && value == '\n'.code -> 2
                matchedLineEndBytes == 2 && value == '\r'.code -> 3
                matchedLineEndBytes == 3 && value == '\n'.code -> 4
                value == '\r'.code -> 1
                else -> 0
            }
            if (matchedLineEndBytes == 4) break
        }
        if (matchedLineEndBytes != 4) throw IOException("ESP HTTP 响应头过长")
        val firstLine = header.toString(Charsets.US_ASCII.name()).lineSequence().firstOrNull().orEmpty()
        val status = firstLine.split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IOException("ESP 返回了无效 HTTP 状态行：$firstLine")
        return LocalHttpResponse(status = status, body = readBytes())
    }

    private data class LocalHttpResponse(
        val status: Int,
        val body: ByteArray
    )

    private companion object {
        const val HTTP_PORT = 80
        const val CONNECT_TIMEOUT_MS = 2_000
        const val READ_TIMEOUT_MS = 5_000
        const val MAX_HEADER_BYTES = 8 * 1024
        const val STATUS_PATH = "/api/v1/range-test/status"
        const val START_PATH = "/api/v1/range-test/start"
        const val STOP_PATH = "/api/v1/range-test/stop"
        const val EVENTS_PATH = "/api/v1/range-test/events"
        const val REPORT_PATH = "/api/v1/range-test/report.csv"
    }
}
