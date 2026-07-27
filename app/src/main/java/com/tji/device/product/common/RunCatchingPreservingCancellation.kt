package com.tji.device.product.common

import kotlinx.coroutines.CancellationException

/**
 * Equivalent to [runCatching], except structured-concurrency cancellation is
 * always propagated instead of being converted into a normal failure result.
 */
inline fun <T> runCatchingPreservingCancellation(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
