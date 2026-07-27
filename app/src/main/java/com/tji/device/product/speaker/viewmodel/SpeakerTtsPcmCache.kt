package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Small LRU cache for synthesized PCM.
 *
 * Requests for the same key share one in-flight synthesis, so phone preview and
 * device playback cannot perform the same expensive TTS work concurrently.
 */
internal class SpeakerTtsPcmCache(
    private val maxItems: Int,
    private val maxBytes: Long = Long.MAX_VALUE,
    private val maxEntryBytes: Long = maxBytes
) {
    private val mutex = Mutex()
    private val cachedPcm =
        LinkedHashMap<SpeakerTtsPcmCacheKey, ByteArray>(maxItems, 0.75f, true)
    private var cachedBytes = 0L
    private val inFlight =
        mutableMapOf<SpeakerTtsPcmCacheKey, CompletableDeferred<ByteArray>>()

    init {
        require(maxItems > 0) { "TTS PCM cache size must be positive" }
        require(maxBytes > 0L) { "TTS PCM cache byte budget must be positive" }
        require(maxEntryBytes > 0L && maxEntryBytes <= maxBytes) {
            "TTS PCM entry budget must be positive and no larger than total budget"
        }
    }

    suspend fun getOrSynthesize(
        key: SpeakerTtsPcmCacheKey,
        synthesize: suspend () -> ByteArray
    ): ByteArray {
        while (true) {
            val lookup = mutex.withLock {
                cachedPcm[key]?.let { return@withLock CacheLookup.Cached(it) }
                inFlight[key]?.let { return@withLock CacheLookup.Await(it) }
                val result = CompletableDeferred<ByteArray>()
                inFlight[key] = result
                CacheLookup.Synthesize(result)
            }

            when (lookup) {
                is CacheLookup.Cached -> return lookup.pcm
                is CacheLookup.Synthesize ->
                    return synthesizeAndCache(key, lookup.result, synthesize)
                is CacheLookup.Await -> {
                    try {
                        return lookup.result.await()
                    } catch (cancellation: CancellationException) {
                        // The synthesis owner may have been replaced by a newer
                        // UI operation. An active waiter should take ownership
                        // and retry instead of being canceled with the old job.
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
        }
    }

    private suspend fun synthesizeAndCache(
        key: SpeakerTtsPcmCacheKey,
        result: CompletableDeferred<ByteArray>,
        synthesize: suspend () -> ByteArray
    ): ByteArray {
        return try {
            val pcm = synthesize()
            mutex.withLock {
                inFlight.remove(key, result)
                cacheIfWithinBudget(key, pcm)
            }
            result.complete(pcm)
            pcm
        } catch (throwable: Throwable) {
            mutex.withLock {
                inFlight.remove(key, result)
            }
            result.completeExceptionally(throwable)
            throw throwable
        }
    }

    private fun cacheIfWithinBudget(key: SpeakerTtsPcmCacheKey, pcm: ByteArray) {
        if (pcm.size.toLong() > maxEntryBytes) return

        cachedPcm.put(key, pcm)?.let { replaced ->
            cachedBytes -= replaced.size
        }
        cachedBytes += pcm.size

        val iterator = cachedPcm.entries.iterator()
        while (
            iterator.hasNext() &&
            (cachedPcm.size > maxItems || cachedBytes > maxBytes)
        ) {
            val eldest = iterator.next()
            cachedBytes -= eldest.value.size
            iterator.remove()
        }
    }

    private sealed interface CacheLookup {
        data class Cached(val pcm: ByteArray) : CacheLookup
        data class Await(val result: CompletableDeferred<ByteArray>) : CacheLookup
        data class Synthesize(val result: CompletableDeferred<ByteArray>) : CacheLookup
    }
}

internal data class SpeakerTtsPcmCacheKey(
    val text: String,
    val voiceId: String,
    val sampleRate: Int
)

internal fun speakerTtsPcmCacheKey(
    text: String,
    systemVoice: SpeakerTtsVoicePreset,
    sampleRate: Int
): SpeakerTtsPcmCacheKey = SpeakerTtsPcmCacheKey(
    text = text,
    voiceId = systemVoice.name,
    sampleRate = sampleRate
)
