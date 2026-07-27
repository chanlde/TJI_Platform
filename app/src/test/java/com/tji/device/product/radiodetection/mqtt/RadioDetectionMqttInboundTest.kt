package com.tji.device.product.radiodetection.mqtt

import com.tji.device.product.radiodetection.repository.RadioDetectionRepo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioDetectionMqttInboundTest {
    @Test
    fun retainedOnlineCannotReviveDevice() = runBlocking {
        val repository = RadioDetectionRepo()
        val inbound = RadioDetectionMqttInbound(repository) { _, _ -> }

        inbound.handleMessage(SERIAL, "online", isRetained = true)

        assertTrue(repository.devices.value.isEmpty())
    }

    @Test
    fun retainedOfflineStillMarksDeviceOffline() = runBlocking {
        val repository = RadioDetectionRepo()
        val inbound = RadioDetectionMqttInbound(repository) { _, _ -> }

        inbound.handleMessage(SERIAL, "offline", isRetained = true)

        assertEquals(false, repository.devices.value.single().isOnline)
    }

    @Test
    fun retainedRidCannotCreateGhostTargetOrOverwriteReplay() = runBlocking {
        val repository = RadioDetectionRepo()
        val recordedPayloads = mutableListOf<String>()
        val inbound = RadioDetectionMqttInbound(repository) { _, payload ->
            recordedPayloads += payload
        }
        val payload = ridPayload("RID-OLD")

        inbound.handleMessage(SERIAL, payload, isRetained = true)

        assertTrue(repository.devices.value.isEmpty())
        assertTrue(recordedPayloads.isEmpty())
    }

    @Test
    fun liveRidMarksDeviceOnlineAndUpdatesReplay() = runBlocking {
        val repository = RadioDetectionRepo()
        val recordedPayloads = mutableListOf<String>()
        val inbound = RadioDetectionMqttInbound(repository) { _, payload ->
            recordedPayloads += payload
        }
        val payload = ridPayload("RID-LIVE")

        inbound.handleMessage(SERIAL, payload)

        val state = repository.devices.value.single()
        assertEquals(true, state.isOnline)
        assertEquals(listOf("RID-LIVE"), state.targets.map { it.id })
        assertEquals(listOf(payload), recordedPayloads)
    }

    private fun ridPayload(targetId: String): String {
        val json =
            """{"type":"rid","sn":"$targetId","rssi":-55,"channel":6,"drone_lon":113.9,"drone_lat":22.5}"""
        return json.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SERIAL = "RADIO-001"
    }
}
