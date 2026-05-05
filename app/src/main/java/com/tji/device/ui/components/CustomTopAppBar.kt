package com.tji.device.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.LoginColors

@ExperimentalMaterial3Api
@Composable
fun CustomTopAppBar(
    title: String,
    onSettingsClick: () -> Unit = { /* 默认空操作 */ }
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            // 设置一个透明的图标，填充左侧空间
            Spacer(modifier = Modifier.width(24.dp))  // 可以根据需要调整宽度
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = LoginColors.Surface,
            titleContentColor = LoginColors.OnSurface,
            actionIconContentColor = LoginColors.OnSurfaceVariant
        )
    )
}

@ExperimentalMaterial3Api
@Preview
@Composable
fun PreviewCustomTopAppBar() {
    MaterialTheme {
        CustomTopAppBar(title = "dddd", onSettingsClick = {})
    }
}
