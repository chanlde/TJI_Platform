package com.tji.device.ui.floating

import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.Switch

enum class FloatingWindowMode {
    ICON,
    EXPANDED
}

data class FloatingSwitchSummary(
    val serialNumber: String,
    val name: String,
    val isOnline: Boolean,
    val currentAngle: Int,
    val inputVoltage: Double = 0.0 // 输入电压
)

data class FloatingLinkSummary(
    val serialNumber: String,
    val name: String,
    val isOnline: Boolean,
    val productType: ProductType,
    val onlineSwitches: List<FloatingSwitchSummary>,
    val offlineSwitches: List<FloatingSwitchSummary>
) {
    val allSwitches: List<FloatingSwitchSummary>
        get() = onlineSwitches + offlineSwitches

    companion object {
        fun fromSwitches(
            serialNumber: String,
            name: String,
            isOnline: Boolean,
            productType: ProductType,
            switches: List<Switch>
        ): FloatingLinkSummary {
            val online = mutableListOf<FloatingSwitchSummary>()
            val offline = mutableListOf<FloatingSwitchSummary>()

            switches.forEach { switch ->
                val summary = FloatingSwitchSummary(
                    serialNumber = switch.serialNumber,
                    name = switch.deviceName,
                    isOnline = switch.isOnline,
                    currentAngle = switch.currentAngle.toInt(),
                    inputVoltage = switch.inputVoltage
                )
                if (switch.isOnline) {
                    online.add(summary)
                } else {
                    offline.add(summary)
                }
            }
            return FloatingLinkSummary(
                serialNumber = serialNumber,
                name = name,
                isOnline = isOnline,
                productType = productType,
                onlineSwitches = online,
                offlineSwitches = offline
            )
        }
    }
}

data class FloatingWindowUiState(
    var mode: FloatingWindowMode = FloatingWindowMode.ICON,
    val links: List<FloatingLinkSummary> = emptyList(),
    val selectedLinkSerial: String? = null,
    val selectedLinkName: String? = null,
    val preferredProductType: ProductType = ProductType.FireBucket,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val showOfflineSwitches: Boolean = false
) {
    val hasLinks: Boolean
        get() = links.isNotEmpty()

    val selectedLink: FloatingLinkSummary?
        get() {
            val serial = selectedLinkSerial
            return if (serial != null) {
                links.firstOrNull { it.serialNumber == serial }
                    ?: FloatingLinkSummary(
                        serialNumber = serial,
                        name = selectedLinkName?.takeIf { it.isNotBlank() } ?: serial,
                        isOnline = false,
                        productType = preferredProductType,
                        onlineSwitches = emptyList(),
                        offlineSwitches = emptyList()
                    )
            } else {
                links.firstOrNull { it.productType == preferredProductType }
            }
        }

    val activeProductType: ProductType
        get() = selectedLink?.productType ?: preferredProductType
}
