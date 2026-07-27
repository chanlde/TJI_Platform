package com.tji.device.product.common

import java.util.concurrent.atomic.AtomicLong

/**
 * 生成进程内唯一、日志可读的设备控制命令 ID。
 *
 * 毫秒时间戳便于排障；递增序号避免同一毫秒内连续点击或多个 ViewModel 并发生成相同 ID。
 */
object DeviceCommandId {
    private val sequence = AtomicLong(0L)

    fun next(product: String, action: String): String {
        val normalizedProduct = product.trim().lowercase().ifBlank { "device" }
        val normalizedAction = action.trim().lowercase().ifBlank { "command" }
        return "$normalizedProduct-$normalizedAction-${System.currentTimeMillis()}-${sequence.incrementAndGet()}"
    }
}
