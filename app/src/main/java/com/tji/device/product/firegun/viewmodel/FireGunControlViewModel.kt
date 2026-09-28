package com.tji.device.product.firegun.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.data.model.ProductType
import com.tji.device.product.firegun.control.FireGunControlState
import com.tji.device.product.firegun.control.FireGunControlStateMachine
import com.tji.device.product.firegun.control.FireGunPendingCommand
import com.tji.device.product.firegun.control.FireGunRequestIds
import com.tji.device.product.firegun.model.FireGunLinkState
import com.tji.device.product.firegun.protocol.FireGunAction
import com.tji.device.product.firegun.repository.FireGunControlRepository
import com.tji.device.product.firegun.repository.FireGunRepository
import com.tji.device.service.mqtt.ProductMqttRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FireGunControlViewModel(
    private val stateRepository: FireGunRepository,
    private val controlRepository: FireGunControlRepository
) : ViewModel() {
    private val stateMachine = FireGunControlStateMachine()
    private val timeoutJobs = mutableMapOf<String, Job>()
    private var activeLinkSerial: String? = null

    private val _state = MutableStateFlow(FireGunControlState())
    val state: StateFlow<FireGunControlState> = _state.asStateFlow()
    private val _linkStatus = MutableStateFlow<FireGunLinkState?>(null)
    val linkStatus: StateFlow<FireGunLinkState?> = _linkStatus.asStateFlow()
    val mqttConnected: StateFlow<Boolean> =
        ProductMqttRouter.managerFor(ProductType.FireGun).isConnected

    init {
        viewModelScope.launch {
            stateRepository.responses.collect { inbound ->
                if (inbound.topicLinkSerial != activeLinkSerial || inbound.retained) return@collect
                cancelTimeout(inbound.response.requestId)
                _state.update {
                    stateMachine.responseReceived(
                        state = it,
                        response = inbound.response,
                        retained = false
                    )
                }
            }
        }
        viewModelScope.launch {
            stateRepository.links.collect { links ->
                val link = activeLinkSerial?.let(links::get)
                _linkStatus.value = link
                link?.deviceStatus?.let { status ->
                    _state.update { stateMachine.statusReceived(it, status) }
                }
            }
        }
    }

    fun bindDevice(linkSerial: String) {
        if (activeLinkSerial == linkSerial) return
        cancelAllTimeouts()
        activeLinkSerial = linkSerial
        _state.value = FireGunControlState()
        _linkStatus.value = stateRepository.links.value[linkSerial]
        _linkStatus.value?.deviceStatus?.let { status ->
            _state.update { stateMachine.statusReceived(it, status) }
        }
    }

    fun unbindDevice(linkSerial: String) {
        if (activeLinkSerial != linkSerial) return
        cancelAllTimeouts()
        activeLinkSerial = null
        _state.value = FireGunControlState()
        _linkStatus.value = null
    }

    fun unlock(linkSerial: String) {
        if (!isActive(linkSerial) || _state.value.pendingUnlock != null) return
        val targetSerial = targetSerialOrReport(forUnlock = true) ?: return
        val requestId = FireGunRequestIds.nextUnlock()
        val command = FireGunPendingCommand.Unlock(requestId, linkSerial, targetSerial)
        _state.update { stateMachine.commandStarted(it, command) }
        publish(requestId) {
            controlRepository.sendUnlock(linkSerial, targetSerial, requestId)
        }
    }

    fun actuator(linkSerial: String, action: FireGunAction) {
        if (!isActive(linkSerial)) return
        val targetSerial = targetSerialOrReport(forUnlock = false) ?: return
        val current = _state.value
        if (current.pendingActuator != null && action != FireGunAction.STOP) return
        current.pendingActuator?.requestId?.let(::cancelTimeout)
        val requestId = FireGunRequestIds.nextActuator()
        val command = FireGunPendingCommand.Actuator(requestId, linkSerial, targetSerial, action)
        _state.update { stateMachine.commandStarted(it, command) }
        publish(requestId) {
            controlRepository.sendActuator(linkSerial, targetSerial, requestId, action)
        }
    }

    private fun targetSerialOrReport(forUnlock: Boolean): String? {
        val targetSerial = _linkStatus.value
            ?.deviceStatus
            ?.serialNumber
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        if (targetSerial != null) return targetSerial
        _state.update { current ->
            if (forUnlock) {
                current.copy(
                    unlockFeedback = "未获取设备信息，请稍后重试",
                    actuatorFeedback = null,
                    lastCommandSucceeded = false
                )
            } else {
                current.copy(
                    actuatorFeedback = "未获取设备信息，请稍后重试",
                    unlockFeedback = null,
                    lastCommandSucceeded = false
                )
            }
        }
        return null
    }

    private fun publish(requestId: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                timeoutJobs[requestId] = viewModelScope.launch {
                    delay(COMMAND_TIMEOUT_MILLIS)
                    _state.update { stateMachine.commandTimedOut(it, requestId) }
                    timeoutJobs.remove(requestId)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    stateMachine.commandFailed(
                        state = it,
                        requestId = requestId,
                        reason = throwable.message ?: "MQTT 发送失败"
                    )
                }
            }
        }
    }

    private fun isActive(linkSerial: String): Boolean =
        linkSerial.isNotBlank() && activeLinkSerial == linkSerial

    private fun cancelTimeout(requestId: String) {
        timeoutJobs.remove(requestId)?.cancel()
    }

    private fun cancelAllTimeouts() {
        timeoutJobs.values.forEach(Job::cancel)
        timeoutJobs.clear()
    }

    override fun onCleared() {
        cancelAllTimeouts()
        super.onCleared()
    }

    private companion object {
        const val COMMAND_TIMEOUT_MILLIS = 10_000L
    }
}

class FireGunControlViewModelFactory(
    private val stateRepository: FireGunRepository,
    private val controlRepository: FireGunControlRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FireGunControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FireGunControlViewModel(stateRepository, controlRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
