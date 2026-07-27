package com.tji.device.product.radiodetection.mqtt

import android.util.Log
import com.tji.device.BuildConfig
import com.tji.device.product.radiodetection.protocol.RadioRidParser
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import com.tji.device.product.radiodetection.replay.RadioDetectionReplayStore
import org.json.JSONObject

class RadioDetectionMqttInbound(
    private val repository: RadioDetectionRepository,
    private val recordReplayPayload: (serialNumber: String, payload: String) -> Unit
) {
    constructor(
        repository: RadioDetectionRepository,
        replayStore: RadioDetectionReplayStore
    ) : this(repository, replayStore::recordRidPayload)

    suspend fun handleMessage(
        serialNumber: String,
        message: String,
        isRetained: Boolean = false,
        parsedJson: JSONObject? = null
    ) {
        if (message.equals("online", ignoreCase = true) || message.equals("offline", ignoreCase = true)) {
            if (isRetained && message.equals("online", ignoreCase = true)) {
                debugLog { "忽略 retained online: deviceId=$serialNumber" }
                return
            }
            repository.updateOnlineStatus(serialNumber, message.equals("online", ignoreCase = true))
            return
        }

        // ACK 和 RID 都表示实时活动。使用 retained 副本会让旧命令或过期目标在重连后“复活”。
        if (isRetained) {
            debugLog { "忽略 retained 非生命周期消息: deviceId=$serialNumber" }
            return
        }

        val rgbAck = if (parsedJson != null) {
            RadioRgbAckParser.parse(parsedJson)
        } else {
            RadioRgbAckParser.parse(message)
        }
        rgbAck?.let { ack ->
            repository.updateRgbAck(serialNumber, ack)
            debugLog { "RGB ack: deviceId=$serialNumber msgId=${ack.msgId} ok=${ack.ok} code=${ack.code}" }
            return
        }

        // RID 主协议是十六进制 JSON；已经作为普通 JSON 解析的载荷按旧协议不视为 RID。
        val packet = if (parsedJson == null) RadioRidParser.parse(message) else null
        if (packet != null) {
            recordReplayPayload(serialNumber, message)
            repository.upsertRidPacket(serialNumber, packet)
            debugLog { "RID: deviceId=$serialNumber target=${packet.targetId} retained=$isRetained" }
        }
    }

    fun cleanup() = Unit

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    private companion object {
        const val TAG = "RadioDetectionMqttInbound"
    }
}
