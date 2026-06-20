package com.tji.device.service

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttTopics
import com.tji.device.service.mqtt.ProductMqttRouter
import com.tji.device.service.mqtt.mqttTopicsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

data class SubscriptionTarget(
    val serialNumber: String,
    val productType: ProductType
)

/**
 * MQTT 订阅管理器
 *
 * 订阅时由调用方传入该产品线下的 [ProductType]，并写入 deviceId 映射；入站消息直接带此类型分发，不再在 Handler 内推断。
 */
class MqttSubscriptionManager(
    private val mqttEventHandler: MqttEventHandler,
) {
    private val subscribedTopics = ConcurrentHashMap.newKeySet<String>()
    private val subscribedDevices = ConcurrentHashMap.newKeySet<SubscriptionTarget>()
    private var messageScope = newMessageScope()
    /** 订阅时登记的「此 deviceId 当前属于哪条产品线」 */
    private val subscriptionProductBySerial = ConcurrentHashMap<String, ProductType>()
    /** 同一 deviceId 上串行「退订 / 强制重订」，避免与 `async`、重连叠加以致 unsubscribe/subscribe 交错重复。 */
    private val subscriptionTargetLocks = ConcurrentHashMap<String, Mutex>()
    private fun lockForTarget(serialNumber: String, productType: ProductType): Mutex =
        subscriptionTargetLocks.getOrPut("${productType.name}:$serialNumber") { Mutex() }

    private val TAG = "MqttSubscriptionManager"

    /**
     * @param productType 本次列表中所有 deviceId 所属产品线（与 `openProduct`、登录下发的绑定设备一致）
     */
    suspend fun subscribeToDevices(deviceIds: List<String>, productType: ProductType) =
        coroutineScope {
            deviceIds.map { serialNumber ->
                async {
                    subscribeToDevice(serialNumber, productType)
                }
            }.awaitAll()
        }

    suspend fun unsubscribeFromDevices(deviceIds: List<String>) = coroutineScope {
        deviceIds.map { serialNumber ->
            async {
                unsubscribeFromDevice(serialNumber)
            }
        }.awaitAll()
    }

    suspend fun unsubscribeFromTargets(targets: List<SubscriptionTarget>) = coroutineScope {
        targets.map { target ->
            async {
                unsubscribeFromTarget(target)
            }
        }.awaitAll()
    }

    private suspend fun unsubscribeFromDevice(serialNumber: String) {
        val targets = subscribedDevices.filter { it.serialNumber == serialNumber }
        if (targets.isEmpty()) {
            unsubscribeFromDeviceUnlocked(serialNumber, productType = null)
            return
        }
        targets.forEach { target ->
            unsubscribeFromTarget(target)
        }
    }

    private suspend fun unsubscribeFromTarget(target: SubscriptionTarget) {
        lockForTarget(target.serialNumber, target.productType).withLock {
            unsubscribeFromDeviceUnlocked(target.serialNumber, target.productType)
        }
    }

    private suspend fun unsubscribeFromDeviceUnlocked(serialNumber: String, productType: ProductType?) {
        Log.d(TAG, "--- 处理设备取消订阅: $serialNumber product=$productType ---")

        try {
            if (productType == null) {
                Log.w(TAG, "退订时无 deviceId 产品线记录，按全部产品线尝试退订: $serialNumber")
            }

            val topics = when (productType) {
                null -> ProductType.values().flatMap { subscriptionTopicsFor(serialNumber, it) }.map { it.first }
                else -> subscriptionTopicsFor(serialNumber, productType).map { it.first }
            }

            topics.forEach { topic ->
                try {
                    val keys = subscriptionKeysForTopic(topic, productType)
                    if (keys.isNotEmpty()) {
                        Log.d(TAG, "🔄 取消订阅主题: $topic")
                        productType?.let { ProductMqttRouter.managerFor(it) }
                            ?: ProductType.values()
                                .map { ProductMqttRouter.managerFor(it) }
                                .distinctBy { it.getConfig() }
                                .forEach { it.unsubscribe(topic) }
                        keys.forEach { subscribedTopics.remove(it) }
                        Log.d(TAG, "✅ 取消订阅成功: $topic")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "取消订阅异常: $topic", e)
                }
            }

            if (productType == null) {
                subscribedDevices.removeAll { it.serialNumber == serialNumber }
                subscriptionProductBySerial.remove(serialNumber)
            } else {
                subscribedDevices.remove(SubscriptionTarget(serialNumber, productType))
                val remainingForSerial = subscribedDevices.any { it.serialNumber == serialNumber }
                if (!remainingForSerial) {
                    subscriptionProductBySerial.remove(serialNumber)
                }
            }
            Log.d(TAG, "设备 $serialNumber 取消订阅完成，当前总订阅数: ${subscribedTopics.size}")

        } catch (e: Exception) {
            Log.e(TAG, "处理设备取消订阅失败 - deviceId: $serialNumber", e)
        }

        Log.d(TAG, "--- 设备取消订阅处理结束: $serialNumber ---")
    }

    suspend fun clearAllSubscriptions() {
        Log.d(TAG, "--- 清空所有订阅 ---")
        val targetsToUnsubscribe = subscribedDevices.toList()
        if (targetsToUnsubscribe.isNotEmpty()) {
            unsubscribeFromTargets(targetsToUnsubscribe)
        }
        subscribedTopics.clear()
        subscribedDevices.clear()
        subscriptionProductBySerial.clear()
        Log.d(TAG, "✅ 所有订阅已清空")
    }

    fun getSubscribedDevices(productType: ProductType? = null): List<String> =
        subscribedDevices
            .filter { productType == null || it.productType == productType }
            .map { it.serialNumber }
            .distinct()

    fun getSubscribedTargets(): List<SubscriptionTarget> = subscribedDevices.toList()

    private suspend fun subscribeToDevice(serialNumber: String, productType: ProductType) {
        lockForTarget(serialNumber, productType).withLock {
            subscribeToDeviceUnlocked(serialNumber, productType)
        }
    }

    private suspend fun subscribeToDeviceUnlocked(serialNumber: String, productType: ProductType) {
        Log.d(TAG, "--- 处理设备订阅: $serialNumber product=$productType ---")

        subscriptionProductBySerial[serialNumber] = productType

        try {
            val topics = subscriptionTopicsFor(serialNumber, productType)

            topics.forEach { (topic, qos) ->
                try {
                    val key = subscriptionKey(topic, productType)
                    if (subscribedTopics.contains(key)) {
                        Log.d(TAG, "跳过已订阅主题: $topic")
                        subscribedDevices.add(SubscriptionTarget(serialNumber, productType))
                        return@forEach
                    }

                    Log.d(TAG, "订阅主题: $topic qos=$qos")

                    ProductMqttRouter.managerFor(productType).subscribe(
                        topic = topic,
                        qos = qos,
                        onMessage = { message ->
                            messageScope.launch {
                                try {
                                    mqttEventHandler.handleMessage(
                                        serialNumber,
                                        productType,
                                        message
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "消息处理失败 - deviceId: $serialNumber", e)
                                }
                            }
                        },
                        onMessageWithMeta = { message, isRetained ->
                            messageScope.launch {
                                try {
                                    mqttEventHandler.handleMessage(
                                        serialNumber,
                                        productType,
                                        message,
                                        isRetained = isRetained
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "消息处理失败 - deviceId: $serialNumber", e)
                                }
                            }
                        },
                        onError = { throwable ->
                            Log.e(TAG, "订阅失败 - 主题: $topic", throwable)
                        },
                        onSubscribed = {
                            subscribedTopics.add(key)
                            subscribedDevices.add(SubscriptionTarget(serialNumber, productType))
                            Log.d(TAG, "✅ 订阅成功: $topic")
                        }
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "订阅异常: $topic", e)
                }
            }

            Log.d(TAG, "设备 $serialNumber 订阅完成，当前总订阅数: ${subscribedTopics.size}")

        } catch (e: Exception) {
            Log.e(TAG, "处理设备订阅失败 - deviceId: $serialNumber", e)
        }

        Log.d(TAG, "--- 设备订阅处理结束: $serialNumber ---")
    }

    fun cleanup() {
        messageScope.cancel()
        messageScope = newMessageScope()
        subscribedTopics.clear()
        subscribedDevices.clear()
        subscriptionProductBySerial.clear()
        subscriptionTargetLocks.clear()
        mqttEventHandler.cleanup()
    }

    private fun newMessageScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun subscriptionTopicsFor(serialNumber: String, productType: ProductType): List<Pair<String, Int>> {
        val layout = mqttTopicsFor(productType)
        val topics = mutableListOf(
            layout.lifecycleTopic(serialNumber) to 1,
            layout.statusTopic(serialNumber) to 0,
        )
        if (productType == ProductType.RadioDetection) {
            topics += RadioDetectionMqttTopics.rgbAckTopic(serialNumber) to 1
        }
        return topics
    }

    private fun subscriptionKey(topic: String, productType: ProductType?): String {
        val profileKey = productType?.let { ProductMqttRouter.profileKeyFor(it) } ?: "all"
        return "$profileKey::$topic"
    }

    private fun subscriptionKeysForTopic(topic: String, productType: ProductType?): List<String> {
        if (productType != null) {
            val key = subscriptionKey(topic, productType)
            return if (subscribedTopics.contains(key)) listOf(key) else emptyList()
        }
        return subscribedTopics.filter { it.endsWith("::$topic") }
    }
}
