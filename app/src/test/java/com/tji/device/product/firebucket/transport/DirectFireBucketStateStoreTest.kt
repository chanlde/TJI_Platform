package com.tji.device.product.firebucket.transport

import com.tji.device.product.firebucket.model.FireBucketSwitchState
import com.tji.device.product.firebucket.repository.FireBucketLinkRepo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectFireBucketStateStoreTest {
    @Test
    fun discoversAndUpdatesMultipleWirelessBuckets() {
        val store = DirectFireBucketStateStore()

        store.applyStatus(bucket("FB00A123", angle = 10.0))
        store.applyStatus(bucket("FB00B456", angle = 20.0))
        store.applyStatus(bucket("FB00A123", angle = 45.0))

        assertEquals(listOf("FB00A123", "FB00B456"), store.state.value.buckets.map { it.serialNumber })
        assertEquals(45.0, store.state.value.buckets.first().currentAngle, 0.0)
    }

    @Test
    fun limitsWirelessBucketListToFive() {
        val store = DirectFireBucketStateStore()

        repeat(6) { index ->
            store.applyStatus(bucket("FB00A12$index", angle = index.toDouble()))
        }

        assertEquals(5, store.state.value.buckets.size)
    }

    @Test
    fun disconnectMarksEveryKnownBucketOffline() {
        val store = DirectFireBucketStateStore()
        store.applyStatus(bucket("FB00A123", angle = 10.0))
        store.updateConnection(true)
        assertTrue(store.state.value.isConnected)

        store.updateConnection(false)

        assertFalse(store.state.value.isConnected)
        assertFalse(store.state.value.buckets.single().isOnline)
    }

    @Test
    fun directStatusDoesNotMutateCloudMqttDeviceTree() {
        val cloudRepository = FireBucketLinkRepo()
        val directStore = DirectFireBucketStateStore()

        directStore.applyStatus(bucket("FB00A123", angle = 45.0))

        assertEquals(1, directStore.state.value.buckets.size)
        assertTrue(cloudRepository.links.value.isEmpty())
    }

    private fun bucket(serialNumber: String, angle: Double) = FireBucketSwitchState(
        serialNumber = serialNumber,
        deviceName = "消防吊桶",
        deviceType = "HydroSwitch",
        isOnline = true,
        currentAngle = angle,
        currentCurrent = 10.0,
        inputVoltage = 24.0,
        servoMinAngle = 0.0,
        servoMaxAngle = 90.0,
        uptime = 1,
        batteryPercentage = 80.0
    )
}
