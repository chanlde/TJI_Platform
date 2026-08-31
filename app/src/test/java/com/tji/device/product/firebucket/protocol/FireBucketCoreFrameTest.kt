package com.tji.device.product.firebucket.protocol

import com.tji.device.product.firebucket.model.ControlMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FireBucketCoreFrameTest {
    @Test
    fun setServoMatchesLinkGoldenFrame() {
        val frame = FireBucketCoreFrame.encodeSetServo(
            deviceId = "FB00A123",
            mode = ControlMode.ABSOLUTE,
            angleDegrees = 90,
            speed = 100,
            sequence = 0x0021
        )

        assertArrayEquals(
            hex("54 4A 01 04 02 00 00 21 00 0D 08 46 42 30 30 41 31 32 33 00 03 84 64 1B EC"),
            frame
        )
    }

    @Test
    fun supportsRealSixteenCharacterBucketSerialNumber() {
        val frame = FireBucketCoreFrame.encodeSetServo(
            deviceId = "E46540D8C3143D2A",
            mode = ControlMode.ABSOLUTE,
            angleDegrees = 0,
            speed = 0,
            sequence = 0x23
        )

        assertEquals(16, frame[10].toInt() and 0xFF)
        assertEquals("E46540D8C3143D2A", frame.copyOfRange(11, 27).decodeToString())
    }

    @Test
    fun acceptedAckMustMatchSequenceAndCrc() {
        FireBucketCoreFrame.requireAcceptedAck(
            frame = hex("54 4A 01 04 02 01 00 21 00 01 00 C1 BD"),
            expectedSequence = 0x0021
        )
    }

    @Test
    fun rejectsInvalidControlRange() {
        assertThrows(IllegalArgumentException::class.java) {
            FireBucketCoreFrame.encodeSetServo(
                deviceId = "FB00A123",
                mode = ControlMode.ABSOLUTE,
                angleDegrees = 361,
                speed = 100,
                sequence = 1
            )
        }
    }

    @Test
    fun deviceIdUsesTheSameRestrictedAsciiSetAsLink() {
        assertThrows(IllegalArgumentException::class.java) {
            FireBucketCoreFrame.encodeSetServo(
                deviceId = "FB-0A123",
                mode = ControlMode.ABSOLUTE,
                angleDegrees = 90,
                speed = 100,
                sequence = 1
            )
        }
    }

    @Test
    fun decodesStatusReportGoldenFrame() {
        val status = FireBucketCoreFrame.decodeStatusReport(
            hex(
                "54 4A 01 04 03 02 00 22 00 1C 08 46 42 30 30 41 31 32 33 01 " +
                    "03 85 00 00 08 89 09 74 03 66 00 00 0E 10 00 00 00 7B 59 2B"
            )
        )

        assertEquals("FB00A123", status.serialNumber)
        assertTrue(status.isOnline)
        assertEquals(90.1, status.currentAngle, 0.0)
        assertEquals(218.5, status.currentCurrent, 0.0)
        assertEquals(24.2, status.inputVoltage, 0.0)
        assertEquals(87.0, status.batteryPercentage, 0.0)
        assertEquals(123L, status.uptimeSeconds)
    }

    private fun hex(value: String): ByteArray = value
        .trim()
        .split(Regex("\\s+"))
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
