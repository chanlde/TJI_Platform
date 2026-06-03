package com.tji.device.product.solarclean.ui.icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tji.device.ui.theme.TjiPrimary

val PumpPressure: ImageVector
    get() {
        if (_PumpPressure != null) return _PumpPressure!!

        _PumpPressure = ImageVector.Builder(
            name = "PumpPressure",
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
                moveTo(5f, 14f)
                curveTo(5f, 10.1f, 8.1f, 7f, 12f, 7f)
                curveTo(15.9f, 7f, 19f, 10.1f, 19f, 14f)
                moveTo(7f, 18f)
                horizontalLineTo(17f)
                moveTo(12f, 14f)
                lineTo(15.5f, 10.5f)
                moveTo(12f, 3f)
                verticalLineTo(5f)
                moveTo(4.9f, 6.9f)
                lineTo(6.3f, 8.3f)
                moveTo(19.1f, 6.9f)
                lineTo(17.7f, 8.3f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12.5f)
                curveTo(12.83f, 12.5f, 13.5f, 13.17f, 13.5f, 14f)
                curveTo(13.5f, 14.83f, 12.83f, 15.5f, 12f, 15.5f)
                curveTo(11.17f, 15.5f, 10.5f, 14.83f, 10.5f, 14f)
                curveTo(10.5f, 13.17f, 11.17f, 12.5f, 12f, 12.5f)
                close()
            }
        }.build()

        return _PumpPressure!!
    }

private var _PumpPressure: ImageVector? = null

val SprayAngle: ImageVector
    get() {
        if (_SprayAngle != null) return _SprayAngle!!

        _SprayAngle = ImageVector.Builder(
            name = "SprayAngle",
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
                moveTo(6f, 17f)
                curveTo(7.3f, 13.2f, 9.5f, 10.3f, 12f, 8f)
                curveTo(14.5f, 10.3f, 16.7f, 13.2f, 18f, 17f)
                moveTo(12f, 8f)
                verticalLineTo(4f)
                moveTo(8.5f, 19.5f)
                horizontalLineTo(15.5f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(7.2f, 7.8f)
                curveTo(7.2f, 8.52f, 6.62f, 9.1f, 5.9f, 9.1f)
                curveTo(5.18f, 9.1f, 4.6f, 8.52f, 4.6f, 7.8f)
                curveTo(4.6f, 7.08f, 5.18f, 6.5f, 5.9f, 6.5f)
                curveTo(6.62f, 6.5f, 7.2f, 7.08f, 7.2f, 7.8f)
                close()
                moveTo(19.4f, 7.8f)
                curveTo(19.4f, 8.52f, 18.82f, 9.1f, 18.1f, 9.1f)
                curveTo(17.38f, 9.1f, 16.8f, 8.52f, 16.8f, 7.8f)
                curveTo(16.8f, 7.08f, 17.38f, 6.5f, 18.1f, 6.5f)
                curveTo(18.82f, 6.5f, 19.4f, 7.08f, 19.4f, 7.8f)
                close()
            }
        }.build()

        return _SprayAngle!!
    }

private var _SprayAngle: ImageVector? = null

val SwingSpeed: ImageVector
    get() {
        if (_SwingSpeed != null) return _SwingSpeed!!

        _SwingSpeed = ImageVector.Builder(
            name = "SwingSpeed",
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
                moveTo(5f, 15f)
                curveTo(7.3f, 18f, 16.7f, 18f, 19f, 15f)
                moveTo(5f, 9f)
                curveTo(7.3f, 6f, 16.7f, 6f, 19f, 9f)
                moveTo(8f, 12f)
                horizontalLineTo(8.1f)
                moveTo(12f, 12f)
                horizontalLineTo(12.1f)
                moveTo(16f, 12f)
                horizontalLineTo(16.1f)
            }
        }.build()

        return _SwingSpeed!!
    }

private var _SwingSpeed: ImageVector? = null

@Preview(showBackground = true, name = "SolarClean 控制图标")
@Composable
private fun SolarCleanControlIconsPreview() {
    MaterialTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(PumpPressure, contentDescription = "水泵压力", tint = TjiPrimary)
            Icon(SprayAngle, contentDescription = "喷洒角度", tint = TjiPrimary)
            Icon(SwingSpeed, contentDescription = "摆动速度", tint = TjiPrimary)
        }
    }
}
