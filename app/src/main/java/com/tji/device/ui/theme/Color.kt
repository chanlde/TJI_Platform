package com.tji.device.ui.theme

import androidx.compose.ui.graphics.Color

val TjiPrimary = Color(0xFF1677FF)
val TjiPrimaryDark = Color(0xFF155EEF)
val TjiPrimarySoft = Color(0xFFEAF2FF)
val TjiSecondary = Color(0xFF475569)
val TjiBackground = Color(0xFFF5F7FA)
val TjiSurface = Color.White
val TjiSurfaceSoft = Color(0xFFFAFBFF)
val TjiTextPrimary = Color(0xFF1A1A2E)
val TjiTextSecondary = Color(0xFF667085)
val TjiTextMuted = Color(0xFF8C8C8C)
val TjiBorder = Color(0xFFE8EDF5)
val TjiControlInactive = Color(0xFFE5ECF8)
val TjiControlDisabled = Color(0xFFE5E7EB)
val TjiControlDisabledStrong = Color(0xFFCBD5E1)
val TjiOnline = Color(0xFF52C41A)
val TjiWarning = Color(0xFFFA8C16)
val TjiError = Color(0xFFFF4D4F)
val TjiDangerSoft = Color(0xFFFFF1F0)
val TjiSuccessSoft = Color(0xFFEFFBEF)
val TjiWarningSoft = Color(0xFFFFF7E6)

// Kept for older screens that still reference the template names.
val Purple80 = Color(0xFF9BC7FF)
val PurpleGrey80 = Color(0xFFCBD5E1)
val Pink80 = Color(0xFFB7E4C7)

val Purple40 = TjiPrimary
val PurpleGrey40 = TjiSecondary
val Pink40 = TjiOnline

// 颜色定义
object LoginColors {
    val Primary = TjiPrimary
    val PrimaryVariant = TjiPrimaryDark
    val Secondary = TjiOnline
    val Background = TjiBackground
    val Surface = TjiSurface
    val OnSurface = TjiTextPrimary
    val OnSurfaceVariant = TjiTextSecondary
    val TextSecondary = Color(0xFF344054)
    val TextTertiary = TjiTextMuted
    val Border = TjiBorder
    val Error = TjiError
    val Google = Color(0xFF4285F4)
    val Facebook = Color(0xFF1877F2)
}
