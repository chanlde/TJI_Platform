package com.tji.device.di


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tji.device.data.repository.AuthRepository
import com.tji.device.data.session.AppSessionStore
import com.tji.device.data.viewmodel.LoginViewModel
import com.tji.device.data.viewmodel.MainViewModel
import com.tji.device.product.runtime.ProductRuntimeRegistry
import com.tji.device.product.ota.ProductOtaRuntimeRepository
import com.tji.device.service.MqttSubscriptionManager

/**
 * 分别创建 MainViewModel、LoginViewModel；两者由同一个 Activity ViewModelStore 独立管理。
 */
class MainViewModelFactory(
    private val authRepository: AuthRepository,
    private val sessionStore: AppSessionStore,
    private val productRuntimeRegistryProvider: () -> ProductRuntimeRegistry,
    private val mqttSubscriptionManagerProvider: () -> MqttSubscriptionManager,
    private val initializedMqttSubscriptionManager: () -> MqttSubscriptionManager?,
    private val productOtaRuntimeRepositoryProvider: () -> ProductOtaRuntimeRepository,
    private val clearRadioDetectionReplay: () -> Unit
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                MainViewModel(
                    authRepository,
                    sessionStore,
                    productRuntimeRegistryProvider,
                    mqttSubscriptionManagerProvider,
                    productOtaRuntimeRepositoryProvider,
                    clearRadioDetectionReplay
                ) as T
            }
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                createLoginViewModel() as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private fun createLoginViewModel(): LoginViewModel {
        return LoginViewModel(
            authRepository = authRepository,
            sessionStore = sessionStore,
            initializedMqttSubscriptionManager = initializedMqttSubscriptionManager
        )
    }
}
