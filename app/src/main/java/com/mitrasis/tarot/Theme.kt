package com.mitrasis.tarot

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Night = darkColorScheme(
    primary = Color(0xFFC9A26B),
    onPrimary = Color(0xFF1A130B),
    primaryContainer = Color(0xFF2A2118),
    onPrimaryContainer = Color(0xFFE8D9BE),
    secondary = Color(0xFF8A7B63),
    secondaryContainer = Color(0xFF1B1720),
    onSecondaryContainer = Color(0xFFE6DCC8),
    background = Color(0xFF0C0A0F),
    onBackground = Color(0xFFE6DCC8),
    surface = Color(0xFF121016),
    onSurface = Color(0xFFE6DCC8),
    surfaceVariant = Color(0xFF191520),
    onSurfaceVariant = Color(0xFFC6B9A2),
    error = Color(0xFFC97A6A),
    errorContainer = Color(0xFF2E1A16)
)

private val Day = lightColorScheme(
    primary = Color(0xFF5A4526),
    onPrimary = Color(0xFFF6EEDF),
    primaryContainer = Color(0xFFEFE3CB),
    onPrimaryContainer = Color(0xFF2A2114),
    secondary = Color(0xFF6A5A42),
    secondaryContainer = Color(0xFFEDE4D2),
    onSecondaryContainer = Color(0xFF2A2318),
    background = Color(0xFFF3ECDD),
    onBackground = Color(0xFF241C12),
    surface = Color(0xFFF7F1E4),
    onSurface = Color(0xFF241C12),
    surfaceVariant = Color(0xFFE7DCC6),
    onSurfaceVariant = Color(0xFF4A3E2C)
)

private val serif = FontFamily.Serif

private val antique = Typography(
    headlineMedium = TextStyle(
        fontFamily = serif, fontSize = 27.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Normal
    ),
    headlineSmall = TextStyle(fontFamily = serif, fontSize = 22.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontFamily = serif, fontSize = 19.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontFamily = serif, fontSize = 16.sp, letterSpacing = 2.sp),
    bodyMedium = TextStyle(fontFamily = serif, fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = serif, fontSize = 13.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = serif, fontSize = 11.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = serif, fontSize = 12.sp, letterSpacing = 1.sp)
)

@Composable
fun TarotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Night else Day,
        typography = antique,
        content = content
    )
}
