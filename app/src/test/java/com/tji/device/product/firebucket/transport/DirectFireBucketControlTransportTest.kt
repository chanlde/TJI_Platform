package com.tji.device.product.firebucket.transport

import com.tji.device.product.firebucket.model.ControlMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class DirectFireBucketControlTransportTest {
    @Test
    fun sendsCoreFrameAndRequiresMatchingAck() = runTest {
        var request = byteArrayOf()
        val transport = DirectFireBucketControlTransport(
            exchange = { frame ->
                request = frame
                hex("54 4A 01 04 02 01 00 01 00 01 00 F6 F3")
            },
            initialSequence = 0
        )

        transport.setServo(
            FireBucketSetServoCommand(
                linkId = "LINK-001",
                deviceId = "FB00A123",
                angleDegrees = 90,
                speed = 100,
                mode = ControlMode.ABSOLUTE
            )
        )

        assertArrayEquals(
            hex("54 4A 01 04 02 00 00 01 00 0D 08 46 42 30 30 41 31 32 33 00 03 84 64 5D F8"),
            request
        )
    }

    private fun hex(value: String): ByteArray = value
        .trim()
        .split(Regex("\\s+"))
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
