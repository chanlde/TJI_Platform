package com.tji.device.product.firebucket.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tji.device.concurrent.LatestRequestTracker
import com.tji.device.product.firebucket.model.FireBucketSwitchControlParams
import com.tji.device.product.firebucket.model.FireBucketSwitchUiState
import com.tji.device.product.firebucket.repository.FireBucketSwitchRepository
import com.tji.device.error.toUserVisibleMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

class FireBucketSwitchViewModel(
    private val repository: FireBucketSwitchRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FireBucketSwitchUiState())
    val uiState: StateFlow<FireBucketSwitchUiState> = _uiState.asStateFlow()
    private val angleRequests = LatestRequestTracker<Unit>()
    private var angleJob: Job? = null

    fun setAngle(linkSn: String, params: FireBucketSwitchControlParams) {
        val requestId = angleRequests.begin(Unit)
        angleJob?.cancel()
        _uiState.value = FireBucketSwitchUiState()
        angleJob = viewModelScope.launch {
            try {
                repository.setAngle(linkSn, params)
                if (angleRequests.isLatest(Unit, requestId)) {
                    _uiState.value = FireBucketSwitchUiState()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                if (angleRequests.isLatest(Unit, requestId)) {
                    _uiState.value = FireBucketSwitchUiState(
                        errorMessage = e.toUserVisibleMessage("设置角度失败")
                    )
                }
            } finally {
                if (angleRequests.isLatest(Unit, requestId)) {
                    angleRequests.complete(Unit, requestId)
                    angleJob = null
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

class FireBucketSwitchViewModelFactory(
    private val switchRepository: FireBucketSwitchRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FireBucketSwitchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FireBucketSwitchViewModel(switchRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
