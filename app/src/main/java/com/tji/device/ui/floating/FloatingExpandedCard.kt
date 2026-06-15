package com.tji.device.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tji.device.ui.icon.common.minimize
import com.tji.device.di.AppContainer
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import com.tji.device.product.droppersixstage.ui.floating.DropperSixStageFloatingPanel
import com.tji.device.product.firebucket.ui.floating.EmptyProductPanel
import com.tji.device.product.firebucket.ui.floating.FireBucketFloatingPanel
import com.tji.device.product.solarclean.ui.floating.SolarCleanFloatingPanel
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedback
import com.tji.device.product.solarclean.viewmodel.SolarCleanCommandFeedbackStatus
import com.tji.device.product.solarclean.viewmodel.SolarCleanControlViewModel
import com.tji.device.ui.components.TjiFeedbackBadge
import com.tji.device.ui.components.TjiOnlineStatus
import com.tji.device.ui.theme.PayloadColors
import com.tji.device.ui.theme.PayloadDimens
import com.tji.device.ui.theme.TjiError
import com.tji.device.ui.theme.TjiOnline

@Composable
fun ExpandedCard(
    productType: ProductType,
    link: FloatingLinkSummary?,
    allSwitches: List<FloatingSwitchSummary>,
    currentSwitchIndex: Int,
    onSwitchSelected: (Int) -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onSwitchQuickToggle: (String, FloatingSwitchSummary, Boolean) -> Unit,
    onMove: (Float, Float) -> Unit
) {
    val switch = allSwitches.getOrNull(currentSwitchIndex)
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        FloatingWindowAppearance.load(context)
    }
    val configuredAlpha by FloatingWindowAppearance.backgroundAlpha.collectAsStateWithLifecycle()
    val solarAlpha = configuredAlpha.coerceIn(0f, 1f)
    val solarCleanViewModel: SolarCleanControlViewModel? = if (productType == ProductType.SolarClean && !isPreview) {
        viewModel(factory = AppContainer.solarCleanControlViewModelFactory)
    } else {
        null
    }
    val solarCleanFeedback by solarCleanViewModel?.commandFeedback?.collectAsStateWithLifecycle().let {
        it ?: remember { mutableStateOf(SolarCleanCommandFeedback()) }
    }

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 280.dp, minHeight = 0.dp)
            .wrapContentHeight()
            .dragGesture(onMove)
            .background(
                color = PayloadColors.Surface.copy(alpha = if (productType == ProductType.SolarClean) solarAlpha else 0.92f),
                shape = RoundedCornerShape(PayloadDimens.CardRadius)
            )
            .border(
                width = 1.dp,
                color = PayloadColors.Border.copy(alpha = if (productType == ProductType.SolarClean) solarAlpha else 0.92f),
                shape = RoundedCornerShape(PayloadDimens.CardRadius)
            )
    ) {
        Column(modifier = Modifier.padding(vertical = if (productType == ProductType.SolarClean) 3.dp else 5.dp)) {
            FloatingWindowHeader(
                productType = productType,
                link = link,
                commandFeedback = if (productType == ProductType.SolarClean) solarCleanFeedback else null,
                onMinimize = onMinimize,
                onClose = onClose
            )

            when (productType) {
                ProductType.FireBucket -> FireBucketFloatingPanel(
                    link = link,
                    switch = switch,
                    allSwitches = allSwitches,
                    currentSwitchIndex = currentSwitchIndex,
                    onSwitchSelected = onSwitchSelected,
                    onSwitchQuickToggle = onSwitchQuickToggle
                )
                ProductType.SolarClean -> SolarCleanFloatingPanel(link = link)
                ProductType.DropperSixStage -> DropperSixStageFloatingPanel(link = link)
                ProductType.RadioDetection -> EmptyProductPanel(message = "无线电检测暂不提供悬浮窗快捷控制")
                ProductType.Speaker -> EmptyProductPanel(message = "喊话器请在 App 内使用实时喊话")
                ProductType.BreakWindowProjectile -> EmptyProductPanel(message = "破窗弹暂不提供悬浮窗快捷控制")
                ProductType.Searchlight -> EmptyProductPanel(message = "探照灯暂不提供悬浮窗快捷控制")
            }
        }
    }
}

@Composable
private fun FloatingWindowHeader(
    productType: ProductType,
    link: FloatingLinkSummary?,
    commandFeedback: SolarCleanCommandFeedback?,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val title = link?.name
        ?: link?.serialNumber
        ?: ProductCatalog.definitionOf(productType).displayName

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (productType == ProductType.SolarClean) 10.dp else 12.dp,
                vertical = if (productType == ProductType.SolarClean) 6.dp else 8.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            ProductFloatingGlyph(
                productType = productType,
                compact = true
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (productType == ProductType.SolarClean) 13.sp else 14.sp
                ),
                color = PayloadColors.TextPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (productType == ProductType.SolarClean || productType == ProductType.DropperSixStage) {
                TjiOnlineStatus(isOnline = link?.isOnline == true, pill = true)
            }
            if (productType == ProductType.SolarClean) {
                FloatingCommandFeedbackBadge(commandFeedback)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(
                onClick = onMinimize,
                modifier = Modifier.size(if (productType == ProductType.SolarClean) 24.dp else 28.dp)
            ) {
                Icon(
                    imageVector = minimize,
                    contentDescription = "收起",
                    modifier = Modifier.size(if (productType == ProductType.SolarClean) 14.dp else 16.dp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(if (productType == ProductType.SolarClean) 24.dp else 28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.size(if (productType == ProductType.SolarClean) 16.dp else 18.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingCommandFeedbackBadge(feedback: SolarCleanCommandFeedback?) {
    if (feedback == null) return
    val color = when (feedback.status) {
        SolarCleanCommandFeedbackStatus.Success -> TjiOnline
        SolarCleanCommandFeedbackStatus.Failed,
        SolarCleanCommandFeedbackStatus.Timeout -> TjiError
        SolarCleanCommandFeedbackStatus.Pending,
        SolarCleanCommandFeedbackStatus.Idle -> PayloadColors.TextSecondary
    }
    TjiFeedbackBadge(text = feedback.text, color = color, horizontalPadding = 7.dp, verticalPadding = 4.dp)
}
