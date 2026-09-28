package com.tji.device.product.firegun.protocol

import com.tji.device.product.firegun.model.FireGunDeviceStatus
import com.tji.device.product.firegun.model.FireGunLinkHeartbeat
import com.tji.device.product.firegun.model.FireGunLinkState
import org.json.JSONArray
import org.json.JSONObject

enum class FireGunAction(val wireValue: String) {
    OPEN("open"),
    CLOSE("close"),
    STOP("stop");

    companion object {
        fun fromWire(value: String): FireGunAction =
            entries.firstOrNull { it.wireValue == value }
                ?: throw IllegalArgumentException("未知动作: $value")
    }
}

sealed interface FireGunResponse {
    val serialNumber: String
    val requestId: String
    val success: Boolean
    val state: String
    val error: String?

    data class Lock(
        override val serialNumber: String,
        override val requestId: String,
        override val success: Boolean,
        val action: String,
        override val state: String,
        val pulseMs: Long,
        override val error: String?
    ) : FireGunResponse

    data class Actuator(
        override val serialNumber: String,
        override val requestId: String,
        override val success: Boolean,
        val action: FireGunAction,
        override val state: String,
        val maxRunMs: Long,
        override val error: String?
    ) : FireGunResponse
}

@Suppress("TooManyFunctions") // Request, status and ACK parsing share the same wire contract.
object FireGunProtocol {
    fun unlockRequest(targetSerial: String, requestId: String): JSONObject = JSONObject().apply {
        put("event_type", "LockControlRequest")
        put("serial_number", requiredValue(targetSerial, "设备序列号"))
        put("request_id", requiredValue(requestId, "request_id"))
        put("action", "unlock")
    }

    fun actuatorRequest(
        targetSerial: String,
        requestId: String,
        action: FireGunAction
    ): JSONObject = JSONObject().apply {
        put("event_type", "ActuatorControlRequest")
        put("serial_number", requiredValue(targetSerial, "设备序列号"))
        put("request_id", requiredValue(requestId, "request_id"))
        put("action", action.wireValue)
    }

    fun parseResponse(payload: String): FireGunResponse = parseResponse(JSONObject(payload))

    fun parseResponse(json: JSONObject): FireGunResponse =
        when (val eventType = json.requiredString("event_type")) {
            "LockControlResponse" -> parseLockResponse(json)
            "ActuatorControlResponse" -> parseActuatorResponse(json)
            else -> throw IllegalArgumentException("未知消防喷枪回执类型: $eventType")
        }

    fun parseStatus(linkSerial: String, json: JSONObject): FireGunDeviceStatus {
        val eventType = json.requiredString("event_type")
        require(eventType == STATUS_EVENT_TYPE) { "未知消防喷枪状态类型: $eventType" }
        return parseDeviceStatus(linkSerial, json)
    }

    fun parseLinkStartup(topicLinkSerial: String, json: JSONObject): FireGunLinkState {
        val eventType = json.requiredString("event_type")
        require(eventType == STARTUP_EVENT_TYPE) { "未知 Link 启动类型: $eventType" }
        val payloadLinkSerial = json.requiredString("serial_number")
        require(payloadLinkSerial == requiredValue(topicLinkSerial, "Link 序列号")) {
            "Link 序列号与 Topic 不一致"
        }
        val subDevices = json.optJSONArray("subDevices") ?: JSONArray()
        val deviceStatus = if (subDevices.length() > 0) {
            parseDeviceStatus(payloadLinkSerial, subDevices.getJSONObject(0))
        } else {
            null
        }
        return FireGunLinkState(
            serialNumber = payloadLinkSerial,
            name = json.optString("deviceName").trim().ifEmpty { payloadLinkSerial },
            isOnline = json.getBoolean("isOnline"),
            hwVersion = json.optionalString("hwVersion"),
            swVersion = json.optionalString("swVersion"),
            uptime = json.optionalLong("uptime"),
            timestamp = json.optionalLong("timestamp"),
            deviceStatus = deviceStatus
        )
    }

    fun parseLinkHeartbeat(topicLinkSerial: String, json: JSONObject): FireGunLinkHeartbeat {
        val eventType = json.requiredString("event_type")
        require(eventType == HEARTBEAT_EVENT_TYPE) { "未知 Link 心跳类型: $eventType" }
        val payloadLinkSerial = json.requiredString("serial_number")
        require(payloadLinkSerial == requiredValue(topicLinkSerial, "Link 序列号")) {
            "Link 序列号与 Topic 不一致"
        }
        return FireGunLinkHeartbeat(
            serialNumber = payloadLinkSerial,
            isOnline = json.getBoolean("isOnline"),
            uptime = json.optionalLong("uptime"),
            timestamp = json.optionalLong("timestamp")
        )
    }

    private fun parseDeviceStatus(
        linkSerial: String,
        json: JSONObject
    ): FireGunDeviceStatus = FireGunDeviceStatus(
            linkSerial = requiredValue(linkSerial, "Link 序列号"),
            serialNumber = json.requiredString("serial_number", "serialNumber"),
            deviceName = json.optString("deviceName").trim(),
            deviceType = json.optString("deviceType").trim(),
            isOnline = json.getBoolean("isOnline"),
            currentAngle = json.optionalDouble("currentAngle"),
            currentCurrent = json.optionalDouble("currentCurrent"),
            inputVoltage = json.optionalDouble("inputVoltage"),
            servoMinAngle = json.optionalDouble("servoMinAngle"),
            servoMaxAngle = json.optionalDouble("servoMaxAngle"),
            uptime = json.optionalLong("uptime"),
            timestamp = json.optionalLong("timestamp"),
            rssi = json.optionalInt("rssi"),
            lockState = json.optionalString("lockState"),
            actuatorState = json.optionalString("actuatorState")
        )

    private fun parseLockResponse(json: JSONObject): FireGunResponse.Lock {
        val action = json.requiredString("action")
        require(action == "unlock") { "未知解锁动作: $action" }
        val state = json.requiredString("state")
        require(state == "unlocking" || state == "locked") { "未知解锁状态: $state" }
        return FireGunResponse.Lock(
            serialNumber = json.requiredString("serial_number"),
            requestId = json.requiredString("request_id"),
            success = json.getBoolean("success"),
            action = action,
            state = state,
            pulseMs = json.optLong("pulse_ms", 0L),
            error = json.optionalError()
        )
    }

    private fun parseActuatorResponse(json: JSONObject): FireGunResponse.Actuator {
        val state = json.requiredString("state")
        require(state in ACTUATOR_STATES) { "未知动作状态: $state" }
        return FireGunResponse.Actuator(
            serialNumber = json.requiredString("serial_number"),
            requestId = json.requiredString("request_id"),
            success = json.getBoolean("success"),
            action = FireGunAction.fromWire(json.requiredString("action")),
            state = state,
            maxRunMs = json.optLong("max_run_ms", 0L),
            error = json.optionalError()
        )
    }

    private fun JSONObject.requiredString(name: String): String =
        requiredValue(getString(name), name)

    private fun JSONObject.requiredString(vararg names: String): String {
        names.forEach { name ->
            if (has(name) && !isNull(name)) {
                return requiredValue(getString(name), names.joinToString("/"))
            }
        }
        throw IllegalArgumentException("缺少字段: ${names.joinToString("/")}")
    }

    private fun JSONObject.optionalError(): String? =
        optString("error").trim().takeIf(String::isNotEmpty)

    private fun JSONObject.optionalString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).trim().takeIf(String::isNotEmpty) else null

    private fun JSONObject.optionalDouble(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name).takeUnless(Double::isNaN) else null

    private fun JSONObject.optionalLong(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null

    private fun JSONObject.optionalInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun requiredValue(value: String, label: String): String =
        value.trim().also { require(it.isNotEmpty()) { "$label 不能为空" } }

    private val ACTUATOR_STATES = setOf("stopped", "opening", "closing")
    const val STATUS_EVENT_TYPE = "SubDeviceStatusChanged"
    const val STARTUP_EVENT_TYPE = "LinkDeviceStartup"
    const val HEARTBEAT_EVENT_TYPE = "LinkDeviceHeartbeat"
}
