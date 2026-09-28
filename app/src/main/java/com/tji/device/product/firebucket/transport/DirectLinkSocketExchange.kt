package com.tji.device.product.firebucket.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.tji.device.product.firebucket.protocol.FireBucketCoreFrame
import com.tji.device.product.firebucket.protocol.FireBucketStatusReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * App 到 ESP32 的长连接。ESP32 和两端数传只透明转发字节；本类统一读取 ACK 与吊桶异步状态帧。
 */
class DirectLinkSocketExchange(
    context: Context,
    private val serverPort: Int,
    private val onStatusReport: suspend (FireBucketStatusReport) -> Unit = {},
    private val onConnectionChanged: suspend (Boolean) -> Unit = {},
    private val onDisconnected: suspend () -> Unit = {}
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleLock = Any()
    private val socketLock = Any()
    private val writeLock = Mutex()
    private val connected = MutableStateFlow(false)
    private val pendingAcks = ConcurrentHashMap<Int, CompletableDeferred<ByteArray>>()

    @Volatile
    private var desired = false
    private var connectionJob: Job? = null
    private var socket: Socket? = null

    fun start() {
        synchronized(lifecycleLock) {
            if (desired && connectionJob?.isActive == true) return
            desired = true
            connectionJob = scope.launch { connectionLoop() }
        }
    }

    fun stop() {
        val job = synchronized(lifecycleLock) {
            desired = false
            connectionJob.also { connectionJob = null }
        }
        job?.cancel()
        closeCurrentSocket()
        connected.value = false
        failPending(IOException("数传连接已关闭"))
    }

    suspend fun exchange(request: ByteArray): ByteArray {
        start()
        withTimeout(CONNECTION_WAIT_TIMEOUT_MS) { connected.first { it } }
        val sequence = FireBucketCoreFrame.sequence(request)
        val response = CompletableDeferred<ByteArray>()
        check(pendingAcks.putIfAbsent(sequence, response) == null) { "CoreFrame Seq 正在等待 ACK" }
        try {
            withContext(Dispatchers.IO) {
                writeLock.withLock {
                    val active = synchronized(socketLock) { socket }
                        ?: throw IOException("数传连接不可用")
                    active.getOutputStream().apply {
                        write(request)
                        flush()
                    }
                }
            }
            return withTimeout(ACK_TIMEOUT_MS) { response.await() }
        } catch (error: Exception) {
            if (shouldCloseDirectLinkSocket(error)) closeCurrentSocket()
            throw error
        } finally {
            pendingAcks.remove(sequence, response)
        }
    }

    private suspend fun connectionLoop() {
        while (desired && currentCoroutineContext().isActive) {
            var candidate: Socket? = null
            try {
                val target = resolveDirectLinkWifiTarget(connectivityManager)
                candidate = target.network.socketFactory.createSocket().apply {
                    connect(directLinkSocketAddress(target.gateway, serverPort), CONNECT_TIMEOUT_MS)
                    tcpNoDelay = true
                }
                synchronized(socketLock) { socket = candidate }
                connected.value = true
                onConnectionChanged(true)
                readFrames(candidate)
            } catch (error: Exception) {
                Log.w(TAG, "Direct Link connection failed", error)
            } finally {
                clearSocket(candidate)
                val wasConnected = connected.value
                connected.value = false
                failPending(IOException("数传连接已断开"))
                if (wasConnected) onConnectionChanged(false)
                if (wasConnected && desired) onDisconnected()
            }
            if (desired && currentCoroutineContext().isActive) delay(RECONNECT_DELAY_MS)
        }
    }

    private suspend fun readFrames(active: Socket) {
        val input = active.getInputStream()
        while (desired && currentCoroutineContext().isActive) {
            val frame = input.readCoreFrame()
            val acceptedAsAck = runCatching {
                if (!FireBucketCoreFrame.isAck(frame)) return@runCatching false
                pendingAcks[FireBucketCoreFrame.sequence(frame)]?.complete(frame) == true
            }.getOrDefault(false)
            if (acceptedAsAck) continue
            val status = runCatching { FireBucketCoreFrame.decodeStatusReport(frame) }.getOrNull() ?: continue
            onStatusReport(status)
        }
    }

    private fun clearSocket(candidate: Socket?) {
        synchronized(socketLock) {
            if (socket === candidate) socket = null
        }
        runCatching { candidate?.close() }
    }

    private fun closeCurrentSocket() {
        val current = synchronized(socketLock) { socket.also { socket = null } }
        runCatching { current?.close() }
    }

    private fun failPending(error: Exception) {
        pendingAcks.values.forEach { it.completeExceptionally(error) }
        pendingAcks.clear()
    }

    private fun InputStream.readExactly(byteCount: Int): ByteArray {
        val result = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val count = read(result, offset, byteCount - offset)
            if (count < 0) throw EOFException("Link 在返回完整 CoreFrame 前断开连接")
            offset += count
        }
        return result
    }

    /** UART 可能在 App 建连时正传到半帧；先找统一帧头，再读取完整 CoreFrame。 */
    private fun InputStream.readCoreFrame(): ByteArray {
        var matchedMagicBytes = 0
        while (matchedMagicBytes < 2) {
            val value = read()
            if (value < 0) throw EOFException("Link 已断开连接")
            matchedMagicBytes = when {
                matchedMagicBytes == 0 && value == CORE_FRAME_MAGIC_T -> 1
                matchedMagicBytes == 1 && value == CORE_FRAME_MAGIC_J -> 2
                value == CORE_FRAME_MAGIC_T -> 1
                else -> 0
            }
        }
        val header = byteArrayOf(CORE_FRAME_MAGIC_T.toByte(), CORE_FRAME_MAGIC_J.toByte()) +
            readExactly(CORE_FRAME_HEADER_BYTES - 2)
        val frameLength = FireBucketCoreFrame.frameLengthFromHeader(header)
        require(frameLength <= MAX_DIRECT_FRAME_BYTES) { "Link 返回帧过长" }
        return header + readExactly(frameLength - header.size)
    }

    private companion object {
        const val TAG = "DirectLinkSocket"
        const val CORE_FRAME_HEADER_BYTES = 10
        const val CORE_FRAME_MAGIC_T = 0x54
        const val CORE_FRAME_MAGIC_J = 0x4A
        const val MAX_DIRECT_FRAME_BYTES = 4_364
        const val CONNECT_TIMEOUT_MS = 2_000
        const val CONNECTION_WAIT_TIMEOUT_MS = 3_000L
        const val ACK_TIMEOUT_MS = 3_000L
        const val RECONNECT_DELAY_MS = 1_000L
    }
}

/** 单条命令取消或 ACK 超时不代表长连接损坏；只有底层 I/O 错误才重建 Socket。 */
internal fun shouldCloseDirectLinkSocket(error: Throwable): Boolean = error is IOException

internal fun selectIpv4DefaultGateway(routes: List<GatewayRouteCandidate>): InetAddress? = routes
    .firstOrNull { route -> route.isDefaultRoute && route.gateway is Inet4Address }
    ?.gateway

internal fun directLinkSocketAddress(gateway: InetAddress, serverPort: Int): InetSocketAddress =
    InetSocketAddress(gateway, serverPort)

internal data class GatewayRouteCandidate(
    val isDefaultRoute: Boolean,
    val gateway: InetAddress?
)

internal data class DirectLinkWifiTarget(
    val network: Network,
    val gateway: InetAddress
)

/**
 * 直连控制 TCP 与 ESP 的本地调试 HTTP 共用同一 Wi-Fi 网络选择规则。
 * 显式绑定 Android Network，避免手机同时开着蜂窝数据时把请求发到错误的默认网络。
 */
@Suppress("DEPRECATION")
internal fun resolveDirectLinkWifiTarget(
    connectivityManager: ConnectivityManager
): DirectLinkWifiTarget {
    val activeNetwork = connectivityManager.activeNetwork
    val candidateNetworks = buildList {
        activeNetwork?.let(::add)
        addAll(connectivityManager.allNetworks.filterNot { it == activeNetwork })
    }
    var wifiFound = false

    candidateNetworks.forEach { network ->
        val isWifi = connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!isWifi) return@forEach
        wifiFound = true

        val gateway = connectivityManager.getLinkProperties(network)
            ?.routes
            ?.map { route ->
                GatewayRouteCandidate(
                    isDefaultRoute = route.isDefaultRoute,
                    gateway = route.gateway
                )
            }
            ?.let(::selectIpv4DefaultGateway)
            ?: return@forEach
        return DirectLinkWifiTarget(network = network, gateway = gateway)
    }

    error(
        if (wifiFound) {
            "当前 Wi-Fi 未提供数传网关地址"
        } else {
            "请先连接数传设备 Wi-Fi"
        }
    )
}
