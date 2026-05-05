package com.tji.device.product.firebucket.repository

import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.model.Switch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface FireBucketLinkRepository {
    val links: StateFlow<List<FireBucketLinkDevice>>

    fun productTypeForLink(serialNumber: String): ProductType?

    suspend fun updateLinkDevice(linkDevice: FireBucketLinkDevice)

    suspend fun updateLinkStatus(isOnline: Boolean)

    suspend fun addSubDevice(linkSn: String, switch: Switch)

    suspend fun removeSubDevice(linkSn: String, switchSn: String)

    suspend fun updateLinkDeviceStatus(serialNumber: String, isOnline: Boolean)

    suspend fun updateSubDevice(linkSn: String, updatedSwitch: Switch)

    fun clearLinks()
}

class FireBucketLinkRepo : FireBucketLinkRepository {
    private val _links = MutableStateFlow<List<FireBucketLinkDevice>>(emptyList())
    override val links: StateFlow<List<FireBucketLinkDevice>> = _links.asStateFlow()

    override fun productTypeForLink(serialNumber: String): ProductType? =
        _links.value.find { it.serial_number == serialNumber }?.productType

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

    override suspend fun addSubDevice(linkSn: String, switch: Switch) {
        _links.update { current ->
            current.map { linkDevice ->
                if (linkDevice.serial_number == linkSn) {
                    linkDevice.copy(subDevices = linkDevice.subDevices + switch)
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

    override suspend fun updateSubDevice(linkSn: String, updatedSwitch: Switch) {
        _links.update { current ->
            current.map { link ->
                if (link.serial_number == linkSn) {
                    link.copy(
                        subDevices = link.subDevices.map { switch ->
                            if (switch.serialNumber == updatedSwitch.serialNumber) {
                                updatedSwitch
                            } else {
                                switch
                            }
                        }
                    )
                } else {
                    link
                }
            }
        }
    }

    override suspend fun updateLinkStatus(isOnline: Boolean) {
        _links.update { current ->
            current.map { linkDevice ->
                linkDevice.copy(isOnline = isOnline)
            }
        }
    }

    override fun clearLinks() {
        _links.value = emptyList()
    }
}
