package com.tji.device.product.common

import kotlinx.coroutines.delay

/**
 * Publishes a command before starting its device ACK timeout window.
 *
 * MQTT publish latency is transport time, not device response time. Keeping the
 * order here explicit prevents slow broker connections from consuming the ACK
 * budget before the device has received the command.
 */
internal suspend fun publishThenWaitForDeviceAck(
    ackTimeoutMs: Long,
    waitForTimeout: suspend (Long) -> Unit = { delay(it) },
    publish: suspend () -> Unit
) {
    require(ackTimeoutMs > 0) { "ACK timeout must be positive" }
    publish()
    waitForTimeout(ackTimeoutMs)
}
