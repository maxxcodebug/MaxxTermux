package com.maxxcodebug.maxxtermux.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** AX Design: colors. Dark-first, tonal teal surfaces. */
object AxColors {
    val Bg = Color(0xFF0B0F11)
    val Surface = Color(0xFF141D21)
    val SurfaceHigh = Color(0xFF1A2A32)
    val Container = Color(0xFF2B4450)
    val Teal = Color(0xFF2B5566)
    val TealDeep = Color(0xFF1B3A48)

    val Accent = Color(0xFFA5CFE0)
    val AccentSoft = Color(0xFFBFE6F5)
    val OnAccent = Color(0xFF12303D)

    val OnSurface = Color(0xFFE3EDF1)
    val OnMuted = Color(0xFF8FA3AC)
    val OnTeal = Color(0xFFF0FAFE)
    val OnTealMuted = Color(0xFF9CCBDC)

    val Online = Color(0xFF6FD89A)
    val Offline = Color(0xFF9AA7AD)

    val CircleA = Color(0xFF16323D)
    val CircleB = Color(0xFF1A2D5A)
    val CircleC = Color(0xFF1F4A57)

    val Glass = Color(0x613C6E82)
    val GlassSelected = Color(0x42DCF0FA)
    val GlassAccent = Color(0x80A5CFE0)
    val GlassEdge = Color(0x38FFFFFF)

    val IndigoBg = Color(0xFF1D2552)
    val IndigoIcon = Color(0xFF4A50A8)
    val IndigoText = Color(0xFFEEF0FF)
    val IndigoSub = Color(0xFFB8BCF0)

    val GreenBg = Color(0xFF27381F)
    val GreenIcon = Color(0xFF55703F)
    val GreenText = Color(0xFFF0F8E6)
    val GreenSub = Color(0xFFC3D9AB)

    val RedBg = Color(0xFF3B1D21)
    val RedIcon = Color(0xFF7A3A40)
    val RedText = Color(0xFFFFEEEF)
    val RedSub = Color(0xFFF0B9BD)
}

/** AX Design: shapes. Large radii, mixed corners for expressive tiles. */
object AxShapes {
    val Card = RoundedCornerShape(32.dp)
    val Row = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(36.dp)
    val Chip = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(20.dp)
    val IconServer = RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp)
    val IconDevice = RoundedCornerShape(50)
    val IconEmbedded = RoundedCornerShape(6.dp, 16.dp, 16.dp, 16.dp)
}
