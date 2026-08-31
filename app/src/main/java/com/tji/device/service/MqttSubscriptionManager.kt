package com.tji.device.service

import android.util.Log
import com.tji.device.BuildConfig
import com.tji.device.data.model.ProductType
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttTopics
import com.tji.device.service.mqtt.ProductMqttRouter
import com.tji.device.service.mqtt.mqttTopicsFor
import com.tji.network.MqttClientGateway
import com.tji.network.MqttStatusMessageClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class SubscriptionTarget(
    val serialNumber: String,
    val productType: ProductType
)

/**
 * 维护 MQTT 的实际订阅状态。
 *
 * 本地状态只在 SUBACK/UNSUBACK 成功后更新；清理期间通过 generation 让晚到的订阅结果失效。
 * 同一优先级内保持接收顺序；QoS1 生命周期/ACK 优先于 QoS0 遥测，避免遥测洪峰拖延控制反馈。
 */
class MqttSubscriptionManager(
    private val mqttEventHandler: MqttMessageHandler,
    private val clientFor: (ProductType) -> MqttClientGateway = {
        ProductMqttRouter.managerFor(it)
    }
) {
    private val subscribedTopics = ConcurrentHashMap.newKeySet<String>()
    private val inFlightTopics = ConcurrentHashMap.newKeySet<String>()
    private val subscribedDevices = ConcurrentHashMap.newKeySet<SubscriptionTarget>()
    private val activeMessageGenerations = ConcurrentHashMap<SubscriptionTarget, Long>()
    private val targetLocks = Array(SUBSCRIPTION_LOCK_STRIPES) { Mutex() }
    private val messageChannels = ConcurrentHashMap<SubscriptionTarget, InboundMessageQueue>()
    private val messageConsumerJobs = ConcurrentHashMap<SubscriptionTarget, Job>()
    private val desiredTargets = ConcurrentHashMap.newKeySet<SubscriptionTarget>()
    private val connectionObserverJobs = ConcurrentHashMap<MqttClientGateway, Job>()
    private val reconciliationMutex = Mutex()
    private val subscriptionStateLock = Any()
    private val subscriptionGeneration = AtomicLong(0L)
    private var messageScope = newMessageScope()

    suspend fun subscribeToDevices(deviceIds: List<String>, productType: ProductType) {
        val requestedGeneration = subscriptionGeneration.get()
        reconciliationMutex.withLock {
            ensureCurrentGeneration(requestedGeneration)
            subscribeToDevicesUnlocked(deviceIds, productType)
            ensureCurrentGeneration(requestedGeneration)
        }
    }

    private suspend fun subscribeToDevicesUnlocked(
        deviceIds: List<String>,
        productType: ProductType
    ) = coroutineScope {
            deviceIds.distinct().map { serialNumber ->
                async {
                    subscribeToDevice(SubscriptionTarget(serialNumber, productType))
                }
            }.awaitAll()
        }

    suspend fun unsubscribeFromTargets(targets: List<SubscriptionTarget>) {
        val requestedGeneration = subscriptionGeneration.get()
        reconciliationMutex.withLock {
            ensureCurrentGeneration(requestedGeneration)
            unsubscribeFromTargetsUnlocked(targets)
            ensureCurrentGeneration(requestedGeneration)
        }
    }

    private suspend fun unsubscribeFromTargetsUnlocked(
        targets: List<SubscriptionTarget>
    ) = coroutineScope {
            targets.distinct().map { target ->
                async {
                    lockFor(target).withLock {
                        unsubscribeTargetUnlocked(target)
                    }
                }
            }.awaitAll()
        }

    suspend fun reconcileSubscriptions(desiredTargets: Collection<SubscriptionTarget>) {
        val requestedGeneration = subscriptionGeneration.get()
        val desired = desiredTargets.toSet()
        reconciliationMutex.withLock {
            prepareDesiredTargets(desired, requestedGeneration)
            reconcileSubscriptionsUnlocked(desired)
            ensureCurrentGeneration(requestedGeneration)
        }
    }

    private suspend fun reconcileSubscriptionsUnlocked(desired: Set<SubscriptionTarget>) {
        val current = subscribedDevices.toSet()
        val toUnsubscribe = current - desired
        if (toUnsubscribe.isNotEmpty()) {
            unsubscribeFromTargetsUnlocked(toUnsubscribe.toList())
        }

        val remaining = subscribedDevices.toSet()
        (desired - remaining)
            .groupBy { it.productType }
            .forEach { (productType, targets) ->
                subscribeToDevicesUnlocked(
                    deviceIds = targets.map { it.serialNumber },
                    productType = productType
                )
            }
    }

    suspend fun clearAllSubscriptions() = reconciliationMutex.withLock {
        beginSubscriptionReset()
        cancelAllMessageConsumersAndJoin()
        val targets = subscribedDevices.toList()
        val failures = try {
            withContext(NonCancellable) {
                coroutineScope {
                    targets.map { target ->
                        async {
                            runCatching {
                                withTimeout(SUBSCRIPTION_OPERATION_TIMEOUT_MILLIS) {
                                    lockFor(target).withLock {
                                        unsubscribeTargetUnlocked(target)
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }
            }.mapNotNull { it.exceptionOrNull() }
        } finally {
            // Local session state must never survive a reset, even if its
            // caller is canceled while the broker cleanup is in flight.
            forgetAllSubscriptionsLocally()
        }

        // Preserve structured cancellation after the bounded cleanup has
        // restored broker/local consistency.
        currentCoroutineContext().ensureActive()
        if (failures.isNotEmpty()) {
            throw IllegalStateException(
                "部分 MQTT 订阅未能取消 (${failures.size}/${targets.size})",
                failures.first()
            )
        }
        debugLog { "所有 MQTT 订阅已清空" }
    }

    private fun observeConnection(client: MqttClientGateway) {
        val epochs = client.connectionEpoch ?: return
        connectionObserverJobs.computeIfAbsent(client) {
            messageScope.launch {
                var lastEpoch = epochs.value
                epochs.collect { epoch ->
                    if (epoch <= lastEpoch) return@collect
                    val isReconnect = lastEpoch > 0L
                    lastEpoch = epoch
                    if (isReconnect) {
                        restoreSubscriptionsAfterReconnect(client, epoch)
                    }
                }
            }
        }
    }

    private suspend fun restoreSubscriptionsAfterReconnect(
        client: MqttClientGateway,
        epoch: Long
    ) {
        val requestedGeneration = subscriptionGeneration.get()
        reconciliationMutex.withLock {
            ensureCurrentGeneration(requestedGeneration)
            val desiredForClient = desiredTargets
                .filter { clientFor(it.productType) === client }
                .toSet()
            if (desiredForClient.isEmpty()) return

            val knownForClient = (subscribedDevices + desiredForClient)
                .filter { clientFor(it.productType) === client }
                .toSet()
            knownForClient.forEach { target -> forgetTargetLocally(target) }
            debugLog {
                "MQTT 会话已重连，恢复订阅: epoch=$epoch targets=${desiredForClient.size}"
            }
            reconcileSubscriptionsUnlocked(desiredForClient)
            ensureCurrentGeneration(requestedGeneration)
        }
    }

    fun getSubscribedDevices(productType: ProductType? = null): List<String> =
        subscribedDevices
            .filter { productType == null || it.productType == productType }
            .map { it.serialNumber }
            .distinct()

    fun getSubscribedTargets(): List<SubscriptionTarget> = subscribedDevices.toList()

    private suspend fun subscribeToDevice(target: SubscriptionTarget) {
        lockFor(target).withLock {
            if (target in subscribedDevices) return
            val generation = subscriptionGeneration.get()
            val client = clientFor(target.productType)
            val topics = subscriptionTopicsFor(target)
            val statusTopics = mqttTopicsFor(target.productType)
                .statusTopics(target.serialNumber)
                .toSet()
            val newlySubscribed = mutableListOf<Pair<String, String>>()

            try {
                if (!activateMessageTarget(target, generation)) {
                    throw subscriptionLifecycleReset()
                }
                topics.forEach { (topic, qos) ->
                    val key = subscriptionKey(topic, target.productType)
                    if (key in subscribedTopics) return@forEach

                    check(inFlightTopics.add(key)) {
                        "订阅已在进行中: target=$target topic=$topic"
                    }
                    val result = try {
                        withTimeout(SUBSCRIPTION_OPERATION_TIMEOUT_MILLIS) {
                            client.subscribeAwait(
                                topic = topic,
                                qos = qos,
                                onMessage = { message, isRetained ->
                                    enqueueMessage(
                                        target = target,
                                        message = message,
                                        isRetained = isRetained,
                                        reliable = qos > 0 && (
                                            topic !in statusTopics ||
                                                isPriorityStatusMessage(message)
                                            )
                                    )
                                }
                            )
                        }
                    } finally {
                        inFlightTopics.remove(key)
                    }
                    result.getOrThrow()
                    newlySubscribed += topic to key
                    if (!commitSubscribedTopic(key, generation)) {
                        throw subscriptionLifecycleReset()
                    }
                    debugLog { "订阅成功: target=$target topic=$topic qos=$qos" }
                }

                if (!commitSubscribedTarget(target, topics, generation)) {
                    throw subscriptionLifecycleReset()
                }
            } catch (throwable: Throwable) {
                activeMessageGenerations.remove(target, generation)
                // Cancellation can arrive after one topic has received SUBACK.
                // Roll back those confirmed subscriptions outside the canceled
                // request context so the broker cannot retain ghost subscriptions.
                withContext(NonCancellable) {
                    newlySubscribed.forEach { (topic, key) ->
                        runCatching {
                            withTimeout(SUBSCRIPTION_OPERATION_TIMEOUT_MILLIS) {
                                client.unsubscribeAwait(topic).getOrThrow()
                            }
                        }.onFailure {
                            Log.e(TAG, "回滚部分订阅失败: product=${target.productType}", it)
                        }
                        subscribedTopics.remove(key)
                    }
                }
                closeMessageChannelAndJoin(target)
                throw throwable
            }
        }
    }

    private suspend fun unsubscribeTargetUnlocked(target: SubscriptionTarget) {
        val client = clientFor(target.productType)
        subscriptionTopicsFor(target).forEach { (topic, _) ->
            val key = subscriptionKey(topic, target.productType)
            if (key !in subscribedTopics) return@forEach
            client.unsubscribeAwait(topic).getOrThrow()
            subscribedTopics.remove(key)
            debugLog { "取消订阅成功: target=$target topic=$topic" }
        }
        subscribedDevices.remove(target)
        activeMessageGenerations.remove(target)
        closeMessageChannelAndJoin(target)
    }

    private fun enqueueMessage(
        target: SubscriptionTarget,
        message: String,
        isRetained: Boolean,
        reliable: Boolean
    ) {
        val queue = synchronized(subscriptionStateLock) {
            val activeGeneration = activeMessageGenerations[target]
            if (
                activeGeneration == null ||
                activeGeneration != subscriptionGeneration.get()
            ) {
                return
            }
            channelFor(target)
        }
        val result = if (reliable) {
            queue.reliable.trySend(InboundMessage(message, isRetained))
        } else {
            queue.telemetry.trySend(InboundMessage(message, isRetained))
        }
        if (result.isFailure) {
            Log.w(TAG, "MQTT 入站队列已关闭，丢弃消息: product=${target.productType}")
        }
    }

    private fun channelFor(target: SubscriptionTarget): InboundMessageQueue =
        messageChannels.computeIfAbsent(target) {
            InboundMessageQueue(
                reliable = Channel(
                    capacity = MAX_PENDING_RELIABLE_PER_DEVICE,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                ),
                telemetry = Channel(
                    capacity = MAX_PENDING_TELEMETRY_PER_DEVICE,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
            ).also { queue ->
                val consumerJob = messageScope.launch {
                    var reliableOpen = true
                    var telemetryOpen = true
                    while (reliableOpen || telemetryOpen) {
                        val message = select<InboundMessage?> {
                            if (reliableOpen) {
                                queue.reliable.onReceiveCatching { result ->
                                    if (result.isClosed) reliableOpen = false
                                    result.getOrNull()
                                }
                            }
                            if (telemetryOpen) {
                                queue.telemetry.onReceiveCatching { result ->
                                    if (result.isClosed) telemetryOpen = false
                                    result.getOrNull()
                                }
                            }
                        } ?: continue
                        try {
                            mqttEventHandler.handleMessage(
                                serialNumber = target.serialNumber,
                                productType = target.productType,
                                message = message.payload,
                                isRetained = message.isRetained
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Throwable) {
                            Log.e(TAG, "MQTT 消息处理失败: product=${target.productType}", throwable)
                        }
                    }
                }
                messageConsumerJobs[target] = consumerJob
                consumerJob.invokeOnCompletion {
                    messageConsumerJobs.remove(target, consumerJob)
                }
            }
        }

    private suspend fun closeMessageChannelAndJoin(target: SubscriptionTarget) {
        messageChannels.remove(target)?.close()
        messageConsumerJobs.remove(target)?.cancelAndJoin()
    }

    private suspend fun forgetTargetLocally(target: SubscriptionTarget) {
        synchronized(subscriptionStateLock) {
            subscriptionTopicsFor(target).forEach { (topic, _) ->
                subscribedTopics.remove(subscriptionKey(topic, target.productType))
            }
            subscribedDevices.remove(target)
            activeMessageGenerations.remove(target)
        }
        closeMessageChannelAndJoin(target)
    }

    private suspend fun cancelAllMessageConsumersAndJoin() {
        val jobs = synchronized(subscriptionStateLock) {
            messageChannels.values.forEach { it.close() }
            messageChannels.clear()
            messageConsumerJobs.values.toList().also { messageConsumerJobs.clear() }
        }
        jobs.forEach { it.cancelAndJoin() }
    }

    private fun forgetAllSubscriptionsLocally() {
        synchronized(subscriptionStateLock) {
            forgetAllSubscriptionsLocallyLocked()
        }
    }

    fun cleanup() {
        synchronized(subscriptionStateLock) {
            forgetAllSubscriptionsLocallyLocked()
            desiredTargets.clear()
            connectionObserverJobs.values.forEach { it.cancel() }
            connectionObserverJobs.clear()
            messageScope.cancel()
            messageScope = newMessageScope()
            mqttEventHandler.cleanup()
            // Publish the new generation only after every reusable component
            // has been reset. Requests started during cleanup captured the old
            // generation and will be rejected.
            subscriptionGeneration.incrementAndGet()
        }
    }

    private fun forgetAllSubscriptionsLocallyLocked() {
        messageChannels.values.forEach { it.close() }
        messageChannels.clear()
        messageConsumerJobs.values.forEach { it.cancel() }
        messageConsumerJobs.clear()
        subscribedTopics.clear()
        inFlightTopics.clear()
        subscribedDevices.clear()
        activeMessageGenerations.clear()
    }

    private fun beginSubscriptionReset() {
        synchronized(subscriptionStateLock) {
            desiredTargets.clear()
            connectionObserverJobs.values.forEach { it.cancel() }
            connectionObserverJobs.clear()
            subscriptionGeneration.incrementAndGet()
        }
    }

    private fun prepareDesiredTargets(
        targets: Set<SubscriptionTarget>,
        generation: Long
    ) {
        synchronized(subscriptionStateLock) {
            ensureCurrentGeneration(generation)
            desiredTargets.clear()
            desiredTargets.addAll(targets)
            val requiredClients = targets
                .mapTo(mutableSetOf()) { clientFor(it.productType) }
            connectionObserverJobs.keys
                .filterNot(requiredClients::contains)
                .forEach { unusedClient ->
                    connectionObserverJobs.remove(unusedClient)?.cancel()
                }
            requiredClients.forEach(::observeConnection)
        }
    }

    internal fun connectionObserverCount(): Int = connectionObserverJobs.size

    private fun ensureCurrentGeneration(expected: Long) {
        if (subscriptionGeneration.get() != expected) {
            throw subscriptionLifecycleReset()
        }
    }

    private fun commitSubscribedTopic(key: String, generation: Long): Boolean =
        synchronized(subscriptionStateLock) {
            if (subscriptionGeneration.get() != generation) {
                false
            } else {
                subscribedTopics.add(key)
                true
            }
        }

    private fun activateMessageTarget(
        target: SubscriptionTarget,
        generation: Long
    ): Boolean = synchronized(subscriptionStateLock) {
        if (subscriptionGeneration.get() != generation) {
            false
        } else {
            activeMessageGenerations[target] = generation
            true
        }
    }

    private fun commitSubscribedTarget(
        target: SubscriptionTarget,
        topics: List<Pair<String, Int>>,
        generation: Long
    ): Boolean = synchronized(subscriptionStateLock) {
        if (subscriptionGeneration.get() != generation) {
            return@synchronized false
        }
        check(
            topics.all { (topic, _) ->
                subscriptionKey(topic, target.productType) in subscribedTopics
            }
        ) {
            "订阅主题未完整提交: target=$target"
        }
        subscribedDevices.add(target)
        true
    }

    private fun subscriptionLifecycleReset(): CancellationException =
        CancellationException("MQTT 订阅生命周期已重置")

    private fun lockFor(target: SubscriptionTarget): Mutex =
        targetLocks[(target.hashCode() and Int.MAX_VALUE) % targetLocks.size]

    private fun newMessageScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun subscriptionTopicsFor(target: SubscriptionTarget): List<Pair<String, Int>> {
        val layout = mqttTopicsFor(target.productType)
        return buildList {
            layout.lifecycleTopics(target.serialNumber).forEach { add(it to 1) }
            // ACK 与遥测共用 status topic。订阅 QoS 1 才能保留设备发布的 ACK
            // 可靠性；入站后再按 payload 分类，周期 state 仍走普通遥测队列。
            layout.statusTopics(target.serialNumber).forEach { add(it to 1) }
            if (target.productType == ProductType.RadioDetection) {
                add(RadioDetectionMqttTopics.rgbAckTopic(target.serialNumber) to 1)
            }
        }
    }

    private fun subscriptionKey(topic: String, productType: ProductType): String =
        "${ProductMqttRouter.profileKeyFor(productType)}::$topic"

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    private data class InboundMessage(
        val payload: String,
        val isRetained: Boolean
    )

    private data class InboundMessageQueue(
        val reliable: Channel<InboundMessage>,
        val telemetry: Channel<InboundMessage>
    ) {
        fun close() {
            reliable.close()
            telemetry.close()
        }
    }

    private companion object {
        const val TAG = "MqttSubscriptionManager"
        // 可靠消息通常只有生命周期和 ACK。异常积压时保留最新状态，避免无限队列导致进程 OOM。
        const val MAX_PENDING_RELIABLE_PER_DEVICE = 256
        const val MAX_PENDING_TELEMETRY_PER_DEVICE = 256
        const val SUBSCRIPTION_OPERATION_TIMEOUT_MILLIS = 15_000L
        const val SUBSCRIPTION_LOCK_STRIPES = 64
    }
}

internal fun isPriorityStatusMessage(message: String): Boolean {
    return MqttStatusMessageClassifier.isPriority(message)
}
