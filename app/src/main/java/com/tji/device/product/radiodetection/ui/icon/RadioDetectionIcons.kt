package com.tji.device.product.radiodetection.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val RadioDetectionRadar: ImageVector
    get() {
        if (_radioDetectionRadar != null) return _radioDetectionRadar!!
        _radioDetectionRadar = ImageVector.Builder(
            name = "RadioDetectionRadar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 2f)
                curveTo(17.52f, 2f, 22f, 6.48f, 22f, 12f)
                curveTo(22f, 17.52f, 17.52f, 22f, 12f, 22f)
                curveTo(6.48f, 22f, 2f, 17.52f, 2f, 12f)
                curveTo(2f, 6.48f, 6.48f, 2f, 12f, 2f)
                close()
                moveTo(12f, 4.2f)
                curveTo(7.69f, 4.2f, 4.2f, 7.69f, 4.2f, 12f)
                curveTo(4.2f, 16.31f, 7.69f, 19.8f, 12f, 19.8f)
                curveTo(16.31f, 19.8f, 19.8f, 16.31f, 19.8f, 12f)
                curveTo(19.8f, 7.69f, 16.31f, 4.2f, 12f, 4.2f)
                close()
                moveTo(12f, 7.1f)
                curveTo(14.71f, 7.1f, 16.9f, 9.29f, 16.9f, 12f)
                curveTo(16.9f, 14.71f, 14.71f, 16.9f, 12f, 16.9f)
                curveTo(9.29f, 16.9f, 7.1f, 14.71f, 7.1f, 12f)
                curveTo(7.1f, 9.29f, 9.29f, 7.1f, 12f, 7.1f)
                close()
                moveTo(12f, 9.1f)
                curveTo(10.4f, 9.1f, 9.1f, 10.4f, 9.1f, 12f)
                curveTo(9.1f, 13.6f, 10.4f, 14.9f, 12f, 14.9f)
                curveTo(13.6f, 14.9f, 14.9f, 13.6f, 14.9f, 12f)
                curveTo(14.9f, 10.4f, 13.6f, 9.1f, 12f, 9.1f)
                close()
                moveTo(12f, 10.7f)
                curveTo(12.72f, 10.7f, 13.3f, 11.28f, 13.3f, 12f)
                curveTo(13.3f, 12.72f, 12.72f, 13.3f, 12f, 13.3f)
                curveTo(11.28f, 13.3f, 10.7f, 12.72f, 10.7f, 12f)
                curveTo(10.7f, 11.28f, 11.28f, 10.7f, 12f, 10.7f)
                close()
                moveTo(12.7f, 5.5f)
                lineTo(18.4f, 11.2f)
                lineTo(16.85f, 12.75f)
                lineTo(11.15f, 7.05f)
                lineTo(12.7f, 5.5f)
                close()
            }
        }.build()
        return _radioDetectionRadar!!
    }

private var _radioDetectionRadar: ImageVector? = null
