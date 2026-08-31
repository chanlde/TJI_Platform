package com.tji.device.product.firebucket.mqtt

import android.util.Log
import com.tji.device.BuildConfig
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.model.FireBucketSwitchState
import com.tji.device.product.firebucket.repository.FireBucketLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 消防吊桶（FireBucket）端使用的 **整包** MQTT 入站协议：Link 启动/心跳/离线、子设备增删改、subDevices 解析。
 * 与 `MqttEventHandler` 的关系：仅当路由到 FireBucket 时才会进入本类。
 */
class FireBucketMqttInbound(
    private val linkDeviceRepo: FireBucketLinkRepository,
    private val heartbeatTimeoutMillis: Long = DEFAULT_HEARTBEAT_TIMEOUT_MILLIS
) {

    private val heartbeatLock = Any()
    private var scope = newHeartbeatScope()
    private val heartbeatJobs = mutableMapOf<String, Job>()
    private val realtimeStatusLock = Any()
    private val realtimeLinkStatus = mutableMapOf<String, Boolean>()
    private val reportedSubDeviceStatus = mutableMapOf<String, MutableMap<String, Boolean>>()

    /**
     * 处理本产品线在 `lifecycle` / `status` 上约定的 [event_type]（见 when 分支）。
     */
    suspend fun handleEvent(
        linkSn: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean = false
    ) {
        when (eventType) {
            "LinkDeviceStartup" -> handleLinkDeviceStartup(json, isRetained)
            "LinkDeviceHeartbeat" -> if (!isRetained) handleLinkHeartBeat(linkSn, json)
            "LinkDeviceOffline" -> handleLinkDeviceOffline(linkSn)
            "SubDeviceAdded" -> if (!isRetained) handleSubDeviceAdded(linkSn, json)
            "SubDeviceRemoved" -> if (!isRetained) handleSubDeviceRemoved(linkSn, json)
            "SubDeviceStatusChanged" -> if (!isRetained) handleSubDeviceStatusChanged(linkSn, json)
            else -> Log.w(TAG, "FireBucket: 未处理事件 $eventType")
        }
    }

    private suspend fun handleLinkDeviceStartup(json: JSONObject, isRetained: Boolean) {
        debugLog { "处理 LinkDeviceStartup" }

        val serialNumber = json.getString("serial_number")
        val realtimeStatus = if (isRetained) {
            currentRealtimeLinkStatus(serialNumber)
        } else {
            json.getBoolean("isOnline").also {
                recordRealtimeLinkStatus(serialNumber, it)
            }
        }
        val reportedSubDevices = parseSubDevices(json.getJSONArray("subDevices"))
        recordReportedSubDevices(
            linkSn = serialNumber,
            switches = reportedSubDevices,
            preserveExisting = isRetained && realtimeStatus != null
        )
        val linkDevice = FireBucketLinkDevice(
            event_type = json.getString("event_type"),
            serial_number = serialNumber,
            deviceName = json.getString("deviceName"),
            deviceType = json.getString("deviceType"),
            manufacturer = json.getString("manufacturer"),
            deviceModel = json.getString("deviceModel"),
            isOnline = realtimeStatus ?: false,
            hwVersion = json.getString("hwVersion"),
            swVersion = json.getString("swVersion"),
            uptime = json.getInt("uptime"),
            deviceConfig = json.getString("deviceConfig"),
            subDevices = reportedSubDevices.map { switch ->
                switch.copy(isOnline = realtimeStatus == true && switch.isOnline)
            },
            timestamp = json.getString("timestamp"),
            productType = ProductType.FireBucket
        )
        if (isRetained) {
            linkDeviceRepo.applyRetainedLinkSnapshot(
                linkDevice = linkDevice,
                preserveExistingRealtimeState = realtimeStatus != null
            )
        } else {
            linkDeviceRepo.updateLinkDevice(linkDevice)
        }
        if (linkDevice.isOnline) {
            resetHeartbeatTimer(linkDevice.serial_number)
        } else if (!isRetained) {
            cancelHeartbeatTimer(linkDevice.serial_number)
        }
    }

    private suspend fun handleLinkHeartBeat(linkSn: String, json: JSONObject) {
        val serialNumber = json.optString("serial_number").ifBlank {
            json.optString("serialNumber").ifBlank { linkSn }
        }
        val isOnline = json.getBoolean("isOnline")
        recordRealtimeLinkStatus(serialNumber, isOnline)
        updateLinkAvailability(serialNumber, isOnline)
        if (isOnline) {
            resetHeartbeatTimer(serialNumber)
        } else {
            cancelHeartbeatTimer(serialNumber)
        }
    }

    private fun resetHeartbeatTimer(serialNumber: String) {
        lateinit var timeoutJob: Job
        synchronized(heartbeatLock) {
            heartbeatJobs.remove(serialNumber)?.cancel()
            timeoutJob = scope.launch(start = CoroutineStart.LAZY) {
                delay(heartbeatTimeoutMillis)
                val stillCurrent = synchronized(heartbeatLock) {
                    heartbeatJobs[serialNumber] === timeoutJob
                }
                if (!stillCurrent) return@launch
                Log.w(TAG, "心跳超时，设备离线")
                recordRealtimeLinkStatus(serialNumber, false)
                updateLinkAvailability(serialNumber, false)
                synchronized(heartbeatLock) {
                    heartbeatJobs.remove(serialNumber, timeoutJob)
                }
            }
            heartbeatJobs[serialNumber] = timeoutJob
        }
        timeoutJob.start()
    }

    private suspend fun handleLinkDeviceOffline(serialNumber: String) {
        debugLog { "LinkDeviceOffline, LinkSN: $serialNumber" }
        recordRealtimeLinkStatus(serialNumber, false)
        cancelHeartbeatTimer(serialNumber)
        updateLinkAvailability(serialNumber, false)
    }

    private fun cancelHeartbeatTimer(serialNumber: String) {
        synchronized(heartbeatLock) {
            heartbeatJobs.remove(serialNumber)?.cancel()
        }
    }

    private fun recordRealtimeLinkStatus(serialNumber: String, isOnline: Boolean) {
        synchronized(realtimeStatusLock) {
            realtimeLinkStatus[serialNumber] = isOnline
        }
    }

    private fun currentRealtimeLinkStatus(serialNumber: String): Boolean? =
        synchronized(realtimeStatusLock) {
            realtimeLinkStatus[serialNumber]
        }

    private suspend fun handleSubDeviceAdded(linkSn: String, json: JSONObject) {
        val switch = json.toSwitch()
        recordReportedSubDevice(linkSn, switch.serialNumber, switch.isOnline)
        debugLog { "SubDevice 数据: $switch" }
        val linkOnline = linkDeviceRepo.links.value
            .firstOrNull { it.serial_number == linkSn }
            ?.isOnline == true
        linkDeviceRepo.addSubDevice(
            linkSn,
            switch.copy(isOnline = linkOnline && switch.isOnline)
        )
    }

    private suspend fun handleSubDeviceRemoved(linkSn: String, json: JSONObject) {
        val switchSn = json.getString("serial_number")
        removeReportedSubDevice(linkSn, switchSn)
        linkDeviceRepo.removeSubDevice(linkSn, switchSn)
    }

    private suspend fun handleSubDeviceStatusChanged(linkSn: String, json: JSONObject) {
        val serialNumber = json.getRequiredString("serial_number", "serialNumber")
        val previous = linkDeviceRepo.links.value
            .firstOrNull { it.serial_number == linkSn }
            ?.subDevices
            ?.firstOrNull { it.serialNumber == serialNumber }
        if (previous == null) {
            Log.w(TAG, "忽略未知子设备状态")
            return
        }
        val lastReportedOnline = reportedSubDeviceOnline(linkSn, serialNumber)
        val reportedSwitch = json.toSwitch(
            previous.copy(isOnline = lastReportedOnline ?: previous.isOnline)
        )
        recordReportedSubDevice(linkSn, serialNumber, reportedSwitch.isOnline)
        val linkOnline = linkDeviceRepo.links.value
            .firstOrNull { it.serial_number == linkSn }
            ?.isOnline == true
        linkDeviceRepo.updateSubDevice(
            linkSn,
            reportedSwitch.copy(isOnline = linkOnline && reportedSwitch.isOnline)
        )
    }

    /**
     * Link 心跳是旧版吊桶协议唯一持续发送的实时在线证据；子设备在线位只存在于
     * LinkDeviceStartup 的 subDevices 快照中。保留快照本身不能证明当前在线，但在
     * 收到新鲜 Link 心跳后，可以恢复快照中最后一次上报的子设备可用性。
     */
    private suspend fun updateLinkAvailability(serialNumber: String, isOnline: Boolean) {
        linkDeviceRepo.updateLinkDeviceStatus(serialNumber, isOnline)
        val link = linkDeviceRepo.links.value
            .firstOrNull { it.serial_number == serialNumber }
            ?: return
        val reportedStatus = synchronized(realtimeStatusLock) {
            reportedSubDeviceStatus[serialNumber]?.toMap().orEmpty()
        }
        link.subDevices.forEach { switch ->
            val effectiveOnline = isOnline &&
                (reportedStatus[switch.serialNumber] ?: switch.isOnline)
            if (switch.isOnline != effectiveOnline) {
                linkDeviceRepo.updateSubDevice(
                    serialNumber,
                    switch.copy(isOnline = effectiveOnline)
                )
            }
        }
    }

    private fun recordReportedSubDevices(
        linkSn: String,
        switches: List<FireBucketSwitchState>,
        preserveExisting: Boolean
    ) {
        synchronized(realtimeStatusLock) {
            val status = if (preserveExisting) {
                reportedSubDeviceStatus.getOrPut(linkSn) { mutableMapOf() }
            } else {
                mutableMapOf<String, Boolean>().also { reportedSubDeviceStatus[linkSn] = it }
            }
            switches.forEach { switch ->
                if (preserveExisting) {
                    status.putIfAbsent(switch.serialNumber, switch.isOnline)
                } else {
                    status[switch.serialNumber] = switch.isOnline
                }
            }
        }
    }

    private fun recordReportedSubDevice(linkSn: String, switchSn: String, isOnline: Boolean) {
        synchronized(realtimeStatusLock) {
            reportedSubDeviceStatus.getOrPut(linkSn) { mutableMapOf() }[switchSn] = isOnline
        }
    }

    private fun reportedSubDeviceOnline(linkSn: String, switchSn: String): Boolean? =
        synchronized(realtimeStatusLock) {
            reportedSubDeviceStatus[linkSn]?.get(switchSn)
        }

    private fun removeReportedSubDevice(linkSn: String, switchSn: String) {
        synchronized(realtimeStatusLock) {
            reportedSubDeviceStatus[linkSn]?.remove(switchSn)
        }
    }

    fun parseSubDevices(
        array: JSONArray,
        allowRealtimeOnline: Boolean = true
    ): List<FireBucketSwitchState> {
        return (0 until array.length()).map { i ->
            array.getJSONObject(i).toSwitch().let { state ->
                if (allowRealtimeOnline) state else state.copy(isOnline = false)
            }
        }
    }

    private fun JSONObject.toSwitch(
        previous: FireBucketSwitchState? = null
    ): FireBucketSwitchState {
        return FireBucketSwitchState(
            serialNumber = getRequiredString("serial_number", "serialNumber"),
            deviceName = optString("deviceName").ifBlank {
                previous?.deviceName ?: getString("deviceName")
            },
            deviceType = optString("deviceType").ifBlank {
                previous?.deviceType ?: getString("deviceType")
            },
            isOnline = optNullableBoolean("isOnline")
                ?: previous?.isOnline
                ?: getBoolean("isOnline"),
            currentAngle = optNullableDouble("currentAngle")
                ?: previous?.currentAngle
                ?: getDouble("currentAngle"),
            currentCurrent = optNullableDouble("currentCurrent")
                ?: previous?.currentCurrent
                ?: getDouble("currentCurrent"),
            inputVoltage = optNullableDouble("inputVoltage")
                ?: previous?.inputVoltage
                ?: getDouble("inputVoltage"),
            batteryPercentage = optNullableDouble("batteryPercentage")
                ?: previous?.batteryPercentage
                ?: 0.0,
            servoMinAngle = optNullableDouble("servoMinAngle")
                ?: previous?.servoMinAngle
                ?: getDouble("servoMinAngle"),
            servoMaxAngle = optNullableDouble("servoMaxAngle")
                ?: previous?.servoMaxAngle
                ?: getDouble("servoMaxAngle"),
            uptime = optNullableInt("uptime")
                ?: previous?.uptime
                ?: getInt("uptime"),
            productType = ProductCatalog.inferType(
                deviceType = optString("deviceType").ifBlank { previous?.deviceType.orEmpty() },
                deviceModel = optString("deviceModel"),
                deviceName = optString("deviceName").ifBlank { previous?.deviceName.orEmpty() }
            )
        )
    }

    private fun JSONObject.getRequiredString(vararg keys: String): String {
        val key = keys.firstOrNull { has(it) && !isNull(it) }
            ?: error("FireBucket Switch 缺少字段: ${keys.joinToString(" / ")}")
        return getString(key)
    }

    private fun JSONObject.optNullableBoolean(name: String): Boolean? =
        if (has(name) && !isNull(name)) runCatching { getBoolean(name) }.getOrNull() else null

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (has(name) && !isNull(name)) runCatching { getDouble(name) }.getOrNull() else null

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) runCatching { getInt(name) }.getOrNull() else null

    fun cleanup() {
        synchronized(heartbeatLock) {
            heartbeatJobs.values.forEach { it.cancel() }
            heartbeatJobs.clear()
            scope.cancel()
            scope = newHeartbeatScope()
        }
        synchronized(realtimeStatusLock) {
            realtimeLinkStatus.clear()
            reportedSubDeviceStatus.clear()
        }
    }

    private fun newHeartbeatScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    private companion object {
        const val TAG = "FireBucketMqttInbound"
        const val DEFAULT_HEARTBEAT_TIMEOUT_MILLIS = 5_000L
    }
}
