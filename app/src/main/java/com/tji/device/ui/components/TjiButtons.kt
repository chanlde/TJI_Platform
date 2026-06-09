package com.tji.device.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.TjiWarning

@Composable
fun TjiActionButton(
    text: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PayloadActionButton(
        text = text,
        enabled = enabled,
        color = color,
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .defaultMinSize(minWidth = 96.dp)
    )
}

@Preview(showBackground = true, name = "TJI Action Buttons")
@Composable
private fun TjiActionButtonPreview() {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .height(64.dp)
            .defaultMinSize(minWidth = 220.dp)
    ) {
        TjiActionButton(
            text = "开启",
            enabled = true,
            color = PayloadColors.Primary,
            onClick = {}
        )
        TjiActionButton(
            text = "关闭",
            enabled = true,
            color = TjiWarning,
            onClick = {}
        )
    }
}
