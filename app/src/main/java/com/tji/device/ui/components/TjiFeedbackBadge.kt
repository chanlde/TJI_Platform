package com.tji.device.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import com.tji.device.ui.theme.TjiTextSecondary

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

    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(radius))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
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

    if (pill) {
        TjiFeedbackBadge(
            text = text,
            color = color,
            modifier = modifier,
            horizontalPadding = 8.dp,
            verticalPadding = 4.dp
        )
        return
    }

    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
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
        TjiFeedbackBadge(text = "等待响应", color = TjiTextSecondary)
    }
}
