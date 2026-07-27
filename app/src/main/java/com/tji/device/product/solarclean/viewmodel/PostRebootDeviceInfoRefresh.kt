package com.tji.device.product.solarclean.viewmodel

import kotlinx.coroutines.CancellationException

/**
 * 执行 OTA 重启后的设备信息刷新。
 *
 * 设备刚上线时网络链路可能尚未稳定，因此允许少量重试；取消仍交给所属 ViewModel，
 * 普通发送异常则作为结果返回，避免后台协程异常终止进程。
 */
internal suspend fun runPostRebootDeviceInfoRefresh(
    maxAttempts: Int,
    waitBeforeFirstAttempt: suspend () -> Unit,
    waitBeforeRetry: suspend () -> Unit,
    sendRefresh: suspend (attempt: Int) -> Unit
): Result<Unit> {
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    waitBeforeFirstAttempt()

    var lastFailure: Exception? = null
    repeat(maxAttempts) { index ->
        val attempt = index + 1
        try {
            sendRefresh(attempt)
            return Result.success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            lastFailure = failure
            if (attempt < maxAttempts) {
                waitBeforeRetry()
            }
        }
    }
    return Result.failure(checkNotNull(lastFailure))
}
