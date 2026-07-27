package com.tji.device.product.speaker.viewmodel

import kotlinx.coroutines.Job

/**
 * Owns the one audio operation that is allowed to produce playback or upload work.
 *
 * Methods are synchronized because operations finish on an IO dispatcher while
 * replacements normally start from the main thread.
 */
internal class LatestAudioOperation {
    private var activeJob: Job? = null

    @Synchronized
    fun replaceWith(job: Job) {
        activeJob?.cancel()
        activeJob = job
    }

    @Synchronized
    fun clearIfCurrent(job: Job) {
        if (activeJob === job) {
            activeJob = null
        }
    }

    @Synchronized
    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }
}
