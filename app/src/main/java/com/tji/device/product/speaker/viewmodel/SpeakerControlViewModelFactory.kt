package com.tji.device.product.speaker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tji.device.product.speaker.audio.SpeakerAudioRelay
import com.tji.device.product.speaker.audio.SpeakerFeedbackReceiver
import com.tji.device.product.speaker.audio.SpeakerRecordUploadClient
import com.tji.device.product.speaker.audio.SpeakerTtsSynthesizer
import com.tji.device.product.speaker.repository.SpeakerControlRepository
import com.tji.device.product.speaker.repository.SpeakerRepository

class SpeakerControlViewModelFactory(
    private val stateRepository: SpeakerRepository,
    private val controlRepository: SpeakerControlRepository,
    private val audioRelay: SpeakerAudioRelay,
    private val ttsSynthesizer: SpeakerTtsSynthesizer,
    private val recordUploadClient: SpeakerRecordUploadClient,
    private val feedbackReceiver: SpeakerFeedbackReceiver
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpeakerControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpeakerControlViewModel(
                stateRepository,
                controlRepository,
                audioRelay,
                ttsSynthesizer,
                recordUploadClient,
                feedbackReceiver
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
