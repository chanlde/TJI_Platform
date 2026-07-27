package com.tji.device.product.speaker.viewmodel

import android.util.Log
import com.tji.device.product.common.runCatchingPreservingCancellation
import com.tji.device.product.speaker.audio.SpeakerAudioConfig
import com.tji.device.product.speaker.audio.SpeakerLocalAudioPlayer
import com.tji.device.product.speaker.audio.SpeakerToneSettings
import com.tji.device.product.speaker.audio.SpeakerTtsSynthesizer
import com.tji.device.product.speaker.audio.SpeakerTtsVoicePreset
import com.tji.device.product.speaker.core.SpeakerCoreAudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns Android system-TTS discovery, synthesis caching and local preview.
 */
internal class SpeakerTtsCoordinator(
    scope: CoroutineScope,
    private val synthesizer: SpeakerTtsSynthesizer
) {
    private val voicePresetState = MutableStateFlow(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET)
    val voicePreset: StateFlow<SpeakerTtsVoicePreset> = voicePresetState.asStateFlow()

    private val availableVoicePresetsState =
        MutableStateFlow(listOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET))
    val availableVoicePresets: StateFlow<List<SpeakerTtsVoicePreset>> =
        availableVoicePresetsState.asStateFlow()

    private val audioPlayer = SpeakerLocalAudioPlayer()
    private val pcmCache = SpeakerTtsPcmCache(
        maxItems = SpeakerAudioConfig.Tts.PCM_CACHE_MAX_ITEMS,
        maxBytes = SpeakerAudioConfig.Tts.PCM_CACHE_MAX_BYTES,
        maxEntryBytes = SpeakerAudioConfig.Tts.PCM_CACHE_MAX_ENTRY_BYTES
    )

    init {
        scope.launch(Dispatchers.IO) {
            runCatchingPreservingCancellation {
                synthesizer.inspectChineseVoices()
            }.onSuccess { inventory ->
                availableVoicePresetsState.value = inventory.availablePresets
                if (voicePresetState.value !in inventory.availablePresets) {
                    voicePresetState.value = SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET
                }
            }.onFailure { throwable ->
                Log.w(SpeakerAudioConfig.Debug.AUDIO_DEBUG_TAG, "TTS voice inspect failed", throwable)
                availableVoicePresetsState.value =
                    listOf(SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET)
                voicePresetState.value = SpeakerAudioConfig.Tts.DEFAULT_VOICE_PRESET
            }
        }
    }

    fun selectVoice(preset: SpeakerTtsVoicePreset) {
        if (preset in availableVoicePresetsState.value) {
            voicePresetState.value = preset
        }
    }

    suspend fun synthesize(text: String, sampleRate: Int): ByteArray {
        val voice = voicePresetState.value
        val key = speakerTtsPcmCacheKey(text, voice, sampleRate)
        return pcmCache.getOrSynthesize(key) {
            synthesizer.synthesizeToPcm(
                text = text,
                voicePreset = voice,
                targetSampleRate = sampleRate
            )
        }
    }

    suspend fun preview(
        text: String,
        sampleRate: Int,
        toneSettings: SpeakerToneSettings
    ) {
        val pcm = synthesize(text, sampleRate)
        require(pcm.isNotEmpty()) { "文字语音合成音频为空" }
        val processed = SpeakerCoreAudioEngine.applyPlaybackTone(pcm, toneSettings, sampleRate)
        audioPlayer.playPcm16le(processed, sampleRate)
    }

    fun stopPreview() {
        audioPlayer.stop()
    }
}
