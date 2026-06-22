package com.tji.network

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.also
import kotlin.run
import kotlin.text.decodeToString
import kotlin.text.toByteArray

class MqttManager private constructor(private val config: MQTTConfig) {
    private val _isConnected = MutableStateFlow(false) // 追踪连接状态
    val isConnected: StateFlow<Boolean> = _isConnected
    private val isConnecting = AtomicBoolean(false)
    private val pendingConnectedCallbacks = ConcurrentLinkedQueue<() -> Unit>()
    private val pendingFailedCallbacks = ConcurrentLinkedQueue<(Throwable) -> Unit>()
    private val messageSequence = java.util.concurrent.atomic.AtomicLong(0L)
    @Volatile
    private var connectStartedAt: Long = 0L
    companion object {
        private val INSTANCES = ConcurrentHashMap<String, MqttManager>()

        /**
         * 返回进程内单例。**仅在首次创建时**会使用 [config]（若传入非 null）；后续调用一律忽略 [config]，避免误以为可热切换 Broker。
         * 若需在登录后使用用户侧 [MQTTConfig]，请在应用冷启动或明确「首次」调用时传入。
         */
        fun getInstance(config: MQTTConfig? = null): MqttManager {
            return getInstance(MqttProfiles.PLATFORM, config)
        }

        fun getInstance(profileKey: String, config: MQTTConfig? = null): MqttManager {
            return INSTANCES[profileKey] ?: synchronized(this) {
                INSTANCES[profileKey] ?: run {
                    val finalConfig = config ?: MQTTConfig.default()
                    MqttManager(finalConfig).also { INSTANCES[profileKey] = it }
                }
            }
        }

        fun getInstance(): MqttManager {
            return getInstance(null)
        }

        fun reset(config: MQTTConfig = MQTTConfig.default()): MqttManager {
            return reset(MqttProfiles.PLATFORM, config)
        }

        fun reset(profileKey: String, config: MQTTConfig = MQTTConfig.default()): MqttManager {
            return synchronized(this) {
                INSTANCES[profileKey]?.client?.disconnect()
                MqttManager(config).also { INSTANCES[profileKey] = it }
            }
        }

        fun disconnectAll() {
            INSTANCES.values.forEach { manager ->
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
            _isConnected.value = true  // ✅ 添加这行
            isConnecting.set(false)
            val costMs = if (connectStartedAt > 0L) {
                System.currentTimeMillis() - connectStartedAt
            } else {
                -1L
            }
            Log.d(
                TAG,
                "MQTT connected listener: host=${it.clientConfig.serverHost}:${it.clientConfig.serverPort}, " +
                        "state=${it.clientConfig.state.name}, cost=${costMs}ms"
            )
            drainConnectedCallbacks()
        }
        .addDisconnectedListener {
            _isConnected.value = false    // ✅ 添加这行
            if (it.clientConfig.state != MqttClientState.CONNECTING_RECONNECT) {
                isConnecting.set(false)
            }

            // 客户端断开连接，或者连接失败都会回调这里
            Log.d(
                TAG,
                "MQTT disconnected listener: host=${it.clientConfig.serverHost}:${it.clientConfig.serverPort}, " +
                        "state=${it.clientConfig.state.name}, cause=${it.cause::class.java.simpleName}, message=${it.cause.message}"
            )
            when (it.clientConfig.state) {
                MqttClientState.CONNECTING ->  Log.d(TAG,"手动连接失败")             // 即主动调用connect时没连接成功
                MqttClientState.CONNECTING_RECONNECT ->  Log.d(TAG,"自动重连失败")   // 即连接成功后异常断开自动重连时连接失败
                MqttClientState.CONNECTED ->  Log.d(TAG,"连接正常断开或异常断开")
                else ->  Log.d(TAG,"连接断开：${it.clientConfig.state.name}")
            }
        }
        .buildAsync()

    fun connect(
        onConnected: (() -> Unit)? = null,
        onFailed: ((Throwable) -> Unit)? = null
    ) {
        val state = client.config.state
        if (isMqttConnected()) {
            Log.d(TAG, "MQTT already connected, skip connect")
            _isConnected.value = true
            onConnected?.invoke()
            return
        }
        onConnected?.let { pendingConnectedCallbacks.add(it) }
        onFailed?.let { pendingFailedCallbacks.add(it) }

        if (!isConnecting.compareAndSet(false, true)) {
            Log.d(TAG, "MQTT is connecting, queued connect callback: $state")
            return
        }
        if (state == MqttClientState.CONNECTING || state == MqttClientState.CONNECTING_RECONNECT) {
            Log.d(TAG, "MQTT client already connecting, queued connect callback: $state")
            return
        }

        connectStartedAt = System.currentTimeMillis()
        Log.d(
            TAG,
            "MQTT connect called: host=${config.serverHost}:${config.serverPort}, " +
                    "clientId=${config.clientId}, username=${config.username}, cleanSession=${config.cleanSession}, " +
                    "keepAlive=${config.keepAliveInterval}, qos=${config.qos}, state=${client.config.state.name}"
        )

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
                    Log.d(TAG,"MQTT connect failed after ${costMs}ms: ${throwable::class.java.simpleName}, ${throwable.message}")
                    drainFailedCallbacks(throwable)
                } else {
                    isConnecting.set(false)
                    Log.d(TAG,"MQTT connect future success after ${costMs}ms")
                    _isConnected.value = true
                    drainConnectedCallbacks()

                }
            }
            .exceptionally { ex ->
                isConnecting.set(false)
                 Log.d(TAG,"MQTT connection exceptionally failed: ${ex.message}")
                null
            }

         Log.d(TAG,"MQTT connect call sent at $connectStartedAt")
    }

    fun subscribe(
        topic: String,
        onMessage: (String) -> Unit,
        onMessageWithMeta: ((String, Boolean) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
        onSubscribed: (() -> Unit)? = null,
        qos: Int = config.qos
    ) {
        if (!isMqttConnected()) {
            Log.d(TAG, "MQTT not connected, connect before subscribe: $topic")
            connect(
                onConnected = {
                    subscribe(
                        topic = topic,
                        onMessage = onMessage,
                        onMessageWithMeta = onMessageWithMeta,
                        onError = onError,
                        onSubscribed = onSubscribed,
                        qos = qos
                    )
                },
                onFailed = onError
            )
            return
        }

        val startAt = System.currentTimeMillis()
        Log.d(TAG, "MQTT subscribe start: topic=$topic qos=$qos state=${client.config.state.name}")

        client.toAsync().subscribeWith()
            .topicFilter(topic)
            .qos(qos.toMqttQos())
            .callback { publish ->
                val receiveAt = System.currentTimeMillis()
                val seq = messageSequence.incrementAndGet()
                val message = publish.payloadAsBytes.decodeToString()
                Log.d(
                    TAG,
                    "MQTT message received #$seq: topic=$topic qos=${publish.qos.code}, " +
                            "retain=${publish.isRetain}, bytes=${publish.payloadAsBytes.size}, receiveAt=$receiveAt"
                )
                onMessageWithMeta?.invoke(message, publish.isRetain) ?: onMessage(message)
            }
            .send()
            .whenComplete { _, throwable ->
                val costMs = System.currentTimeMillis() - startAt
                if (throwable != null) {
                     Log.d(TAG,"MQTT subscribe failed after ${costMs}ms: topic=$topic, ${throwable.message}")
                    onError?.invoke(throwable)
                } else {
	                     Log.d(TAG,"MQTT subscribed after ${costMs}ms: topic=$topic qos=$qos")
                    onSubscribed?.invoke()
                }
            }
    }

    fun publish(
        topic: String,
        message: String,
        qos: Int = config.qos,
        retain: Boolean = false,
        queueWhenDisconnected: Boolean = true,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        if (!isMqttConnected()) {
            Log.d(
                TAG,
                "MQTT not connected before publish: topic=$topic, queueWhenDisconnected=$queueWhenDisconnected, state=${client.config.state.name}"
            )
            if (!queueWhenDisconnected) {
                val throwable = IllegalStateException("MQTT 未连接，实时指令已取消发送")
                Log.d(TAG, "MQTT realtime publish dropped: topic=$topic")
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
                        onError = onError
                    )
                },
                onFailed = onError
            )
            return
        }

        val startAt = System.currentTimeMillis()
        Log.d(TAG, "MQTT publish start: topic=$topic qos=$qos retain=$retain state=${client.config.state.name}")

        client.publishWith()
            .topic(topic)
            .qos(qos.toMqttQos())
            .retain(retain)
            .payload(message.toByteArray(Charsets.UTF_8))
            .send()
            .whenComplete { _, throwable ->
                val costMs = System.currentTimeMillis() - startAt
                if (throwable != null) {
                     Log.d(TAG,"MQTT publish failed after ${costMs}ms: ${throwable.message}")
                    onError?.invoke(throwable)
                } else {
                     Log.d(TAG,"MQTT publish succeeded after ${costMs}ms: topic=$topic")
                    onSuccess?.invoke()
                }
            }
    }
    fun unsubscribe(topic: String) {
        if (!isMqttConnected()) {
            Log.d(TAG, "MQTT not connected, cannot unsubscribe.")
            return
        }

        client.toAsync().unsubscribeWith()
            .topicFilter(topic)
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.d(TAG, "MQTT unsubscribe failed: ${throwable.message}")
                } else {
                    Log.d(TAG, "MQTT unsubscribed from $topic")
                }
            }
    }

    fun disconnect(
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        if (!isMqttConnected()) {
             Log.d(TAG,"MQTT not connected, skip disconnect.")
             onSuccess?.invoke()
            return
        }

        client.disconnect()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                     Log.d(TAG,"MQTT disconnect failed: ${throwable.message}")
                    onError?.invoke(throwable)
                } else {
                     Log.d(TAG,"MQTT disconnected")
                    _isConnected.value = false
                    onSuccess?.invoke()
                }
            }
    }

    // 获取当前配置
    fun getConfig(): MQTTConfig = config

    private fun isMqttConnected(): Boolean =
        _isConnected.value || client.config.state == MqttClientState.CONNECTED

    private fun drainConnectedCallbacks() {
        while (true) {
            val callback = pendingConnectedCallbacks.poll() ?: break
            runCatching { callback() }
                .onFailure { Log.d(TAG, "MQTT connected callback failed: ${it.message}") }
        }
        pendingFailedCallbacks.clear()
    }

    private fun drainFailedCallbacks(throwable: Throwable) {
        while (true) {
            val callback = pendingFailedCallbacks.poll() ?: break
            runCatching { callback(throwable) }
                .onFailure { Log.d(TAG, "MQTT failed callback failed: ${it.message}") }
        }
        pendingConnectedCallbacks.clear()
    }

    private fun Int.toMqttQos(): MqttQos {
        return when (this) {
            0 -> MqttQos.AT_MOST_ONCE
            2 -> MqttQos.EXACTLY_ONCE
            else -> MqttQos.AT_LEAST_ONCE
        }
    }
	}
