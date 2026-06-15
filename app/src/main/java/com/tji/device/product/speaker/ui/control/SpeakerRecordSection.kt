package com.tji.device.product.speaker.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.model.SpeakerStorageStatus
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline

@Composable
internal fun StorageCapacityCard(
    status: SpeakerStorageStatus?,
    state: SpeakerDeviceState?
) {
    val totalBytes = status?.totalBytes?.takeIf { it > 0 } ?: 0L
    val usedRatio = if (totalBytes > 0L) {
        ((totalBytes - (status?.freeBytes ?: 0L)).toFloat() / totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }
    val recordCount = state?.recordTotal ?: status?.recordCount ?: 0
    SpeakerCard(title = "存储容量") {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (status == null) "正在等待设备容量" else "已使用 ${(usedRatio * 100f).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpeakerMuted
                )
                status?.takeIf { !it.ok }?.let {
                    Text(
                        text = it.message.ifBlank { "容量查询失败" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TjiError
                    )
                }
            }
            Text(
                text = "${(usedRatio * 100f).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = SpeakerFg,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(SpeakerBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(usedRatio)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(SpeakerAccent)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SpeakerMiniMetric(
                value = status?.freeBytes?.let { formatBytes(it) } ?: "--",
                label = "剩余",
                modifier = Modifier.weight(1f)
            )
            SpeakerMiniMetric(
                value = recordCount.toString(),
                label = "录音",
                modifier = Modifier.weight(1f)
            )
            SpeakerMiniMetric(
                value = status?.maxRecords?.toString() ?: "--",
                label = "上限",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SpeakerMiniMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SpeakerBg)
            .padding(12.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = SpeakerFg,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SpeakerMuted
        )
    }
}

@Composable
internal fun RecordEventText(state: SpeakerDeviceState?) {
    val event = state?.lastRecordEvent ?: return
    val label = when (event.type) {
        "record_saved" -> "保存完成"
        "record_failed" -> "保存失败"
        "record_updated" -> "改名完成"
        "record_deleted" -> "删除完成"
        "record_playback" -> "播放结果"
        else -> "设备反馈"
    }
    SpeakerStatusBadge(
        text = listOf(label, event.message).filter { it.isNotBlank() }.joinToString(" ").ifBlank { label },
        color = if (event.ok) TjiOnline else TjiError
    )
}

@Composable
internal fun RecordList(
    records: List<SpeakerRecord>,
    total: Int,
    hasMore: Boolean,
    enabled: Boolean,
    currentVolume: Int,
    onPlay: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (records.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, SpeakerBorder, RoundedCornerShape(12.dp))
                .background(SpeakerSurface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无录音，先按住保存一条",
                style = MaterialTheme.typography.bodyMedium,
                color = SpeakerMuted
            )
        }
        return
    }
    Text(
        text = "最近录音 · $total 条${if (hasMore) " · 还有更多" else ""}",
        style = MaterialTheme.typography.bodyMedium,
        color = SpeakerMuted
    )
    records.forEach { record ->
        RecordRow(
            record = record,
            enabled = enabled,
            currentVolume = currentVolume,
            onPlay = onPlay,
            onRename = onRename,
            onDelete = onDelete
        )
    }
    if (hasMore) {
        SpeakerActionButton(
            text = "加载更多",
            enabled = enabled,
            color = SpeakerAccent,
            soft = true,
            onClick = onLoadMore,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RecordRow(
    record: SpeakerRecord,
    enabled: Boolean,
    currentVolume: Int,
    onPlay: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var editingName by remember(record.recordId, record.name) { mutableStateOf(record.name) }
    var editing by remember(record.recordId) { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SpeakerBorder, RoundedCornerShape(12.dp))
            .background(SpeakerSurface)
            .padding(12.dp)
    ) {
        WaveThumbnail()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            if (editing) {
                OutlinedTextField(
                    value = editingName,
                    onValueChange = { editingName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("名称") }
                )
            } else {
                Text(
                    text = record.name.ifBlank { "未命名录音" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = SpeakerFg,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${formatDuration(record.durationMs)} · ${record.createdAt ?: formatBytes(record.fileSize)}",
                style = MaterialTheme.typography.labelMedium,
                color = SpeakerMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButtonLike(
                    text = if (editing) "保存" else "改名",
                    enabled = enabled && (!editing || editingName.trim().isNotBlank()),
                    color = SpeakerMuted,
                    onClick = {
                        if (editing) {
                            onRename(record.recordId, editingName)
                            editing = false
                        } else {
                            editing = true
                        }
                    }
                )
                TextButtonLike(
                    text = "删除",
                    enabled = enabled,
                    color = SpeakerDanger,
                    onClick = { onDelete(record.recordId) }
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, SpeakerBorder, CircleShape)
                .background(SpeakerSurface)
                .pointerInput(enabled, record.recordId) {
                    detectTapGestures(onTap = { if (enabled) onPlay(record.recordId) })
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "▶", color = SpeakerFg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WaveThumbnail() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .size(width = 58.dp, height = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SpeakerAccent.copy(alpha = 0.07f))
            .padding(horizontal = 8.dp)
    ) {
        SpeakerWaveMeter(active = true)
    }
}

@Composable
internal fun TextButtonLike(
    text: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (enabled) color else SpeakerMuted.copy(alpha = 0.42f),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.pointerInput(enabled) {
            detectTapGestures(onTap = { if (enabled) onClick() })
        }
    )
}

internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--"
    val seconds = durationMs / 1_000
    val minutes = seconds / 60
    val remain = seconds % 60
    return if (minutes > 0) "${minutes}分${remain}秒" else "${remain}秒"
}

internal fun formatBytes(bytes: Long): String =
    when {
        bytes <= 0 -> "--"
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }
