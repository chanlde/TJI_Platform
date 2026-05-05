package com.tji.device.product.firebucket.model

import com.tji.device.data.model.ProductType

data class Switch(
    val serialNumber: String,     // HydroDevice序列号
    val deviceName: String,       // HydroDevice名称
    val deviceType: String,       // HydroDevice类型
    val isOnline: Boolean,        // 在线状态
    val currentAngle: Double,     // 当前角度
    val currentCurrent: Double,   // 当前电流（mA）
    val inputVoltage: Double,     // 输入电压（V）
    val servoMinAngle: Double,    // 舵机最小角度
    val servoMaxAngle: Double,    // 舵机最大角度
    val uptime: Int,              // 运行时间（秒）
    val productType: ProductType = ProductType.FireBucket
)

data class SwitchControlParms(
    val sn: String,            // HydroDevice序列号
    var angle: Int,            // 目标角度 (0-360度)
    var speed: Int,            // 旋转速度 (可选)
    val mode: ControlMode      // 控制模式: absolute 或 relative
)

data class SwitchUiState(
    val errorMessage: String? = null
)

enum class ControlMode {
    ABSOLUTE,    // 绝对角度
    RELATIVE     // 相对角度
}
