package com.tji.device.product.solarclean.ui.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

val SolarPanelClean: ImageVector
    get() {
        if (_SolarPanelClean != null) return _SolarPanelClean!!

        _SolarPanelClean = ImageVector.Builder(
            name = "SolarPanelClean",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.2f, 10f)
                lineTo(18.8f, 10f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.35f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8.3f, 10.4f)
                lineTo(7.1f, 18.6f)
                moveTo(12f, 10.4f)
                lineTo(12f, 18.6f)
                moveTo(15.7f, 10.4f)
                lineTo(16.9f, 18.6f)
                moveTo(4.3f, 13.2f)
                lineTo(19.7f, 13.2f)
                moveTo(3.6f, 16.1f)
                lineTo(20.4f, 16.1f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(7.5f, 3.2f)
                curveTo(6.65f, 4.25f, 6.05f, 5.1f, 6.05f, 5.8f)
                curveTo(6.05f, 6.6f, 6.7f, 7.25f, 7.5f, 7.25f)
                curveTo(8.3f, 7.25f, 8.95f, 6.6f, 8.95f, 5.8f)
                curveTo(8.95f, 5.1f, 8.35f, 4.25f, 7.5f, 3.2f)
                close()
                moveTo(12f, 2.2f)
                curveTo(11.05f, 3.35f, 10.35f, 4.35f, 10.35f, 5.15f)
                curveTo(10.35f, 6.06f, 11.09f, 6.8f, 12f, 6.8f)
                curveTo(12.91f, 6.8f, 13.65f, 6.06f, 13.65f, 5.15f)
                curveTo(13.65f, 4.35f, 12.95f, 3.35f, 12f, 2.2f)
                close()
                moveTo(16.5f, 3.2f)
                curveTo(15.65f, 4.25f, 15.05f, 5.1f, 15.05f, 5.8f)
                curveTo(15.05f, 6.6f, 15.7f, 7.25f, 16.5f, 7.25f)
                curveTo(17.3f, 7.25f, 17.95f, 6.6f, 17.95f, 5.8f)
                curveTo(17.95f, 5.1f, 17.35f, 4.25f, 16.5f, 3.2f)
                close()
            }
        }.build()

        return _SolarPanelClean!!
    }

private var _SolarPanelClean: ImageVector? = null

@Preview(showBackground = true, name = "光伏清洗图标")
@Composable
private fun SolarPanelCleanPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ProductIconPreviewBox(
                icon = SolarPanelClean,
                tint = Color(0xFFE6A11B)
            )
        }
    }
}

@Composable
private fun ProductIconPreviewBox(
    icon: ImageVector,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(Color(0xFFEFF5FF), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = tint
        )
    }
}
