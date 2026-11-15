package com.mitrasis.tarot

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val Night = darkColorScheme(
    primary = Color(0xFFD9B25F),
    onPrimary = Color(0xFF241B08),
    primaryContainer = Color(0xFF3A2E14),
    onPrimaryContainer = Color(0xFFF3DCA6),
    secondary = Color(0xFF8E7FD6),
    secondaryContainer = Color(0xFF2A2440),
    onSecondaryContainer = Color(0xFFE5DEFF),
    background = Color(0xFF0E0C14),
    onBackground = Color(0xFFE8E4F0),
    surface = Color(0xFF14111C),
    onSurface = Color(0xFFE8E4F0),
    surfaceVariant = Color(0xFF1E1A29),
    onSurfaceVariant = Color(0xFFC9C3DA),
    error = Color(0xFFE07A7A),
    errorContainer = Color(0xFF3A1F1F)
)

private val Day = lightColorScheme(
    primary = Color(0xFF6B4E12),
    primaryContainer = Color(0xFFF6E7C6),
    secondary = Color(0xFF5A4BA8),
    secondaryContainer = Color(0xFFE7E1FA),
    background = Color(0xFFFBF8F2),
    surface = Color(0xFFFFFCF6),
    surfaceVariant = Color(0xFFF0EADF)
)

@Composable
fun TarotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Night else Day,
        content = content
    )
}
