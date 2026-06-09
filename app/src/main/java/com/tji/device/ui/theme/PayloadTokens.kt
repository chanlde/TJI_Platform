package com.tji.device.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object PayloadColors {
    val Background = TjiBackground
    val Surface = TjiSurface
    val SurfaceSoft = TjiSurfaceSoft
    val TextPrimary = TjiTextPrimary
    val TextSecondary = TjiTextSecondary
    val TextMuted = TjiTextMuted
    val Border = TjiBorder
    val Primary = TjiPrimary
    val PrimarySoft = TjiPrimarySoft
    val Success = TjiOnline
    val SuccessSoft = TjiSuccessSoft
    val Warning = TjiWarning
    val WarningSoft = TjiWarningSoft
    val Danger = TjiError
    val DangerSoft = TjiDangerSoft
}

object PayloadDimens {
    val ScreenPadding = 16.dp
    val SectionGap = 12.dp
    val CardRadius = 16.dp
    val ControlRadius = 10.dp
    val CompactRadius = 8.dp
    val CardPadding = 16.dp
    val ControlHeight = 44.dp
    val BottomNavHeight = 54.dp
}

fun PayloadColors.statusSoft(color: Color): Color = color.copy(alpha = 0.10f)
