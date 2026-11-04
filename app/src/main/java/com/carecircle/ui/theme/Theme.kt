package com.carecircle.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.carecircle.domain.Display
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colours read the high-contrast switch from Compose state, so flipping it in Settings recolours every screen at once.
 * The high-contrast values are in [Display.HighContrast] and are checked for 7:1 text contrast in DisplayTest.
 */
object Palette {
    var highContrast by mutableStateOf(false)
    private val hc = Display.HighContrast
    private fun pick(normal: Long, high: Long) = Color(if (highContrast) high else normal)

    val Canvas: Color get() = pick(0xFFF1F1F3, hc.CANVAS)
    val Surface: Color get() = pick(0xFFFFFFFF, hc.SURFACE)
    val Ink: Color get() = pick(0xFF111114, hc.INK)
    val Muted: Color get() = pick(0xFF86868F, hc.MUTED)
    val Line: Color get() = pick(0xFFE6E6EA, hc.LINE)
    val Orange: Color get() = pick(0xFFF26A21, hc.ORANGE)
    val OrangeSoft: Color get() = pick(0xFFFDE7DA, hc.ORANGE_SOFT)
    val Organ: Color get() = pick(0xFFD2391F, hc.BAD)
    val Good: Color get() = pick(0xFF2E9E6B, hc.GOOD)
    val GoodSoft: Color get() = pick(0xFFDDF3E8, hc.GOOD_SOFT)
    val Warn: Color get() = pick(0xFFE5A100, hc.WARN)
    val WarnSoft: Color get() = pick(0xFFFFF1CC, hc.WARN_SOFT)
    val Bad: Color get() = pick(0xFFD32F2F, hc.BAD)
    val BadSoft: Color get() = pick(0xFFFDE0E0, hc.BAD_SOFT)
}

private fun scheme() = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Color.White,
    secondary = Palette.Orange,
    onSecondary = Color.White,
    secondaryContainer = Palette.OrangeSoft,
    onSecondaryContainer = Palette.Ink,
    background = Palette.Canvas,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = if (Palette.highContrast) Color.White else Color(0xFFF6F6F8),
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Bad,
    errorContainer = Palette.BadSoft,
    onErrorContainer = Palette.Bad,
)

private val face = FontFamily.SansSerif

private val type = Typography(
    displayLarge = TextStyle(fontFamily = face, fontWeight = FontWeight.Light, fontSize = 72.sp, lineHeight = 76.sp),
    headlineLarge = TextStyle(fontFamily = face, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = face, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = face, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = face, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = face, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = face, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = face, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = face, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = face, fontWeight = FontWeight.Medium, fontSize = 13.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
)

@Composable
fun CareCircleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(), typography = type, shapes = shapes, content = content)
}
