package com.tji.device.product.firegun.mqtt

import android.util.Log
import com.tji.device.product.firegun.protocol.FireGunProtocol
import com.tji.device.product.firegun.repository.FireGunInboundResponse
import com.tji.device.product.firegun.repository.FireGunRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class FireGunMqttInbound(
    private val repository: FireGunRepository,
    private val heartbeatTimeoutMillis: Long = DEFAULT_HEARTBEAT_TIMEOUT_MILLIS
) {
    private val heartbeatLock = Any()
    private val realtimeLock = Any()
    private var scope = newHeartbeatScope()
    private val heartbeatJobs = mutableMapOf<String, Job>()
    private val realtimeLinkStatus = mutableMapOf<String, Boolean>()

    suspend fun handleEvent(
        topicLinkSerial: String,
        eventType: String,
        json: JSONObject,
        isRetained: Boolean
    ) {
        when {
            eventType in RESPONSE_EVENT_TYPES -> repository.recordResponse(
                FireGunInboundResponse(
                    topicLinkSerial = topicLinkSerial,
                    response = FireGunProtocol.parseResponse(json),
                    retained = isRetained
                )
            )
            eventType == FireGunProtocol.STATUS_EVENT_TYPE && !isRetained -> {
                recordRealtimeStatus(topicLinkSerial, true)
                repository.recordDeviceStatus(FireGunProtocol.parseStatus(topicLinkSerial, json))
                resetHeartbeatTimer(topicLinkSerial)
            }
            eventType == FireGunProtocol.STARTUP_EVENT_TYPE ->
                handleStartup(topicLinkSerial, json, isRetained)
            eventType == FireGunProtocol.HEARTBEAT_EVENT_TYPE && !isRetained ->
                handleHeartbeat(topicLinkSerial, json)
            eventType == OFFLINE_EVENT_TYPE && !isRetained ->
                handleOffline(topicLinkSerial)
            else -> Log.d(TAG, "FireGun MQTT ignored: link=$topicLinkSerial event=$eventType")
        }
    }

    private suspend fun handleStartup(
        topicLinkSerial: String,
        json: JSONObject,
        retained: Boolean
    ) {
        val startup = FireGunProtocol.parseLinkStartup(topicLinkSerial, json)
        val effectiveOnline = if (retained) {
            currentRealtimeStatus(topicLinkSerial) ?: false
        } else {
            startup.isOnline.also { recordRealtimeStatus(topicLinkSerial, it) }
        }
        repository.recordStartup(startup.copy(isOnline = effectiveOnline), retained)
        if (effectiveOnline) resetHeartbeatTimer(topicLinkSerial) else cancelHeartbeatTimer(topicLinkSerial)
    }

    private suspend fun handleHeartbeat(topicLinkSerial: String, json: JSONObject) {
        val heartbeat = FireGunProtocol.parseLinkHeartbeat(topicLinkSerial, json)
        recordRealtimeStatus(topicLinkSerial, heartbeat.isOnline)
        repository.recordHeartbeat(heartbeat)
        if (heartbeat.isOnline) resetHeartbeatTimer(topicLinkSerial) else cancelHeartbeatTimer(topicLinkSerial)
    }

    private suspend fun handleOffline(topicLinkSerial: String) {
        recordRealtimeStatus(topicLinkSerial, false)
        cancelHeartbeatTimer(topicLinkSerial)
        repository.markLinkOffline(topicLinkSerial)
    }

    private fun resetHeartbeatTimer(linkSerial: String) {
        lateinit var timeoutJob: Job
        synchronized(heartbeatLock) {
            heartbeatJobs.remove(linkSerial)?.cancel()
            timeoutJob = scope.launch(start = CoroutineStart.LAZY) {
                delay(heartbeatTimeoutMillis)
                val stillCurrent = synchronized(heartbeatLock) {
                    heartbeatJobs[linkSerial] === timeoutJob
                }
                if (!stillCurrent) return@launch
                recordRealtimeStatus(linkSerial, false)
                repository.markLinkOffline(linkSerial)
                synchronized(heartbeatLock) {
                    heartbeatJobs.remove(linkSerial, timeoutJob)
                }
            }
            heartbeatJobs[linkSerial] = timeoutJob
        }
        timeoutJob.start()
    }

    private fun cancelHeartbeatTimer(linkSerial: String) {
        synchronized(heartbeatLock) {
            heartbeatJobs.remove(linkSerial)?.cancel()
        }
    }

    private fun recordRealtimeStatus(linkSerial: String, isOnline: Boolean) {
        synchronized(realtimeLock) {
            realtimeLinkStatus[linkSerial] = isOnline
        }
    }

    private fun currentRealtimeStatus(linkSerial: String): Boolean? =
        synchronized(realtimeLock) { realtimeLinkStatus[linkSerial] }

    fun cleanup() {
        synchronized(heartbeatLock) {
            heartbeatJobs.values.forEach(Job::cancel)
            heartbeatJobs.clear()
            scope.cancel()
            scope = newHeartbeatScope()
        }
        synchronized(realtimeLock) {
            realtimeLinkStatus.clear()
        }
        repository.clear()
    }

    private fun newHeartbeatScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        const val TAG = "FireGunMqtt"
        const val OFFLINE_EVENT_TYPE = "LinkDeviceOffline"
        const val DEFAULT_HEARTBEAT_TIMEOUT_MILLIS = 5_000L
        val RESPONSE_EVENT_TYPES = setOf("LockControlResponse", "ActuatorControlResponse")
    }
}
