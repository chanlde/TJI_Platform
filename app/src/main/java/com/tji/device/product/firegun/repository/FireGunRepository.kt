package com.tji.device.product.firegun.repository

import com.tji.device.data.model.ProductType
import com.tji.device.product.firegun.mqtt.FireGunMqttTopics
import com.tji.device.product.firegun.model.FireGunDeviceStatus
import com.tji.device.product.firegun.model.FireGunLinkHeartbeat
import com.tji.device.product.firegun.model.FireGunLinkState
import com.tji.device.product.firegun.protocol.FireGunAction
import com.tji.device.product.firegun.protocol.FireGunProtocol
import com.tji.device.product.firegun.protocol.FireGunResponse
import com.tji.device.service.mqtt.ProductMqttRouter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FireGunInboundResponse(
    val topicLinkSerial: String,
    val response: FireGunResponse,
    val retained: Boolean
)

interface FireGunRepository {
    val responses: SharedFlow<FireGunInboundResponse>
    val links: StateFlow<Map<String, FireGunLinkState>>
    suspend fun recordResponse(response: FireGunInboundResponse)
    suspend fun recordStartup(startup: FireGunLinkState, retained: Boolean)
    suspend fun recordHeartbeat(heartbeat: FireGunLinkHeartbeat)
    suspend fun recordDeviceStatus(status: FireGunDeviceStatus)
    suspend fun markLinkOffline(linkSerial: String)
    fun clear()
}

class FireGunRepo : FireGunRepository {
    private val _responses = MutableSharedFlow<FireGunInboundResponse>(
        extraBufferCapacity = RESPONSE_BUFFER_CAPACITY
    )
    override val responses: SharedFlow<FireGunInboundResponse> = _responses.asSharedFlow()
    private val _links = MutableStateFlow<Map<String, FireGunLinkState>>(emptyMap())
    override val links: StateFlow<Map<String, FireGunLinkState>> = _links.asStateFlow()

    override suspend fun recordResponse(response: FireGunInboundResponse) {
        _responses.emit(response)
    }

    override suspend fun recordStartup(startup: FireGunLinkState, retained: Boolean) {
        _links.update { current ->
            val existing = current[startup.serialNumber]
            val updated = if (retained && existing != null) {
                existing.copy(
                    name = startup.name,
                    hwVersion = startup.hwVersion,
                    swVersion = startup.swVersion,
                    deviceStatus = existing.deviceStatus ?: startup.deviceStatus
                )
            } else {
                startup
            }
            current + (startup.serialNumber to updated)
        }
    }

    override suspend fun recordHeartbeat(heartbeat: FireGunLinkHeartbeat) {
        _links.update { current ->
            val existing = current[heartbeat.serialNumber]
            val updated = existing?.copy(
                isOnline = heartbeat.isOnline,
                uptime = heartbeat.uptime ?: existing.uptime,
                timestamp = heartbeat.timestamp ?: existing.timestamp
            ) ?: FireGunLinkState(
                serialNumber = heartbeat.serialNumber,
                name = heartbeat.serialNumber,
                isOnline = heartbeat.isOnline,
                hwVersion = null,
                swVersion = null,
                uptime = heartbeat.uptime,
                timestamp = heartbeat.timestamp,
                deviceStatus = null
            )
            current + (heartbeat.serialNumber to updated)
        }
    }

    override suspend fun recordDeviceStatus(status: FireGunDeviceStatus) {
        _links.update { current ->
            val existing = current[status.linkSerial]
            val updated = existing?.copy(deviceStatus = status) ?: FireGunLinkState(
                serialNumber = status.linkSerial,
                name = status.linkSerial,
                isOnline = true,
                hwVersion = null,
                swVersion = null,
                uptime = null,
                timestamp = status.timestamp,
                deviceStatus = status
            )
            current + (status.linkSerial to updated)
        }
    }

    override suspend fun markLinkOffline(linkSerial: String) {
        _links.update { current ->
            val existing = current[linkSerial] ?: return@update current
            current + (linkSerial to existing.copy(isOnline = false))
        }
    }

    override fun clear() {
        _links.value = emptyMap()
    }

    private companion object {
        const val RESPONSE_BUFFER_CAPACITY = 16
    }
}

interface FireGunControlRepository {
    suspend fun sendUnlock(linkSerial: String, targetSerial: String, requestId: String)
    suspend fun sendActuator(
        linkSerial: String,
        targetSerial: String,
        requestId: String,
        action: FireGunAction
    )
}

class FireGunControlRepo(
    private val mqttPublisher: suspend (topic: String, payload: String) -> Unit = { topic, payload ->
        ProductMqttRouter.managerFor(ProductType.FireGun).publishAwait(
            topic = topic,
            message = payload,
            qos = 1,
            retain = false,
            queueWhenDisconnected = false
        ).getOrThrow()
    }
) : FireGunControlRepository {
    override suspend fun sendUnlock(linkSerial: String, targetSerial: String, requestId: String) {
        publish(
            linkSerial = linkSerial,
            payload = FireGunProtocol.unlockRequest(targetSerial, requestId).toString()
        )
    }

    override suspend fun sendActuator(
        linkSerial: String,
        targetSerial: String,
        requestId: String,
        action: FireGunAction
    ) {
        publish(
            linkSerial = linkSerial,
            payload = FireGunProtocol.actuatorRequest(targetSerial, requestId, action).toString()
        )
    }

    private suspend fun publish(linkSerial: String, payload: String) {
        mqttPublisher(FireGunMqttTopics.controlTopic(linkSerial), payload)
    }
}
