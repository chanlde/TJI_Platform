package com.tji.device.product.radiodetection.mqtt

import com.tji.device.product.radiodetection.model.RadioRgbColor
import com.tji.device.product.radiodetection.model.RadioRgbCommand
import com.tji.device.product.radiodetection.model.RadioRgbMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioRgbMqttPayloadsTest {

    @Test
    fun buildsBreathCommandJsonWithSpeed() {
        val json = RadioRgbCommand(
            msgId = "rgb-test-001",
            mode = RadioRgbMode.Breath,
            color = RadioRgbColor.Green,
            brightness = 120,
            speed = -5,
            save = false
        ).toMqttJson()

        assertEquals(1, json.getInt("v"))
        assertEquals("rgb-test-001", json.getString("msgId"))
        assertEquals("rgb", json.getString("module"))
        assertEquals("set", json.getString("action"))
        assertEquals("breath", json.getString("mode"))
        assertEquals("green", json.getString("color"))
        assertEquals(100, json.getInt("brightness"))
        assertEquals(0, json.getInt("speed"))
        assertFalse(json.getBoolean("save"))
    }

    @Test
    fun omitsSpeedForStrobeCommand() {
        val json = RadioRgbCommand(
            msgId = "rgb-test-002",
            mode = RadioRgbMode.Strobe,
            color = RadioRgbColor.RedBlue,
            brightness = 70,
            speed = 40,
            save = true
        ).toMqttJson()

        assertEquals("strobe", json.getString("mode"))
        assertEquals("red_blue", json.getString("color"))
        assertFalse(json.has("speed"))
        assertTrue(json.getBoolean("save"))
    }

    @Test
    fun parsesRgbAck() {
        val ack = RadioRgbAckParser.parse(
            """
            {
              "v": 1,
              "type": "ack",
              "msgId": "rgb-test-003",
              "module": "rgb",
              "action": "set",
              "ok": true,
              "code": 0,
              "msg": "rgb config saved",
              "ts": 123456
            }
            """.trimIndent()
        )

        assertNotNull(ack)
        assertEquals("rgb-test-003", ack!!.msgId)
        assertTrue(ack.ok)
        assertEquals(0, ack.code)
        assertEquals("rgb config saved", ack.message)
        assertEquals("默认灯语已保存", ack.statusText)
        assertEquals(123456L, ack.timestamp)
    }

    @Test
    fun mapsRgbErrorCodeToUserText() {
        val ack = RadioRgbAckParser.parse(
            """
            {
              "v": 1,
              "type": "ack",
              "msgId": "rgb-test-004",
              "module": "rgb",
              "action": "set",
              "ok": false,
              "code": 123,
              "msg": "ota in progress",
              "ts": 123456
            }
            """.trimIndent()
        )

        assertNotNull(ack)
        assertEquals("设备正在升级，灯语控制暂不可用", ack!!.statusText)
    }

    @Test
    fun supportsBrightnessZeroForLightOffPreview() {
        val json = RadioRgbCommand(
            msgId = "rgb-test-005",
            mode = RadioRgbMode.Steady,
            color = RadioRgbColor.Red,
            brightness = 0,
            speed = null,
            save = false
        ).toMqttJson()

        assertEquals(0, json.getInt("brightness"))
        assertFalse(json.getBoolean("save"))
    }

    @Test
    fun ignoresNonRgbAckJson() {
        val ack = RadioRgbAckParser.parse("""{"type":"rid","module":"radio"}""")

        assertNull(ack)
    }
}
