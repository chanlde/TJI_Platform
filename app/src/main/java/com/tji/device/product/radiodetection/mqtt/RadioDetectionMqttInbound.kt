package com.tji.device.product.radiodetection.mqtt

import android.util.Log
import com.tji.device.product.radiodetection.protocol.RadioRidParser
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import com.tji.device.product.radiodetection.replay.RadioDetectionReplayStore

class RadioDetectionMqttInbound(
    private val repository: RadioDetectionRepository,
    private val replayStore: RadioDetectionReplayStore
) {
    suspend fun handleMessage(
        serialNumber: String,
        message: String,
        isRetained: Boolean = false
    ) {
        if (message.equals("online", ignoreCase = true) || message.equals("offline", ignoreCase = true)) {
            repository.updateOnlineStatus(serialNumber, message.equals("online", ignoreCase = true))
            return
        }

        RadioRgbAckParser.parse(message)?.let { ack ->
            repository.updateRgbAck(serialNumber, ack)
            Log.d(TAG, "RadioDetection RGB ack: deviceId=$serialNumber msgId=${ack.msgId} ok=${ack.ok} code=${ack.code}")
            return
        }

        val packet = RadioRidParser.parse(message)
        if (packet != null) {
            replayStore.recordRidPayload(serialNumber, message)
            repository.upsertRidPacket(serialNumber, packet)
            Log.d(TAG, "RadioDetection RID: deviceId=$serialNumber target=${packet.targetId} retained=$isRetained")
        }
    }

    fun cleanup() = Unit

    private companion object {
        const val TAG = "RadioDetectionMqttInbound"
    }
}
