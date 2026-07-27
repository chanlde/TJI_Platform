package com.tji.device.data.session

import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSessionStoreTest {
    @Test
    fun sessionOwnsDevicesSelectionAndRenameAsOneStateSource() {
        val store = AppSessionStore()
        val device = BoundAccountDevice(
            serialNumber = "SPEAKER-01",
            name = "一号喊话器",
            productType = ProductType.Speaker,
            serverId = 42
        )

        store.startSession(userId = "user-1", devices = listOf(device, device))
        store.selectDevice(device)
        store.renameDevice(device, "现场喊话器")

        assertEquals("user-1", store.state.value.userId)
        assertEquals(1, store.state.value.boundDevices.size)
        assertEquals("现场喊话器", store.state.value.boundDevices.single().name)
        assertEquals(device.deviceKey, store.state.value.selectedDeviceKey)
        assertEquals(ProductType.Speaker, store.state.value.preferredProductType)

        store.openProduct(ProductType.SolarClean)
        assertNull(store.state.value.selectedDeviceKey)
        assertEquals(ProductType.SolarClean, store.state.value.preferredProductType)
    }

    @Test
    fun everyMutationPublishesOneConsistentSessionState() = runBlocking {
        val store = AppSessionStore()
        val speaker = BoundAccountDevice(
            serialNumber = "SPEAKER-01",
            name = "喊话器",
            productType = ProductType.Speaker
        )
        val observed = mutableListOf<AppSessionState>()
        val collectionJob = launch(start = CoroutineStart.UNDISPATCHED) {
            store.state.collect(observed::add)
        }

        store.startSession(userId = "user-1", devices = listOf(speaker))
        yield()
        store.selectDevice(speaker)
        yield()

        val selected = store.state.value
        assertEquals(speaker.deviceKey, selected.selectedDeviceKey)
        assertEquals(selected.selectedDeviceKey?.productType, selected.preferredProductType)

        val unknownKey = DeviceKey(ProductType.SolarClean, "UNKNOWN")
        store.selectDeviceKey(unknownKey)
        yield()

        val afterInvalidSelection = store.state.value
        assertNull(afterInvalidSelection.selectedDeviceKey)
        assertTrue(afterInvalidSelection.boundDevices.none { it.deviceKey == unknownKey })

        store.clear()
        yield()
        collectionJob.cancelAndJoin()

        assertEquals(AppSessionState(), store.state.value)
        assertEquals(5, observed.size)
        assertTrue(
            observed.all { session ->
                session.selectedDeviceKey == null ||
                    session.boundDevices.any { it.deviceKey == session.selectedDeviceKey } &&
                    session.preferredProductType == session.selectedDeviceKey.productType
            }
        )
    }

    @Test
    fun deviceKeySeparatesSameSerialAcrossProducts() {
        val fireBucket = BoundAccountDevice(
            serialNumber = "SHARED-001",
            name = "消防设备",
            productType = ProductType.FireBucket
        )
        val speaker = fireBucket.copy(
            name = "喊话器",
            productType = ProductType.Speaker
        )

        assertNotEquals(fireBucket.deviceKey, speaker.deviceKey)
    }

    @Test
    fun sessionGenerationChangesOnlyWhenAccountSessionChanges() {
        val store = AppSessionStore()
        val initialGeneration = store.currentSessionGeneration()
        val device = BoundAccountDevice(
            serialNumber = "SPEAKER-01",
            name = "喊话器",
            productType = ProductType.Speaker
        )

        store.startSession(userId = "user-1", devices = listOf(device))
        val loggedInGeneration = store.currentSessionGeneration()
        store.selectDevice(device)
        store.renameDevice(device, "新名称")

        assertNotEquals(initialGeneration, loggedInGeneration)
        assertEquals(loggedInGeneration, store.currentSessionGeneration())

        store.beginSessionTransition()
        val transitionGeneration = store.currentSessionGeneration()
        assertNotEquals(loggedInGeneration, transitionGeneration)
        assertEquals("user-1", store.state.value.userId)

        store.clear()
        assertNotEquals(transitionGeneration, store.currentSessionGeneration())
    }
}
