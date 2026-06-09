package com.tji.device.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline

@Composable
fun TjiFeedbackBadge(
    text: String?,
    color: Color,
    modifier: Modifier = Modifier,
    radius: Dp = 999.dp,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 4.dp
) {
    if (text.isNullOrBlank()) return
    PayloadStatusBadge(
        text = text,
        color = color,
        modifier = modifier,
        showDot = false
    )
}

@Composable
fun TjiOnlineStatus(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    pill: Boolean = false,
    dotSize: Dp = 8.dp
) {
    val color = if (isOnline) TjiOnline else TjiError
    val text = if (isOnline) "在线" else "离线"

    PayloadStatusBadge(
        text = text,
        color = color,
        modifier = modifier,
        showDot = true
    )
}

@Preview(showBackground = true, name = "TJI Feedback")
@Composable
private fun TjiFeedbackBadgePreview() {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        TjiFeedbackBadge(text = "水泵设置成功", color = TjiOnline)
        TjiFeedbackBadge(text = "设置失败", color = TjiError)
        TjiOnlineStatus(isOnline = true)
        TjiOnlineStatus(isOnline = false, pill = true)
        TjiFeedbackBadge(text = "等待响应", color = PayloadColors.TextSecondary)
    }
}
