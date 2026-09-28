package com.tji.device.product.firebucket.transport

import com.tji.device.product.firebucket.model.FireBucketSwitchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val DIRECT_FIRE_BUCKET_LINK_ID = "DIRECT-H618"
const val DIRECT_FIRE_BUCKET_LINK_NAME = "本地消防吊桶"

data class DirectFireBucketState(
    val isConnected: Boolean = false,
    val buckets: List<FireBucketSwitchState> = emptyList()
)

/** 数传模式只保存 H618 本地发现的无线吊桶，不与 4G MQTT 设备树混用。 */
class DirectFireBucketStateStore(
    private val maximumBuckets: Int = MAXIMUM_WIRELESS_BUCKETS
) {
    private val _state = MutableStateFlow(DirectFireBucketState())
    val state: StateFlow<DirectFireBucketState> = _state.asStateFlow()

    init {
        require(maximumBuckets > 0) { "无线吊桶数量上限必须大于 0" }
    }

    fun beginSession() {
        _state.value = DirectFireBucketState()
    }

    fun updateConnection(isConnected: Boolean) {
        _state.update { current ->
            current.copy(
                isConnected = isConnected,
                buckets = if (isConnected) {
                    current.buckets
                } else {
                    current.buckets.map { it.copy(isOnline = false) }
                }
            )
        }
    }

    fun applyStatus(bucket: FireBucketSwitchState) {
        _state.update { current ->
            val existingIndex = current.buckets.indexOfFirst {
                it.serialNumber == bucket.serialNumber
            }
            when {
                existingIndex >= 0 -> current.copy(
                    buckets = current.buckets.toMutableList().apply {
                        this[existingIndex] = bucket
                    }
                )
                current.buckets.size < maximumBuckets -> current.copy(
                    buckets = current.buckets + bucket
                )
                else -> current
            }
        }
    }

    fun clear() {
        _state.value = DirectFireBucketState()
    }

    private companion object {
        const val MAXIMUM_WIRELESS_BUCKETS = 5
    }
}
