package com.tji.device.product.ota

import com.tji.device.data.model.ProductType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProductOtaRuntimeState(
    val productType: ProductType,
    val serialNumber: String,
    val deviceInfo: ProductDeviceInfo? = null,
    val otaStatus: ProductOtaStatus? = null,
    val maxOtaSeqByCmdId: Map<String, Long> = emptyMap(),
    val retiredOtaCmdIds: Set<String> = emptySet(),
    val rebootWaitStartedAtMillis: Long? = null,
    val rebootOfflineObserved: Boolean = false
)

interface ProductOtaRuntimeRepository {
    val states: StateFlow<List<ProductOtaRuntimeState>>

    fun updateDeviceInfo(
        productType: ProductType,
        serialNumber: String,
        deviceInfo: ProductDeviceInfo
    )

    fun updateOtaStatus(
        productType: ProductType,
        serialNumber: String,
        otaStatus: ProductOtaStatus
    )

    fun updateLifecycle(
        productType: ProductType,
        serialNumber: String,
        eventType: String,
        timestamp: Long? = null,
        isRetained: Boolean = false
    )

    fun clearAll()
}

class ProductOtaRuntimeRepo(
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ProductOtaRuntimeRepository {
    private val _states = MutableStateFlow<List<ProductOtaRuntimeState>>(emptyList())
    override val states: StateFlow<List<ProductOtaRuntimeState>> = _states.asStateFlow()

    override fun updateDeviceInfo(
        productType: ProductType,
        serialNumber: String,
        deviceInfo: ProductDeviceInfo
    ) {
        updateState(productType, serialNumber) { current ->
            current.copy(deviceInfo = deviceInfo)
        }
    }

    override fun updateOtaStatus(
        productType: ProductType,
        serialNumber: String,
        otaStatus: ProductOtaStatus
    ) {
        updateState(productType, serialNumber) { current ->
            current.nextWithOtaStatus(otaStatus, nowMillis())
        }
    }

    override fun updateLifecycle(
        productType: ProductType,
        serialNumber: String,
        eventType: String,
        timestamp: Long?,
        isRetained: Boolean
    ) {
        if (isRetained) return
        val receivedAtMillis = timestamp ?: nowMillis()
        when {
            eventType.equals("offline", ignoreCase = true) -> {
                updateState(productType, serialNumber) { current ->
                    current.markRebootOfflineObserved(receivedAtMillis)
                }
            }
            eventType.equals("online", ignoreCase = true) -> {
                updateState(productType, serialNumber) { current ->
                    current.completeRebootWaitOnOnline(receivedAtMillis)
                }
            }
        }
    }

    override fun clearAll() {
        _states.value = emptyList()
    }

    private fun ProductOtaRuntimeState.completeRebootWaitOnOnline(
        receivedAtMillis: Long
    ): ProductOtaRuntimeState {
        val currentStatus = otaStatus ?: return this
        val rebootWaitStartedAt = rebootWaitStartedAtMillis ?: return this
        if (!rebootOfflineObserved) return this
        if (receivedAtMillis < rebootWaitStartedAt) return this
        return when (currentStatus.status.normalizedOtaStatus()) {
            "READY_TO_REBOOT",
            "PENDING_REBOOT",
            "REBOOTING" -> copy(
                otaStatus = currentStatus.copy(
                    status = "SUCCESS",
                    progress = 100,
                    message = "设备已重启并上线",
                    reason = null,
                    timestamp = receivedAtMillis
                ),
                rebootWaitStartedAtMillis = null,
                rebootOfflineObserved = false
            )
            else -> this
        }
    }

    private fun ProductOtaRuntimeState.markRebootOfflineObserved(
        receivedAtMillis: Long
    ): ProductOtaRuntimeState {
        val rebootWaitStartedAt = rebootWaitStartedAtMillis ?: return this
        if (receivedAtMillis < rebootWaitStartedAt) return this
        return copy(rebootOfflineObserved = true)
    }

    private fun ProductOtaRuntimeState.nextWithOtaStatus(
        incoming: ProductOtaStatus,
        receivedAtMillis: Long
    ): ProductOtaRuntimeState {
        val currentStatus = otaStatus
        val incomingCmdId = incoming.cmdId
        val currentCmdId = currentStatus?.cmdId
        val sameTask = when {
            !incomingCmdId.isNullOrBlank() && !currentCmdId.isNullOrBlank() -> incomingCmdId == currentCmdId
            !incomingCmdId.isNullOrBlank() && currentCmdId.isNullOrBlank() -> false
            else -> true
        }
        if (!incomingCmdId.isNullOrBlank() &&
            incomingCmdId != currentCmdId &&
            incomingCmdId in retiredOtaCmdIds
        ) {
            return this
        }

        if (incomingCmdId != null && incoming.seq != null) {
            val lastSeq = maxOtaSeqByCmdId[incomingCmdId]
            if (lastSeq != null && incoming.seq <= lastSeq) {
                return this
            }
        }
        if (currentStatus != null && sameTask) {
            if (currentStatus.isTerminalOtaState() && !incoming.isTerminalOtaState()) {
                return this
            }
            // 旧固件可能不回传 cmdId。只要无法用 seq 排序，就仍需使用时间戳和
            // 进度保证状态单调，不能让迟到的匿名消息把当前具名任务拉回旧阶段。
            if (incoming.seq == null &&
                (incomingCmdId == currentCmdId || incomingCmdId.isNullOrBlank())
            ) {
                val currentTs = currentStatus.timestamp
                val incomingTs = incoming.timestamp
                if (currentTs != null && incomingTs != null && incomingTs < currentTs) {
                    return this
                }
                val currentProgress = currentStatus.progress
                val incomingProgress = incoming.progress
                if (currentProgress != null && incomingProgress != null && incomingProgress < currentProgress) {
                    return this
                }
            }
        }

        val nextRetiredCmdIds = if (!currentCmdId.isNullOrBlank() &&
            !incomingCmdId.isNullOrBlank() &&
            currentCmdId != incomingCmdId
        ) {
            (retiredOtaCmdIds + currentCmdId)
                .toList()
                .takeLast(MAX_RETIRED_OTA_COMMANDS)
                .toSet()
        } else {
            retiredOtaCmdIds
        }
        val nextSeqByCmd = if (incomingCmdId != null && incoming.seq != null) {
            (maxOtaSeqByCmdId + (incomingCmdId to incoming.seq))
                .filterKeys { it == incomingCmdId || it !in nextRetiredCmdIds }
                .entries
                .toList()
                .takeLast(MAX_TRACKED_OTA_COMMANDS)
                .associate { it.toPair() }
        } else {
            maxOtaSeqByCmdId
        }
        val normalizedStatus = incoming.status.normalizedOtaStatus()
        val nextRebootWaitStartedAt = when (normalizedStatus) {
            "READY_TO_REBOOT",
            "PENDING_REBOOT",
            "REBOOTING" -> if (sameTask) {
                rebootWaitStartedAtMillis ?: receivedAtMillis
            } else {
                receivedAtMillis
            }
            else -> null
        }
        val nextRebootOfflineObserved = when {
            nextRebootWaitStartedAt == null -> false
            sameTask -> rebootOfflineObserved
            else -> false
        }
        return copy(
            otaStatus = incoming,
            maxOtaSeqByCmdId = nextSeqByCmd,
            retiredOtaCmdIds = nextRetiredCmdIds,
            rebootWaitStartedAtMillis = nextRebootWaitStartedAt,
            rebootOfflineObserved = nextRebootOfflineObserved
        )
    }

    private fun updateState(
        productType: ProductType,
        serialNumber: String,
        transform: (ProductOtaRuntimeState) -> ProductOtaRuntimeState
    ) {
        _states.update { current ->
            val index = current.indexOfFirst {
                it.productType == productType && it.serialNumber == serialNumber
            }
            if (index >= 0) {
                current.toMutableList().apply {
                    set(index, transform(get(index)))
                }
            } else {
                current + transform(
                    ProductOtaRuntimeState(
                        productType = productType,
                        serialNumber = serialNumber
                    )
                )
            }
        }
    }
}

private const val MAX_RETIRED_OTA_COMMANDS = 8
private const val MAX_TRACKED_OTA_COMMANDS = 8

private fun ProductOtaStatus.isTerminalOtaState(): Boolean {
    return when (status.normalizedOtaStatus()) {
        "FAILED",
        "TEST_DONE",
        "SUCCESS",
        "ROLLBACK" -> true
        else -> false
    }
}
