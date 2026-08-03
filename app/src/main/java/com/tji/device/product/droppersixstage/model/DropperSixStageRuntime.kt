package com.tji.device.product.droppersixstage.model

import com.tji.device.product.runtime.ProductRuntimePayload

const val DROPPER_STAGE_COUNT = 6
const val DROPPER_MIN_OPEN_DURATION_MS = 100
const val DROPPER_MAX_OPEN_DURATION_MS = 30_000

object DropperControlLimits {
    fun isValidStage(stage: Int): Boolean = stage in 1..DROPPER_STAGE_COUNT

    fun normalizeOpenDuration(durationMs: Int): Int =
        durationMs.coerceIn(DROPPER_MIN_OPEN_DURATION_MS, DROPPER_MAX_OPEN_DURATION_MS)

    fun isValidOpenDuration(durationMs: Int): Boolean =
        durationMs in DROPPER_MIN_OPEN_DURATION_MS..DROPPER_MAX_OPEN_DURATION_MS
}

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

sealed interface DropperSixStageCommand {
    val msgId: String

    data class Ping(override val msgId: String) : DropperSixStageCommand

    data class Arm(override val msgId: String) : DropperSixStageCommand

    data class Disarm(override val msgId: String) : DropperSixStageCommand

    data class StageSwitch(
        override val msgId: String,
        val stage: Int,
        val open: Boolean,
        val durationMs: Int? = null
    ) : DropperSixStageCommand {
        init {
            require(DropperControlLimits.isValidStage(stage)) { "Dropper stage out of range: $stage" }
            require(durationMs == null || DropperControlLimits.isValidOpenDuration(durationMs)) {
                "Dropper open duration out of range: $durationMs"
            }
        }
    }

    data class AllStages(
        override val msgId: String,
        val open: Boolean,
        val durationMs: Int? = null
    ) : DropperSixStageCommand {
        init {
            require(durationMs == null || DropperControlLimits.isValidOpenDuration(durationMs)) {
                "Dropper open duration out of range: $durationMs"
            }
        }
    }
}
