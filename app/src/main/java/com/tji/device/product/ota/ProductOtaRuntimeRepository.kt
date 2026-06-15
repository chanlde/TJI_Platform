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
    val maxOtaSeqByCmdId: Map<String, Long> = emptyMap()
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
}

class ProductOtaRuntimeRepo : ProductOtaRuntimeRepository {
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
            current.nextWithOtaStatus(otaStatus)
        }
    }

    private fun ProductOtaRuntimeState.nextWithOtaStatus(
        incoming: ProductOtaStatus
    ): ProductOtaRuntimeState {
        val currentStatus = otaStatus
        val incomingCmdId = incoming.cmdId
        val currentCmdId = currentStatus?.cmdId
        val sameTask = when {
            !incomingCmdId.isNullOrBlank() && !currentCmdId.isNullOrBlank() -> incomingCmdId == currentCmdId
            !incomingCmdId.isNullOrBlank() && currentCmdId.isNullOrBlank() -> false
            else -> true
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
            if (incoming.seq == null && incomingCmdId == currentCmdId) {
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

        val nextSeqByCmd = if (incomingCmdId != null && incoming.seq != null) {
            maxOtaSeqByCmdId + (incomingCmdId to incoming.seq)
        } else {
            maxOtaSeqByCmdId
        }
        return copy(
            otaStatus = incoming,
            maxOtaSeqByCmdId = nextSeqByCmd
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

private fun ProductOtaStatus.isTerminalOtaState(): Boolean {
    return when (status.normalizedOtaStatus()) {
        "PENDING_REBOOT",
        "FAILED",
        "TEST_DONE",
        "SUCCESS",
        "ROLLBACK" -> true
        else -> false
    }
}
