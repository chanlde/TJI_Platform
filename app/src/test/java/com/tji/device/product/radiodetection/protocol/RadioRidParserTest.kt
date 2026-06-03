package com.tji.device.product.radiodetection.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RadioRidParserTest {

    @Test
    fun parsesHexEncodedPsdkRidPayload() {
        val result = RadioRidParser.parse(hexOf(
            """
            {
              "devId": "EXD001",
              "data": {
                "osid": "1581F5YHX23680022707",
                "RSSI": -44,
                "Fre": 4,
                "UAType": 2,
                "Status": 1,
                "Heading": 361,
                "Speed": 15,
                "Uprate": 0,
                "Lon": 116.295442,
                "Lat": 37.865112,
                "AltGeo": 120,
                "AltBaro": 118,
                "Height": 120,
                "Op_Lon": 116.296095,
                "Op_Lat": 37.864209,
                "Op_Alt": 12,
                "UATime": 1710000000
              }
            }
            """.trimIndent()
        ))

        assertNotNull(result)
        val packet = result!!
        assertEquals("EXD001", packet.sourceDeviceId)
        assertEquals("1581F5YHX23680022707", packet.targetId)
        assertEquals(-44, packet.rssi)
        assertEquals("2.4GHz", packet.frequencyLabel)
        assertEquals(116.295442, packet.droneLongitude, 0.000001)
        assertEquals(37.864209, packet.operatorLatitude, 0.000001)
        assertEquals(1710000000000L, packet.timestampMillis)
    }

    @Test
    fun parsesHexEncodedEsp32RidPayload() {
        val result = RadioRidParser.parse(hexOf(
            """
            {
              "type": "rid",
              "mac": "AA:BB:CC:DD:EE:FF",
              "sn": "RID-SN-001",
              "rssi": -58,
              "channel": 6,
              "ua_type": 2,
              "status": 1,
              "heading": 90,
              "speed": 1.2,
              "vertical_speed": 0,
              "drone_lon": 113.93,
              "drone_lat": 22.53,
              "alt_geo": 100,
              "alt_baro": 99,
              "height": 10,
              "operator_lon": 113.931,
              "operator_lat": 22.531,
              "operator_alt": 0,
              "timestamp": 1710000000
            }
            """.trimIndent()
        ))

        assertNotNull(result)
        val packet = result!!
        assertEquals("RID-SN-001", packet.targetId)
        assertEquals(-58, packet.rssi)
        assertEquals("2.4GHz", packet.frequencyLabel)
        assertEquals(113.93, packet.droneLongitude, 0.000001)
        assertEquals(22.531, packet.operatorLatitude, 0.000001)
    }

    @Test
    fun parsesCompactHexRidPayload() {
        val json = """{"type":"rid","mac":"AA","rssi":-70,"channel":149,"drone_lon":116.1,"drone_lat":37.1,"operator_lon":116.2,"operator_lat":37.2}"""

        val result = RadioRidParser.parse(hexOf(json))

        assertNotNull(result)
        val packet = result!!
        assertEquals("AA", packet.targetId)
        assertEquals("5GHz", packet.frequencyLabel)
    }

    @Test
    fun ignoresPlainJsonPayloads() {
        val result = RadioRidParser.parse("""{"type":"rid","mac":"AA","rssi":-70}""")

        assertNull(result)
    }

    private fun hexOf(json: String): String =
        json.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
}
