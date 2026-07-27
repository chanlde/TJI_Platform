package com.tji.device.product.speaker.viewmodel

import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SpeakerTtsPcmCacheTest {
    @Test
    fun concurrentRequestsForSameKeyShareOneSynthesis() = runBlocking {
        val cache = SpeakerTtsPcmCache(maxItems = 2)
        val key = key("共享文本")
        val synthesisCount = AtomicInteger()
        val synthesisStarted = CompletableDeferred<Unit>()
        val allowSynthesisToFinish = CompletableDeferred<Unit>()

        val first = async {
            cache.getOrSynthesize(key) {
                synthesisCount.incrementAndGet()
                synthesisStarted.complete(Unit)
                allowSynthesisToFinish.await()
                byteArrayOf(1, 2, 3)
            }
        }
        synthesisStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrSynthesize(key) {
                synthesisCount.incrementAndGet()
                byteArrayOf(9)
            }
        }

        allowSynthesisToFinish.complete(Unit)

        assertArrayEquals(byteArrayOf(1, 2, 3), first.await())
        assertArrayEquals(byteArrayOf(1, 2, 3), second.await())
        assertEquals(1, synthesisCount.get())
    }

    @Test
    fun activeWaiterRetriesWhenSynthesisOwnerIsCanceled() = runBlocking {
        val cache = SpeakerTtsPcmCache(maxItems = 2)
        val key = key("重复点击文本")
        val synthesisCount = AtomicInteger()
        val firstSynthesisStarted = CompletableDeferred<Unit>()

        val first = async {
            cache.getOrSynthesize(key) {
                synthesisCount.incrementAndGet()
                firstSynthesisStarted.complete(Unit)
                awaitCancellation()
            }
        }
        firstSynthesisStarted.await()
        val replacement = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrSynthesize(key) {
                synthesisCount.incrementAndGet()
                byteArrayOf(4, 2)
            }
        }

        first.cancelAndJoin()

        assertArrayEquals(byteArrayOf(4, 2), replacement.await())
        assertEquals(2, synthesisCount.get())
    }

    @Test
    fun failedSynthesisIsRemovedSoNextRequestCanRetry() {
        val cache = SpeakerTtsPcmCache(maxItems = 1)
        val key = key("重试文本")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                cache.getOrSynthesize(key) { error("synthesis failed") }
            }
        }

        val pcm = runBlocking {
            cache.getOrSynthesize(key) { byteArrayOf(7) }
        }
        assertArrayEquals(byteArrayOf(7), pcm)
    }

    @Test
    fun leastRecentlyUsedEntryIsEvicted() = runBlocking {
        val cache = SpeakerTtsPcmCache(maxItems = 2)
        val counts = mutableMapOf<String, Int>()

        suspend fun get(text: String): ByteArray =
            cache.getOrSynthesize(key(text)) {
                counts[text] = counts.getOrDefault(text, 0) + 1
                byteArrayOf(text.first().code.toByte())
            }

        get("A")
        get("B")
        get("A")
        get("C")
        get("B")

        assertEquals(1, counts["A"])
        assertEquals(2, counts["B"])
        assertEquals(1, counts["C"])
    }

    @Test
    fun cacheKeyChangesWithVoiceAndSampleRate() {
        val base = key("文本")
        val differentVoice = speakerTtsPcmCacheKey(
            text = "文本",
            systemVoice = SpeakerTtsVoicePreset.Female,
            sampleRate = 16_000
        )
        val differentRate = base.copy(sampleRate = 8_000)

        assertNotEquals(base, differentVoice)
        assertNotEquals(base, differentRate)
    }

    @Test
    fun byteBudgetEvictsLeastRecentlyUsedEntries() = runBlocking {
        val cache = SpeakerTtsPcmCache(
            maxItems = 10,
            maxBytes = 5,
            maxEntryBytes = 5
        )
        val counts = mutableMapOf<String, Int>()

        suspend fun get(text: String): ByteArray =
            cache.getOrSynthesize(key(text)) {
                counts[text] = counts.getOrDefault(text, 0) + 1
                ByteArray(3) { text.first().code.toByte() }
            }

        get("A")
        get("B")
        get("B")
        get("A")

        assertEquals(2, counts["A"])
        assertEquals(1, counts["B"])
    }

    @Test
    fun oversizedEntryIsReturnedButNotRetained() = runBlocking {
        val cache = SpeakerTtsPcmCache(
            maxItems = 2,
            maxBytes = 8,
            maxEntryBytes = 2
        )
        var synthesisCount = 0

        repeat(2) {
            val pcm = cache.getOrSynthesize(key("大段文本")) {
                synthesisCount += 1
                byteArrayOf(1, 2, 3)
            }
            assertArrayEquals(byteArrayOf(1, 2, 3), pcm)
        }

        assertEquals(2, synthesisCount)
    }

    private fun key(text: String): SpeakerTtsPcmCacheKey =
        speakerTtsPcmCacheKey(
            text = text,
            systemVoice = SpeakerTtsVoicePreset.Standard,
            sampleRate = 16_000
        )
}
