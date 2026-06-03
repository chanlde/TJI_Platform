package com.tji.device.ui.floating

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit
) {
    val activeLink = uiState.selectedLink
    val allSwitches = activeLink?.allSwitches.orEmpty()
    var selectedSwitchIndex by remember { mutableStateOf(0) }

    if (selectedSwitchIndex >= allSwitches.size && allSwitches.isNotEmpty()) {
        selectedSwitchIndex = 0
    }

    val activeSwitch = allSwitches.getOrNull(selectedSwitchIndex)

    if (isExpanded) {
        ExpandedCard(
            productType = uiState.activeProductType,
            link = activeLink,
            allSwitches = allSwitches,
            currentSwitchIndex = selectedSwitchIndex,
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
