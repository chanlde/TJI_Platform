package com.tji.device.concurrent

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 为同一业务目标标记“最后一次请求”。
 *
 * 网络响应返回时必须再次检查令牌；旧响应和账号切换前的响应不能再提交 UI/会话状态。
 */
internal class LatestRequestTracker<Key : Any> {
    private val sequence = AtomicLong(0L)
    private val latestRequestByKey = ConcurrentHashMap<Key, Long>()

    fun begin(key: Key): Long =
        sequence.incrementAndGet().also { latestRequestByKey[key] = it }

    fun isLatest(key: Key, requestId: Long): Boolean =
        latestRequestByKey[key] == requestId

    fun complete(key: Key, requestId: Long) {
        latestRequestByKey.remove(key, requestId)
    }

    fun invalidateAll() {
        latestRequestByKey.clear()
    }
}
