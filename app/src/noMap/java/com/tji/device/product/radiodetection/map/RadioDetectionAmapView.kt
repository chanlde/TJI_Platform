package com.tji.device.product.radiodetection.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tji.device.product.radiodetection.model.RadioDetectionTarget
import com.tji.device.product.radiodetection.model.RadioDetectionUiState
import com.tji.device.product.radiodetection.ui.control.MapBg

/**
 * 保持地图视图接口一致，使无线电侦测页可在无地图产品包中编译。
 * 实际运行时 RadioDetectionMapRuntime 会选择示意地图，不会进入这个占位实现。
 */
@Composable
fun RadioDetectionAmapView(
    config: RadioDetectionMapConfig,
    state: RadioDetectionUiState,
    focusedTargetId: String?,
    focusTargetSignal: Int,
    zoomSignal: Int,
    zoomDelta: Float,
    recenterSignal: Int,
    onTargetClick: (RadioDetectionTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(MapBg))
}
