package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.speaker.model.SpeakerCommand

data class SpeakerTalkState(
    val mode: SpeakerTalkMode = SpeakerTalkMode.Idle,
    val progress: Float = 0f,
    val error: String? = null
)

enum class SpeakerMcuMicrophonePhase {
    Idle,
    Connecting,
    Listening,
    Stopping,
    Failed
}

data class SpeakerMcuMicrophoneState(
    val phase: SpeakerMcuMicrophonePhase = SpeakerMcuMicrophonePhase.Idle,
    val packetsReceived: Long = 0,
    val packetsPlayed: Long = 0,
    val packetsConcealed: Long = 0,
    val packetsRejected: Long = 0,
    val duplicatePackets: Long = 0,
    val error: String? = null
) {
    val enabled: Boolean
        get() = phase == SpeakerMcuMicrophonePhase.Connecting ||
            phase == SpeakerMcuMicrophonePhase.Listening
}

enum class SpeakerTalkMode {
    Idle,
    Recording,
    Sending,
    RecordingToStore,
    SavingRecord,
    Tts,
    Tone
}

data class SpeakerCommandFeedback(
    val msgId: String? = null,
    val status: SpeakerCommandFeedbackStatus = SpeakerCommandFeedbackStatus.Idle,
    val text: String? = null
)

enum class SpeakerCommandFeedbackStatus {
    Idle,
    Pending,
    Success,
    Failed,
    Timeout
}

const val SPEAKER_SERVO_MIN_ANGLE = 0
const val SPEAKER_SERVO_MAX_ANGLE = 180
const val SPEAKER_SERVO_MIN_SPEED_DPS = 1
const val SPEAKER_SERVO_MAX_SPEED_DPS = 360
const val SPEAKER_SERVO_DEFAULT_SPEED_DPS = 60
const val SPEAKER_SERVO_MIN_CYCLES = 0
const val SPEAKER_SERVO_MAX_CYCLES = 100
const val SPEAKER_SERVO_DEFAULT_CYCLES = 1
const val SPEAKER_SERVO_MIN_HOLD_MS = 0
const val SPEAKER_SERVO_MAX_HOLD_MS = 5_000
const val SPEAKER_SERVO_DEFAULT_HOLD_MS = 300
const val SPEAKER_SERVO_MIN_INTERVAL_MS = 20
const val SPEAKER_SERVO_MAX_INTERVAL_MS = 60_000
const val SPEAKER_SERVO_DEFAULT_INTERVAL_MS = 2_000
const val SPEAKER_SERVO_DEFAULT_STEP_ANGLE = 10

internal fun SpeakerCommand.requiresOnlineDevice(): Boolean = when (this) {
    is SpeakerCommand.Stop,
    is SpeakerCommand.GetStatus -> false
    is SpeakerCommand.SetMcuMicrophoneFeedback -> enabled
    else -> true
}
