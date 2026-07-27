package com.tji.device.product.solarclean.viewmodel

import com.tji.device.product.solarclean.model.SolarCleanControlSettings

/**
 * 协调控制面板的乐观值与设备 ACK。
 *
 * 每个字段独立排序：成功 ACK 成为已确认值，失败或超时回到最近仍在途的值；
 * 晚到的旧 ACK 不会覆盖更新的成功结果。
 */
internal class SolarControlSettingsTracker {
    private var nextSequence = 0L
    private val fields = mutableMapOf<ControlFieldKey, FieldState>()
    private val commands = mutableMapOf<String, TrackedCommand>()

    fun begin(
        serialNumber: String,
        msgId: String,
        field: SolarControlField,
        value: SolarControlValue,
        current: SolarCleanControlSettings
    ): SolarCleanControlSettings {
        val key = ControlFieldKey(serialNumber, field)
        val state = fields.getOrPut(key) {
            FieldState(confirmedValue = field.read(current))
        }
        val command = TrackedCommand(
            key = key,
            sequence = ++nextSequence,
            value = value
        )
        state.pending[msgId] = command
        commands[msgId] = command
        return field.write(current, value)
    }

    fun resolve(
        msgId: String,
        successful: Boolean,
        current: SolarCleanControlSettings
    ): SolarControlSettingsResolution? {
        val command = commands.remove(msgId) ?: return null
        val state = fields[command.key] ?: return null
        state.pending.remove(msgId)

        if (successful && command.sequence > state.confirmedSequence) {
            state.confirmedSequence = command.sequence
            state.confirmedValue = command.value
            val obsoleteIds = state.pending
                .filterValues { it.sequence <= state.confirmedSequence }
                .keys
            obsoleteIds.forEach {
                state.pending.remove(it)
                commands.remove(it)
            }
        }

        val displayValue = state.pending
            .values
            .maxByOrNull { it.sequence }
            ?.value
            ?: state.confirmedValue
        val settings = command.key.field.write(current, displayValue)
        if (state.pending.isEmpty()) {
            fields.remove(command.key)
        }
        return SolarControlSettingsResolution(
            serialNumber = command.key.serialNumber,
            settings = settings
        )
    }

    fun serialNumberFor(msgId: String): String? =
        commands[msgId]?.key?.serialNumber
}

internal enum class SolarControlField {
    Pump,
    PumpPressure,
    SprayAngle,
    Swing,
    SwingSpeed;

    fun read(settings: SolarCleanControlSettings): SolarControlValue = when (this) {
        Pump -> SolarControlValue.Toggle(settings.pumpOn)
        PumpPressure -> SolarControlValue.Level(settings.pumpPressurePercent)
        SprayAngle -> SolarControlValue.Level(settings.sprayAngleDegrees)
        Swing -> SolarControlValue.Toggle(settings.swingOn)
        SwingSpeed -> SolarControlValue.Level(settings.swingSpeedPercent)
    }

    fun write(
        settings: SolarCleanControlSettings,
        value: SolarControlValue
    ): SolarCleanControlSettings = when (this) {
        Pump -> settings.copy(pumpOn = value.requireToggle())
        PumpPressure -> settings.copy(pumpPressurePercent = value.requireLevel())
        SprayAngle -> settings.copy(sprayAngleDegrees = value.requireLevel())
        Swing -> settings.copy(swingOn = value.requireToggle())
        SwingSpeed -> settings.copy(swingSpeedPercent = value.requireLevel())
    }
}

internal sealed interface SolarControlValue {
    data class Toggle(val enabled: Boolean) : SolarControlValue
    data class Level(val value: Double) : SolarControlValue
}

internal data class SolarControlSettingsResolution(
    val serialNumber: String,
    val settings: SolarCleanControlSettings
)

private data class ControlFieldKey(
    val serialNumber: String,
    val field: SolarControlField
)

private data class TrackedCommand(
    val key: ControlFieldKey,
    val sequence: Long,
    val value: SolarControlValue
)

private data class FieldState(
    var confirmedValue: SolarControlValue,
    var confirmedSequence: Long = 0L,
    val pending: MutableMap<String, TrackedCommand> = linkedMapOf()
)

private fun SolarControlValue.requireToggle(): Boolean =
    (this as? SolarControlValue.Toggle)?.enabled
        ?: error("控制字段需要开关值")

private fun SolarControlValue.requireLevel(): Double =
    (this as? SolarControlValue.Level)?.value
        ?: error("控制字段需要数值")
