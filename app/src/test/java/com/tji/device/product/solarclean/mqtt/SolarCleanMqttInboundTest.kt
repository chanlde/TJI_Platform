package com.tji.device.product.solarclean.mqtt

import com.tji.device.product.solarclean.repository.SolarCleanRepo
import com.tji.device.product.solarclean.model.SolarCleanEvent
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SolarCleanMqttInboundTest {

    @Test
    fun parsesDeviceInfoPayloadWrappedInData() = runBlocking {
        val repo = SolarCleanRepo()
        val inbound = SolarCleanMqttInbound(repo)

        inbound.handleEvent(
            linkSn = SERIAL,
            eventType = "deviceInfo",
            json = JSONObject(
                """
                {
                  "ts": 1710000000000,
                  "data": {
                    "hardwareVersion": "HW-A",
                    "firmwareVersion": "1.2.3",
                    "innerVersion": 12,
                    "otaStatus": "IDLE",
                    "battery": 87
                  }
                }
                """.trimIndent()
            ),
            isRetained = true
        )

        val state = repo.devices.value.single()
        assertEquals(SERIAL, state.serialNumber)
        assertEquals("HW-A", state.deviceInfo?.hardwareVersion)
        assertEquals("1.2.3", state.deviceInfo?.firmwareVersion)
        assertEquals(12, state.deviceInfo?.firmwareInnerVersion)
        assertEquals("IDLE", state.deviceInfo?.otaStatus)
        assertEquals(87, state.deviceInfo?.batteryPercent)
        assertEquals(false, state.isOnline)

        inbound.cleanup()
    }

    @Test
    fun parsesOtaStatusPayloadWithCompatibilityFieldNames() = runBlocking {
        val repo = SolarCleanRepo()
        val inbound = SolarCleanMqttInbound(repo)

        inbound.handleEvent(
            linkSn = SERIAL,
            eventType = "otaStatus",
            json = JSONObject(
                """
                {
                  "type": "ota_status",
                  "params": {
                    "otaStatus": "OTA_DOWNLOADING",
                    "progress": 64,
                    "targetVersion": "V0.7.0",
                    "targetInnerVersion": 7,
                    "firmwareVersion": "V0.6.0",
                    "downloaded": 640,
                    "total": 1000,
                    "msg": "downloading"
                  }
                }
                """.trimIndent()
            )
        )

        val ota = repo.devices.value.single().otaStatus
        assertEquals("OTA_DOWNLOADING", ota?.status)
        assertEquals(64, ota?.progress)
        assertEquals("V0.7.0", ota?.targetVersion)
        assertEquals(7, ota?.targetInnerVersion)
        assertEquals("V0.6.0", ota?.firmwareVersion)
        assertEquals(640L, ota?.downloaded)
        assertEquals(1000L, ota?.total)
        assertEquals("downloading", ota?.message)

        inbound.cleanup()
    }

    @Test
    fun ignoresRetainedOnlineLifecycleSignal() = runBlocking {
        val repo = SolarCleanRepo()
        val inbound = SolarCleanMqttInbound(repo)

        inbound.handleEvent(
            linkSn = SERIAL,
            eventType = "online",
            json = JSONObject("""{"type":"online","ts":1710000000000}"""),
            isRetained = true
        )

        assertEquals(emptyList<Any>(), repo.devices.value)

        inbound.cleanup()
    }

    @Test
    fun routeAckUpdatesRouteSlotsWithoutShowingRawAckToUiLayer() = runBlocking {
        val repo = SolarCleanRepo()
        val inbound = SolarCleanMqttInbound(repo)

        inbound.handleEvent(
            linkSn = SERIAL,
            eventType = "ack",
            json = JSONObject(
                """
                {
                  "msgId": "route-list-1",
                  "ofType": "routeList",
                  "ok": true,
                  "code": 0,
                  "data": {
                    "slots": [
                      { "index": 1, "bytes": 2048, "valid": true }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        val state = repo.devices.value.single()
        assertEquals("route-list-1", state.lastAck?.msgId)
        assertEquals(true, state.lastAck?.ok)
        assertEquals(1, state.routeSlots.single().index)
        assertEquals(2048L, state.routeSlots.single().bytes)
        assertEquals(true, state.routeSlots.single().valid)
        assertNull(state.lastAck?.message)

        inbound.cleanup()
    }

    @Test
    fun downloadErrorMarksDownloadInactiveAndKeepsUserFacingMessage() = runBlocking {
        val repo = SolarCleanRepo()
        val inbound = SolarCleanMqttInbound(repo)

        inbound.handleEvent(
            linkSn = SERIAL,
            eventType = "downloadError",
            json = JSONObject("""{"slot":2,"code":340,"msg":"CRC 校验失败","retries":1}""")
        )

        val state = repo.devices.value.single()
        val event = state.lastEvent as SolarCleanEvent.DownloadError
        assertEquals(2, event.slot)
        assertEquals(340, event.code)
        assertEquals("CRC 校验失败", event.message)
        assertEquals(1, event.retries)
        assertEquals(false, state.download?.active)
        assertEquals(0.0, state.download?.percent)
        assertEquals(0L, state.download?.bytes)

        inbound.cleanup()
    }

    @Test
    fun routeExecuteFinishedKeepsExistingDownloadProgress() = runBlocking {
        val repo = SolarCleanRepo()
        val inbound = SolarCleanMqttInbound(repo)

        inbound.handleEvent(
            linkSn = SERIAL,
            eventType = "downloadProgress",
            json = JSONObject("""{"slot":1,"bytes":512,"total":1024,"pct":50.0}""")
        )
        inbound.handleEvent(
            linkSn = SERIAL,
            eventType = "routeExecuteFinished",
            json = JSONObject("""{"slot":1,"ok":true}""")
        )

        val state = repo.devices.value.single()
        val event = state.lastEvent as SolarCleanEvent.RouteExecuteFinished
        assertEquals(1, event.slot)
        assertEquals(true, event.ok)
        assertEquals(true, state.download?.active)
        assertEquals(50.0, state.download?.percent)
        assertEquals(512L, state.download?.bytes)

        inbound.cleanup()
    }

    @Test
    fun statePayloadParsesTelemetryMqttAndDownloadFields() = runBlocking {
        val repo = SolarCleanRepo()
        val inbound = SolarCleanMqttInbound(repo)

        inbound.handleEvent(
            linkSn = SERIAL,
            eventType = "state",
            json = JSONObject(
                """
                {
                  "lat": 31.2304,
                  "lon": 121.4737,
                  "alt": 18.5,
                  "speed": 2.4,
                  "yaw": 91.0,
                  "pitch": -3.5,
                  "roll": 1.2,
                  "sat": 17,
                  "battery": 76.5,
                  "waypoint": 4,
                  "water": 62,
                  "mqtt": { "tcp": true, "lastErr": 0 },
                  "download": { "slot": 3, "active": true, "pct": 42.5, "bytes": 425, "total": 1000 },
                  "ts": 8481
                }
                """.trimIndent()
            )
        )

        val state = repo.devices.value.single()
        assertEquals(true, state.isOnline)
        assertEquals(31.2304, state.latitude)
        assertEquals(121.4737, state.longitude)
        assertEquals(18.5, state.altitudeMeters)
        assertEquals(2.4, state.speedMetersPerSecond)
        assertEquals(91.0, state.yawDegrees)
        assertEquals(-3.5, state.pitchDegrees)
        assertEquals(1.2, state.rollDegrees)
        assertEquals(17, state.satelliteCount)
        assertEquals(76.5, state.batteryPercent)
        assertEquals(4, state.waypointIndex)
        assertEquals(62, state.waterLevel)
        assertEquals(true, state.mqttConnected)
        assertEquals(0, state.mqttLastError)
        assertEquals(3, state.download?.slot)
        assertEquals(true, state.download?.active)
        assertEquals(42.5, state.download?.percent)
        assertEquals(425L, state.download?.bytes)
        assertEquals(1000L, state.download?.total)
        assertEquals(8481L, state.timestamp)

        inbound.cleanup()
    }

    @Test
    fun retainedStateKeepsTelemetryButDoesNotMarkDeviceOnline() = runBlocking {
        val repo = SolarCleanRepo()
        val inbound = SolarCleanMqttInbound(repo)

        inbound.handleEvent(
            linkSn = SERIAL,
            eventType = "state",
            json = JSONObject("""{"battery":66.0,"water":40,"ts":1710000000000}"""),
            isRetained = true
        )

        val state = repo.devices.value.single()
        assertEquals(false, state.isOnline)
        assertEquals(66.0, state.batteryPercent)
        assertEquals(40, state.waterLevel)
        assertEquals(1710000000000L, state.timestamp)

        inbound.cleanup()
    }

    private companion object {
        const val SERIAL = "SOLAR-001"
    }
}
