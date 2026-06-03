package com.tji.device.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.TjiControlDisabled
import com.tji.device.ui.theme.TjiControlDisabledStrong
import com.tji.device.ui.theme.TjiControlInactive
import com.tji.device.ui.theme.TjiPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TjiControlSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    enabled: Boolean = true,
    thumbSize: Dp = 18.dp,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Slider(
        value = value,
        onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier,
        thumb = {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .shadow(3.dp, RoundedCornerShape(50))
                    .background(Color.White, RoundedCornerShape(50))
                    .padding((thumbSize * 0.22f).coerceAtLeast(3.dp))
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TjiPrimary, RoundedCornerShape(50))
                )
            }
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(4.dp),
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 2.dp,
                colors = SliderDefaults.colors(
                    activeTrackColor = TjiPrimary,
                    inactiveTrackColor = TjiControlInactive,
                    disabledActiveTrackColor = TjiControlDisabledStrong,
                    disabledInactiveTrackColor = TjiControlDisabled
                )
            )
        },
        colors = SliderDefaults.colors(
            thumbColor = TjiPrimary,
            activeTrackColor = TjiPrimary,
            inactiveTrackColor = TjiControlInactive,
            disabledThumbColor = TjiControlDisabledStrong,
            disabledActiveTrackColor = TjiControlDisabledStrong,
            disabledInactiveTrackColor = TjiControlDisabled
        )
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
