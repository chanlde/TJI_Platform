package com.tji.device.product.firebucket.mqtt

import android.util.Log
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.model.Switch
import com.tji.device.product.firebucket.repository.FireBucketLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 消防吊桶（FireBucket）端使用的 **整包** MQTT 入站协议：Link 启动/心跳/离线、子设备增删改、subDevices 解析。
 * 与 `MqttEventHandler` 的关系：仅当路由到 FireBucket 时才会进入本类。
 */
class FireBucketMqttInbound(
    private val linkDeviceRepo: FireBucketLinkRepository
) {

    private val heartbeatJobs = mutableMapOf<String, Job>()
    private val heartbeatTimeout = 5000L

    /**
     * 处理本产品线在 `lifecycle` / `status` 上约定的 [event_type]（见 when 分支）。
     */
    suspend fun handleEvent(linkSn: String, eventType: String, json: JSONObject) {
        when (eventType) {
            "LinkDeviceStartup" -> handleLinkDeviceStartup(json)
            "LinkDeviceHeartbeat" -> handleLinkHeartBeat(linkSn, json)
            "LinkDeviceOffline" -> handleLinkDeviceOffline(linkSn)
            "SubDeviceAdded" -> handleSubDeviceAdded(linkSn, json)
            "SubDeviceRemoved" -> handleSubDeviceRemoved(linkSn, json)
            "SubDeviceStatusChanged" -> handleSubDeviceStatusChanged(linkSn, json)
            else -> Log.w(TAG, "FireBucket: 未处理事件 $eventType")
        }
    }

    private suspend fun handleLinkDeviceStartup(json: JSONObject) {
        Log.d(TAG, "处理 LinkDeviceStartup")

        val linkDevice = FireBucketLinkDevice(
            event_type = json.getString("event_type"),
            serial_number = json.getString("serial_number"),
            deviceName = json.getString("deviceName"),
            deviceType = json.getString("deviceType"),
            manufacturer = json.getString("manufacturer"),
            deviceModel = json.getString("deviceModel"),
            isOnline = json.getBoolean("isOnline"),
            hwVersion = json.getString("hwVersion"),
            swVersion = json.getString("swVersion"),
            uptime = json.getInt("uptime"),
            deviceConfig = json.getString("deviceConfig"),
            subDevices = parseSubDevices(json.getJSONArray("subDevices")),
            timestamp = json.getString("timestamp"),
            productType = ProductType.FireBucket
        )
        linkDeviceRepo.updateLinkDevice(linkDevice)
    }

    private suspend fun handleLinkHeartBeat(linkSn: String, json: JSONObject) {
        val serialNumber = json.optString("serial_number").ifBlank {
            json.optString("serialNumber").ifBlank { linkSn }
        }
        val isOnline = json.getBoolean("isOnline")
        linkDeviceRepo.updateLinkDeviceStatus(serialNumber, isOnline)
        resetHeartbeatTimer(serialNumber)
    }

    private fun resetHeartbeatTimer(serialNumber: String) {
        heartbeatJobs.remove(serialNumber)?.cancel()
        heartbeatJobs[serialNumber] = CoroutineScope(Dispatchers.IO).launch {
            delay(heartbeatTimeout)
            Log.w(TAG, "心跳超时，设备离线: $serialNumber")
            linkDeviceRepo.updateLinkDeviceStatus(serialNumber, false)
        }
    }

    private suspend fun handleLinkDeviceOffline(serialNumber: String) {
        Log.d(TAG, "LinkDeviceOffline, LinkSN: $serialNumber")
        heartbeatJobs.remove(serialNumber)?.cancel()
        linkDeviceRepo.updateLinkDeviceStatus(serialNumber, false)
    }

    private suspend fun handleSubDeviceAdded(linkSn: String, json: JSONObject) {
        val switch = json.toSwitch()
        Log.d(TAG, "SubDevice 数据: $switch")
        linkDeviceRepo.addSubDevice(linkSn, switch)
    }

    private suspend fun handleSubDeviceRemoved(linkSn: String, json: JSONObject) {
        val switchSn = json.getString("serial_number")
        linkDeviceRepo.removeSubDevice(linkSn, switchSn)
    }

    private suspend fun handleSubDeviceStatusChanged(linkSn: String, json: JSONObject) {
        linkDeviceRepo.updateSubDevice(linkSn, json.toSwitch())
    }

    fun parseSubDevices(array: JSONArray): List<Switch> {
        return (0 until array.length()).map { i ->
            array.getJSONObject(i).toSwitch()
        }
    }

    private fun JSONObject.toSwitch(): Switch {
        return Switch(
            serialNumber = getRequiredString("serial_number", "serialNumber"),
            deviceName = getString("deviceName"),
            deviceType = getString("deviceType"),
            isOnline = getBoolean("isOnline"),
            currentAngle = getDouble("currentAngle"),
            currentCurrent = getDouble("currentCurrent"),
            inputVoltage = getDouble("inputVoltage"),
            servoMinAngle = getDouble("servoMinAngle"),
            servoMaxAngle = getDouble("servoMaxAngle"),
            uptime = getInt("uptime"),
            productType = ProductCatalog.inferType(
                deviceType = optString("deviceType"),
                deviceModel = optString("deviceModel"),
                deviceName = optString("deviceName")
            )
        )
    }

    private fun JSONObject.getRequiredString(vararg keys: String): String {
        val key = keys.firstOrNull { has(it) && !isNull(it) }
            ?: error("FireBucket Switch 缺少字段: ${keys.joinToString(" / ")}")
        return getString(key)
    }

    fun cleanup() {
        heartbeatJobs.values.forEach { it.cancel() }
        heartbeatJobs.clear()
    }

    private companion object {
        const val TAG = "FireBucketMqttInbound"
    }
}
