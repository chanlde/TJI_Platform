package com.tji.device.product.solarclean.repository

import android.util.Log
import com.tji.device.BuildConfig
import com.tji.device.data.model.ProductType
import com.tji.device.product.solarclean.model.SolarCleanCommand
import com.tji.device.product.solarclean.model.SolarCleanCommandCode
import com.tji.device.product.solarclean.mqtt.SolarCleanMqttTopics
import com.tji.device.service.mqtt.ProductMqttRouter
import org.json.JSONObject

interface SolarCleanControlRepository {
    suspend fun sendCommand(serialNumber: String, command: SolarCleanCommand)
}

class SolarCleanControlRepo : SolarCleanControlRepository {

    override suspend fun sendCommand(serialNumber: String, command: SolarCleanCommand) {
        val topic = SolarCleanMqttTopics.controlTopic(serialNumber)
        val payload = command.toJson()
        val message = payload.toString()
        val requestAt = if (BuildConfig.DEBUG) System.currentTimeMillis() else 0L
        val msgId = payload.optString("msgId")
        val cmd = payload.optInt("cmd")
        val payloadTs = payload.optLong("ts")

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "SolarClean 控制指令请求发送: topic=$topic, msgId=$msgId, cmd=$cmd, " +
                    "requestAt=$requestAt, payloadTs=$payloadTs, delta=${requestAt - payloadTs}ms"
            )
        }

        ProductMqttRouter.managerFor(ProductType.SolarClean).publishAwait(
            topic = topic,
            message = message,
            qos = 0,
            queueWhenDisconnected = false
        ).getOrThrow()
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "SolarClean 控制指令发送成功: topic=$topic, msgId=$msgId, " +
                    "cost=${System.currentTimeMillis() - requestAt}ms, message=$message"
            )
        }
    }

    private fun SolarCleanCommand.toJson(): JSONObject {
        return JSONObject().apply {
            put("v", 1)
            put("msgId", msgId)
            put("ts", System.currentTimeMillis())
            put("cmd", this@toJson.commandCode())
            put("cmdName", this@toJson.commandName())

            when (this@toJson) {
                is SolarCleanCommand.Ping -> {
                    // No extra payload.
                }
                is SolarCleanCommand.PumpSwitch -> {
                    put("on", on)
                }
                is SolarCleanCommand.PumpPressure -> {
                    put("percent", percent)
                }
                is SolarCleanCommand.SprayAngle -> {
                    put("amplitudeDeg", amplitudeDeg)
                }
                is SolarCleanCommand.ServoSwing -> {
                    put("on", on)
                    speedPercent?.let { put("speedPercent", it.coerceIn(0.0, 100.0)) }
                    amplitude?.let { put("amplitude", it) }
                }
                is SolarCleanCommand.SwingSpeed -> {
                    put("speedPercent", speedPercent)
                }
                is SolarCleanCommand.GetDeviceInfo -> {
                    // No extra payload.
                }
                is SolarCleanCommand.RouteList -> {
                    // No extra payload.
                }
                is SolarCleanCommand.RouteDelete -> {
                    put("slot", slot)
                }
                is SolarCleanCommand.RouteDownload -> {
                    put("slot", slot)
                    put("url", url)
                    put("size", size)
                    checksum?.let { put("checksum", it) }
                }
                is SolarCleanCommand.RouteDownloadCancel -> {
                    slot?.let { put("slot", it) }
                }
                is SolarCleanCommand.ExecuteSlot -> {
                    put("slot", slot)
                }
            }
        }
    }

    private fun SolarCleanCommand.commandCode(): Int = when (this) {
        is SolarCleanCommand.Ping -> SolarCleanCommandCode.PING
        is SolarCleanCommand.GetDeviceInfo -> SolarCleanCommandCode.GET_DEVICE_INFO
        is SolarCleanCommand.PumpSwitch -> SolarCleanCommandCode.SET_PUMP
        is SolarCleanCommand.PumpPressure -> SolarCleanCommandCode.SET_PUMP_PRESSURE
        is SolarCleanCommand.SprayAngle -> SolarCleanCommandCode.SET_SPRAY_ANGLE
        is SolarCleanCommand.SwingSpeed -> SolarCleanCommandCode.SET_SWING_SPEED
        is SolarCleanCommand.ServoSwing -> SolarCleanCommandCode.SET_SERVO_SWING
        is SolarCleanCommand.RouteList -> SolarCleanCommandCode.ROUTE_LIST
        is SolarCleanCommand.RouteDelete -> SolarCleanCommandCode.ROUTE_DELETE
        is SolarCleanCommand.RouteDownload -> SolarCleanCommandCode.ROUTE_DOWNLOAD
        is SolarCleanCommand.RouteDownloadCancel -> SolarCleanCommandCode.ROUTE_DOWNLOAD_CANCEL
        is SolarCleanCommand.ExecuteSlot -> SolarCleanCommandCode.EXECUTE_SLOT
    }

    private fun SolarCleanCommand.commandName(): String = when (this) {
        is SolarCleanCommand.Ping -> "PING"
        is SolarCleanCommand.GetDeviceInfo -> "GET_DEVICE_INFO"
        is SolarCleanCommand.PumpSwitch -> "SET_PUMP"
        is SolarCleanCommand.PumpPressure -> "SET_PUMP_PRESSURE"
        is SolarCleanCommand.SprayAngle -> "SET_SPRAY_ANGLE"
        is SolarCleanCommand.SwingSpeed -> "SET_SWING_SPEED"
        is SolarCleanCommand.ServoSwing -> "SET_SERVO_SWING"
        is SolarCleanCommand.RouteList -> "ROUTE_LIST"
        is SolarCleanCommand.RouteDelete -> "ROUTE_DELETE"
        is SolarCleanCommand.RouteDownload -> "ROUTE_DOWNLOAD"
        is SolarCleanCommand.RouteDownloadCancel -> "ROUTE_DOWNLOAD_CANCEL"
        is SolarCleanCommand.ExecuteSlot -> "EXECUTE_SLOT"
    }

    private companion object {
        const val TAG = "SolarCleanControlRepo"
    }
}
