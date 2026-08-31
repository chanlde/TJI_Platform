package com.tji.device.product.glassbreaker.ui.floating

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.ui.floating.EmptyProductPanel
import com.tji.device.product.glassbreaker.ui.control.GlassBreakerControlContent
import com.tji.device.ui.floating.FloatingLinkSummary

/** 直接复用破窗器主控制页内容，避免悬浮窗形成第二套操作语义。 */
@Composable
fun GlassBreakerFloatingPanel(link: FloatingLinkSummary?) {
    if (link == null) {
        EmptyProductPanel(message = "暂无破窗器设备")
        return
    }

    GlassBreakerControlContent(
        device = BoundAccountDevice(
            serialNumber = link.serialNumber,
            name = link.name,
            productType = ProductType.BreakWindowProjectile
        ),
        modifier = Modifier.height(420.dp),
        showDeviceHeader = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    )
}
