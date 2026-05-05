package com.tji.device.di

import com.tji.device.data.model.ProductType
import com.tji.device.data.repository.AuthRepo
import com.tji.device.data.repository.AuthRepository
import com.tji.device.product.firebucket.control.FireBucketFloatingQuickControl
import com.tji.device.product.firebucket.mqtt.FireBucketMqttInbound
import com.tji.device.product.firebucket.repository.FireBucketLinkRepo
import com.tji.device.product.firebucket.repository.FireBucketLinkRepository
import com.tji.device.product.firebucket.repository.FireBucketSwitchRepository
import com.tji.device.product.firebucket.repository.SwitchRepo
import com.tji.device.product.firebucket.runtime.FireBucketRuntimeController
import com.tji.device.product.firebucket.viewmodel.FireBucketSwitchViewModelFactory
import com.tji.device.product.runtime.ProductRuntimeRegistry
import com.tji.device.product.solarclean.mqtt.SolarCleanMqttInbound
import com.tji.device.product.solarclean.repository.SolarCleanControlRepo
import com.tji.device.product.solarclean.repository.SolarCleanControlRepository
import com.tji.device.product.solarclean.repository.SolarCleanOtaRepo
import com.tji.device.product.solarclean.repository.SolarCleanOtaRepository
import com.tji.device.product.solarclean.repository.SolarCleanRepo
import com.tji.device.product.solarclean.repository.SolarCleanRepository
import com.tji.device.product.solarclean.runtime.SolarCleanRuntimeController
import com.tji.device.product.solarclean.viewmodel.SolarCleanControlViewModelFactory
import com.tji.device.service.MqttEventHandler
import com.tji.device.service.MqttSubscriptionManager
import com.tji.device.util.MainViewModelFactory

object AppContainer {

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

    val authRepository: AuthRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AuthRepo()
    }

    private val fireBucketMqttInbound: FireBucketMqttInbound by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FireBucketMqttInbound(fireBucketLinkRepository)
    }

    private val solarCleanMqttInbound: SolarCleanMqttInbound by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SolarCleanMqttInbound(solarCleanRepository)
    }

    private val mqttEventHandler: MqttEventHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MqttEventHandler(
            fireBucketInbound = fireBucketMqttInbound,
            solarCleanInbound = solarCleanMqttInbound,
        )
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

    val productRuntimeRegistry: ProductRuntimeRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductRuntimeRegistry(
            listOf(
                FireBucketRuntimeController(fireBucketLinkRepository),
                SolarCleanRuntimeController(solarCleanRepository)
            )
        )
    }

    /**
     * 悬浮窗快捷开关：按产品线注册；未注册则 [NoOpProductFloatingQuickControl]。
     */
    private val floatingQuickControls: Map<ProductType, ProductFloatingQuickControl> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        mapOf(ProductType.FireBucket to FireBucketFloatingQuickControl(switchRepository))
    }

    fun floatingQuickControlFor(productType: ProductType): ProductFloatingQuickControl =
        floatingQuickControls[productType] ?: NoOpProductFloatingQuickControl

    val mainViewModelFactory: MainViewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MainViewModelFactory(
            authRepository = authRepository,
            productRuntimeRegistry = productRuntimeRegistry,
            mqttSubscriptionManager = mqttSubscriptionManager
        )
    }
}
