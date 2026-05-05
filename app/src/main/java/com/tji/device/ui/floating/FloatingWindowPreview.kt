package com.tji.device.ui.floating

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tji.device.data.model.ProductType

@Preview(showBackground = true, widthDp = 220)
@Composable
private fun FloatingWindowCollapsedPreview() {
    FloatingWindowContent(
        uiState = previewUiState(includeDevice = false),
        isExpanded = false,
        onToggleExpand = {},
        onMinimize = {},
        onClose = {},
        onSwitchQuickToggle = { _, _, _ -> },
        onMove = { _, _ -> },
        onResize = { _, _ -> }
    )
}

@Preview(showBackground = true, widthDp = 320, heightDp = 220)
@Composable
private fun FloatingWindowExpandedPreview() {
    FloatingWindowContent(
        uiState = previewUiState(includeDevice = true),
        isExpanded = true,
        onToggleExpand = {},
        onMinimize = {},
        onClose = {},
        onSwitchQuickToggle = { _, _, _ -> },
        onMove = { _, _ -> },
        onResize = { _, _ -> }
    )
}

@Preview(showBackground = true, widthDp = 320, heightDp = 220)
@Composable
private fun FloatingWindowMultiSwitchPreview() {
    FloatingWindowContent(
        uiState = previewUiStateMultiSwitch(),
        isExpanded = true,
        onToggleExpand = {},
        onMinimize = {},
        onClose = {},
        onSwitchQuickToggle = { _, _, _ -> },
        onMove = { _, _ -> },
        onResize = { _, _ -> }
    )
}

@Preview(showBackground = true, widthDp = 320, heightDp = 220)
@Composable
private fun FloatingWindowEmptyStatePreview() {
    FloatingWindowContent(
        uiState = previewUiStateEmpty(),
        isExpanded = true,
        onToggleExpand = {},
        onMinimize = {},
        onClose = {},
        onSwitchQuickToggle = { _, _, _ -> },
        onMove = { _, _ -> },
        onResize = { _, _ -> }
    )
}

private fun previewUiState(includeDevice: Boolean): FloatingWindowUiState {
    val switch = if (includeDevice) {
        FloatingSwitchSummary(
            serialNumber = "SW-001",
            name = "示例水桶",
            isOnline = true,
            currentAngle = 45,
            inputVoltage = 7.5
        )
    } else null

    val link = FloatingLinkSummary(
        serialNumber = "LINK-001",
        name = "示例 Link",
        isOnline = true,
        productType = ProductType.FireBucket,
        onlineSwitches = listOfNotNull(switch),
        offlineSwitches = emptyList()
    )

    return FloatingWindowUiState(
        mode = if (includeDevice) FloatingWindowMode.EXPANDED else FloatingWindowMode.ICON,
        links = listOf(link),
        selectedLinkSerial = link.serialNumber,
        preferredProductType = ProductType.FireBucket,
        isLoading = false,
        errorMessage = if (includeDevice) null else "暂无设备",
        showOfflineSwitches = false
    )
}

private fun previewUiStateEmpty(): FloatingWindowUiState {
    val link = FloatingLinkSummary(
        serialNumber = "LINK-001",
        name = "示例 Link",
        isOnline = true,
        productType = ProductType.SolarClean,
        onlineSwitches = emptyList(),
        offlineSwitches = emptyList()
    )

    return FloatingWindowUiState(
        mode = FloatingWindowMode.EXPANDED,
        links = listOf(link),
        selectedLinkSerial = link.serialNumber,
        preferredProductType = ProductType.SolarClean,
        isLoading = false,
        errorMessage = null,
        showOfflineSwitches = false
    )
}

private fun previewUiStateMultiSwitch(): FloatingWindowUiState {
    val switches = listOf(
        FloatingSwitchSummary("SW-001", "水桶1", true, 45, 8.0),
        FloatingSwitchSummary("SW-002", "水桶2", true, 30, 7.2),
        FloatingSwitchSummary("SW-003", "水桶3", false, 60, 6.5),
        FloatingSwitchSummary("SW-004", "水桶4", true, 90, 7.8),
        FloatingSwitchSummary("SW-005", "水桶5", true, 120, 6.2)
    )

    val link = FloatingLinkSummary(
        serialNumber = "LINK-001",
        name = "示例 Link",
        isOnline = true,
        productType = ProductType.FireBucket,
        onlineSwitches = switches,
        offlineSwitches = emptyList()
    )

    return FloatingWindowUiState(
        mode = FloatingWindowMode.EXPANDED,
        links = listOf(link),
        selectedLinkSerial = link.serialNumber,
        preferredProductType = ProductType.FireBucket,
        isLoading = false,
        errorMessage = null,
        showOfflineSwitches = false
    )
}
