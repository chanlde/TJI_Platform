package com.tji.device.product.ota

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProductOtaMqttParserTest {

    @Test
    fun resolvesGetDeviceInfoCamelCasePayload() {
        val json = JSONObject(
            """
            {
              "v": 1,
              "cmdName": "GET_DEVICE_INFO",
              "deviceId": "TEWNHZDBK",
              "ts": 8481,
              "hardwareVersion": "HW-A",
              "firmwareVersion": "0.0.1",
              "innerVersion": 3,
              "otaStatus": "IDLE"
            }
            """.trimIndent()
        )

        val eventType = ProductOtaMqttParser.resolveEventType(json)
        val info = ProductOtaMqttParser.parseDeviceInfo(json)

        assertEquals("deviceInfo", eventType)
        assertNotNull(info)
        assertEquals("HW-A", info?.hardwareVersion)
        assertEquals("0.0.1", info?.firmwareVersion)
        assertEquals(3, info?.firmwareInnerVersion)
        assertEquals("IDLE", info?.otaStatus)
    }

    @Test
    fun parsesDeviceInfoWrappedInData() {
        val json = JSONObject(
            """
            {
              "type": "DEVICE_INFO",
              "ts": 8481,
              "data": {
                "hardwareVersion": "HW-B",
                "firmwareVersion": "1.2.3",
                "innerVersion": 12
              }
            }
            """.trimIndent()
        )

        val eventType = ProductOtaMqttParser.resolveEventType(json)
        val info = ProductOtaMqttParser.parseDeviceInfo(json)

        assertEquals("deviceInfo", eventType)
        assertNotNull(info)
        assertEquals("HW-B", info?.hardwareVersion)
        assertEquals("1.2.3", info?.firmwareVersion)
        assertEquals(12, info?.firmwareInnerVersion)
        assertEquals(8481L, info?.timestamp)
    }

    @Test
    fun parsesDownloadTestDoneStatusPayload() {
        val json = JSONObject(
            """
            {
              "v": 1,
              "type": "ota_status",
              "deviceId": "TEWNHZDBK",
              "cmdId": "ota-test-001",
              "seq": 12,
              "otaStatus": "OTA_TEST_DONE",
              "progress": 100,
              "targetVersion": "V0.7.0",
              "targetInnerVersion": 7,
              "fileSize": 187340,
              "downloadedBytes": 187340,
              "msg": "download verify ok, no reboot"
            }
            """.trimIndent()
        )

        val eventType = ProductOtaMqttParser.resolveEventType(json)
        val status = ProductOtaMqttParser.parseOtaStatus(json)

        assertEquals("otaStatus", eventType)
        assertNotNull(status)
        assertEquals("OTA_TEST_DONE", status?.status)
        assertEquals("ota-test-001", status?.cmdId)
        assertEquals(12L, status?.seq)
        assertEquals(100, status?.progress)
        assertEquals(7, status?.targetInnerVersion)
        assertEquals(187340L, status?.downloaded)
        assertEquals(187340L, status?.total)
    }

    @Test
    fun normalizesFractionalProgressToPercent() {
        val json = JSONObject(
            """
            {
              "type": "otaStatus",
              "otaStatus": "OTA_DOWNLOADING",
              "progress": 0.36
            }
            """.trimIndent()
        )

        val status = ProductOtaMqttParser.parseOtaStatus(json)

        assertEquals(36, status?.progress)
    }
}
