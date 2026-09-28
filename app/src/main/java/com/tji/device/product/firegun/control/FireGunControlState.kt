package com.tji.device.product.firegun.control

import com.tji.device.product.firegun.protocol.FireGunAction
import com.tji.device.product.firegun.protocol.FireGunResponse
import com.tji.device.product.firegun.model.FireGunDeviceStatus
import java.util.UUID

sealed interface FireGunPendingCommand {
    val requestId: String
    val linkSerial: String
    val targetSerial: String

    data class Unlock(
        override val requestId: String,
        override val linkSerial: String,
        override val targetSerial: String
    ) : FireGunPendingCommand

    data class Actuator(
        override val requestId: String,
        override val linkSerial: String,
        override val targetSerial: String,
        val action: FireGunAction
    ) : FireGunPendingCommand
}

data class FireGunControlState(
    val pendingUnlock: FireGunPendingCommand.Unlock? = null,
    val pendingActuator: FireGunPendingCommand.Actuator? = null,
    val lockState: String = "locked",
    val actuatorState: String = "stopped",
    val unlockFeedback: String? = null,
    val actuatorFeedback: String? = null,
    val lastCommandSucceeded: Boolean? = null
)

class FireGunControlStateMachine {
    fun statusReceived(
        state: FireGunControlState,
        status: FireGunDeviceStatus
    ): FireGunControlState = state.copy(
        lockState = status.lockState ?: state.lockState,
        actuatorState = status.actuatorState ?: state.actuatorState
    )

    fun commandStarted(
        state: FireGunControlState,
        command: FireGunPendingCommand
    ): FireGunControlState = when (command) {
        is FireGunPendingCommand.Unlock -> state.copy(
            pendingUnlock = command,
            unlockFeedback = "等待设备回执",
            actuatorFeedback = null,
            lastCommandSucceeded = null
        )
        is FireGunPendingCommand.Actuator -> state.copy(
            pendingActuator = command,
            actuatorFeedback = "等待设备回执",
            unlockFeedback = null,
            lastCommandSucceeded = null
        )
    }

    fun responseReceived(
        state: FireGunControlState,
        response: FireGunResponse,
        retained: Boolean = false
    ): FireGunControlState {
        if (retained) return state
        return when (response) {
            is FireGunResponse.Lock -> applyLockResponse(state, response)
            is FireGunResponse.Actuator -> applyActuatorResponse(state, response)
        }
    }

    fun commandTimedOut(state: FireGunControlState, requestId: String): FireGunControlState = when {
        state.pendingUnlock?.requestId == requestId -> state.copy(
            pendingUnlock = null,
            unlockFeedback = "设备未在规定时间内回执",
            lastCommandSucceeded = false
        )
        state.pendingActuator?.requestId == requestId -> state.copy(
            pendingActuator = null,
            actuatorFeedback = "设备未在规定时间内回执",
            lastCommandSucceeded = false
        )
        else -> state
    }

    fun commandFailed(
        state: FireGunControlState,
        requestId: String,
        reason: String
    ): FireGunControlState = when {
        state.pendingUnlock?.requestId == requestId -> state.copy(
            pendingUnlock = null,
            unlockFeedback = "发送失败：$reason",
            lastCommandSucceeded = false
        )
        state.pendingActuator?.requestId == requestId -> state.copy(
            pendingActuator = null,
            actuatorFeedback = "发送失败：$reason",
            lastCommandSucceeded = false
        )
        else -> state
    }

    private fun applyLockResponse(
        state: FireGunControlState,
        response: FireGunResponse.Lock
    ): FireGunControlState {
        val pending = state.pendingUnlock ?: return state
        if (pending.requestId != response.requestId || pending.targetSerial != response.serialNumber) {
            return state
        }
        return state.copy(
            pendingUnlock = null,
            lockState = response.state,
            unlockFeedback = if (response.success) {
                "解锁指令已接受"
            } else {
                FireGunErrorMessages.userMessage(response.error)
            },
            lastCommandSucceeded = response.success
        )
    }

    private fun applyActuatorResponse(
        state: FireGunControlState,
        response: FireGunResponse.Actuator
    ): FireGunControlState {
        val pending = state.pendingActuator ?: return state
        if (pending.requestId != response.requestId || pending.targetSerial != response.serialNumber) {
            return state
        }
        return state.copy(
            pendingActuator = null,
            actuatorState = response.state,
            actuatorFeedback = if (response.success) {
                when (response.action) {
                    FireGunAction.OPEN -> "展开指令已接受"
                    FireGunAction.CLOSE -> "收回指令已接受"
                    FireGunAction.STOP -> "停止指令已接受"
                }
            } else {
                FireGunErrorMessages.userMessage(response.error)
            },
            lastCommandSucceeded = response.success
        )
    }
}

object FireGunErrorMessages {
    fun userMessage(error: String?): String = when (error) {
        "invalid_action" -> "设备不支持该动作"
        "target_mismatch" -> "控制目标不匹配"
        "unlock_in_progress" -> "正在解锁"
        "lock_not_ready" -> "暂时无法解锁"
        "actuator_not_ready" -> "暂时无法动作"
        "ota_in_progress" -> "设备正在升级，暂时不能控制"
        "command_expired" -> "控制指令已过期"
        "command_rejected" -> "设备拒绝执行指令"
        null, "" -> "设备拒绝执行指令"
        else -> "设备执行失败：$error"
    }
}

object FireGunRequestIds {
    fun nextUnlock(): String = "lock-${UUID.randomUUID()}"
    fun nextActuator(): String = "actuator-${UUID.randomUUID()}"
}
