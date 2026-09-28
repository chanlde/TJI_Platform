package com.tji.device.product.firegun.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A compact water-cannon glyph used for the fire-gun product entry. */
val FireGun: ImageVector
    get() {
        if (_FireGun != null) return _FireGun!!

        _FireGun = ImageVector.Builder(
            name = "FireGun",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Water drop
                moveTo(5.5f, 2.25f)
                curveTo(5.5f, 2.25f, 2.5f, 5.75f, 2.5f, 8.2f)
                curveTo(2.5f, 9.86f, 3.84f, 11.2f, 5.5f, 11.2f)
                curveTo(7.16f, 11.2f, 8.5f, 9.86f, 8.5f, 8.2f)
                curveTo(8.5f, 5.75f, 5.5f, 2.25f, 5.5f, 2.25f)
                close()

                // Cannon body and nozzle
                moveTo(9.2f, 7.1f)
                lineTo(18.1f, 7.1f)
                curveTo(18.93f, 7.1f, 19.6f, 7.77f, 19.6f, 8.6f)
                lineTo(19.6f, 9.1f)
                lineTo(22f, 9.1f)
                lineTo(22f, 12.1f)
                lineTo(19.6f, 12.1f)
                lineTo(19.6f, 12.6f)
                curveTo(19.6f, 13.43f, 18.93f, 14.1f, 18.1f, 14.1f)
                lineTo(9.2f, 14.1f)
                close()

                // Pivot and support
                moveTo(13.1f, 14.1f)
                lineTo(16.7f, 14.1f)
                lineTo(17.7f, 20.2f)
                lineTo(15.1f, 20.2f)
                lineTo(14.55f, 16.75f)
                lineTo(12.7f, 20.2f)
                lineTo(9.75f, 20.2f)
                close()
            }
        }.build()

        return _FireGun!!
    }

private var _FireGun: ImageVector? = null
