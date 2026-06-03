package com.tji.device.product.droppersixstage.model

import com.tji.device.product.runtime.ProductRuntimePayload

const val DROPPER_STAGE_COUNT = 6

data class DropperSixStageState(
    val serialNumber: String,
    val name: String? = null,
    val isOnline: Boolean = false,
    val stages: List<DropperStageState> = DropperStageState.defaults(),
    val batteryPercent: Int? = null,
    val firmwareVersion: String? = null,
    val lastAck: DropperSixStageAck? = null,
    val timestamp: Long? = null
) : ProductRuntimePayload

data class DropperStageState(
    val index: Int,
    val isOpen: Boolean = false,
    val payloadLoaded: Boolean? = null
) {
    val displayName: String
        get() = "${index}段"

    companion object {
        fun defaults(): List<DropperStageState> =
            (1..DROPPER_STAGE_COUNT).map { DropperStageState(index = it) }
    }
}

data class DropperSixStageAck(
    val msgId: String,
    val ok: Boolean,
    val stage: Int? = null,
    val message: String? = null
)

object DropperSixStageCommandCode {
    const val PING = 0
    const val SET_STAGE_SWITCH = 10
    const val SET_ALL_STAGES = 11
}

sealed interface DropperSixStageCommand {
    val msgId: String

    data class Ping(override val msgId: String) : DropperSixStageCommand

    data class StageSwitch(
        override val msgId: String,
        val stage: Int,
        val open: Boolean
    ) : DropperSixStageCommand

    data class AllStages(
        override val msgId: String,
        val open: Boolean
    ) : DropperSixStageCommand
}
