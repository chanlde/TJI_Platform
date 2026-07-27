package com.tji.device.product.firebucket.repository

import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.model.FireBucketSwitchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface FireBucketLinkRepository {
    val links: StateFlow<List<FireBucketLinkDevice>>

    suspend fun updateLinkDevice(linkDevice: FireBucketLinkDevice)

    suspend fun applyRetainedLinkSnapshot(
        linkDevice: FireBucketLinkDevice,
        preserveExistingRealtimeState: Boolean
    )

    suspend fun addSubDevice(linkSn: String, switch: FireBucketSwitchState)

    suspend fun removeSubDevice(linkSn: String, switchSn: String)

    suspend fun updateLinkDeviceStatus(serialNumber: String, isOnline: Boolean)

    suspend fun updateSubDevice(linkSn: String, updatedSwitch: FireBucketSwitchState)

    fun clearLinks()
}

class FireBucketLinkRepo : FireBucketLinkRepository {
    private val _links = MutableStateFlow<List<FireBucketLinkDevice>>(emptyList())
    override val links: StateFlow<List<FireBucketLinkDevice>> = _links.asStateFlow()

    override suspend fun updateLinkDevice(linkDevice: FireBucketLinkDevice) {
        _links.update { current ->
            val updatedList = current.toMutableList()
            val existingIndex = current.indexOfFirst { it.serial_number == linkDevice.serial_number }
            if (existingIndex >= 0) {
                updatedList[existingIndex] = linkDevice
            } else {
                updatedList.add(linkDevice)
            }
            updatedList
        }
    }

    override suspend fun applyRetainedLinkSnapshot(
        linkDevice: FireBucketLinkDevice,
        preserveExistingRealtimeState: Boolean
    ) {
        _links.update { current ->
            val existingIndex = current.indexOfFirst {
                it.serial_number == linkDevice.serial_number
            }
            if (existingIndex < 0) {
                current + linkDevice
            } else if (preserveExistingRealtimeState) {
                current
            } else {
                current.toMutableList().apply {
                    this[existingIndex] = linkDevice
                }
            }
        }
    }

    override suspend fun addSubDevice(linkSn: String, switch: FireBucketSwitchState) {
        _links.update { current ->
            current.map { linkDevice ->
                if (linkDevice.serial_number == linkSn) {
                    linkDevice.copy(
                        subDevices = linkDevice.subDevices.replaceOrAppend(switch)
                    )
                } else {
                    linkDevice
                }
            }
        }
    }

    override suspend fun removeSubDevice(linkSn: String, switchSn: String) {
        _links.update { current ->
            current.map { linkDevice ->
                if (linkDevice.serial_number == linkSn) {
                    linkDevice.copy(
                        subDevices = linkDevice.subDevices.filterNot { it.serialNumber == switchSn }
                    )
                } else {
                    linkDevice
                }
            }
        }
    }

    override suspend fun updateLinkDeviceStatus(serialNumber: String, isOnline: Boolean) {
        _links.update { current ->
            current.map { link ->
                if (link.serial_number == serialNumber) {
                    link.copy(isOnline = isOnline)
                } else {
                    link
                }
            }
        }
    }

    override suspend fun updateSubDevice(linkSn: String, updatedSwitch: FireBucketSwitchState) {
        _links.update { current ->
            current.map { link ->
                if (link.serial_number == linkSn) {
                    link.copy(
                        subDevices = link.subDevices.replaceOrAppend(updatedSwitch)
                    )
                } else {
                    link
                }
            }
        }
    }

    override fun clearLinks() {
        _links.value = emptyList()
    }

    /**
     * 状态刷新保持设备原有顺序，避免高频遥测让控制列表不断跳位。
     */
    private fun List<FireBucketSwitchState>.replaceOrAppend(
        switch: FireBucketSwitchState
    ): List<FireBucketSwitchState> {
        val index = indexOfFirst { it.serialNumber == switch.serialNumber }
        if (index < 0) return this + switch
        return toMutableList().apply { this[index] = switch }
    }
}
