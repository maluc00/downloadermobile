package com.klipr.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KliprBg = Color(0xFF0B0F19)
val KliprSurface = Color(0xFF151D2A)
val KliprCard = Color(0xFF1E293B)
val KliprPrimary = Color(0xFF6366F1)
val KliprSecondary = Color(0xFF06B6D4)
val KliprAccent = Color(0xFF818CF8)
val KliprTextPrimary = Color(0xFFF8FAFC)
val KliprTextSecondary = Color(0xFF94A3B8)
val KliprBorder = Color(0xFF334155)
val KliprSuccess = Color(0xFF10B981)
val KliprError = Color(0xFFEF4444)

private val DarkColorScheme = darkColorScheme(
    primary = KliprPrimary,
    secondary = KliprSecondary,
    tertiary = KliprAccent,
    background = KliprBg,
    surface = KliprSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = KliprTextPrimary,
    onSurface = KliprTextPrimary,
    surfaceVariant = KliprCard
)

@Composable
fun KliprTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

