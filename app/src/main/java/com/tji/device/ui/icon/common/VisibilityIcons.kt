package com.tji.device.ui.icon.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Eye: ImageVector
    get() {
        if (_Eye != null) return _Eye!!

        _Eye = ImageVector.Builder(
            name = "Eye",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2.5f, 12f)
                curveTo(5.4f, 7.2f, 8.6f, 5f, 12f, 5f)
                curveTo(15.4f, 5f, 18.6f, 7.2f, 21.5f, 12f)
                curveTo(18.6f, 16.8f, 15.4f, 19f, 12f, 19f)
                curveTo(8.6f, 19f, 5.4f, 16.8f, 2.5f, 12f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 9f)
                curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f)
                curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
                curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
                curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f)
                close()
            }
        }.build()

        return _Eye!!
    }

private var _Eye: ImageVector? = null

val EyeOff: ImageVector
    get() {
        if (_EyeOff != null) return _EyeOff!!

        _EyeOff = ImageVector.Builder(
            name = "EyeOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 3f)
                lineTo(21f, 21f)
                moveTo(9.9f, 5.5f)
                curveTo(10.6f, 5.2f, 11.3f, 5f, 12f, 5f)
                curveTo(15.4f, 5f, 18.6f, 7.2f, 21.5f, 12f)
                curveTo(20.5f, 13.7f, 19.5f, 15f, 18.4f, 16f)
                moveTo(14.1f, 18.5f)
                curveTo(13.4f, 18.8f, 12.7f, 19f, 12f, 19f)
                curveTo(8.6f, 19f, 5.4f, 16.8f, 2.5f, 12f)
                curveTo(3.8f, 9.9f, 5.1f, 8.3f, 6.5f, 7.2f)
                moveTo(10.6f, 10.6f)
                curveTo(10.2f, 11f, 10f, 11.5f, 10f, 12f)
                curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
                curveTo(12.5f, 14f, 13f, 13.8f, 13.4f, 13.4f)
            }
        }.build()

        return _EyeOff!!
    }

private var _EyeOff: ImageVector? = null
