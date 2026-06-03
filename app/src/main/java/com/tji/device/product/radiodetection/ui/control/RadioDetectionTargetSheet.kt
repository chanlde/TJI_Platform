package com.tji.device.product.radiodetection.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.product.radiodetection.model.RadioDetectionTarget
import com.tji.device.product.radiodetection.model.RadioDetectionUiState
import com.tji.device.product.radiodetection.model.RadioListStatus

@Composable
internal fun TargetSheet(
    state: RadioDetectionUiState,
    expanded: Boolean,
    statusFilter: RadioListStatus?,
    onHide: () -> Unit,
    onOpenFilter: () -> Unit,
    onReplayLatestRid: () -> Unit,
    onTargetLocate: (RadioDetectionTarget) -> Unit,
    onTargetClick: (RadioDetectionTarget) -> Unit,
    onTargetAction: (RadioDetectionTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val targets = remember(state.targets, statusFilter) {
        statusFilter?.let { status -> state.targets.filter { it.listStatus == status } } ?: state.targets
    }
    val sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (expanded) Modifier.height(430.dp) else Modifier.defaultMinSize(minHeight = 78.dp))
            .clip(sheetShape)
            .background(CardBg)
            .border(1.dp, Border, sheetShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!expanded) {
            val primary = targets.firstOrNull() ?: state.targets.firstOrNull()
            if (primary == null) {
                EmptyCompactTargetRow(
                    onHide = onHide,
                    onReplayLatestRid = onReplayLatestRid
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTargetLocate(primary) },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip("${primary.listStatus.label} 1", true, primary.listStatus.statusColor())
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${primary.name} · ${primary.pilotDistanceText} ${primary.pilotName}", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${primary.altitudeMeters}m · ${primary.speedMetersPerSecond}m/s · ${primary.frequencyLabel} · 最近更新${primary.lastSeenText}", color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlineAction("处置") { onTargetAction(primary) }
                    CompactHideAction(onHide)
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("实时目标", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("网络良好 · 加密链路已开启", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineAction("隐藏", onClick = onHide)
                    SolidAction("筛选", onClick = onOpenFilter)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("全部 ${state.discoveredTargetCount}", statusFilter == null, Blue)
                StatusChip("黑名单 ${state.blacklistCount}", statusFilter == RadioListStatus.Blacklist, Red)
                StatusChip("白名单 ${state.whitelistCount}", statusFilter == RadioListStatus.Whitelist, Green)
                StatusChip("未知 ${state.unknownCount}", statusFilter == RadioListStatus.Unknown, Amber)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (targets.isEmpty()) {
                    item { EmptyRealtimeTargetCard(onReplayLatestRid) }
                } else {
                    items(targets, key = { it.id }) { target ->
                        TargetCard(
                            target = target,
                            onLocate = { onTargetLocate(target) },
                            onDetail = { onTargetClick(target) },
                            onAction = { onTargetAction(target) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCompactTargetRow(
    onHide: () -> Unit,
    onReplayLatestRid: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusChip("目标 0", true, Blue)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("等待真实 RID 数据", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("收到无人机和飞手位置后会自动刷新", color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlineAction("回放", onClick = onReplayLatestRid)
        CompactHideAction(onHide)
    }
}

@Composable
private fun EmptyRealtimeTargetCard(onReplayLatestRid: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("暂无实时目标", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        Text("真实 RID 数据到达后会显示在这里", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        OutlineAction("回放缓存", onClick = onReplayLatestRid)
    }
}

@Composable
private fun CompactHideAction(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .border(1.dp, Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("×", color = TextMuted, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun MiniTargetHandle(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(CardBg.copy(alpha = 0.94f))
            .border(1.dp, Border, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Blue)
        )
        Text("目标 $count", color = TextPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TargetCard(
    target: RadioDetectionTarget,
    onLocate: () -> Unit,
    onDetail: () -> Unit,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(target.name, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(target.serialNumber, color = TextMuted, style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            StatusChip(target.listStatus.label, true, target.listStatus.statusColor())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("高度", "${target.altitudeMeters}m", Modifier.weight(1f))
            MiniMetric("速度", "${target.speedMetersPerSecond}m/s", Modifier.weight(1f))
            MiniMetric("航向", "${target.headingDegrees}°", Modifier.weight(1f))
            MiniMetric("信号", target.signalLevel.label, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineAction("定位", Modifier.weight(1f), onLocate)
            OutlineAction("详情", Modifier.weight(1f), onDetail)
            OutlineAction(if (target.listStatus == RadioListStatus.Blacklist) "处置" else "加入名单", Modifier.weight(1f), onAction)
        }
    }
}

@Composable
internal fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(androidx.compose.ui.graphics.Color(0xFFF7F8FA))
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
