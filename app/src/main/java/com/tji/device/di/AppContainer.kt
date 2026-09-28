package com.tji.device.di

import android.content.Context
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.data.repository.AuthRepository
import com.tji.device.data.repository.NetworkAuthRepository
import com.tji.device.data.session.AppSessionStore
import com.tji.device.product.droppersixstage.repository.DropperSixStageControlRepo
import com.tji.device.product.droppersixstage.repository.DropperSixStageControlRepository
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepo
import com.tji.device.product.droppersixstage.repository.DropperSixStageRepository
import com.tji.device.product.droppersixstage.viewmodel.DropperSixStageViewModelFactory
import com.tji.device.product.firebucket.repository.FireBucketLinkRepo
import com.tji.device.product.firebucket.repository.FireBucketLinkRepository
import com.tji.device.product.firebucket.repository.FireBucketSwitchRepository
import com.tji.device.product.firebucket.repository.FireBucketSwitchCommandRepository
import com.tji.device.product.firebucket.model.FireBucketSwitchState
import com.tji.device.product.firebucket.transport.CloudFireBucketControlTransport
import com.tji.device.product.firebucket.transport.DirectFireBucketControlTransport
import com.tji.device.product.firebucket.transport.DirectFireBucketStateStore
import com.tji.device.product.firebucket.transport.DirectLinkSocketExchange
import com.tji.device.product.firebucket.transport.DirectLinkRangeTestClient
import com.tji.device.product.firebucket.transport.FireBucketConnectionMode
import com.tji.device.product.firebucket.transport.FireBucketConnectionModeStore
import com.tji.device.BuildConfig
import com.tji.device.product.firebucket.viewmodel.FireBucketSwitchViewModelFactory
import com.tji.device.product.firegun.repository.FireGunControlRepo
import com.tji.device.product.firegun.repository.FireGunControlRepository
import com.tji.device.product.firegun.repository.FireGunRepo
import com.tji.device.product.firegun.repository.FireGunRepository
import com.tji.device.product.firegun.viewmodel.FireGunControlViewModelFactory
import com.tji.device.product.glassbreaker.repository.GlassBreakerControlRepo
import com.tji.device.product.glassbreaker.repository.GlassBreakerControlRepository
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepo
import com.tji.device.product.glassbreaker.repository.GlassBreakerRepository
import com.tji.device.product.glassbreaker.viewmodel.GlassBreakerControlViewModelFactory
import com.tji.device.product.ota.ProductOtaMqttCommandPublisher
import com.tji.device.product.ota.ProductOtaRepo
import com.tji.device.product.ota.ProductOtaRepository
import com.tji.device.product.ota.ProductOtaRuntimeRepo
import com.tji.device.product.ota.ProductOtaRuntimeRepository
import com.tji.device.product.ota.ProductOtaViewModelFactory
import com.tji.device.product.ota.SharedPreferencesProductOtaTaskStore
import com.tji.device.product.radiodetection.repository.RadioDetectionControlRepo
import com.tji.device.product.radiodetection.repository.RadioDetectionControlRepository
import com.tji.device.product.radiodetection.repository.RadioDetectionRepo
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import com.tji.device.product.radiodetection.replay.RadioDetectionReplayStore
import com.tji.device.product.radiodetection.viewmodel.RadioDetectionControlViewModelFactory
import com.tji.device.product.runtime.ProductRuntimeRegistry
import com.tji.device.product.speaker.audio.SpeakerAudioRelay
import com.tji.device.product.speaker.audio.SpeakerFeedbackClient
import com.tji.device.product.speaker.audio.SpeakerTtsSynthesizer
import java.io.File
import kotlin.math.min
import com.tji.device.product.speaker.repository.SpeakerControlRepo
import com.tji.device.product.speaker.repository.SpeakerControlRepository
import com.tji.device.product.speaker.repository.SpeakerRepo
import com.tji.device.product.speaker.repository.SpeakerRepository
import com.tji.device.product.speaker.viewmodel.SpeakerControlViewModelFactory
import com.tji.device.product.solarclean.repository.SolarCleanControlRepo
import com.tji.device.product.solarclean.repository.SolarCleanControlRepository
import com.tji.device.product.solarclean.repository.SolarCleanRepo
import com.tji.device.product.solarclean.repository.SolarCleanRepository
import com.tji.device.product.solarclean.viewmodel.SolarCleanControlViewModelFactory
import com.tji.device.service.MqttEventHandler
import com.tji.device.service.MqttSubscriptionManager

object AppContainer {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val appSessionStore: AppSessionStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppSessionStore()
    }

    val fireBucketLinkRepository: FireBucketLinkRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FireBucketLinkRepo()
    }

    val fireBucketConnectionMode: FireBucketConnectionModeStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FireBucketConnectionModeStore()
    }

    val directFireBucketState = DirectFireBucketStateStore()

    private val cloudFireBucketControl by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CloudFireBucketControlTransport()
    }

    private val directLinkSocketExchangeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(::appContext.isInitialized) { "AppContainer.initialize(context) must be called first" }
        DirectLinkSocketExchange(
            context = appContext,
            serverPort = BuildConfig.TJI_DIRECT_LINK_PORT,
            onStatusReport = { status ->
                directFireBucketState.applyStatus(
                    FireBucketSwitchState(
                        serialNumber = status.serialNumber,
                        deviceName = "消防吊桶",
                        deviceType = "HydroSwitch",
                        isOnline = status.isOnline,
                        currentAngle = status.currentAngle,
                        currentCurrent = status.currentCurrent,
                        inputVoltage = status.inputVoltage,
                        servoMinAngle = status.servoMinAngle,
                        servoMaxAngle = status.servoMaxAngle,
                        uptime = min(status.uptimeSeconds, Int.MAX_VALUE.toLong()).toInt(),
                        batteryPercentage = status.batteryPercentage
                    )
                )
            },
            onConnectionChanged = directFireBucketState::updateConnection,
            onDisconnected = {
                directFireBucketState.updateConnection(false)
            }
        )
    }
    private val directLinkSocketExchange by directLinkSocketExchangeDelegate

    val directLinkRangeTestClient: DirectLinkRangeTestClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(::appContext.isInitialized) { "AppContainer.initialize(context) must be called first" }
        DirectLinkRangeTestClient(appContext)
    }

    private val directFireBucketControl by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DirectFireBucketControlTransport(directLinkSocketExchange::exchange)
    }

    fun useCloudFireBucketControl() {
        fireBucketConnectionMode.useCloud()
        if (directLinkSocketExchangeDelegate.isInitialized()) directLinkSocketExchange.stop()
    }

    fun useDirectFireBucketControl() {
        fireBucketConnectionMode.useDirectLink()
        directFireBucketState.beginSession()
        directLinkSocketExchange.start()
    }

    fun leaveDirectFireBucketControl() {
        useCloudFireBucketControl()
        directFireBucketState.clear()
    }

    val switchRepository: FireBucketSwitchRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FireBucketSwitchCommandRepository {
            when (fireBucketConnectionMode.current) {
                FireBucketConnectionMode.CLOUD -> cloudFireBucketControl
                FireBucketConnectionMode.DIRECT_LINK -> directFireBucketControl
            }
        }
    }

    val solarCleanRepository: SolarCleanRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SolarCleanRepo()
    }

    val fireGunRepository: FireGunRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FireGunRepo()
    }

    val fireGunControlRepository: FireGunControlRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FireGunControlRepo()
    }

    val solarCleanControlRepository: SolarCleanControlRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SolarCleanControlRepo()
    }

    val productOtaRepository: ProductOtaRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductOtaRepo()
    }

    val productOtaRuntimeRepository: ProductOtaRuntimeRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductOtaRuntimeRepo()
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

    val glassBreakerRepository: GlassBreakerRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GlassBreakerRepo()
    }

    val glassBreakerControlRepository: GlassBreakerControlRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GlassBreakerControlRepo()
    }

    val speakerAudioRelay: SpeakerAudioRelay by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SpeakerAudioRelay()
    }

    val speakerFeedbackClient: SpeakerFeedbackClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(::appContext.isInitialized) { "AppContainer.initialize(context) must be called first" }
        SpeakerFeedbackClient(
            debugCaptureDirectory = File(
                appContext.getExternalFilesDir(null),
                "speaker-audio-debug"
            )
        )
    }

    val speakerTtsSynthesizer: SpeakerTtsSynthesizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(::appContext.isInitialized) { "语音功能还没有准备好，请重新打开页面" }
        SpeakerTtsSynthesizer(appContext)
    }

    val radioDetectionReplayStore: RadioDetectionReplayStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        check(::appContext.isInitialized) { "AppContainer.initialize(context) must be called before using replay store" }
        RadioDetectionReplayStore(appContext)
    }

    val authRepository: AuthRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NetworkAuthRepository()
    }

    private val productModules: ProductModuleRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductModuleRegistry(
            modules = listOf(
                FireBucketProductModule(
                    linkRepository = fireBucketLinkRepository,
                    switchRepository = switchRepository
                ),
                FireGunProductModule(fireGunRepository),
                SolarCleanProductModule(solarCleanRepository),
                DropperSixStageProductModule(dropperSixStageRepository),
                RadioDetectionProductModule(
                    repository = radioDetectionRepository,
                    replayStore = radioDetectionReplayStore
                ),
                SpeakerProductModule(speakerRepository),
                GlassBreakerProductModule(glassBreakerRepository)
            ),
            requiredProductTypes = ProductCatalog.enabledTypes
        )
    }

    private val mqttEventHandler: MqttEventHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MqttEventHandler(
            productModules = productModules,
            productOtaRuntimeRepository = productOtaRuntimeRepository
        )
    }

    private val mqttSubscriptionManagerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MqttSubscriptionManager(mqttEventHandler = mqttEventHandler)
    }

    val mqttSubscriptionManager: MqttSubscriptionManager
        get() = mqttSubscriptionManagerDelegate.value

    fun initializedMqttSubscriptionManagerOrNull(): MqttSubscriptionManager? =
        if (mqttSubscriptionManagerDelegate.isInitialized()) {
            mqttSubscriptionManagerDelegate.value
        } else {
            null
        }

    val fireBucketSwitchViewModelFactory: FireBucketSwitchViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FireBucketSwitchViewModelFactory(switchRepository)
    }

    val fireGunControlViewModelFactory: FireGunControlViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FireGunControlViewModelFactory(
            stateRepository = fireGunRepository,
            controlRepository = fireGunControlRepository
        )
    }

    val solarCleanControlViewModelFactory: SolarCleanControlViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SolarCleanControlViewModelFactory(
            stateRepository = solarCleanRepository,
            controlRepository = solarCleanControlRepository
        )
    }

    val productOtaViewModelFactory: ProductOtaViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductOtaViewModelFactory(
            repository = productOtaRepository,
            commandPublisher = ProductOtaMqttCommandPublisher(),
            taskStore = SharedPreferencesProductOtaTaskStore(appContext)
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
            feedbackReceiver = speakerFeedbackClient
        )
    }

    val glassBreakerControlViewModelFactory: GlassBreakerControlViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GlassBreakerControlViewModelFactory(
            stateRepository = glassBreakerRepository,
            controlRepository = glassBreakerControlRepository
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
            sessionStore = appSessionStore,
            productRuntimeRegistryProvider = { productRuntimeRegistry },
            mqttSubscriptionManagerProvider = { mqttSubscriptionManager },
            initializedMqttSubscriptionManager = ::initializedMqttSubscriptionManagerOrNull,
            productOtaRuntimeRepositoryProvider = { productOtaRuntimeRepository },
            clearRadioDetectionReplay = { radioDetectionReplayStore.clearAll() }
        )
    }
}
