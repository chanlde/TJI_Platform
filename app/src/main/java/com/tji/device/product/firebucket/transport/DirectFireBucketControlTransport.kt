package com.tji.device.product.firebucket.transport

import com.tji.device.product.firebucket.protocol.FireBucketCoreFrame
import java.util.concurrent.atomic.AtomicInteger

class DirectFireBucketControlTransport(
    private val exchange: suspend (ByteArray) -> ByteArray,
    initialSequence: Int = (System.currentTimeMillis() % 0xFFFF).toInt()
) : FireBucketControlTransport {
    private val sequence = AtomicInteger(initialSequence.coerceIn(0, 0xFFFE))

    override suspend fun setServo(command: FireBucketSetServoCommand) {
        val requestSequence = nextSequence()
        val request = FireBucketCoreFrame.encodeSetServo(
            deviceId = command.deviceId,
            mode = command.mode,
            angleDegrees = command.angleDegrees,
            speed = command.speed,
            sequence = requestSequence
        )
        val response = exchange(request)
        FireBucketCoreFrame.requireAcceptedAck(response, requestSequence)
    }

    private fun nextSequence(): Int = sequence.updateAndGet { current ->
        if (current >= 0xFFFF) 1 else current + 1
    }
}
