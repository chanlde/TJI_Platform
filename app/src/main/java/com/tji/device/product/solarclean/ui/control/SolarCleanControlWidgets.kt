package com.tji.device.product.solarclean.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tji.device.product.solarclean.ui.icon.PumpPressure
import com.tji.device.product.solarclean.ui.icon.SprayAngle
import com.tji.device.product.solarclean.ui.icon.SwingSpeed
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedback
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedbackStatus
import com.tji.device.ui.components.TjiActionButton
import com.tji.device.ui.components.TjiControlSlider
import com.tji.device.ui.components.TjiFeedbackBadge
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline
import kotlin.math.roundToInt

@Composable
internal fun CommandFeedbackBadge(feedback: SolarCleanCommandFeedback) {
    val color = when (feedback.status) {
        SolarCleanCommandFeedbackStatus.Success -> TjiOnline
        SolarCleanCommandFeedbackStatus.Failed,
        SolarCleanCommandFeedbackStatus.Timeout -> TjiError
        SolarCleanCommandFeedbackStatus.Pending,
        SolarCleanCommandFeedbackStatus.Idle -> PayloadColors.TextMuted
    }
    TjiFeedbackBadge(
        text = feedback.text,
        color = color,
        radius = 10.dp,
        horizontalPadding = 8.dp,
        verticalPadding = 5.dp
    )
}

@Composable
internal fun ControlSlider(
    kind: SliderKind,
    title: String,
    value: Float,
    unit: String,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SolarCleanControlGlyph(kind = kind, size = 28)
                Text(text = title, fontSize = 12.sp, color = PayloadColors.TextMuted)
            }
            Text(
                text = "${value.roundToInt()}$unit",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = PayloadColors.TextPrimary
            )
        }
        TjiControlSlider(
            value = value,
            onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}

@Composable
private fun SolarCleanControlGlyph(kind: SliderKind, size: Int = 30) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(PayloadColors.PrimarySoft, RoundedCornerShape(PayloadDimens.ControlRadius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (kind) {
                SliderKind.Pressure -> PumpPressure
                SliderKind.Angle -> SprayAngle
                SliderKind.Speed -> SwingSpeed
            },
            contentDescription = null,
            modifier = Modifier.size((size - 12).dp),
            tint = PayloadColors.Primary
        )
    }
}

internal enum class SliderKind {
    Pressure,
    Angle,
    Speed
}

@Composable
internal fun ControlButton(
    text: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    TjiActionButton(
        text = text,
        enabled = enabled,
        color = color,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.47f)
    )
}
