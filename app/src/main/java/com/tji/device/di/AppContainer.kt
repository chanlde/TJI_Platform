package com.tji.device.di

import android.content.Context
import com.tji.device.data.model.ProductType
import com.tji.device.data.repository.AuthRepo
import com.tji.device.data.repository.AuthRepository
import com.tji.device.product.droppersixstage.repository.DropperSixStageControlRepo
import com.tji.device.product.droppersixstage.repository.DropperSixStageControlRepository
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepo
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepository
import com.tji.device.product.droppersixstage.viewmodel.DropperSixStageViewModelFactory
import com.tji.device.product.firebucket.repository.FireBucketLinkRepo
import com.tji.device.product.firebucket.repository.FireBucketLinkRepository
import com.tji.device.product.firebucket.repository.FireBucketSwitchRepository
import com.tji.device.product.firebucket.repository.SwitchRepo
import com.tji.device.product.firebucket.viewmodel.FireBucketSwitchViewModelFactory
import com.tji.device.product.radiodetection.repository.RadioDetectionControlRepo
import com.tji.device.product.radiodetection.repository.RadioDetectionControlRepository
import com.tji.device.product.radiodetection.repository.RadioDetectionRepo
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import com.tji.device.product.radiodetection.replay.RadioDetectionReplayStore
import com.tji.device.product.radiodetection.viewmodel.RadioDetectionControlViewModelFactory
import com.tji.device.product.runtime.ProductRuntimeRegistry
import com.tji.device.product.speaker.audio.SpeakerAudioRelay
import com.tji.device.product.speaker.audio.SpeakerRecordUploadClient
import com.tji.device.product.speaker.audio.SpeakerRemoteTtsClient
import com.tji.device.product.speaker.audio.SpeakerTtsSynthesizer
import com.tji.device.product.speaker.repository.SpeakerControlRepo
import com.tji.device.product.speaker.repository.SpeakerControlRepository
import com.tji.device.product.speaker.repository.SpeakerRepo
import com.tji.device.product.speaker.repository.SpeakerRepository
import com.tji.device.product.speaker.viewmodel.SpeakerControlViewModelFactory
import com.tji.device.product.solarclean.repository.SolarCleanControlRepo
import com.tji.device.product.solarclean.repository.SolarCleanControlRepository
import com.tji.device.product.solarclean.repository.SolarCleanOtaRepo
import com.tji.device.product.solarclean.repository.SolarCleanOtaRepository
import com.tji.device.product.solarclean.repository.SolarCleanRepo
import com.tji.device.product.solarclean.repository.SolarCleanRepository
import com.tji.device.product.solarclean.viewmodel.SolarCleanControlViewModelFactory
import com.tji.device.service.MqttEventHandler
import com.tji.device.service.MqttSubscriptionManager
import com.tji.device.util.MainViewModelFactory

object AppContainer {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val fireBucketLinkRepository: FireBucketLinkRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FireBucketLinkRepo()
    }

    val switchRepository: FireBucketSwitchRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SwitchRepo()
    }

    val solarCleanRepository: SolarCleanRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SolarCleanRepo()
    }

    val solarCleanControlRepository: SolarCleanControlRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SolarCleanControlRepo()
    }

    val solarCleanOtaRepository: SolarCleanOtaRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SolarCleanOtaRepo()
    }

    val dropperSixStageRepository: DropperSixStageRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DropperSixStageRepo()
    }

    val dropperSixStageControlRepository: DropperSixStageControlRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DropperSixStageControlRepo()
    }

    val radioDetectionRepository: RadioDetectionRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RadioDetectionRepo()
    }

    val radioDetectionControlRepository: RadioDetectionControlRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RadioDetectionControlRepo()
    }

    val speakerRepository: SpeakerRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SpeakerRepo()
    }

    val speakerControlRepository: SpeakerControlRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SpeakerControlRepo()
    }

    val speakerAudioRelay: SpeakerAudioRelay by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SpeakerAudioRelay()
    }

    val speakerTtsSynthesizer: SpeakerTtsSynthesizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(::appContext.isInitialized) { "AppContainer.initialize(context) must be called before using speaker TTS" }
        SpeakerTtsSynthesizer(appContext)
    }

    val speakerRemoteTtsClient: SpeakerRemoteTtsClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SpeakerRemoteTtsClient()
    }

    val speakerRecordUploadClient: SpeakerRecordUploadClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SpeakerRecordUploadClient()
    }

    val radioDetectionReplayStore: RadioDetectionReplayStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(::appContext.isInitialized) { "AppContainer.initialize(context) must be called before using replay store" }
        RadioDetectionReplayStore(appContext)
    }

    val authRepository: AuthRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AuthRepo()
    }

    private val productModules: ProductModuleRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductModuleRegistry(
            listOf(
                FireBucketProductModule(
                    linkRepository = fireBucketLinkRepository,
                    switchRepository = switchRepository
                ),
                SolarCleanProductModule(solarCleanRepository),
                DropperSixStageProductModule(dropperSixStageRepository),
                RadioDetectionProductModule(
                    repository = radioDetectionRepository,
                    replayStore = radioDetectionReplayStore
                ),
                SpeakerProductModule(speakerRepository)
            )
        )
    }

    private val mqttEventHandler: MqttEventHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MqttEventHandler(productModules = productModules)
    }

    val mqttSubscriptionManager: MqttSubscriptionManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MqttSubscriptionManager(mqttEventHandler = mqttEventHandler)
    }

    val fireBucketSwitchViewModelFactory: FireBucketSwitchViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FireBucketSwitchViewModelFactory(switchRepository)
    }

    val solarCleanControlViewModelFactory: SolarCleanControlViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SolarCleanControlViewModelFactory(
            stateRepository = solarCleanRepository,
            controlRepository = solarCleanControlRepository,
            otaRepository = solarCleanOtaRepository
        )
    }

    val dropperSixStageViewModelFactory: DropperSixStageViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DropperSixStageViewModelFactory(
            stateRepository = dropperSixStageRepository,
            controlRepository = dropperSixStageControlRepository
        )
    }

    val radioDetectionControlViewModelFactory: RadioDetectionControlViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RadioDetectionControlViewModelFactory(
            stateRepository = radioDetectionRepository,
            controlRepository = radioDetectionControlRepository,
            replayStore = radioDetectionReplayStore
        )
    }

    val speakerControlViewModelFactory: SpeakerControlViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SpeakerControlViewModelFactory(
            stateRepository = speakerRepository,
            controlRepository = speakerControlRepository,
            audioRelay = speakerAudioRelay,
            ttsSynthesizer = speakerTtsSynthesizer,
            remoteTtsClient = speakerRemoteTtsClient,
            recordUploadClient = speakerRecordUploadClient
        )
    }

    val productRuntimeRegistry: ProductRuntimeRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductRuntimeRegistry(productModules.runtimeControllers)
    }

    fun floatingQuickControlFor(productType: ProductType): ProductFloatingQuickControl =
        productModules.floatingQuickControlFor(productType)

    val mainViewModelFactory: MainViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MainViewModelFactory(
            authRepository = authRepository,
            productRuntimeRegistry = productRuntimeRegistry,
            mqttSubscriptionManager = mqttSubscriptionManager
        )
    }
}
