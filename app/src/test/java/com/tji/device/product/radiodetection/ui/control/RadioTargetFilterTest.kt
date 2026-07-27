package com.tji.device.product.radiodetection.ui.control

import com.tji.device.product.radiodetection.model.RadioDetectionTarget
import com.tji.device.product.radiodetection.model.RadioListStatus
import com.tji.device.product.radiodetection.model.RadioSignalLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioTargetFilterTest {
    private val targets = listOf(
        target("drone-black-strong", "无人机", RadioListStatus.Blacklist, RadioSignalLevel.Strong),
        target("drone-white-weak", "无人机", RadioListStatus.Whitelist, RadioSignalLevel.Weak),
        target("aircraft-black-strong", "民航", RadioListStatus.Blacklist, RadioSignalLevel.Strong)
    )

    @Test
    fun emptyFilterReturnsEveryTarget() {
        assertEquals(targets, targets.filteredBy(RadioTargetFilter()))
    }

    @Test
    fun filtersByEachSupportedDimension() {
        assertEquals(
            listOf("drone-black-strong", "drone-white-weak"),
            targets.filteredBy(RadioTargetFilter(type = "无人机")).map { it.id }
        )
        assertEquals(
            listOf("drone-black-strong", "aircraft-black-strong"),
            targets.filteredBy(RadioTargetFilter(listStatus = RadioListStatus.Blacklist)).map { it.id }
        )
        assertEquals(
            listOf("drone-white-weak"),
            targets.filteredBy(RadioTargetFilter(signalLevel = RadioSignalLevel.Weak)).map { it.id }
        )
    }

    @Test
    fun combinesDimensionsAsIntersection() {
        val filter = RadioTargetFilter(
            type = "无人机",
            listStatus = RadioListStatus.Blacklist,
            signalLevel = RadioSignalLevel.Strong
        )

        assertEquals(listOf("drone-black-strong"), targets.filteredBy(filter).map { it.id })
    }

    @Test
    fun returnsEmptyWhenNoTargetMatches() {
        val filter = RadioTargetFilter(
            type = "民航",
            signalLevel = RadioSignalLevel.Weak
        )

        assertEquals(emptyList<RadioDetectionTarget>(), targets.filteredBy(filter))
    }

    private fun target(
        id: String,
        type: String,
        status: RadioListStatus,
        signal: RadioSignalLevel
    ) = RadioDetectionTarget(
        id = id,
        name = id,
        type = type,
        serialNumber = id,
        listStatus = status,
        latitude = 0.0,
        longitude = 0.0,
        altitudeMeters = 0,
        speedMetersPerSecond = 0,
        headingDegrees = 0,
        frequencyLabel = "-",
        signalLevel = signal,
        lastSeenAtMillis = 0L,
        pilotName = "",
        pilotLatitude = 0.0,
        pilotLongitude = 0.0,
        pilotDistanceText = "-",
        mapXPercent = 0.5f,
        mapYPercent = 0.5f
    )
}
