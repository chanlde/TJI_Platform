package com.tji.device.product.speaker.audio

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * 在设备麦克风监听开启时，让媒体 ACK 与回传音频共用同一个稳定 UDP/NAT 端口。
 *
 * 这里只有 socket 端点和按路由分发，不包含媒体状态机或音频缓冲；两条业务链路
 * 仍由各自 client 独立处理。未命中媒体路由的包会返回给回传 client 正常解析。
 */
internal object SpeakerSharedUdpTransport {
    internal data class Route(
        val deviceId: String,
        val sessionId: String,
        val talkId: String
    )

    private data class Endpoint(
        val socket: DatagramSocket,
        val relayAddress: InetAddress,
        val relayPort: Int
    )

    internal class MediaRouteLease internal constructor(
        val socket: DatagramSocket,
        val relayAddress: InetAddress,
        val relayPort: Int,
        val packets: ReceiveChannel<ByteArray>,
        private val route: Route,
        private val channel: Channel<ByteArray>
    ) : AutoCloseable {
        override fun close() {
            SpeakerSharedUdpTransport.release(route, channel)
        }
    }

    private val lock = Any()
    private var endpoint: Endpoint? = null
    private val mediaRoutes = mutableMapOf<Route, Channel<ByteArray>>()

    fun attach(socket: DatagramSocket, relayAddress: InetAddress, relayPort: Int) {
        synchronized(lock) {
            if (endpoint?.socket === socket) return
            closeRoutesLocked()
            endpoint = Endpoint(socket, relayAddress, relayPort)
        }
    }

    fun detach(socket: DatagramSocket) {
        synchronized(lock) {
            if (endpoint?.socket !== socket) return
            endpoint = null
            closeRoutesLocked()
        }
    }

    fun acquireMediaRoute(
        deviceId: String,
        sessionId: String,
        talkId: String,
        relayAddress: InetAddress,
        relayPort: Int
    ): MediaRouteLease? = synchronized(lock) {
        val active = endpoint ?: return@synchronized null
        if (active.socket.isClosed ||
            active.relayAddress != relayAddress ||
            active.relayPort != relayPort
        ) return@synchronized null
        val route = Route(deviceId, sessionId, talkId)
        if (mediaRoutes.containsKey(route)) return@synchronized null
        val channel = Channel<ByteArray>(MEDIA_ROUTE_QUEUE_PACKETS)
        mediaRoutes[route] = channel
        MediaRouteLease(
            socket = active.socket,
            relayAddress = active.relayAddress,
            relayPort = active.relayPort,
            packets = channel,
            route = route,
            channel = channel
        )
    }

    /** @return true 表示包属于活动媒体路由，回传 client 不应再次解析。 */
    fun dispatch(packet: ByteArray, length: Int): Boolean {
        val route = parseRoute(packet, length) ?: return false
        val channel = synchronized(lock) { mediaRoutes[route] } ?: return false
        channel.trySend(packet.copyOf(length))
        return true
    }

    private fun parseRoute(packet: ByteArray, length: Int): Route? {
        if (length <= 0 || length > packet.size) return null
        if (packet.startsWith(APP_ACK_PREFIX, length)) {
            val parts = packet.copyOf(length).toString(Charsets.UTF_8).split(' ')
            if (parts.size != 5 || parts[0] != "HLAPPACK1") return null
            return Route(parts[2], parts[3], parts[4])
        }
        if (length < UDP_FIXED_HEADER_BYTES ||
            packet.readU16(0) != UDP_MAGIC ||
            packet[2].toInt() and 0xFF != UDP_VERSION
        ) return null
        val headerBytes = packet.readU16(4)
        val deviceBytes = packet[24].toInt() and 0xFF
        val sessionBytes = packet[25].toInt() and 0xFF
        val talkBytes = packet[26].toInt() and 0xFF
        if (headerBytes != UDP_FIXED_HEADER_BYTES + deviceBytes + sessionBytes + talkBytes ||
            headerBytes > length
        ) return null
        var offset = UDP_FIXED_HEADER_BYTES
        val deviceId = packet.decodeUtf8(offset, deviceBytes)
        offset += deviceBytes
        val sessionId = packet.decodeUtf8(offset, sessionBytes)
        offset += sessionBytes
        return Route(deviceId, sessionId, packet.decodeUtf8(offset, talkBytes))
    }

    private fun release(route: Route, channel: Channel<ByteArray>) {
        synchronized(lock) {
            if (mediaRoutes[route] === channel) mediaRoutes.remove(route)
        }
        channel.close()
    }

    private fun closeRoutesLocked() {
        mediaRoutes.values.forEach { it.close() }
        mediaRoutes.clear()
    }

    private fun ByteArray.startsWith(prefix: ByteArray, length: Int): Boolean =
        length >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.readU16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.decodeUtf8(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).toString(Charsets.UTF_8)

    private const val UDP_MAGIC = 0xA55A
    private const val UDP_VERSION = 2
    private const val UDP_FIXED_HEADER_BYTES = 28
    private const val MEDIA_ROUTE_QUEUE_PACKETS = 256
    private val APP_ACK_PREFIX = "HLAPPACK1 ".encodeToByteArray()
}
