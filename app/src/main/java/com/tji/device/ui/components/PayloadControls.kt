package com.tji.device.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

@Composable
fun PayloadCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PayloadDimens.CardRadius))
            .border(2.dp, PayloadColors.Border, RoundedCornerShape(PayloadDimens.CardRadius))
            .background(PayloadColors.Surface)
            .padding(PayloadDimens.CardPadding)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = PayloadColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
fun PayloadStatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    showDot: Boolean = true
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PayloadInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = PayloadColors.TextPrimary
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PayloadDimens.ControlRadius))
            .background(PayloadColors.Background)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = PayloadColors.TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PayloadActionButton(
    text: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    soft: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = when {
        !enabled -> PayloadColors.Border.copy(alpha = 0.62f)
        soft -> color.copy(alpha = 0.09f)
        else -> color
    }
    val contentColor = when {
        !enabled -> PayloadColors.TextMuted.copy(alpha = 0.55f)
        soft -> color
        else -> PayloadColors.Surface
    }
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .height(PayloadDimens.ControlHeight)
            .defaultMinSize(minWidth = 72.dp),
        shape = RoundedCornerShape(PayloadDimens.CompactRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = PayloadColors.Border.copy(alpha = 0.62f),
            disabledContentColor = PayloadColors.TextMuted.copy(alpha = 0.55f)
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = PayloadColors.Primary,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier,
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 34.dp)
                    .clip(CircleShape)
                    .background(if (enabled) color else PayloadColors.TextMuted.copy(alpha = 0.42f))
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(10.dp),
                thumbTrackGapSize = 6.dp,
                trackInsideCornerSize = 5.dp,
                colors = SliderDefaults.colors(
                    activeTrackColor = if (enabled) color else PayloadColors.TextMuted.copy(alpha = 0.30f),
                    inactiveTrackColor = color.copy(alpha = if (enabled) 0.14f else 0.08f),
                    disabledActiveTrackColor = PayloadColors.TextMuted.copy(alpha = 0.30f),
                    disabledInactiveTrackColor = PayloadColors.TextMuted.copy(alpha = 0.10f)
                )
            )
        },
        colors = SliderDefaults.colors(
            thumbColor = color,
            activeTrackColor = color,
            inactiveTrackColor = color.copy(alpha = 0.14f),
            disabledThumbColor = PayloadColors.TextMuted.copy(alpha = 0.42f),
            disabledActiveTrackColor = PayloadColors.TextMuted.copy(alpha = 0.30f),
            disabledInactiveTrackColor = PayloadColors.TextMuted.copy(alpha = 0.10f)
        )
    )
}

@Composable
fun PayloadWaveMeter(
    modifier: Modifier = Modifier,
    color: Color = PayloadColors.Primary,
    active: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        listOf(10.dp, 18.dp, 26.dp, 15.dp, 22.dp).forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = if (active && index % 2 == 0) height + 4.dp else height)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (active) 0.88f else 0.34f))
            )
        }
    }
}
