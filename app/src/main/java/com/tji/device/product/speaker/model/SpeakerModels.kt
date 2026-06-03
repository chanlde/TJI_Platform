package com.tji.device.product.speaker.model

import com.tji.device.product.runtime.ProductRuntimePayload

data class SpeakerDeviceState(
    val serialNumber: String,
    val name: String? = null,
    val isOnline: Boolean = false,
    val playing: Boolean = false,
    val currentFile: String? = null,
    val volume: Int = DEFAULT_SPEAKER_VOLUME,
    val servoAngle: Int? = null,
    val lastError: String? = null,
    val network: String? = null,
    val lastAck: SpeakerAck? = null,
    val timestamp: Long? = null
) : ProductRuntimePayload

data class SpeakerAck(
    val msgId: String,
    val ofType: String,
    val ofCmd: Int,
    val ok: Boolean,
    val code: Int,
    val message: String,
    val timestamp: Long?
)

sealed class SpeakerCommand(
    val msgId: String,
    val code: Int,
    val commandName: String
) {
    class SpeakText(
        msgId: String,
        val text: String,
        val volume: Int
    ) : SpeakerCommand(msgId, 101, "SPEAK_TEXT")

    class PrepareText(
        msgId: String,
        val text: String
    ) : SpeakerCommand(msgId, 102, "PREPARE_TEXT")

    class PlayFile(
        msgId: String,
        val file: String,
        val volume: Int
    ) : SpeakerCommand(msgId, 103, "PLAY_FILE")

    class Stop(msgId: String) : SpeakerCommand(msgId, 104, "STOP")

    class SetVolume(
        msgId: String,
        val volume: Int
    ) : SpeakerCommand(msgId, 105, "SET_VOLUME")

    class GetStatus(msgId: String) : SpeakerCommand(msgId, 106, "GET_STATUS")

    class SetServoAngle(
        msgId: String,
        val angle: Int
    ) : SpeakerCommand(msgId, 107, "SET_SERVO_ANGLE")
}

const val DEFAULT_SPEAKER_VOLUME = 35
