package com.tji.device.product.firebucket.ui.floating

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tji.device.data.model.ProductType
import com.tji.device.ui.components.BatteryIndicator
import com.tji.device.ui.icon.common.BatteryOutline
import com.tji.device.ui.floating.FloatingLinkSummary
import com.tji.device.ui.floating.FloatingSwitchSummary
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import com.tji.device.ui.theme.TjiOnline

@Composable
fun FireBucketFloatingPanel(
    link: FloatingLinkSummary?,
    switch: FloatingSwitchSummary?,
    allSwitches: List<FloatingSwitchSummary>,
    currentSwitchIndex: Int,
    onSwitchSelected: (Int) -> Unit,
    onSwitchQuickToggle: (String, FloatingSwitchSummary, Boolean) -> Unit
) {
    if (switch != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .then(
                    if (allSwitches.size > 1) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val nextIndex = if (currentSwitchIndex < allSwitches.size - 1) {
                                    currentSwitchIndex + 1
                                } else 0
                                onSwitchSelected(nextIndex)
                            }
                        )
                    } else Modifier
                )
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (switch.isOnline) TjiOnline else PayloadColors.TextMuted,
                        shape = RoundedCornerShape(3.dp)
                    )
            )
            Text(
                text = switch.name,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = PayloadColors.TextMuted
            )
            Spacer(modifier = Modifier.width(8.dp))
            BatteryIndicator(
                voltage = switch.inputVoltage,
                iconSize = 16.dp
            )
        }
    }

    Spacer(modifier = Modifier.height(5.dp))
    HorizontalDivider(
        color = PayloadColors.Border.copy(alpha = 0.8f),
        thickness = 1.dp
    )

    if (switch == null || link == null) {
        EmptyProductPanel(
            message = if (link == null) {
                "设备未连接"
            } else {
                "当前 Link 暂无水桶设备"
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (allSwitches.size > 1) {
                SwitchIndicator(
                    switches = allSwitches,
                    currentIndex = currentSwitchIndex,
                    onSwitchSelected = onSwitchSelected
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            ControlButtons(
                linkSerial = link.serialNumber,
                switch = switch,
                onSwitchQuickToggle = onSwitchQuickToggle
            )
        }
    }
}

@Composable
fun EmptyProductPanel(
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = PayloadColors.WarningSoft,
                    shape = RoundedCornerShape(PayloadDimens.ControlRadius)
                )
                .padding(vertical = 20.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = PayloadColors.Warning,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = PayloadColors.Warning
                )
            }
        }
    }
}

@Composable
private fun ControlButtons(
    linkSerial: String,
    switch: FloatingSwitchSummary,
    onSwitchQuickToggle: (String, FloatingSwitchSummary, Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ControlButton(
            onClick = { onSwitchQuickToggle(linkSerial, switch, true) },
            backgroundColor = PayloadColors.Danger,
            icon = {
                Icon(
                    imageVector = BatteryOutline,
                    contentDescription = "开启",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        Spacer(modifier = Modifier.width(16.dp))

        ControlButton(
            onClick = { onSwitchQuickToggle(linkSerial, switch, false) },
            backgroundColor = PayloadColors.Danger,
            icon = {
                Icon(
                    imageVector = BatteryOutline,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(-45f)
                )
            }
        )
    }
}

@Composable
private fun SwitchIndicator(
    switches: List<FloatingSwitchSummary>,
    currentIndex: Int,
    onSwitchSelected: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = {
                val prevIndex = if (currentIndex > 0) currentIndex - 1 else switches.size - 1
                onSwitchSelected(prevIndex)
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "上一个",
                modifier = Modifier.size(18.dp),
                tint = PayloadColors.TextMuted
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            switches.forEachIndexed { index, switch ->
                val isSelected = index == currentIndex
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 8.dp else 6.dp)
                        .background(
                            color = if (isSelected) {
                                if (switch.isOnline) TjiOnline else PayloadColors.TextMuted
                            } else {
                                PayloadColors.TextMuted.copy(alpha = 0.5f)
                            },
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSwitchSelected(index) }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = {
                val nextIndex = if (currentIndex < switches.size - 1) currentIndex + 1 else 0
                onSwitchSelected(nextIndex)
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "下一个",
                modifier = Modifier.size(18.dp),
                tint = PayloadColors.TextMuted
            )
        }
    }
}

@Composable
private fun ControlButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    icon: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "button_scale"
    )

    Box(
        modifier = Modifier
            .size(30.dp)
            .scale(scale)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(PayloadDimens.ControlRadius)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
