package com.tji.device.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.ui.components.TjiOnlineStatus
import com.tji.device.ui.theme.LoginColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailTopBar(
    title: String,
    isOnline: Boolean? = null,
    showSettings: Boolean = false,
    onBack: () -> Unit,
    onSettings: () -> Unit = {}
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = LoginColors.Surface,
            titleContentColor = LoginColors.OnSurface,
            navigationIconContentColor = LoginColors.OnSurfaceVariant
        ),
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                isOnline?.let { TopBarOnlineStatus(isOnline = it) }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回"
                )
            }
        },
        actions = {
            if (showSettings) {
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "设备设置"
                    )
                }
            }
        }
    )
}

@Composable
private fun TopBarOnlineStatus(isOnline: Boolean) {
    TjiOnlineStatus(isOnline = isOnline, dotSize = 7.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPageTopBar(
    productType: ProductType?,
    onBack: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = LoginColors.Surface,
            titleContentColor = LoginColors.OnSurface,
            navigationIconContentColor = LoginColors.OnSurfaceVariant
        ),
        title = {
            Text(
                text = ProductCatalog.definitionOf(productType ?: ProductType.FireBucket).displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回"
                )
            }
        }
    )
}
