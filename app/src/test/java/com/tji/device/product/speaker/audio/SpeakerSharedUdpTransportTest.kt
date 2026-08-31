package com.tji.device.product.speaker.audio

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeakerSharedUdpTransportTest {

    @Test
    fun dispatchesRegistrationAndBinaryAckOnlyToMatchingMediaRoute() = runBlocking {
        val socket = DatagramSocket()
        val relay = InetAddress.getLoopbackAddress()
        SpeakerSharedUdpTransport.attach(socket, relay, RELAY_PORT)
        val lease = SpeakerSharedUdpTransport.acquireMediaRoute(
            DEVICE_ID, SESSION_ID, RECORD_ID, relay, RELAY_PORT
        )
        assertNotNull(lease)
        try {
            val registration =
                "HLAPPACK1 REGISTERED $DEVICE_ID $SESSION_ID $RECORD_ID".encodeToByteArray()
            assertTrue(SpeakerSharedUdpTransport.dispatch(registration, registration.size))
            assertArrayEquals(registration, withTimeout(1_000L) { lease!!.packets.receive() })

            val binaryAck = binaryAck(DEVICE_ID, SESSION_ID, RECORD_ID)
            assertTrue(SpeakerSharedUdpTransport.dispatch(binaryAck, binaryAck.size))
            assertArrayEquals(binaryAck, withTimeout(1_000L) { lease!!.packets.receive() })

            val other = binaryAck(DEVICE_ID, "OTHER_SESSION", RECORD_ID)
            assertFalse(SpeakerSharedUdpTransport.dispatch(other, other.size))
        } finally {
            lease?.close()
            SpeakerSharedUdpTransport.detach(socket)
            socket.close()
        }
    }

    private fun binaryAck(deviceId: String, sessionId: String, recordId: String): ByteArray {
        val device = deviceId.encodeToByteArray()
        val session = sessionId.encodeToByteArray()
        val record = recordId.encodeToByteArray()
        val headerBytes = 28 + device.size + session.size + record.size
        return ByteBuffer.allocate(headerBytes + 16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0xA55A.toShort())
            .put(2.toByte())
            .put(2.toByte())
            .putShort(headerBytes.toShort())
            .putShort((0x0008 or 0x0020).toShort())
            .putInt(0)
            .putInt(1)
            .putShort(48_000.toShort())
            .put(1.toByte())
            .put(20.toByte())
            .putShort(16)
            .putShort(960.toShort())
            .put(device.size.toByte())
            .put(session.size.toByte())
            .put(record.size.toByte())
            .put(0.toByte())
            .put(device)
            .put(session)
            .put(record)
            .put(ByteArray(16))
            .array()
    }

    private companion object {
        const val RELAY_PORT = 7_000
        const val DEVICE_ID = "T5TNBFM4Q"
        const val SESSION_ID = "MEDIA_SESSION"
        const val RECORD_ID = "MEDIA_RECORD"
    }
}
