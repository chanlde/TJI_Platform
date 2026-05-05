package com.tji.device.ui.icon.common

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 电池外框图标（无填充）
 */
val BatteryOutline: ImageVector
    get() {
        if (_BatteryOutline != null) return _BatteryOutline!!

        _BatteryOutline = ImageVector.Builder(
            name = "BatteryOutline",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            // 电池外框
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 4f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                verticalLineToRelative(4f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineToRelative(10f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                verticalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, -2f)
                close()
            }
            // 电池正极
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(14f, 6f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                verticalLineToRelative(2f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
                verticalLineToRelative(-4f)
                close()
            }
        }.build()

        return _BatteryOutline!!
    }

private var _BatteryOutline: ImageVector? = null

/**
 * 仅预览 BatteryOutline 图标本身，便于在设计阶段单独查看
 * 或者直接复用该图标。
 */
@Preview(showBackground = true)
@Composable
private fun BatteryOutlineIconPreview() {
    MaterialTheme {
        Icon(
            imageVector = BatteryOutline,
            contentDescription = "Battery outline preview",
            tint = Color.Black
        )
    }
}
