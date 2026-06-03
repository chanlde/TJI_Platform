package com.tji.device.product.speaker.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SpeakerHorn: ImageVector
    get() {
        if (_speakerHorn != null) return _speakerHorn!!
        _speakerHorn = ImageVector.Builder(
            name = "SpeakerHorn",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f
            ) {
                moveTo(4f, 9f)
                curveTo(4f, 8.45f, 4.45f, 8f, 5f, 8f)
                horizontalLineTo(8.4f)
                lineTo(13.35f, 4.2f)
                curveTo(14.01f, 3.69f, 15f, 4.16f, 15f, 4.99f)
                verticalLineTo(19.01f)
                curveTo(15f, 19.84f, 14.01f, 20.31f, 13.35f, 19.8f)
                lineTo(8.4f, 16f)
                horizontalLineTo(5f)
                curveTo(4.45f, 16f, 4f, 15.55f, 4f, 15f)
                verticalLineTo(9f)
                close()
                moveTo(17.2f, 8.4f)
                curveTo(17.55f, 8f, 18.18f, 7.96f, 18.58f, 8.31f)
                curveTo(19.61f, 9.21f, 20.25f, 10.54f, 20.25f, 12f)
                curveTo(20.25f, 13.46f, 19.61f, 14.79f, 18.58f, 15.69f)
                curveTo(18.18f, 16.04f, 17.55f, 16f, 17.2f, 15.6f)
                curveTo(16.84f, 15.19f, 16.89f, 14.58f, 17.29f, 14.22f)
                curveTo(17.9f, 13.68f, 18.25f, 12.89f, 18.25f, 12f)
                curveTo(18.25f, 11.11f, 17.9f, 10.32f, 17.29f, 9.78f)
                curveTo(16.89f, 9.42f, 16.84f, 8.81f, 17.2f, 8.4f)
                close()
            }
        }.build()
        return _speakerHorn!!
    }

private var _speakerHorn: ImageVector? = null
