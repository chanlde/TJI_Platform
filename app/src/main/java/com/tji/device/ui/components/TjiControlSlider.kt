package com.tji.device.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.PayloadColors

@OptIn(ExperimentalMaterial3Api::class)
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
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val activeColor = if (enabled) PayloadColors.Primary else PayloadColors.TextMuted.copy(alpha = 0.34f)
    val inactiveColor = if (enabled) PayloadColors.TextMuted.copy(alpha = 0.34f) else PayloadColors.Border
    val knobSize = thumbSize.coerceIn(12.dp, 16.dp)

    Slider(
        value = clampedValue,
        onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
        thumb = {
            Box(
                modifier = Modifier
                    .size(knobSize)
                    .clip(CircleShape)
                    .background(PayloadColors.Surface)
                    .border(
                        width = 1.6.dp,
                        color = if (enabled) PayloadColors.TextSecondary else PayloadColors.TextMuted.copy(alpha = 0.45f),
                        shape = CircleShape
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size((knobSize * 0.34f).coerceAtLeast(4.dp))
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(if (enabled) PayloadColors.Surface else PayloadColors.Border)
                )
            }
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(2.dp),
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 1.dp,
                colors = SliderDefaults.colors(
                    activeTrackColor = activeColor,
                    inactiveTrackColor = inactiveColor,
                    disabledActiveTrackColor = PayloadColors.TextMuted.copy(alpha = 0.24f),
                    disabledInactiveTrackColor = PayloadColors.Border
                )
            )
        },
        colors = SliderDefaults.colors(
            thumbColor = PayloadColors.Surface,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
            disabledThumbColor = PayloadColors.Surface,
            disabledActiveTrackColor = PayloadColors.TextMuted.copy(alpha = 0.24f),
            disabledInactiveTrackColor = PayloadColors.Border
        )
    )
}

@Preview(showBackground = true, name = "TJI Control Slider")
@Composable
private fun TjiControlSliderPreview() {
    TjiControlSlider(
        value = 68f,
        onValueChange = {},
        thumbSize = 14.dp,
        modifier = Modifier.padding(16.dp)
    )
}

@Preview(showBackground = true, name = "TJI Control Slider Compact")
@Composable
private fun TjiControlSliderCompactPreview() {
    TjiControlSlider(
        value = 24f,
        onValueChange = {},
        valueRange = 0f..40f,
        thumbSize = 16.dp,
        modifier = Modifier.padding(16.dp)
    )
}
