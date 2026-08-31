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
