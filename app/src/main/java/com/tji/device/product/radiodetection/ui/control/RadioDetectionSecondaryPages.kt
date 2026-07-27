package com.tji.device.product.radiodetection.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.product.radiodetection.model.RadioDetectionTab
import com.tji.device.product.radiodetection.model.RadioDetectionUiState
import com.tji.device.product.radiodetection.model.RadioEnforcementRecord
import com.tji.device.product.radiodetection.model.RadioListEntry
import com.tji.device.product.radiodetection.model.RadioListStatus
import com.tji.device.product.radiodetection.model.RadioTrackRecord
import com.tji.device.product.radiodetection.model.RadioWarningZone

@Composable
internal fun SecondaryScreen(
    tab: RadioDetectionTab,
    state: RadioDetectionUiState,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(PageBg)) {
        BlackHeader(
            title = "无线电检测",
            subtitle = "${tab.titleText()} · ${tab.subtitleText()}",
            actionText = "",
            trailingText = "",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                RadioDetectionTab.Tracks -> {
                    items(
                        items = state.tracks,
                        key = { "${it.serialNumber}:${it.timeRange}" }
                    ) { TrackRecordCard(it) }
                    if (state.tracks.isEmpty()) {
                        item { EmptyStateCard("暂无轨迹记录", "设备协议尚未提供历史轨迹数据") }
                    }
                }
                RadioDetectionTab.Zones -> {
                    items(
                        items = state.zones,
                        key = { "${it.name}:${it.createdAt}" }
                    ) { WarningZoneCard(it) }
                    if (state.zones.isEmpty()) {
                        item { EmptyStateCard("暂无预警区域", "预警区域配置接口尚未接入") }
                    }
                }
                RadioDetectionTab.Enforcement -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(
                                "执行中 ${state.enforcementRecords.count { it.status == "执行中" }}",
                                true,
                                Blue
                            )
                            StatusChip(
                                "已完成 ${state.enforcementRecords.count { it.status == "已完成" }}",
                                false,
                                Green
                            )
                            StatusChip(
                                "已取消 ${state.enforcementRecords.count { it.status == "已取消" }}",
                                false,
                                TextMuted
                            )
                        }
                    }
                    items(
                        items = state.enforcementRecords,
                        key = { it.recordNumber }
                    ) { EnforcementCard(it) }
                    if (state.enforcementRecords.isEmpty()) {
                        item { EmptyStateCard("暂无执法记录", "执法记录接口尚未接入") }
                    }
                }
                RadioDetectionTab.Lists -> {
                    items(
                        items = state.listEntries,
                        key = { it.serialNumber }
                    ) { ListEntryCard(it) }
                    if (state.listEntries.isEmpty()) {
                        item { EmptyStateCard("暂无名单数据", "名单管理接口尚未接入") }
                    }
                }
                RadioDetectionTab.Monitor -> Unit
            }
        }
    }
}

@Composable
private fun TrackRecordCard(record: RadioTrackRecord) {
    InfoCard(
        title = record.targetName,
        badge = if (record.status == RadioListStatus.Blacklist) "高风险" else "授权",
        badgeColor = record.status.statusColor(),
        subtitle = record.serialNumber
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("起止", record.timeRange, Modifier.weight(1f))
            MiniMetric("时长", record.duration, Modifier.weight(1f))
            MiniMetric("点位", "${record.pointCount}", Modifier.weight(1f))
            MiniMetric("最大高度", "${record.maxAltitudeMeters}m", Modifier.weight(1f))
        }
    }
}

@Composable
private fun WarningZoneCard(zone: RadioWarningZone) {
    InfoCard(
        title = zone.name,
        badge = zone.level,
        badgeColor = if (zone.level == "严重") Red else Blue,
        subtitle = "${zone.shape} · ${zone.center}"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("半径", zone.range, Modifier.weight(1f))
            MiniMetric("状态", if (zone.enabled) "启用" else "停用", Modifier.weight(1f))
            MiniMetric("创建", zone.createdAt, Modifier.weight(1f))
            MiniMetric("类型", zone.shape, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EnforcementCard(record: RadioEnforcementRecord) {
    InfoCard(
        title = record.recordNumber,
        badge = record.status,
        badgeColor = if (record.status == "执行中") Red else Green,
        subtitle = "${record.targetName} · ${record.targetSerialNumber}"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("时间", record.handledAt, Modifier.weight(1f))
            MiniMetric("位置", record.location, Modifier.weight(1f))
            MiniMetric("人员", record.operator, Modifier.weight(1f))
            MiniMetric("状态", record.status, Modifier.weight(1f))
        }
        Text(record.note, color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ListEntryCard(entry: RadioListEntry) {
    InfoCard(
        title = entry.serialNumber,
        badge = entry.status.label,
        badgeColor = entry.status.statusColor(),
        subtitle = "${entry.maker} · ${entry.type}"
    ) {
        Text("添加原因：${entry.reason}。添加人：${entry.createdBy}。添加时间：${entry.createdAt}", color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoCard(
    title: String,
    badge: String,
    badgeColor: Color,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusChip(badge, true, badgeColor)
        }
        content()
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RadioDetectionTab.titleText(): String = when (this) {
    RadioDetectionTab.Monitor -> "实时监控"
    RadioDetectionTab.Tracks -> "轨迹记录"
    RadioDetectionTab.Zones -> "预警区域"
    RadioDetectionTab.Enforcement -> "执法记录"
    RadioDetectionTab.Lists -> "黑白名单"
}

private fun RadioDetectionTab.subtitleText(): String = when (this) {
    RadioDetectionTab.Monitor -> "实时目标"
    RadioDetectionTab.Tracks -> "历史轨迹"
    RadioDetectionTab.Zones -> "区域配置"
    RadioDetectionTab.Enforcement -> "处置记录"
    RadioDetectionTab.Lists -> "名单数据"
}
