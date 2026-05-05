package com.tji.network

import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue

class TcpConnection private constructor() {

    companion object {
        private const val TAG = "TcpConnection"

        @Volatile
        private var INSTANCE: TcpConnection? = null

        fun getInstance(): TcpConnection {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TcpConnection().also { INSTANCE = it }
            }
        }
    }

    // 连接参数
    private var socket: Socket? = null
    private var inputStream: BufferedReader? = null
    private var outputStream: PrintWriter? = null

    // 保存当前连接信息，用于重连
    private var currentHost: String? = null
    private var currentPort: Int? = null
    private var currentTimeout: Int = 5000

    // 连接状态
    @Volatile
    var isConnected: Boolean = false
        private set

    // 协程作用域
    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 消息队列
    private val messageQueue = ConcurrentLinkedQueue<String>()

    // 重连任务
    private var reconnectJob: Job? = null
    private val RECONNECT_INTERVAL = 5000L // 5秒

    // 心跳任务
    private var heartbeatJob: Job? = null
    private var receiveJob: Job? = null

    // 连接监听器
    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onMessageReceived(message: String)
        fun onError(error: String)
    }

    private var connectionListener: ConnectionListener? = null

    fun setConnectionListener(listener: ConnectionListener) {
        this.connectionListener = listener
    }

    /**
     * 连接服务器
     */
    fun connect(host: String, port: Int, timeout: Int = 5000) {
        currentHost = host
        currentPort = port
        currentTimeout = timeout

        if (isConnected) {
            Log.w(TAG, "Already connected")
            return
        }

        attemptConnect()
    }

    /**
     * 内部连接方法
     */
    private fun attemptConnect() {
        connectionScope.launch {
            try {
                val host = currentHost ?: return@launch
                val port = currentPort ?: return@launch
                val timeout = currentTimeout

                Log.i(TAG, "Connecting to $host:$port")
                socket = Socket().apply {
                    soTimeout = timeout
                    connect(InetSocketAddress(host, port), timeout)
                }

                inputStream = BufferedReader(InputStreamReader(socket!!.getInputStream(), StandardCharsets.UTF_8))
                outputStream = PrintWriter(OutputStreamWriter(socket!!.getOutputStream(), StandardCharsets.UTF_8), true)
                isConnected = true

                Log.i(TAG, "Connected successfully")
                withContext(Dispatchers.Main) {
                    connectionListener?.onConnected()
                }

                // 启动接收协程
                startReceiving()

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}", e)
                cleanup()
                withContext(Dispatchers.Main) {
                    connectionListener?.onError("连接失败: ${e.message}")
                }

                // 自动重连
                scheduleReconnect()
            }
        }
    }

    /**
     * 安排重连
     */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true || isConnected) return

        reconnectJob = connectionScope.launch {
            while (!isConnected) {
                delay(RECONNECT_INTERVAL)
                Log.i(TAG, "尝试重连...")
                attemptConnect()
            }
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        cleanup()

        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        receiveJob?.cancel()

        connectionListener?.onDisconnected()

        Log.i(TAG, "Disconnected")

    }

    /**
     * 发送消息
     */
    fun sendMessage(message: String) {
        if (!isConnected) {
            Log.w(TAG, "Not connected, cannot send message")
            connectionListener?.onError("未连接，无法发送消息")
            return
        }

        connectionScope.launch {
            try {
                outputStream?.println(message)
                outputStream?.flush()
                Log.d(TAG, "Message sent: $message")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    connectionListener?.onError("发送消息失败: ${e.message}")
                }

                if (e is SocketException) {
                    cleanup()
                    withContext(Dispatchers.Main) {
                        connectionListener?.onDisconnected()
                    }
                    scheduleReconnect()
                }
            }
        }
    }

    /**
     * 发送字节数据
     */
    fun sendBytes(data: ByteArray) {
        if (!isConnected) {
            Log.w(TAG, "Not connected, cannot send bytes")
            connectionListener?.onError("未连接，无法发送数据")
            return
        }

        connectionScope.launch {
            try {
                socket?.getOutputStream()?.write(data)
                socket?.getOutputStream()?.flush()
                Log.d(TAG, "Bytes sent: ${data.size} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send bytes: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    connectionListener?.onError("发送数据失败: ${e.message}")
                }

                if (e is SocketException) {
                    cleanup()
                    withContext(Dispatchers.Main) {
                        connectionListener?.onDisconnected()
                    }
                    scheduleReconnect()
                }
            }
        }
    }

    /**
     * 启动接收消息协程
     */
    private fun startReceiving() {
        receiveJob=connectionScope.launch(Dispatchers.IO) { // 放IO线程
            try {
                val inputStream = socket?.getInputStream() ?: return@launch
                val buffer = ByteArray(1024) // 字节缓冲区
                while (isConnected) {
                    try {
                        val len = inputStream.read(buffer)
                        if (len > 0) {
                            val message = String(buffer, 0, len, Charsets.UTF_8)
                            Log.d(TAG, "Message received: $message")
                            withContext(Dispatchers.Main) {
                                connectionListener?.onMessageReceived(message)
                            }
                        } else if (len == -1) {
                            Log.w(TAG, "服务器关闭连接")
                            break
                        }
                    } catch (e: SocketTimeoutException) {
                        Log.d(TAG, "读取超时，保持连接")
                    } catch (e: IOException) {
                        Log.e(TAG, "接收异常", e)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "接收协程异常", e)
            } finally {
                if (isConnected) {
                    cleanup()
                    withContext(Dispatchers.Main) {
                        connectionListener?.onDisconnected()
                    }
                    scheduleReconnect()
                }
            }
        }
    }


    /**
     * 启动心跳检测
     */
    fun enableHeartbeat(interval: Long, heartbeatMessage: String) {
        heartbeatJob?.cancel()
        heartbeatJob = connectionScope.launch {
            while (isActive) {
                delay(interval)
                if (isConnected) {
                    sendMessage(heartbeatMessage)
                }
            }
        }
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        isConnected = false
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
        Log.w(TAG, "Nssssssssssssssssssssssssytes")

        inputStream = null
        outputStream = null
        socket = null
        messageQueue.clear()
    }

    fun getConnectionInfo(): String {
        return if (isConnected && socket != null) {
            "Connected to ${socket!!.remoteSocketAddress}"
        } else {
            "Not connected"
        }
    }

    fun destroy() {
        disconnect()
        connectionScope.cancel()
        INSTANCE = null
    }
}
