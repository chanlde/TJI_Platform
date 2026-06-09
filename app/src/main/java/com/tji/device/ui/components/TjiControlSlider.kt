package com.tji.device.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.PayloadColors

@Composable
fun TjiControlSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    enabled: Boolean = true,
    thumbSize: Dp = 34.dp,
    onValueChangeFinished: (() -> Unit)? = null
) {
    PayloadSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
        color = PayloadColors.Primary,
        onValueChangeFinished = onValueChangeFinished
    )
}

@Preview(showBackground = true, name = "TJI Control Slider")
@Composable
private fun TjiControlSliderPreview() {
    TjiControlSlider(
        value = 50f,
        onValueChange = {},
        modifier = Modifier.padding(16.dp)
    )
}
