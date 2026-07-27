package com.tji.network

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.text.decodeToString
import kotlin.text.toByteArray
import kotlin.coroutines.resume

interface MqttClientGateway {
    /**
     * 每次底层 MQTT 会话连接成功时递增。订阅层用它识别 clean-session 重连并恢复订阅。
     * 不管理连接生命周期的测试/替代实现可以保持为 null。
     */
    val connectionEpoch: StateFlow<Long>?
        get() = null

    fun getConfig(): MqttConnectionConfig

    suspend fun subscribeAwait(
        topic: String,
        qos: Int,
        onMessage: (message: String, isRetained: Boolean) -> Unit
    ): Result<Unit>

    suspend fun unsubscribeAwait(topic: String): Result<Unit>

    suspend fun publishAwait(
        topic: String,
        message: String,
        qos: Int = 1,
        retain: Boolean = false,
        queueWhenDisconnected: Boolean
    ): Result<Unit>
}

class MqttManager private constructor(
    private val config: MqttConnectionConfig
) : MqttClientGateway {
    private val _isConnected = MutableStateFlow(false) // 追踪连接状态
    val isConnected: StateFlow<Boolean> = _isConnected
    private val isConnecting = AtomicBoolean(false)
    private val connectionCallbacks =
        MqttConnectionCallbackQueue(MAX_PENDING_CONNECTION_CALLBACKS)
    private val messageSequence = java.util.concurrent.atomic.AtomicLong(0L)
    private val connectionEpochCounter = AtomicLong(0L)
    private val _connectionEpoch = MutableStateFlow(0L)
    override val connectionEpoch: StateFlow<Long> = _connectionEpoch

    @Volatile
    private var connectStartedAt: Long = 0L

    companion object {
        private const val MAX_PENDING_CONNECTION_CALLBACKS = 1_024
        private val INSTANCES = ConcurrentHashMap<String, MqttManager>()

        /**
         * 返回进程内单例。**仅在首次创建时**会使用 [config]（若传入非 null）；后续调用一律忽略 [config]，避免误以为可热切换 Broker。
         * 若需切换 Broker，请显式调用 [reset]。
         */
        fun getInstance(config: MqttConnectionConfig? = null): MqttManager {
            return getInstance(MqttProfiles.PLATFORM, config)
        }

        fun getInstance(profileKey: String, config: MqttConnectionConfig? = null): MqttManager {
            return INSTANCES[profileKey] ?: synchronized(this) {
                INSTANCES[profileKey] ?: run {
                    val finalConfig = config ?: MqttConnectionConfig.default()
                    MqttManager(finalConfig).also { INSTANCES[profileKey] = it }
                }
            }
        }

        fun reset(config: MqttConnectionConfig = MqttConnectionConfig.default()): MqttManager {
            return reset(MqttProfiles.PLATFORM, config)
        }

        fun reset(profileKey: String, config: MqttConnectionConfig = MqttConnectionConfig.default()): MqttManager {
            return synchronized(this) {
                INSTANCES.remove(profileKey)?.disconnect()
                MqttManager(config).also { INSTANCES[profileKey] = it }
            }
        }

        fun disconnectAll() {
            val managers = synchronized(this) {
                INSTANCES.values.toList().also { INSTANCES.clear() }
            }
            managers.forEach { manager ->
                runCatching { manager.disconnect() }
            }
        }
    }
    private val TAG = "MqttManager"

    private val client: Mqtt3AsyncClient = MqttClient.builder()
        .useMqttVersion3()
        .identifier(config.clientId)
        .serverHost(config.serverHost)
        .serverPort(config.serverPort)

        // 重连设置
        .automaticReconnect()
        .initialDelay(1, TimeUnit.SECONDS) // 断线1秒后开始自动重连，如果重连还失败，则下次会等时间会按指数增长，比如2秒、4秒、8秒，双倍增长等待时间，但是不会超过最大值，由maxDelay函数来指定最大值。
        .maxDelay(32, TimeUnit.SECONDS)    // 断线后最多32秒就会自动重连，第5次连会来到32的位置，前面4次已用掉31秒的等待时间了。
        .applyAutomaticReconnect()

        // 连接状态监听器设置
        .addConnectedListener {
            _isConnected.value = true
            _connectionEpoch.value = connectionEpochCounter.incrementAndGet()
            isConnecting.set(false)
            val costMs = if (connectStartedAt > 0L) {
                System.currentTimeMillis() - connectStartedAt
            } else {
                -1L
            }
            debugLog {
                "connected: host=${it.clientConfig.serverHost}:${it.clientConfig.serverPort}, " +
                    "state=${it.clientConfig.state.name}, cost=${costMs}ms"
            }
            drainConnectedCallbacks()
        }
        .addDisconnectedListener {
            _isConnected.value = false
            if (it.clientConfig.state != MqttClientState.CONNECTING_RECONNECT) {
                isConnecting.set(false)
            }

            // 客户端断开连接，或者连接失败都会回调这里
            debugLog {
                "disconnected: host=${it.clientConfig.serverHost}:${it.clientConfig.serverPort}, " +
                    "state=${it.clientConfig.state.name}, cause=${it.cause::class.java.simpleName}, message=${it.cause.message}"
            }
            if (BuildConfig.DEBUG) {
                when (it.clientConfig.state) {
                    MqttClientState.CONNECTING -> Log.d(TAG, "手动连接失败")
                    MqttClientState.CONNECTING_RECONNECT -> Log.d(TAG, "自动重连失败")
                    MqttClientState.CONNECTED -> Log.d(TAG, "连接正常断开或异常断开")
                    else -> Log.d(TAG, "连接断开：${it.clientConfig.state.name}")
                }
            }
        }
        .buildAsync()

    fun connect(
        onConnected: (() -> Unit)? = null,
        onFailed: ((Throwable) -> Unit)? = null,
        requestIsActive: () -> Boolean = { true }
    ) {
        if (!requestIsActive()) return
        val state = client.config.state
        if (isMqttConnected()) {
            debugLog { "already connected, skip connect" }
            _isConnected.value = true
            if (requestIsActive()) onConnected?.invoke()
            return
        }
        if (!connectionCallbacks.offer(onConnected, onFailed, requestIsActive)) {
            val throwable = IllegalStateException(
                "MQTT 连接等待队列已满，拒绝新的离线请求"
            )
            Log.w(TAG, throwable.message.orEmpty())
            onFailed?.invoke(throwable)
            return
        }

        if (!isConnecting.compareAndSet(false, true)) {
            debugLog { "is connecting, queued connect callback: $state" }
            return
        }
        if (state == MqttClientState.CONNECTING || state == MqttClientState.CONNECTING_RECONNECT) {
            debugLog { "client already connecting, queued connect callback: $state" }
            return
        }

        connectStartedAt = System.currentTimeMillis()
        debugLog {
            "connect called: host=${config.serverHost}:${config.serverPort}, " +
                "clientId=${config.clientId}, cleanSession=${config.cleanSession}, " +
                "keepAlive=${config.keepAliveInterval}, qos=${config.qos}, state=${client.config.state.name}"
        }

        client.connectWith()
            .cleanSession(config.cleanSession)
            .keepAlive(config.keepAliveInterval)
            .simpleAuth()
            .username(config.username)
            .password(config.password.toByteArray())
            .applySimpleAuth()
            .send()
            .whenComplete { _, throwable ->
                val costMs = System.currentTimeMillis() - connectStartedAt
                if (throwable != null) {
                    isConnecting.set(false)
                    Log.e(TAG, "MQTT connect failed after ${costMs}ms", throwable)
                    drainFailedCallbacks(throwable)
                } else {
                    isConnecting.set(false)
                    debugLog { "connect future success after ${costMs}ms" }
                    _isConnected.value = true
                    drainConnectedCallbacks()

                }
            }
            .exceptionally { ex ->
                isConnecting.set(false)
                Log.e(TAG, "MQTT connection failed exceptionally", ex)
                null
            }

        debugLog { "connect call sent at $connectStartedAt" }
    }

    fun subscribe(
        topic: String,
        onMessage: (String) -> Unit,
        onMessageWithMeta: ((String, Boolean) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
        onSubscribed: (() -> Unit)? = null,
        qos: Int = config.qos,
        requestIsActive: () -> Boolean = { true }
    ) {
        if (!requestIsActive()) return
        if (!isMqttConnected()) {
            debugLog { "not connected, connect before subscribe: $topic" }
            connect(
                onConnected = {
                    subscribe(
                        topic = topic,
                        onMessage = onMessage,
                        onMessageWithMeta = onMessageWithMeta,
                        onError = onError,
                        onSubscribed = onSubscribed,
                        qos = qos,
                        requestIsActive = requestIsActive
                    )
                },
                onFailed = onError,
                requestIsActive = requestIsActive
            )
            return
        }

        val startAt = System.currentTimeMillis()
        debugLog { "subscribe start: topic=$topic qos=$qos state=${client.config.state.name}" }

        client.toAsync().subscribeWith()
            .topicFilter(topic)
            .qos(qos.toMqttQos())
            .callback { publish ->
                val message = publish.payloadAsBytes.decodeToString()
                if (BuildConfig.DEBUG) {
                    val receiveAt = System.currentTimeMillis()
                    val sequence = messageSequence.incrementAndGet()
                    Log.d(
                        TAG,
                        "message received #$sequence: topic=$topic qos=${publish.qos.code}, " +
                            "retain=${publish.isRetain}, bytes=${publish.payloadAsBytes.size}, " +
                            "receiveAt=$receiveAt"
                    )
                }
                onMessageWithMeta?.invoke(message, publish.isRetain) ?: onMessage(message)
            }
            .send()
            .whenComplete { _, throwable ->
                val costMs = System.currentTimeMillis() - startAt
                if (throwable != null) {
                    Log.e(
                        TAG,
                        "MQTT subscribe failed after ${costMs}ms: " +
                            "topic=$topic, ${throwable.message}",
                        throwable
                    )
                    onError?.invoke(throwable)
                } else if (!requestIsActive()) {
                    client.toAsync().unsubscribeWith()
                        .topicFilter(topic)
                        .send()
                } else {
                    debugLog { "subscribed after ${costMs}ms: topic=$topic qos=$qos" }
                    onSubscribed?.invoke()
                }
            }
    }

    override suspend fun subscribeAwait(
        topic: String,
        qos: Int,
        onMessage: (message: String, isRetained: Boolean) -> Unit
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            connectionCallbacks.pruneInactive()
        }
        subscribe(
            topic = topic,
            qos = qos,
            onMessage = {},
            onMessageWithMeta = onMessage,
            onError = { throwable ->
                if (continuation.isActive) {
                    continuation.resume(Result.failure(throwable))
                }
            },
            onSubscribed = {
                if (continuation.isActive) {
                    continuation.resume(Result.success(Unit))
                }
            },
            requestIsActive = { continuation.isActive }
        )
    }

    fun publish(
        topic: String,
        message: String,
        qos: Int = config.qos,
        retain: Boolean = false,
        queueWhenDisconnected: Boolean = true,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
        requestIsActive: () -> Boolean = { true }
    ) {
        if (!requestIsActive()) return
        if (!isMqttConnected()) {
            debugLog {
                "MQTT not connected before publish: topic=$topic, " +
                    "queueWhenDisconnected=$queueWhenDisconnected, state=${client.config.state.name}"
            }
            if (!queueWhenDisconnected) {
                val throwable = IllegalStateException("MQTT 未连接，实时指令已取消发送")
                debugLog { "MQTT realtime publish dropped: topic=$topic" }
                onError?.invoke(throwable)
                connect()
                return
            }
            connect(
                onConnected = {
                    publish(
                        topic = topic,
                        message = message,
                        qos = qos,
                        retain = retain,
                        queueWhenDisconnected = queueWhenDisconnected,
                        onSuccess = onSuccess,
                        onError = onError,
                        requestIsActive = requestIsActive
                    )
                },
                onFailed = onError,
                requestIsActive = requestIsActive
            )
            return
        }

        val startAt = System.currentTimeMillis()
        debugLog { "publish start: topic=$topic qos=$qos retain=$retain state=${client.config.state.name}" }

        client.publishWith()
            .topic(topic)
            .qos(qos.toMqttQos())
            .retain(retain)
            .payload(message.toByteArray(Charsets.UTF_8))
            .send()
            .whenComplete { _, throwable ->
                val costMs = System.currentTimeMillis() - startAt
                if (throwable != null) {
                    Log.e(TAG, "MQTT publish failed after ${costMs}ms: topic=$topic", throwable)
                    onError?.invoke(throwable)
                } else {
                    debugLog { "publish succeeded after ${costMs}ms: topic=$topic" }
                    onSuccess?.invoke()
                }
            }
    }

    override suspend fun publishAwait(
        topic: String,
        message: String,
        qos: Int,
        retain: Boolean,
        queueWhenDisconnected: Boolean
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            connectionCallbacks.pruneInactive()
        }
        publish(
            topic = topic,
            message = message,
            qos = qos,
            retain = retain,
            queueWhenDisconnected = queueWhenDisconnected,
            onSuccess = {
                if (continuation.isActive) {
                    continuation.resume(Result.success(Unit))
                }
            },
            onError = { throwable ->
                if (continuation.isActive) {
                    continuation.resume(Result.failure(throwable))
                }
            },
            requestIsActive = { continuation.isActive }
        )
    }

    fun unsubscribe(
        topic: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        if (!isMqttConnected()) {
            val throwable = IllegalStateException("MQTT 未连接，无法确认取消订阅")
            Log.d(TAG, throwable.message.orEmpty())
            onError?.invoke(throwable)
            return
        }

        client.toAsync().unsubscribeWith()
            .topicFilter(topic)
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "MQTT unsubscribe failed: topic=$topic", throwable)
                    onError?.invoke(throwable)
                } else {
                    debugLog { "MQTT unsubscribed from $topic" }
                    onSuccess?.invoke()
                }
            }
    }

    override suspend fun unsubscribeAwait(topic: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            unsubscribe(
                topic = topic,
                onSuccess = {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                },
                onError = { throwable ->
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(throwable))
                    }
                }
            )
        }

    fun disconnect(
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        failPendingConnectionRequests()
        if (client.config.state == MqttClientState.DISCONNECTED) {
            debugLog { "MQTT already disconnected" }
            _isConnected.value = false
            isConnecting.set(false)
            onSuccess?.invoke()
            return
        }

        isConnecting.set(false)
        client.disconnect()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "MQTT disconnect failed", throwable)
                    onError?.invoke(throwable)
                } else {
                    debugLog { "MQTT disconnected" }
                    _isConnected.value = false
                    onSuccess?.invoke()
                }
            }
    }

    // 获取当前配置
    override fun getConfig(): MqttConnectionConfig = config

    private fun isMqttConnected(): Boolean =
        _isConnected.value || client.config.state == MqttClientState.CONNECTED

    private fun drainConnectedCallbacks() {
        connectionCallbacks.drainConnected { throwable ->
            Log.d(TAG, "MQTT connected callback failed: ${throwable.message}")
        }
    }

    private fun drainFailedCallbacks(throwable: Throwable) {
        connectionCallbacks.drainFailed(throwable) { callbackFailure ->
            Log.d(TAG, "MQTT failed callback failed: ${callbackFailure.message}")
        }
    }

    private fun failPendingConnectionRequests() {
        drainFailedCallbacks(
            IllegalStateException("MQTT 连接已主动断开，等待中的请求已取消")
        )
    }

    private fun Int.toMqttQos(): MqttQos {
        return when (this) {
            0 -> MqttQos.AT_MOST_ONCE
            2 -> MqttQos.EXACTLY_ONCE
            else -> MqttQos.AT_LEAST_ONCE
        }
    }

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }

}
