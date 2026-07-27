package com.tji.device.ui.floating

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun FloatingWindowContent(
    uiState: FloatingWindowUiState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onSwitchQuickToggle: (String, FloatingSwitchSummary, Boolean) -> Unit,
    onMove: (Float, Float) -> Unit
) {
    val activeLink = uiState.selectedLink
    val allSwitches = activeLink?.allSwitches.orEmpty()
    var selectedSwitchIndex by remember(activeLink?.productType, activeLink?.serialNumber) {
        mutableIntStateOf(0)
    }
    val visibleSwitchIndex =
        selectedSwitchIndex.takeIf { it in allSwitches.indices } ?: 0

    LaunchedEffect(
        activeLink?.productType,
        activeLink?.serialNumber,
        allSwitches.size,
        visibleSwitchIndex
    ) {
        if (selectedSwitchIndex != visibleSwitchIndex) {
            selectedSwitchIndex = visibleSwitchIndex
        }
    }

    val activeSwitch = allSwitches.getOrNull(visibleSwitchIndex)

    if (isExpanded) {
        ExpandedCard(
            productType = uiState.activeProductType,
            link = activeLink,
            allSwitches = allSwitches,
            currentSwitchIndex = visibleSwitchIndex,
            onSwitchSelected = { index -> selectedSwitchIndex = index },
            onClose = onClose,
            onMinimize = onMinimize,
            onSwitchQuickToggle = onSwitchQuickToggle,
            onMove = onMove
        )
    } else {
        CollapsedCard(
            productType = uiState.activeProductType,
            link = activeLink,
            switch = activeSwitch,
            onExpand = onToggleExpand,
            onMove = onMove
        )
    }
}
