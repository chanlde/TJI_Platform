package com.tji.device.product.droppersixstage.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SixStageDropper: ImageVector
    get() {
        if (_sixStageDropper != null) return _sixStageDropper!!
        _sixStageDropper = ImageVector.Builder(
            name = "SixStageDropper",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(4f, 5f)
                curveTo(4f, 3.9f, 4.9f, 3f, 6f, 3f)
                horizontalLineTo(18f)
                curveTo(19.1f, 3f, 20f, 3.9f, 20f, 5f)
                verticalLineTo(7f)
                horizontalLineTo(4f)
                verticalLineTo(5f)
                close()
                moveTo(5f, 9f)
                horizontalLineTo(19f)
                verticalLineTo(11f)
                horizontalLineTo(5f)
                verticalLineTo(9f)
                close()
                moveTo(6f, 13f)
                horizontalLineTo(8f)
                verticalLineTo(20f)
                horizontalLineTo(6f)
                verticalLineTo(13f)
                close()
                moveTo(10f, 13f)
                horizontalLineTo(12f)
                verticalLineTo(20f)
                horizontalLineTo(10f)
                verticalLineTo(13f)
                close()
                moveTo(14f, 13f)
                horizontalLineTo(16f)
                verticalLineTo(20f)
                horizontalLineTo(14f)
                verticalLineTo(13f)
                close()
                moveTo(18f, 13f)
                curveTo(18.6f, 13f, 19f, 13.4f, 19f, 14f)
                verticalLineTo(19f)
                curveTo(19f, 19.6f, 18.6f, 20f, 18f, 20f)
                curveTo(17.4f, 20f, 17f, 19.6f, 17f, 19f)
                verticalLineTo(14f)
                curveTo(17f, 13.4f, 17.4f, 13f, 18f, 13f)
                close()
            }
        }.build()
        return _sixStageDropper!!
    }

private var _sixStageDropper: ImageVector? = null
