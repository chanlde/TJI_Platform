package com.tji.network

/**
 * Bounds work waiting for the MQTT connection and keeps each request's success
 * and failure callbacks together.
 */
internal class MqttConnectionCallbackQueue(
    private val capacity: Int
) {
    private val lock = Any()
    private val requests = ArrayDeque<ConnectionCallbacks>()

    init {
        require(capacity > 0) { "MQTT connection callback capacity must be positive" }
    }

    fun offer(
        onConnected: (() -> Unit)?,
        onFailed: ((Throwable) -> Unit)?,
        isActive: () -> Boolean = { true }
    ): Boolean {
        if (onConnected == null && onFailed == null) return true
        synchronized(lock) {
            requests.removeAll { !it.isActive.safely() }
            if (requests.size >= capacity) return false
            requests.addLast(ConnectionCallbacks(onConnected, onFailed, isActive))
        }
        return true
    }

    fun drainConnected(onCallbackFailure: (Throwable) -> Unit = {}) {
        drainActive().forEach { callbacks ->
            runCatching { callbacks.onConnected?.invoke() }
                .onFailure(onCallbackFailure)
        }
    }

    fun drainFailed(
        connectionFailure: Throwable,
        onCallbackFailure: (Throwable) -> Unit = {}
    ) {
        drainActive().forEach { callbacks ->
            runCatching { callbacks.onFailed?.invoke(connectionFailure) }
                .onFailure(onCallbackFailure)
        }
    }

    fun clear() {
        synchronized(lock) {
            requests.clear()
        }
    }

    /**
     * Releases canceled coroutine requests immediately during long connection
     * outages, instead of retaining them until another request or reconnect.
     */
    fun pruneInactive() {
        synchronized(lock) {
            requests.removeAll { !it.isActive.safely() }
        }
    }

    internal fun size(): Int = synchronized(lock) { requests.size }

    private fun drainActive(): List<ConnectionCallbacks> = synchronized(lock) {
        buildList(requests.size) {
            while (requests.isNotEmpty()) {
                requests.removeFirst()
                    .takeIf { it.isActive.safely() }
                    ?.let(::add)
            }
        }
    }

    private fun (() -> Boolean).safely(): Boolean =
        runCatching { invoke() }.getOrDefault(false)

    private data class ConnectionCallbacks(
        val onConnected: (() -> Unit)?,
        val onFailed: ((Throwable) -> Unit)?,
        val isActive: () -> Boolean
    )
}
