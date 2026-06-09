package com.tji.device.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.FireBucketLinkDevice
import com.tji.device.product.firebucket.ui.control.FireBucketControlScreen
import com.tji.device.product.droppersixstage.ui.control.DropperSixStageControlScreen
import com.tji.device.product.radiodetection.ui.control.RadioDetectionControlScreen
import com.tji.device.product.speaker.ui.control.SpeakerControlScreen
import com.tji.device.product.solarclean.ui.control.SolarCleanControlScreen
import com.tji.device.ui.components.TjiSectionCard
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens

@Composable
fun ProductControlRoute(
    device: BoundAccountDevice,
    fireBucketLink: FireBucketLinkDevice?,
    showSettings: Boolean = false,
    onRenameDevice: (BoundAccountDevice, String) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (showSettings && device.productType != ProductType.SolarClean) {
        CommonDeviceSettingsScreen(
            device = device,
            onRenameDevice = { newName -> onRenameDevice(device, newName) },
            modifier = modifier
        )
        return
    }

    when (device.productType) {
        ProductType.FireBucket -> FireBucketControlScreen(
            link = fireBucketLink ?: fireBucketLinkPlaceholderFromBoundAccount(device),
            modifier = modifier
        )

        ProductType.SolarClean -> SolarCleanControlScreen(
            device = device,
            showSettings = showSettings,
            onRenameDevice = onRenameDevice,
            modifier = modifier
        )

        ProductType.DropperSixStage -> DropperSixStageControlScreen(
            device = device,
            modifier = modifier
        )

        ProductType.RadioDetection -> RadioDetectionControlScreen(
            device = device,
            onBack = onBack,
            modifier = modifier
        )

        ProductType.Speaker -> SpeakerControlScreen(
            device = device,
            onBack = onBack,
            modifier = modifier
        )
    }
}

@Composable
private fun CommonDeviceSettingsScreen(
    device: BoundAccountDevice,
    onRenameDevice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PayloadColors.Background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            },
        contentPadding = PaddingValues(PayloadDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(PayloadDimens.SectionGap)
    ) {
        item {
            TjiSectionCard(title = "设备信息") {
                CommonDeviceNameLine(
                    value = device.name,
                    onRenameDevice = onRenameDevice
                )
                CommonInfoLine(label = "设备 SN", value = device.serialNumber)
                CommonInfoLine(
                    label = "产品类型",
                    value = ProductCatalog.definitionOf(device.productType).displayName
                )
            }
        }
    }
}

@Composable
private fun CommonDeviceNameLine(
    value: String,
    onRenameDevice: (String) -> Unit
) {
    var editing by remember(value) { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    var hasFocused by remember(value) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun commitRename() {
        val nextName = draft.trim()
        editing = false
        hasFocused = false
        if (nextName.isBlank()) {
            draft = value
            return
        }
        if (nextName != value) {
            onRenameDevice(nextName)
        }
    }

    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "设备名称",
            fontSize = 12.sp,
            color = PayloadColors.TextMuted,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier.weight(1.5f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editing) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PayloadColors.TextPrimary
                        ),
                        cursorBrush = SolidColor(PayloadColors.Primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitRename() }),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    hasFocused = true
                                } else if (hasFocused && editing) {
                                    commitRename()
                                }
                            }
                            .fillMaxWidth()
                    )
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(PayloadColors.Primary)
                    )
                }
            } else {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PayloadColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable {
                            draft = value
                            editing = true
                        }
                )
                Text(
                    text = "修改",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PayloadColors.Primary,
                    modifier = Modifier.clickable {
                        draft = value
                        editing = true
                    }
                )
            }
        }
    }
}

@Composable
private fun CommonInfoLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = PayloadColors.TextMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PayloadColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f)
        )
    }
}
