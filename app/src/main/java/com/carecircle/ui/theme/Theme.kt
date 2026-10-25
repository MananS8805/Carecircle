package com.carecircle.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Canvas = Color(0xFFF1F1F3)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111114)
    val Muted = Color(0xFF86868F)
    val Line = Color(0xFFE6E6EA)
    val Orange = Color(0xFFF26A21)
    val OrangeSoft = Color(0xFFFDE7DA)
    val Organ = Color(0xFFD2391F)
    val Good = Color(0xFF2E9E6B)
    val GoodSoft = Color(0xFFDDF3E8)
    val Warn = Color(0xFFE5A100)
    val WarnSoft = Color(0xFFFFF1CC)
    val Bad = Color(0xFFD32F2F)
    val BadSoft = Color(0xFFFDE0E0)
}

private val scheme = lightColorScheme(
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
    surfaceVariant = Color(0xFFF6F6F8),
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
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
}
