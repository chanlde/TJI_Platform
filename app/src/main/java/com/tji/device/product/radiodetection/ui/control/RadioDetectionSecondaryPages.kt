package com.tji.device.product.radiodetection.ui.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.product.radiodetection.model.RadioDetectionTab
import com.tji.device.product.radiodetection.model.RadioDetectionTarget
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
            actionText = if (tab == RadioDetectionTab.Tracks) "筛" else "+",
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
                    item { QueryRow() }
                    items(state.tracks) { TrackRecordCard(it) }
                    if (state.tracks.isEmpty()) {
                        item { EmptyStateCard("暂无轨迹记录", "真实轨迹数据到达后显示") }
                    }
                    state.targets.firstOrNull()?.let { target ->
                        item { TrackMapCard(target) }
                    }
                }
                RadioDetectionTab.Zones -> {
                    items(state.zones) { WarningZoneCard(it) }
                    if (state.zones.isEmpty()) {
                        item { EmptyStateCard("暂无预警区域", "真实区域配置接入后显示") }
                    }
                    item { NewZoneCard() }
                }
                RadioDetectionTab.Enforcement -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip("执行中 --", true, Blue)
                            StatusChip("已完成 --", false, Green)
                            StatusChip("已取消 --", false, TextMuted)
                        }
                    }
                    items(state.enforcementRecords) { EnforcementCard(it) }
                    if (state.enforcementRecords.isEmpty()) {
                        item { EmptyStateCard("暂无执法记录", "真实处置记录到达后显示") }
                    } else {
                        item { TimelineCard() }
                    }
                }
                RadioDetectionTab.Lists -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip("全部", true, Blue)
                            StatusChip("黑名单", false, Red)
                            StatusChip("白名单", false, Green)
                        }
                    }
                    item { SearchBox() }
                    items(state.listEntries) { ListEntryCard(it) }
                    if (state.listEntries.isEmpty()) {
                        item { EmptyStateCard("暂无名单数据", "真实名单数据接入后显示") }
                    }
                    item { AddListCard() }
                }
                RadioDetectionTab.Monitor -> Unit
            }
        }
    }
}

@Composable
private fun QueryRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(62.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardBg)
                .border(1.dp, Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("选择日期", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        SolidAction("查询", modifier = Modifier.width(64.dp).height(62.dp)) {}
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
            MiniMetric("起止", "--", Modifier.weight(1f))
            MiniMetric("时长", record.duration, Modifier.weight(1f))
            MiniMetric("点位", "${record.pointCount}", Modifier.weight(1f))
            MiniMetric("最大高度", "${record.maxAltitudeMeters}m", Modifier.weight(1f))
        }
        OutlineAction("查看轨迹详情", Modifier.fillMaxWidth()) {}
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
            MiniMetric("创建", "--", Modifier.weight(1f))
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
            MiniMetric("状态", "取证", Modifier.weight(1f))
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineAction("复制序列号") {}
            OutlineAction("删除") {}
        }
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
private fun TrackMapCard(target: RadioDetectionTarget) {
    InfoCard("轨迹详情地图", "待回放 · 0%", Blue, "当前点：纬度 37.865112 / 经度 116.295442 / 高度 120m") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(MapBg)
                drawCircle(Blue.copy(alpha = 0.35f), size.minDimension * 0.34f, Offset(size.width * 0.48f, size.height * 0.48f), style = Stroke(1.dp.toPx()))
                drawLine(Blue, Offset(size.width * 0.28f, size.height * 0.62f), Offset(size.width * 0.72f, size.height * 0.28f), strokeWidth = 2.dp.toPx())
            }
            MapMarker("起", Red, {}, Modifier.offset(x = 72.dp, y = 84.dp))
            MapMarker("终", Amber, {}, Modifier.offset(x = 190.dp, y = 36.dp))
        }
        OutlineAction("播放", Modifier.width(70.dp)) {}
    }
}

@Composable
private fun MapMarker(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(CardBg.copy(alpha = 0.28f))
            .padding(5.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, CardBg.copy(alpha = 0.88f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (color == Amber) TextPrimary else Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun NewZoneCard() {
    InfoCard("新建预警区域", "步骤 1 / 4", Blue, "区域名称") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardBg)
                .border(1.dp, Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("请输入区域名称", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlineAction("上一步", Modifier.weight(1f)) {}
            OutlineAction("下一步 / 保存", Modifier.weight(1.7f)) {}
        }
    }
}

@Composable
private fun TimelineCard() {
    InfoCard("记录详情", "状态流转", Blue, "处置过程") {
        Text("等待真实状态流转", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SearchBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text("搜索序列号 / 厂商 / 添加原因", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AddListCard() {
    InfoCard("添加名单", "表单", Blue, "名单类型") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("黑名单", true, Blue)
            StatusChip("白名单", false, TextMuted)
        }
        FormLine("请输入远程识别码 / 序列号")
        FormLine("请输入厂商")
        FormLine("请输入添加原因")
        SolidAction("保存名单", Modifier.fillMaxWidth().height(44.dp)) {}
    }
}

@Composable
private fun FormLine(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun RadioDetectionTab.titleText(): String = when (this) {
    RadioDetectionTab.Monitor -> "实时监控"
    RadioDetectionTab.Tracks -> "轨迹记录"
    RadioDetectionTab.Zones -> "预警区域"
    RadioDetectionTab.Enforcement -> "执法记录"
    RadioDetectionTab.Lists -> "黑白名单"
}

private fun RadioDetectionTab.subtitleText(): String = when (this) {
    RadioDetectionTab.Monitor -> "频谱检测仪 T1640618D"
    RadioDetectionTab.Tracks -> "最近 24 小时"
    RadioDetectionTab.Zones -> "区域列表与新建流程"
    RadioDetectionTab.Enforcement -> "今日处置闭环"
    RadioDetectionTab.Lists -> "设备识别与审核"
}
