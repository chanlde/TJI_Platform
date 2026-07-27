package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.common.DeviceCommandId
import com.tji.device.product.speaker.model.SpeakerCommand

/**
 * Stateless device-control actions shared by the Speaker screen.
 */
internal class SpeakerDeviceActions(
    private val commands: SpeakerCommandCoordinator,
    private val reportValidationError: (String) -> Unit
) {
    fun setVolume(serialNumber: String, volume: Int) {
        commands.send(
            serialNumber,
            SpeakerCommand.SetVolume(id("volume"), volume.coerceIn(0, 100)),
            "音量设置"
        )
    }

    fun setServoAngle(serialNumber: String, angle: Int, speedDps: Int) {
        commands.send(
            serialNumber,
            SpeakerCommand.SetServoAngle(
                msgId = id("servo-angle"),
                angle = angle.coerceIn(SPEAKER_SERVO_MIN_ANGLE, SPEAKER_SERVO_MAX_ANGLE),
                speedDps = speedDps.coerceIn(
                    SPEAKER_SERVO_MIN_SPEED_DPS,
                    SPEAKER_SERVO_MAX_SPEED_DPS
                )
            ),
            "舵机角度"
        )
    }

    fun sweepServo(
        serialNumber: String,
        minAngle: Int,
        maxAngle: Int,
        speedDps: Int,
        cycles: Int,
        durationMs: Int
    ) {
        val min = minAngle.coerceIn(SPEAKER_SERVO_MIN_ANGLE, SPEAKER_SERVO_MAX_ANGLE - 1)
        val max = maxAngle.coerceIn(min + 1, SPEAKER_SERVO_MAX_ANGLE)
        commands.send(
            serialNumber,
            SpeakerCommand.ServoSweepTest(
                msgId = id("servo-sweep"),
                minAngle = min,
                maxAngle = max,
                speedDps = speedDps.coerceIn(
                    SPEAKER_SERVO_MIN_SPEED_DPS,
                    SPEAKER_SERVO_MAX_SPEED_DPS
                ),
                cycles = cycles.coerceIn(SPEAKER_SERVO_MIN_CYCLES, SPEAKER_SERVO_MAX_CYCLES),
                durationMs = durationMs.coerceIn(
                    SPEAKER_SERVO_MIN_HOLD_MS,
                    SPEAKER_SERVO_MAX_HOLD_MS
                )
            ),
            "舵机往返测试"
        )
    }

    fun stepServo(
        serialNumber: String,
        minAngle: Int,
        maxAngle: Int,
        stepAngle: Int,
        speedDps: Int,
        intervalMs: Int
    ) {
        val min = minAngle.coerceIn(SPEAKER_SERVO_MIN_ANGLE, SPEAKER_SERVO_MAX_ANGLE - 1)
        val max = maxAngle.coerceIn(min + 1, SPEAKER_SERVO_MAX_ANGLE)
        commands.send(
            serialNumber,
            SpeakerCommand.ServoStepTest(
                msgId = id("servo-step"),
                minAngle = min,
                maxAngle = max,
                stepAngle = stepAngle.coerceIn(1, max - min),
                speedDps = speedDps.coerceIn(
                    SPEAKER_SERVO_MIN_SPEED_DPS,
                    SPEAKER_SERVO_MAX_SPEED_DPS
                ),
                intervalMs = intervalMs.coerceIn(
                    SPEAKER_SERVO_MIN_INTERVAL_MS,
                    SPEAKER_SERVO_MAX_INTERVAL_MS
                )
            ),
            "舵机分段测试"
        )
    }

    fun getStatus(serialNumber: String) {
        commands.send(serialNumber, SpeakerCommand.GetStatus(id("status")), "状态查询")
    }

    fun refreshRecords(
        serialNumber: String,
        offset: Int,
        limit: Int,
        order: String
    ) {
        commands.send(
            serialNumber,
            SpeakerCommand.ListRecords(
                msgId = id("record-list"),
                offset = offset.coerceAtLeast(0),
                limit = limit.coerceIn(1, RECORD_LIST_PAGE_SIZE),
                order = order.ifBlank { "desc" }
            ),
            "录音列表"
        )
    }

    fun refreshStorageStatus(serialNumber: String) {
        commands.send(
            serialNumber,
            SpeakerCommand.GetStorageStatus(id("storage")),
            "容量查询"
        )
    }

    fun playRecord(serialNumber: String, recordId: String, volumePercent: Int) {
        if (recordId.isBlank()) return
        commands.send(
            serialNumber,
            SpeakerCommand.PlayRecord(
                msgId = id("record-play"),
                recordId = recordId,
                volume = volumePercent.coerceIn(0, 100)
            ),
            "播放录音"
        )
    }

    fun deleteRecord(serialNumber: String, recordId: String) {
        if (recordId.isBlank()) return
        commands.send(
            serialNumber,
            SpeakerCommand.DeleteRecord(id("record-delete"), recordId),
            "删除录音"
        )
    }

    fun updateRecordName(serialNumber: String, recordId: String, name: String) {
        val trimmed = name.trim()
        if (recordId.isBlank() || trimmed.isBlank()) {
            reportValidationError("录音名称不能为空")
            return
        }
        commands.send(
            serialNumber,
            SpeakerCommand.UpdateRecord(id("record-update"), recordId, trimmed),
            "录音改名"
        )
    }

    private fun id(action: String): String = DeviceCommandId.next("speaker", action)

    private companion object {
        const val RECORD_LIST_PAGE_SIZE = 4
    }
}
