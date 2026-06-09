package com.tji.device.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.TjiControlDisabled
import com.tji.device.ui.theme.TjiControlInactive

@Composable
fun TjiMiniSwitch(
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 16.dp else 0.dp,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "tjiMiniSwitchThumbOffset"
    )
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> TjiControlDisabled
            checked -> PayloadColors.Primary
            else -> TjiControlInactive
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "tjiMiniSwitchTrackColor"
    )
    val resolvedModifier = if (onCheckedChange != null) {
        modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    } else {
        modifier
    }

    Box(
        modifier = resolvedModifier
            .size(width = 34.dp, height = 18.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor)
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(14.dp)
                .shadow(1.5.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Preview(showBackground = true, name = "TJI Mini Switch")
@Composable
private fun TjiMiniSwitchPreview() {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        TjiMiniSwitch(checked = false, enabled = true)
        TjiMiniSwitch(checked = true, enabled = true)
        TjiMiniSwitch(checked = false, enabled = false)
    }
}
