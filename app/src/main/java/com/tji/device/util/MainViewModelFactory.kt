package com.tji.device.util


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tji.device.data.repository.AuthRepository
import com.tji.device.data.viewmodel.LoginViewModel
import com.tji.device.data.viewmodel.MainViewModel
import com.tji.device.data.vminterface.LoginViewModelInterface
import com.tji.device.product.runtime.ProductRuntimeRegistry
import com.tji.device.service.MqttSubscriptionManager

/**
 * 创建 MainViewModel、LoginViewModel；产品运行时通过 ProductRuntimeRegistry 注入，避免 MainViewModel 依赖某个具体产品。
 */
class MainViewModelFactory(
    private val authRepository: AuthRepository,
    private val productRuntimeRegistry: ProductRuntimeRegistry,
    private val mqttSubscriptionManager: MqttSubscriptionManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                val loginViewModel = createLoginViewModel()

                @Suppress("UNCHECKED_CAST")
                MainViewModel(
                    loginViewModel,
                    authRepository,
                    productRuntimeRegistry,
                    mqttSubscriptionManager
                ) as T
            }
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                createLoginViewModel() as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private fun createLoginViewModel(): LoginViewModelInterface {
        return LoginViewModel(authRepository)
    }
}
