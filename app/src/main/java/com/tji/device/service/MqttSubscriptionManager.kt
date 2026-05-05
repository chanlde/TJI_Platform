package com.tji.device.service

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.device.service.mqtt.mqttTopicsFor
import com.tji.network.MqttManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
 * 订阅时由调用方传入该产品线下的 [ProductType]，并写入 SN 映射；入站消息直接带此类型分发，不再在 Handler 内推断。
 */
class MqttSubscriptionManager(
    private val mqttEventHandler: MqttEventHandler,
) {
    private val subscribedTopics = ConcurrentHashMap.newKeySet<String>()
    private val subscribedDevices = ConcurrentHashMap.newKeySet<SubscriptionTarget>()
    /** 订阅时登记的「此 SN 当前属于哪条产品线」 */
    private val subscriptionProductBySerial = ConcurrentHashMap<String, ProductType>()
    /** 同一 SN 上串行「退订 / 强制重订」，避免与 `async`、重连叠加以致 unsubscribe/subscribe 交错重复。 */
    private val subscriptionTargetLocks = ConcurrentHashMap<String, Mutex>()
    private fun lockForTarget(serialNumber: String, productType: ProductType): Mutex =
        subscriptionTargetLocks.getOrPut("${productType.name}:$serialNumber") { Mutex() }

    private val TAG = "MqttSubscriptionManager"

    /**
     * @param productType 本次列表中所有 SN 所属产品线（与 `openProduct`、登录下发的绑定设备一致）
     */
    suspend fun subscribeToDevices(serialNumbers: List<String>, productType: ProductType) =
        coroutineScope {
            serialNumbers.map { serialNumber ->
                async {
                    subscribeToDevice(serialNumber, productType)
                }
            }.awaitAll()
        }

    suspend fun unsubscribeFromDevices(serialNumbers: List<String>) = coroutineScope {
        serialNumbers.map { serialNumber ->
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
            val layouts = when (productType) {
                null -> {
                    Log.w(TAG, "退订时无 SN 产品线记录，按全部产品线尝试退订: $serialNumber")
                    ProductType.values().map { mqttTopicsFor(it) }
                }
                else -> listOf(mqttTopicsFor(productType))
            }

            val topics = layouts.flatMap { layout ->
                listOf(layout.lifecycleTopic(serialNumber), layout.statusTopic(serialNumber))
            }

            topics.forEach { topic ->
                try {
                    if (subscribedTopics.contains(topic)) {
                        Log.d(TAG, "🔄 取消订阅主题: $topic")
                        MqttManager.getInstance().unsubscribe(topic)
                        subscribedTopics.remove(topic)
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
            Log.e(TAG, "处理设备取消订阅失败 - SN: $serialNumber", e)
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
            val layout = mqttTopicsFor(productType)
            val topics = listOf(
                layout.lifecycleTopic(serialNumber) to 1,
                layout.statusTopic(serialNumber) to 0,
            )

            topics.forEach { (topic, qos) ->
                try {
                    if (subscribedTopics.contains(topic)) {
                        Log.d(TAG, "跳过已订阅主题: $topic")
                        subscribedDevices.add(SubscriptionTarget(serialNumber, productType))
                        return@forEach
                    }

                    Log.d(TAG, "订阅主题: $topic qos=$qos")

                    MqttManager.getInstance().subscribe(
                        topic = topic,
                        qos = qos,
                        onMessage = { message ->
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    mqttEventHandler.handleMessage(
                                        serialNumber,
                                        productType,
                                        message
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "消息处理失败 - SN: $serialNumber", e)
                                }
                            }
                        },
                        onError = { throwable ->
                            Log.e(TAG, "订阅失败 - 主题: $topic", throwable)
                        },
                        onSubscribed = {
                            subscribedTopics.add(topic)
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
            Log.e(TAG, "处理设备订阅失败 - SN: $serialNumber", e)
        }

        Log.d(TAG, "--- 设备订阅处理结束: $serialNumber ---")
    }
}
