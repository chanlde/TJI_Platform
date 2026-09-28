package com.tji.device.ui.main

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tji.device.di.AppContainer
import com.tji.device.product.firebucket.model.FireBucketSwitchState
import com.tji.device.product.firebucket.transport.DirectLinkRangeTestEvent
import com.tji.device.product.firebucket.transport.DirectLinkRangeTestStatus
import com.tji.device.product.firebucket.transport.DIRECT_FIRE_BUCKET_LINK_ID
import com.tji.device.product.firebucket.transport.FireBucketConnectionMode
import com.tji.device.product.firebucket.ui.control.DirectLinkRangeTestPanel
import com.tji.device.product.firebucket.ui.control.SwitchItemComposable
import com.tji.device.product.firebucket.ui.control.previewFireBucketSwitch
import com.tji.device.ui.components.TjiCardShell
import com.tji.device.ui.components.TjiOnlineStatus
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.theme.BucketTheme
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun DirectLinkControlRoute(
    onBack: () -> Unit,
    isFloatingWindowEnabled: Boolean,
    hasFloatingWindowPermission: Boolean,
    onFloatingWindowEnabledChange: (Boolean) -> Unit,
    onOpenFloatingWindowPermission: () -> Unit,
    onConnectionModeChange: (FireBucketConnectionMode) -> Unit
) {
    val state by AppContainer.directFireBucketState.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var rangeTestStatus by remember { mutableStateOf<DirectLinkRangeTestStatus?>(null) }
    var rangeTestEvents by remember { mutableStateOf(emptyList<DirectLinkRangeTestEvent>()) }
    var rangeTestCursor by remember { mutableStateOf(0) }
    var rangeTestRunId by remember { mutableStateOf("") }
    var rangeTestBusy by remember { mutableStateOf(false) }
    var rangeTestMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refreshRangeTest(includeEvents: Boolean) {
        val client = AppContainer.directLinkRangeTestClient
        val freshStatus = client.status()
        if (freshStatus.runId != rangeTestRunId) {
            rangeTestRunId = freshStatus.runId
            rangeTestCursor = 0
            rangeTestEvents = emptyList()
        }
        if (includeEvents && freshStatus.runId.isNotBlank() && freshStatus.runId != "none") {
            var pages = 0
            var response = client.events(rangeTestCursor)
            val collected = mutableListOf<DirectLinkRangeTestEvent>()
            while (true) {
                collected += response.events
                rangeTestCursor = response.nextCursor
                if (!response.truncated || ++pages >= 4) break
                response = client.events(rangeTestCursor)
            }
            if (collected.isNotEmpty()) {
                rangeTestEvents = (rangeTestEvents + collected).takeLast(MAX_VISIBLE_RANGE_EVENTS)
            }
        }
        rangeTestStatus = freshStatus
    }

    LaunchedEffect(state.isConnected) {
        if (!state.isConnected) return@LaunchedEffect
        runCatching { refreshRangeTest(includeEvents = true) }
    }
    LaunchedEffect(rangeTestStatus?.active) {
        if (rangeTestStatus?.active != true) return@LaunchedEffect
        while (isActive) {
            delay(RANGE_TEST_POLL_INTERVAL_MS)
            runCatching { refreshRangeTest(includeEvents = true) }
                .onFailure { rangeTestMessage = it.message ?: "无法读取 ESP 拉距数据" }
        }
    }

    DirectLinkControlScreen(
        isConnected = state.isConnected,
        buckets = state.buckets,
        onBack = onBack,
        isFloatingWindowEnabled = isFloatingWindowEnabled,
        hasFloatingWindowPermission = hasFloatingWindowPermission,
        onFloatingWindowEnabledChange = onFloatingWindowEnabledChange,
        onOpenFloatingWindowPermission = onOpenFloatingWindowPermission,
        onConnectionModeChange = onConnectionModeChange,
        rangeTestStatus = rangeTestStatus,
        rangeTestEvents = rangeTestEvents,
        rangeTestBusy = rangeTestBusy,
        rangeTestMessage = rangeTestMessage,
        onRangeTestStart = {
            scope.launch {
                rangeTestBusy = true
                rangeTestMessage = null
                try {
                    val started = AppContainer.directLinkRangeTestClient.start()
                    rangeTestRunId = started.runId
                    rangeTestCursor = 0
                    rangeTestEvents = emptyList()
                    rangeTestStatus = started
                    refreshRangeTest(includeEvents = true)
                } catch (error: Exception) {
                    rangeTestMessage = error.message ?: "无法开始 ESP 拉距记录"
                } finally {
                    rangeTestBusy = false
                }
            }
        },
        onRangeTestStop = {
            scope.launch {
                rangeTestBusy = true
                rangeTestMessage = null
                try {
                    rangeTestStatus = AppContainer.directLinkRangeTestClient.stop()
                    refreshRangeTest(includeEvents = true)
                } catch (error: Exception) {
                    rangeTestMessage = error.message ?: "无法结束 ESP 拉距记录"
                } finally {
                    rangeTestBusy = false
                }
            }
        },
        onRangeTestExport = {
            val runId = rangeTestStatus?.runId.orEmpty()
            scope.launch {
                rangeTestBusy = true
                rangeTestMessage = null
                try {
                    val report = AppContainer.directLinkRangeTestClient.downloadReport(runId)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, report)
                        clipData = ClipData.newRawUri("ESP range-test CSV", report)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "导出拉距测试 CSV"))
                } catch (error: Exception) {
                    rangeTestMessage = error.message ?: "无法下载 ESP 拉距 CSV"
                } finally {
                    rangeTestBusy = false
                }
            }
        }
    )
}

/** 数传主页直接展示 H618 的物理负载结构，不增加产品概览和设备选择中间层。 */
@Composable
fun DirectLinkControlScreen(
    isConnected: Boolean,
    buckets: List<FireBucketSwitchState>,
    onBack: () -> Unit,
    isFloatingWindowEnabled: Boolean,
    hasFloatingWindowPermission: Boolean,
    onFloatingWindowEnabledChange: (Boolean) -> Unit,
    onOpenFloatingWindowPermission: () -> Unit,
    onConnectionModeChange: (FireBucketConnectionMode) -> Unit,
    rangeTestStatus: DirectLinkRangeTestStatus? = null,
    rangeTestEvents: List<DirectLinkRangeTestEvent> = emptyList(),
    rangeTestBusy: Boolean = false,
    rangeTestMessage: String? = null,
    onRangeTestStart: () -> Unit = {},
    onRangeTestStop: () -> Unit = {},
    onRangeTestExport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PayloadColors.Background,
        topBar = {
            DeviceDetailTopBar(
                title = "本地数传控制",
                isOnline = isConnected,
                showSettings = true,
                onBack = onBack,
                onSettings = { showSettings = true }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PayloadColors.Background),
            contentPadding = PaddingValues(PayloadDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)
        ) {
            item { DirectConnectionCard(isConnected = isConnected) }
            item { WiredLoadPortsCard() }
            item { SectionHeading(title = "无线负载") }
            if (buckets.isEmpty()) {
                item {
                    TjiCardShell {
                        Text(
                            modifier = Modifier.padding(PayloadDimens.CardPadding),
                            text = directWirelessEmptyMessage(isConnected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PayloadColors.TextSecondary
                        )
                    }
                }
            } else {
                items(buckets, key = { it.serialNumber }) { bucket ->
                    SwitchItemComposable(
                        linkSn = DIRECT_FIRE_BUCKET_LINK_ID,
                        linkOnline = isConnected,
                        switch = bucket
                    )
                }
            }
            item {
                DirectLinkRangeTestPanel(
                    bridgeConnected = isConnected,
                    status = rangeTestStatus,
                    recentEvents = rangeTestEvents,
                    busy = rangeTestBusy,
                    message = rangeTestMessage,
                    onStart = onRangeTestStart,
                    onStop = onRangeTestStop,
                    onExport = onRangeTestExport
                )
            }
        }
    }

    if (showSettings) {
        PlatformSettingsSheet(
            account = null,
            deviceCount = buckets.size,
            isFloatingWindowEnabled = isFloatingWindowEnabled,
            hasFloatingWindowPermission = hasFloatingWindowPermission,
            connectionMode = FireBucketConnectionMode.DIRECT_LINK,
            onConnectionModeChange = onConnectionModeChange,
            onFloatingWindowEnabledChange = onFloatingWindowEnabledChange,
            onOpenFloatingWindowPermission = onOpenFloatingWindowPermission,
            onDismiss = { showSettings = false }
        )
    }

    BackHandler(onBack = onBack)
}

@Composable
private fun DirectConnectionCard(isConnected: Boolean) {
    TjiCardShell {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PayloadDimens.CardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "数传链路",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PayloadColors.TextPrimary
                )
                Text(
                    text = if (isConnected) "已连接" else "未连接",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PayloadColors.TextSecondary
                )
            }
            TjiOnlineStatus(isOnline = isConnected)
        }
    }
}

@Composable
private fun WiredLoadPortsCard() {
    TjiSectionCard(title = "有线负载") {
        directWiredLoadLabels().forEach { loadLabel ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = loadLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PayloadColors.TextPrimary
                )
                Text(
                    text = "未接入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PayloadColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = PayloadColors.TextPrimary
    )
}

internal fun directWiredLoadLabels(): List<String> =
    (1..WIRED_LOAD_PORT_COUNT).map { index -> "LOAD$index" }

internal fun directWirelessEmptyMessage(isConnected: Boolean): String =
    if (isConnected) "等待全志上报无线负载" else "连接数传设备后显示无线负载"

private const val RANGE_TEST_POLL_INTERVAL_MS = 800L
private const val MAX_VISIBLE_RANGE_EVENTS = 24
private const val WIRED_LOAD_PORT_COUNT = 4

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun DirectLinkControlScreenPreview() {
    BucketTheme {
        DirectLinkControlScreen(
            isConnected = true,
            buckets = listOf(
                previewFireBucketSwitch().copy(serialNumber = "FB00A123"),
                previewFireBucketSwitch().copy(serialNumber = "FB00B456", currentAngle = 25.0)
            ),
            onBack = {},
            isFloatingWindowEnabled = true,
            hasFloatingWindowPermission = true,
            onFloatingWindowEnabledChange = {},
            onOpenFloatingWindowPermission = {},
            onConnectionModeChange = {}
        )
    }
}
