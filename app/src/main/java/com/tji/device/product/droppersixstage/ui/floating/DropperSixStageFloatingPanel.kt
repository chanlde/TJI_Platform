package com.tji.device.product.droppersixstage.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tji.device.ui.components.TjiMiniSwitch
import com.tji.device.ui.floating.FloatingLinkSummary
import com.tji.device.ui.theme.TjiBorder
import com.tji.device.ui.theme.TjiPrimary
import com.tji.device.ui.theme.TjiSurface
import com.tji.device.ui.theme.TjiTextPrimary

@Composable
fun DropperSixStageFloatingPanel(
    link: FloatingLinkSummary?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            repeat(6) { index ->
                DropperStageCompactToggle(
                    stage = index + 1,
                    enabled = link?.isOnline == true
                )
            }
        }
    }
}

@Composable
private fun DropperStageCompactToggle(
    stage: Int,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .width(122.dp)
            .background(TjiSurface.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(
                    color = if (enabled) TjiPrimary.copy(alpha = 0.14f) else TjiBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stage.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) TjiPrimary else Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "${stage}段",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = TjiTextPrimary,
            modifier = Modifier.weight(1f)
        )
        TjiMiniSwitch(
            checked = false,
            enabled = enabled
        )
    }
}
