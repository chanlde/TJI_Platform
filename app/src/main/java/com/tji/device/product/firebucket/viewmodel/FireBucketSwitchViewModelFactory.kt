package com.tji.device.product.firebucket.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tji.device.product.firebucket.repository.FireBucketSwitchRepository

/**
 * FireBucket 控制台专用 ViewModel 工厂。
 *
 * 只负责创建消防吊桶的 [SwitchViewModel]；不要放公共 di 包，避免被误认为是平台级工厂。
 */
class FireBucketSwitchViewModelFactory(
    private val switchRepository: FireBucketSwitchRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SwitchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SwitchViewModel(switchRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
