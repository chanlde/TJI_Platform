package com.tji.device.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun StatusChip(isOnline: Boolean) {
    TjiOnlineStatus(isOnline = isOnline)
}


@Preview(showBackground = true)
@Composable
fun onlineExample() {
    StatusChip(isOnline = false)
}

@Preview(showBackground = true)
@Composable
fun offlineExample() {
    StatusChip(isOnline = true)
}
