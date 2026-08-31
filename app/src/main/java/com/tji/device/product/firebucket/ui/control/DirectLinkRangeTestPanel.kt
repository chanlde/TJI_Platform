package com.tji.device.product.firebucket.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.tji.device.product.firebucket.transport.DirectLinkRangeTestEvent
import com.tji.device.product.firebucket.transport.DirectLinkRangeTestStatus
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.components.TjiStatusText
import com.tji.device.ui.theme.PayloadColors

/** 数传主页无线负载下方的紧凑调试与日志导出面板。 */
@Composable
internal fun DirectLinkRangeTestPanel(
    bridgeConnected: Boolean,
    status: DirectLinkRangeTestStatus?,
    recentEvents: List<DirectLinkRangeTestEvent>,
    busy: Boolean,
    message: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = status?.active == true
    val canStart = bridgeConnected && !active && !busy && status?.storageReady != false
    val canStop = active && !busy
    val canExport = !active && !busy && (status?.endedAtMs ?: 0L) > 0L

    TjiSectionCard(
        title = "拉距调试",
        modifier = modifier,
        trailing = {
            TjiStatusText(
                text = if (active) "记录中" else "未记录",
                color = if (active) PayloadColors.Primary else PayloadColors.TextMuted
            )
        }
    ) {
        Text(
            text = buildString {
                append("发送 ${status?.appTx ?: 0} · ACK ${status?.acknowledgements ?: 0}")
                append(" · 重试 ${status?.retries ?: 0} · 超时 ${status?.timeouts ?: 0}")
            },
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = PayloadColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (active || recentEvents.isNotEmpty()) {
            Text(
                text = recentEvents.takeLast(2).asReversed().joinToString(" · ") { event ->
                    val sequence = if (event.sequence == 0) "" else " %04X".format(event.sequence)
                    val latency = if (event.ackLatencyMs == 0L) "" else " ${event.ackLatencyMs}ms"
                    "${event.name}$sequence$latency"
                }.ifBlank { "等待手动控制指令" },
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = PayloadColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        message?.let {
            Text(
                text = it,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = PayloadColors.Warning
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onStart, enabled = canStart) {
                Text("开始")
            }
            OutlinedButton(onClick = onStop, enabled = canStop) {
                Text("结束")
            }
            OutlinedButton(onClick = onExport, enabled = canExport) {
                Text("导出")
            }
        }
    }
}
