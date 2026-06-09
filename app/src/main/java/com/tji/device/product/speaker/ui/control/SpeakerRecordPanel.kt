package com.tji.device.product.speaker.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tji.device.product.speaker.model.SpeakerDeviceState
import com.tji.device.product.speaker.model.SpeakerRecord
import com.tji.device.product.speaker.viewmodel.SpeakerTalkMode

@Composable
internal fun SpeakerRecordSaveCard(
    recordName: String,
    enabled: Boolean,
    hasMicPermission: Boolean,
    mode: SpeakerTalkMode,
    onRecordNameChange: (String) -> Unit,
    requestPermission: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit
) {
    SpeakerCard(title = "保存一段喊话") {
        OutlinedTextField(
            value = recordName,
            onValueChange = onRecordNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("录音名称") }
        )
        SpeakerSoftRow(
            label = "保存方式",
            value = if (hasMicPermission) "按住录制，松开保存" else "需要麦克风权限",
            valueColor = if (hasMicPermission) SpeakerFg else SpeakerDanger
        )
        PushToTalkButton(
            enabled = enabled,
            hasMicPermission = hasMicPermission,
            mode = mode,
            idleLabel = "按住录制",
            activeLabel = "录制中",
            footer = if (hasMicPermission) "保持按住，说完后松开" else "需要麦克风权限",
            compact = true,
            requestPermission = requestPermission,
            onPress = onPress,
            onRelease = onRelease,
            onCancel = onCancel
        )
    }
}

@Composable
internal fun SpeakerRecordBrowser(
    recordQuery: String,
    visibleRecords: List<SpeakerRecord>,
    state: SpeakerDeviceState?,
    enabled: Boolean,
    currentVolume: Int,
    onRecordQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .padding(top = 2.dp)
        ) {
            OutlinedTextField(
                value = recordQuery,
                onValueChange = onRecordQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索录音名称") }
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "当前显示 ${visibleRecords.size} 条",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpeakerMuted,
                    modifier = Modifier.weight(1f)
                )
                SpeakerActionButton(
                    text = "刷新",
                    enabled = enabled,
                    color = SpeakerAccent,
                    soft = true,
                    onClick = onRefresh
                )
            }
        }
        RecordEventText(state)
        RecordList(
            records = visibleRecords,
            total = state?.recordTotal ?: 0,
            hasMore = state?.recordHasMore == true,
            enabled = enabled,
            currentVolume = currentVolume,
            onPlay = onPlay,
            onRename = onRename,
            onDelete = onDelete
        )
    }
}
