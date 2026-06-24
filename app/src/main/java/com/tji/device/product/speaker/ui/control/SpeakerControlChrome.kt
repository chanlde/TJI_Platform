package com.tji.device.product.speaker.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.ui.components.PayloadActionButton
import com.tji.device.ui.components.PayloadCard
import com.tji.device.ui.components.PayloadInfoRow
import com.tji.device.ui.components.PayloadStatusBadge
import com.tji.device.ui.components.PayloadWaveMeter
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

internal val SpeakerBg = PayloadColors.Background
internal val SpeakerSurface = PayloadColors.Surface
internal val SpeakerFg = PayloadColors.TextPrimary
internal val SpeakerMuted = PayloadColors.TextMuted
internal val SpeakerBorder = PayloadColors.Border
internal val SpeakerAccent = PayloadColors.Primary
internal val SpeakerDanger = PayloadColors.Danger
internal val SpeakerSuccess = PayloadColors.Success
internal val SpeakerWarning = PayloadColors.Warning

internal enum class SpeakerPanel(val title: String) {
    Talk("首页"),
    Records("录音库"),
    Text("文字"),
    Settings("高级")
}

@Composable
internal fun SpeakerCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    PayloadCard(title = title, modifier = modifier, content = content)
}

@Composable
internal fun SpeakerScreenTopBar(
    panel: SpeakerPanel,
    device: BoundAccountDevice
) {
    val eyebrow = when (panel) {
        SpeakerPanel.Talk -> "当前设备"
        SpeakerPanel.Records -> "设备录音"
        SpeakerPanel.Text -> "文字转语音"
        SpeakerPanel.Settings -> "设备设置"
    }
    val title = when (panel) {
        SpeakerPanel.Talk -> device.name
        SpeakerPanel.Records -> "录音库"
        SpeakerPanel.Text -> "文字喊话"
        SpeakerPanel.Settings -> "高级"
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = SpeakerMuted
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = SpeakerFg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.dp, SpeakerBorder, CircleShape)
                .background(SpeakerSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (panel == SpeakerPanel.Talk) "!" else device.serialNumber.takeLast(3),
                style = MaterialTheme.typography.labelMedium,
                color = SpeakerMuted,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun SpeakerBottomNavigation(
    selected: SpeakerPanel,
    onSelect: (SpeakerPanel) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SpeakerBorder)
            .background(SpeakerSurface)
            .padding(horizontal = PayloadDimens.CompactRadius, vertical = PayloadDimens.CompactRadius)
    ) {
        SpeakerPanel.entries.forEach { panel ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(PayloadDimens.BottomNavHeight)
                    .clip(RoundedCornerShape(PayloadDimens.ControlRadius))
                    .background(if (panel == selected) SpeakerAccent.copy(alpha = 0.07f) else SpeakerSurface)
                    .pointerInput(panel, selected) {
                        detectTapGestures(onTap = { onSelect(panel) })
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = panel.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (panel == selected) SpeakerAccent else SpeakerMuted,
                    fontWeight = if (panel == selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun SpeakerStatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    showDot: Boolean = true
) {
    PayloadStatusBadge(text = text, color = color, modifier = modifier, showDot = showDot)
}

@Composable
internal fun SpeakerSoftRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = SpeakerFg
) {
    PayloadInfoRow(label = label, value = value, modifier = modifier, valueColor = valueColor)
}

@Composable
internal fun SpeakerActionButton(
    text: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    soft: Boolean = false,
    onClick: () -> Unit
) {
    PayloadActionButton(
        text = text,
        enabled = enabled,
        color = color,
        modifier = modifier,
        soft = soft,
        onClick = onClick
    )
}

@Composable
internal fun SpeakerWaveMeter(
    modifier: Modifier = Modifier,
    color: Color = SpeakerAccent,
    active: Boolean = false
) {
    PayloadWaveMeter(modifier = modifier, color = color, active = active)
}
