package com.tji.device.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.ui.icon.product.productIconVector

@Composable
fun CollapsedCard(
    productType: ProductType,
    link: FloatingLinkSummary?,
    switch: FloatingSwitchSummary?,
    onExpand: () -> Unit,
    onMove: (Float, Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xB31E88E5)),
        modifier = Modifier
            .defaultMinSize(minWidth = 140.dp, minHeight = 80.dp)
            .clip(RoundedCornerShape(15.dp))
            .dragGesture(onMove)
            .clickable { onExpand() }
            .padding(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProductFloatingGlyph(productType = productType)
        }
    }
}

@Composable
fun ProductFloatingGlyph(
    productType: ProductType,
    compact: Boolean = false
) {
    val backgroundColor = when (productType) {
        ProductType.FireBucket -> Color(0xFF6DAEEA)
        ProductType.SolarClean -> Color(0xFFF2C56C)
    }
    val size = if (compact) 24.dp else 44.dp

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(size)
            .background(backgroundColor, RoundedCornerShape(if (compact) 8.dp else 14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = productIconVector(productType),
            contentDescription = ProductCatalog.definitionOf(productType).displayName,
            modifier = Modifier.size(if (compact) 16.dp else 28.dp),
            tint = Color.White
        )
    }
}

fun Modifier.dragGesture(onDrag: (Float, Float) -> Unit): Modifier {
    return pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            onDrag(dragAmount.x.roundToInt().toFloat(), dragAmount.y.roundToInt().toFloat())
        }
    }
}
