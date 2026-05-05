package com.tji.device.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tji.device.BuildConfig
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.Switch
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.runtime.ProductDeviceRuntimeSnapshot
import com.tji.device.ui.components.productEmptyIllustrationRes
import com.tji.device.ui.components.productSceneRes
import com.tji.device.ui.icon.product.productIconVector
import com.tji.device.ui.theme.LoginColors
import com.tji.device.util.userData

private val PlatformHomeBackground = Color(0xFFF5F7FA)
private val PlatformInk = Color(0xFF101828)
private val PlatformMuted = Color(0xFF667085)
private val PlatformBlue = Color(0xFF3478F6)

private enum class MetricKind {
    Device,
    Online,
    Type
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onBack: (() -> Unit)? = null,
    isFloatingWindowEnabled: Boolean = true,
    hasFloatingWindowPermission: Boolean = true,
    onFloatingWindowEnabledChange: (Boolean) -> Unit = {},
    onOpenFloatingWindowPermission: () -> Unit = {},
) {
    val mainViewModel = LocalMainViewModel.current
    val runtimeDevices by mainViewModel.runtimeDevices.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val account by mainViewModel.loginViewModel.account.collectAsStateWithLifecycle()
    var activeProductPage by remember { mutableStateOf<ProductType?>(null) }
    var selectedLinkSerial by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showDeviceSettings by remember { mutableStateOf(false) }
    userData.preferredProductTypeFlow.collectAsStateWithLifecycle()
    val boundAccountDevices = userData.boundAccountDevices.orEmpty()
    val selectedBoundDevice = remember(selectedLinkSerial, boundAccountDevices) {
        val sn = selectedLinkSerial ?: return@remember null
        boundAccountDevices.firstOrNull { it.serialNumber == sn }
    }
    val selectedRuntimeDevice = remember(runtimeDevices, selectedBoundDevice) {
        val info = selectedBoundDevice ?: return@remember null
        runtimeDevices.firstOrNull {
            it.serialNumber == info.serialNumber && it.productType == info.productType
        }
    }
    val selectedFireBucketLink = remember(selectedRuntimeDevice, selectedBoundDevice) {
        val info = selectedBoundDevice ?: return@remember null
        (selectedRuntimeDevice?.payload as? FireBucketLinkDevice)
            ?: if (info.productType == ProductType.FireBucket) {
                fireBucketLinkPlaceholderFromBoundAccount(info)
            } else {
                null
            }
    }

    Scaffold(
        containerColor = PlatformHomeBackground,
        topBar = {
            when {
                selectedBoundDevice != null -> DeviceDetailTopBar(
                    title = if (showDeviceSettings) "设备设置" else selectedBoundDevice.name,
                    isOnline = selectedRuntimeDevice?.isOnline == true,
                    showSettings = selectedBoundDevice.productType == ProductType.SolarClean && !showDeviceSettings,
                    onBack = {
                        if (showDeviceSettings) {
                            showDeviceSettings = false
                        } else {
                            selectedLinkSerial = null
                        }
                    },
                    onSettings = { showDeviceSettings = true }
                )
                activeProductPage != null -> ProductPageTopBar(
                    productType = activeProductPage,
                    onBack = { activeProductPage = null }
                )
            }
        }
    ) { padding ->
        MainScreenContent(
            modifier = Modifier.padding(padding),
            isLoading = isLoading,
            runtimeDevices = runtimeDevices,
            boundAccountDevices = boundAccountDevices,
            activeProductPage = activeProductPage,
            selectedBoundDevice = selectedBoundDevice,
            selectedFireBucketLink = selectedFireBucketLink,
            onProductSelected = {
                selectedLinkSerial = null
                showDeviceSettings = false
                activeProductPage = it
                mainViewModel.openProduct(it)
            },
            onLinkSelected = {
                selectedLinkSerial = it.serialNumber
                showDeviceSettings = false
                userData.preferredProductType = it.productType
                mainViewModel.openDevice(it)
            },
            onSettingsClick = { showSettings = true },
            showDeviceSettings = showDeviceSettings,
        )
    }

    if (showSettings) {
        PlatformSettingsSheet(
            account = account,
            deviceCount = boundAccountDevices.size,
            isFloatingWindowEnabled = isFloatingWindowEnabled,
            hasFloatingWindowPermission = hasFloatingWindowPermission,
            onFloatingWindowEnabledChange = onFloatingWindowEnabledChange,
            onOpenFloatingWindowPermission = onOpenFloatingWindowPermission,
            onDismiss = { showSettings = false }
        )
    }

    if (onBack != null) {
        BackHandler {
            if (showDeviceSettings) {
                showDeviceSettings = false
            } else if (selectedBoundDevice != null) {
                selectedLinkSerial = null
            } else if (activeProductPage != null) {
                activeProductPage = null
            } else {
                onBack()
            }
        }
    }
}

@Composable
private fun MainScreenContent(
    isLoading: Boolean,
    runtimeDevices: List<ProductDeviceRuntimeSnapshot>,
    boundAccountDevices: List<BoundAccountDevice>,
    activeProductPage: ProductType?,
    selectedBoundDevice: BoundAccountDevice?,
    selectedFireBucketLink: FireBucketLinkDevice?,
    onProductSelected: (ProductType) -> Unit,
    onLinkSelected: (BoundAccountDevice) -> Unit,
    onSettingsClick: () -> Unit,
    showDeviceSettings: Boolean = false,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> CircularProgressIndicator(
            modifier = modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
        )
        selectedBoundDevice != null -> ProductControlRoute(
            device = selectedBoundDevice,
            fireBucketLink = selectedFireBucketLink,
            showSettings = showDeviceSettings,
            modifier = modifier
        )
        activeProductPage != null -> {
            ProductDevicesScreen(
                productType = activeProductPage,
                runtimeDevices = runtimeDevices.filter { it.productType == activeProductPage },
                knownLinks = boundAccountDevices.filter { it.productType == activeProductPage },
                onLinkSelected = onLinkSelected,
                modifier = modifier
            )
        }
        else -> {
            ProductHome(
                onProductSelected = onProductSelected,
                onLinkSelected = onLinkSelected,
                boundAccountDevices = boundAccountDevices,
                runtimeDevices = runtimeDevices,
                onSettingsClick = onSettingsClick,
                modifier = modifier
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PlatformMetaCardPreview() {
    PlatformMetaCard(
        title = "我的设备",
        value = "12",
        unit = "台",
        kind = MetricKind.Device,
        modifier = Modifier.width(120.dp)
    )
}
@Composable
private fun ProductHome(
    onProductSelected: (ProductType) -> Unit,
    onLinkSelected: (BoundAccountDevice) -> Unit,
    boundAccountDevices: List<BoundAccountDevice>,
    runtimeDevices: List<ProductDeviceRuntimeSnapshot>,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onlineCount = boundAccountDevices.count { accountDevice ->
        runtimeDevices.any {
            it.serialNumber == accountDevice.serialNumber &&
                it.productType == accountDevice.productType &&
                it.isOnline
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PlatformHomeBackground),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            PlatformHomeHeader(
                totalDeviceCount = boundAccountDevices.size,
                onlineDeviceCount = onlineCount,
                productCount = ProductCatalog.allTypes.size,
                onSettingsClick = onSettingsClick
            )
        }
        item {
            Text(
                text = "产品概览",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PlatformInk,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val products = ProductCatalog.allTypes
                if (maxWidth >= 620.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        products.forEach { productType ->
                            ProductEntryCard(
                                productType = productType,
                                selected = false,
                                onClick = { onProductSelected(productType) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        products.forEach { productType ->
                            ProductEntryCard(
                                productType = productType,
                                selected = false,
                                onClick = { onProductSelected(productType) }
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已绑定设备",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PlatformInk
                )
                Text(
                    text = "按设备进入控制台",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlatformMuted
                )
            }
        }
        when {
            boundAccountDevices.isNotEmpty() -> {
                items(boundAccountDevices, key = { "${it.productType.name}:${it.serialNumber}" }) { device ->
                    val live = runtimeDevices.firstOrNull {
                        it.serialNumber == device.serialNumber && it.productType == device.productType
                    }
                    PlatformDeviceCard(
                        device = device,
                        live = live,
                        onClick = { onLinkSelected(device) }
                    )
                }
            }
            else -> {
                item {
                    EmptyPlatformHomeCard()
                }
            }
        }
    }
}

@Composable
/**
 * 某产品线下先选设备再进控制台：**可选设备清单以登录接口返回的绑定设备列表（boundDeviceRows / 旧 bucketsns）（[knownLinks]）为准**，
 * 这是账号名下设备，与 MQTT 无关；MQTT 仅在有数据时补充在线/子设备等实时态。
 */
private fun ProductDevicesScreen(
    productType: ProductType,
    runtimeDevices: List<ProductDeviceRuntimeSnapshot>,
    knownLinks: List<BoundAccountDevice>,
    onLinkSelected: (BoundAccountDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val scopedLive = runtimeDevices.filter { it.productType == productType }
    val accountDevices = knownLinks.filter { it.productType == productType }
    val showPicker = accountDevices.isNotEmpty() || scopedLive.isNotEmpty()
    val totalChoices = if (accountDevices.isNotEmpty()) {
        accountDevices.size
    } else {
        scopedLive.size
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showPicker && totalChoices >= 1) {
            item {
                Text(
                    text = if (totalChoices > 1) {
                        "请选择要操作的设备（共 $totalChoices 台）"
                    } else {
                        "选择设备"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LoginColors.OnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        when {
            accountDevices.isNotEmpty() -> {
                items(accountDevices, key = { it.serialNumber }) { info ->
                    val live = scopedLive.firstOrNull { it.serialNumber == info.serialNumber }
                    PlatformDeviceCard(
                        device = info,
                        live = live,
                        onClick = { onLinkSelected(info) }
                    )
                }
            }

            scopedLive.isNotEmpty() -> {
                items(scopedLive, key = { it.serialNumber }) { link ->
                    val info = BoundAccountDevice(
                        serialNumber = link.serialNumber,
                        name = link.name,
                        productType = link.productType
                    )
                    PlatformDeviceCard(
                        device = info,
                        live = link,
                        onClick = { onLinkSelected(info) }
                    )
                }
            }

            else -> {
                item {
                    CompactEmptyCard(
                        productType = productType,
                        knownDeviceCount = 0
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductEntryCard(
    productType: ProductType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val definition = ProductCatalog.definitionOf(productType)
    val shape = RoundedCornerShape(24.dp)
    val cardHeight = 228.dp
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isCompactCard = maxWidth < 420.dp
        val imageScale = if (maxWidth > 480.dp) {
            ContentScale.Fit
        } else {
            ContentScale.Crop
        }
        val maskStops = if (isCompactCard) {
            arrayOf(
                0.00f to Color.White,
                0.42f to Color.White.copy(alpha = 0.96f),
                0.58f to Color.White.copy(alpha = 0.64f),
                0.72f to Color.White.copy(alpha = 0.18f),
                1.00f to Color.Transparent
            )
        } else {
            arrayOf(
                0.00f to Color.White,
                0.50f to Color.White.copy(alpha = 0.98f),
                0.68f to Color.White.copy(alpha = 0.82f),
                0.84f to Color.White.copy(alpha = 0.30f),
                1.00f to Color.Transparent
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clickable(onClick = onClick),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 14.dp else 8.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (selected) Color(0xFFB8D0FF) else Color(0xFFE8EDF5)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = when (productType) {
                                ProductType.FireBucket -> listOf(Color.White, Color(0xFFF8FBFF))
                                ProductType.SolarClean -> listOf(Color.White, Color(0xFFF8FCFF))
                            }
                        ),
                        shape = shape
                    )
            ) {
                Image(
                    painter = painterResource(id = productSceneRes(productType)),
                    contentDescription = null,
                    contentScale = imageScale,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colorStops = maskStops
                            ),
                            shape = shape
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(if (isCompactCard) 0.54f else 0.56f)
                        .padding(start = 20.dp, top = 22.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ProductGlyph(productType = productType)
                    Text(
                        text = definition.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = PlatformInk,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = ProductCatalog.definitionOf(productType).platformSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PlatformMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = ProductCatalog.definitionOf(productType).platformValueLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7A8799),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        CircularArrowButton(selected = selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactEmptyCard(
    productType: ProductType,
    knownDeviceCount: Int
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = productEmptyIllustrationRes(productType)),
                contentDescription = null,
                modifier = Modifier.size(88.dp)
            )
            Text(
                text = if (knownDeviceCount > 0) "正在连接设备" else "暂无设备",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = LoginColors.OnSurface
            )
            Text(
                text = if (knownDeviceCount > 0) "已进入 ${productTitle(productType)}，正在等待设备上线" else productTitle(productType),
                style = MaterialTheme.typography.bodyMedium,
                color = LoginColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlatformDeviceCard(
    device: BoundAccountDevice,
    live: ProductDeviceRuntimeSnapshot?,
    onClick: () -> Unit
) {
    val accentColors = when (device.productType) {
        ProductType.FireBucket -> listOf(Color.White, Color(0xFFF7FAFF))
        ProductType.SolarClean -> listOf(Color.White, Color(0xFFFFFBF4))
    }
    val subDeviceCount = live?.childCount
    val isOnline = live?.isOnline == true
    val displayName = live?.name ?: device.name
    val subtitle = subDeviceCount?.let { "$it 台子设备" }
        ?: device.serialNumber.takeUnless { it.equals(displayName, ignoreCase = true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(accentColors),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = productIconVector(device.productType),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = productAccentColor(device.productType)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    InlineDeviceStatus(isOnline = isOnline)
                }
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LoginColors.OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = LoginColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun InlineDeviceStatus(isOnline: Boolean) {
    val color = if (isOnline) Color(0xFF00B578) else Color(0xFFFF4D4F)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = if (isOnline) "在线" else "离线",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun productTitle(productType: ProductType): String {
    return ProductCatalog.definitionOf(productType).displayName
}

@Composable
private fun ProductGlyph(productType: ProductType) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(Color(0xFFEFF5FF), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = productIconVector(productType),
            contentDescription = ProductCatalog.definitionOf(productType).displayName,
            modifier = Modifier.size(34.dp),
            tint = productAccentColor(productType)
        )
    }
}

private fun productAccentColor(productType: ProductType): Color {
    return when (productType) {
        ProductType.FireBucket -> PlatformBlue
        ProductType.SolarClean -> Color(0xFFE6A11B)
    }
}

@Composable
private fun PlatformHomeHeader(
    totalDeviceCount: Int,
    onlineDeviceCount: Int,
    productCount: Int,
    onSettingsClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "设备平台首页",
                    style = MaterialTheme.typography.displaySmall,
                    color = PlatformInk,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = when {
                        totalDeviceCount > 0 -> "统一查看账号下各产品设备，并按设备进入对应控制台。"
                        else -> "暂无绑定设备，请先添加或联系管理员开通。"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = PlatformMuted,
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    tint = PlatformInk
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlatformMetaCard(
                title = "绑定设备",
                value = "$totalDeviceCount",
                unit = "台",
                kind = MetricKind.Device,
                modifier = Modifier.weight(1f)
            )
            PlatformMetaCard(
                title = "在线设备",
                value = "$onlineDeviceCount",
                unit = "台",
                kind = MetricKind.Online,
                modifier = Modifier.weight(1f)
            )
            PlatformMetaCard(
                title = "设备类型",
                value = "$productCount",
                unit = "类",
                kind = MetricKind.Type,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EmptyPlatformHomeCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "暂无已绑定设备",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PlatformInk
            )
            Text(
                text = "登录流程保持不变；后续接入更多产品后，这里会统一展示所有账号设备。",
                style = MaterialTheme.typography.bodyMedium,
                color = PlatformMuted
            )
        }
    }
}

@Composable
private fun CircularArrowButton(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = if (selected) Color(0xFF155EEF) else PlatformBlue
        )
    }
}

@Composable
private fun MetricGlyph(kind: MetricKind) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(Color(0xFFEAF1FF), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            MetricKind.Device -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width((22 - it * 3).dp)
                                .height(5.dp)
                                .background(PlatformBlue, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            MetricKind.Online -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(PlatformBlue.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(PlatformBlue, RoundedCornerShape(3.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(PlatformBlue.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(16.dp)
                            .background(PlatformBlue, RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(4.dp)
                            .background(PlatformBlue, RoundedCornerShape(4.dp))
                    )
                }
            }

            MetricKind.Type -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.width(22.dp)
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(PlatformBlue, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformMetaCard(
    title: String,
    value: String,
    unit: String,
    kind: MetricKind,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth() // 加这个
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color.White, Color(0xFFF8FAFF))
                )
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MetricGlyph(kind = kind)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF475467),
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    color = PlatformBlue,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF667085),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlatformSettingsSheet(
    account: String,
    deviceCount: Int,
    isFloatingWindowEnabled: Boolean,
    hasFloatingWindowPermission: Boolean,
    onFloatingWindowEnabledChange: (Boolean) -> Unit,
    onOpenFloatingWindowPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PlatformInk,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "管理悬浮窗、权限和当前应用信息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PlatformMuted
                )
            }

            SettingsGroup {
                SettingSwitchRow(
                    title = "悬浮窗",
                    description = if (hasFloatingWindowPermission) {
                        "开启后可在其他页面快速查看当前产品控制面板"
                    } else {
                        "需要先授予悬浮窗权限"
                    },
                    checked = isFloatingWindowEnabled,
                    onCheckedChange = { enabled ->
                        onFloatingWindowEnabledChange(enabled)
                        if (enabled && !hasFloatingWindowPermission) {
                            onOpenFloatingWindowPermission()
                        }
                    }
                )
                HorizontalDivider(color = Color(0xFFE8EDF5))
                SettingActionRow(
                    title = "悬浮窗权限",
                    value = if (hasFloatingWindowPermission) "已授权" else "未授权",
                    actionText = if (hasFloatingWindowPermission) null else "去授权",
                    onAction = onOpenFloatingWindowPermission
                )
            }

            SettingsGroup {
                SettingInfoRow(
                    title = "当前账号",
                    value = account.ifBlank { "未获取" }
                )
                HorizontalDivider(color = Color(0xFFE8EDF5))
                SettingInfoRow(
                    title = "绑定设备",
                    value = "$deviceCount 台"
                )
            }

            SettingsGroup {
                SettingInfoRow(
                    title = "当前版本",
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF8FAFF))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PlatformInk,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = PlatformMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    value: String,
    actionText: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingTextPair(
            title = title,
            value = value,
            modifier = Modifier.weight(1f)
        )
        if (actionText != null) {
            TextButton(onClick = onAction) {
                Text(text = actionText)
            }
        }
    }
}

@Composable
private fun SettingInfoRow(
    title: String,
    value: String
) {
    SettingTextPair(
        title = title,
        value = value,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    )
}

@Composable
private fun SettingTextPair(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = PlatformMuted,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = PlatformInk,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDetailTopBar(
    title: String,
    isOnline: Boolean? = null,
    showSettings: Boolean = false,
    onBack: () -> Unit,
    onSettings: () -> Unit = {}
) {
    TopAppBar(
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
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
    val color = if (isOnline) Color(0xFF00B578) else Color(0xFFFF4D4F)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Text(
            text = if (isOnline) "在线" else "离线",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductPageTopBar(
    productType: ProductType?,
    onBack: () -> Unit
) {
    TopAppBar(
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = LoginColors.Surface,
            titleContentColor = LoginColors.OnSurface,
            navigationIconContentColor = LoginColors.OnSurfaceVariant
        ),
        title = {
            Text(
                text = productTitle(productType ?: ProductType.FireBucket),
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

@Preview(
    name = "平台首页",
    showBackground = true,
    widthDp = 393,
    heightDp = 852
)
@Composable
fun PreviewMainScreen() {
    MainScreenContent(
        isLoading = false,
        runtimeDevices = listOf(previewRuntimeDevice()),
        boundAccountDevices = listOf(
            BoundAccountDevice("LINK-001", "示例 Link", ProductType.FireBucket),
            BoundAccountDevice("SC-001", "光伏清洗 01", ProductType.SolarClean)
        ),
        activeProductPage = null,
        selectedBoundDevice = null,
        selectedFireBucketLink = null,
        onProductSelected = {},
        onLinkSelected = {},
        onSettingsClick = {},
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(
    name = "平台首页-空状态",
    showBackground = true,
    widthDp = 393,
    heightDp = 852
)
@Composable
fun PreviewMainScreenEmpty() {
    MainScreenContent(
        isLoading = false,
        runtimeDevices = emptyList(),
        boundAccountDevices = emptyList(),
        activeProductPage = null,
        selectedBoundDevice = null,
        selectedFireBucketLink = null,
        onProductSelected = {},
        onLinkSelected = {},
        onSettingsClick = {},
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(
    name = "消防吊桶设备列表",
    showBackground = true,
    widthDp = 393,
    heightDp = 852
)

@Composable
fun PreviewFireBucketProductPage() {
    MainScreenContent(
        isLoading = false,
        runtimeDevices = listOf(previewRuntimeDevice()),
        boundAccountDevices = listOf(
            BoundAccountDevice("LINK-001", "示例 Link", ProductType.FireBucket)
        ),
        activeProductPage = ProductType.FireBucket,
        selectedBoundDevice = null,
        selectedFireBucketLink = null,
        onProductSelected = {},
        onLinkSelected = {},
        onSettingsClick = {},
        modifier = Modifier.fillMaxSize()
    )
}

/** 账号侧以登录下发的绑定设备列表为准，MQTT 上线后再用实时包替换展示。 */
internal fun fireBucketLinkPlaceholderFromBoundAccount(info: BoundAccountDevice): FireBucketLinkDevice {
    return FireBucketLinkDevice(
        event_type = "LocalPending",
        serial_number = info.serialNumber,
        deviceName = info.name.ifBlank { info.serialNumber },
        deviceType = "Link",
        manufacturer = "",
        deviceModel = "",
        isOnline = false,
        hwVersion = "",
        swVersion = "",
        uptime = 0,
        deviceConfig = "",
        subDevices = emptyList(),
        timestamp = "",
        productType = info.productType,
    )
}

private fun previewFireBucketLinkDevice(): FireBucketLinkDevice {
    val mockSwitch = Switch(
        serialNumber = "SW-001",
        deviceName = "示例水桶",
        deviceType = "Bucket",
        isOnline = true,
        currentAngle = 45.0,
        currentCurrent = 120.0,
        inputVoltage = 12.0,
        servoMinAngle = 0.0,
        servoMaxAngle = 90.0,
        uptime = 3600
    )
    return FireBucketLinkDevice(
        event_type = "Preview",
        serial_number = "LINK-001",
        deviceName = "示例 Link",
        deviceType = "HydroLink",
        manufacturer = "TJI",
        deviceModel = "V3",
        isOnline = true,
        hwVersion = "1.0",
        swVersion = "1.0.0",
        uptime = 12345,
        deviceConfig = "{}",
        subDevices = listOf(mockSwitch),
        timestamp = "2025-01-01T00:00:00Z"
    )
}

private fun previewRuntimeDevice(): ProductDeviceRuntimeSnapshot {
    val link = previewFireBucketLinkDevice()
    return ProductDeviceRuntimeSnapshot(
        serialNumber = link.serial_number,
        name = link.deviceName,
        productType = link.productType,
        isOnline = link.isOnline,
        childCount = link.subDevices.size,
        payload = link
    )
}
