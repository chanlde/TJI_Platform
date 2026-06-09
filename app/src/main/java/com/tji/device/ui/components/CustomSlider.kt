package com.tji.device.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.PayloadColors

@Composable
fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..90f
) {
    PayloadSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        color = PayloadColors.Primary
    )
}


@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun CustomSliderPreview() {
    MaterialTheme {
        var value by remember { mutableFloatStateOf(45f) }
        CustomSlider(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.padding(16.dp)
        )
    }
}
