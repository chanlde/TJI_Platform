package com.tji.device.product.radiodetection.ui.control

import com.tji.device.product.radiodetection.model.RadioDetectionTarget
import com.tji.device.product.radiodetection.model.RadioListStatus
import com.tji.device.product.radiodetection.model.RadioSignalLevel

internal data class RadioTargetFilter(
    val type: String? = null,
    val listStatus: RadioListStatus? = null,
    val signalLevel: RadioSignalLevel? = null
)

internal fun List<RadioDetectionTarget>.filteredBy(filter: RadioTargetFilter): List<RadioDetectionTarget> =
    filter { target ->
        (filter.type == null || target.type == filter.type) &&
            (filter.listStatus == null || target.listStatus == filter.listStatus) &&
            (filter.signalLevel == null || target.signalLevel == filter.signalLevel)
    }
