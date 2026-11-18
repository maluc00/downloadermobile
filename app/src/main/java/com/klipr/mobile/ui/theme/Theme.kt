package com.klipr.mobile.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.klipr.mobile.data.KliprPreferences

data class KliprPalette(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val success: Color,
    val error: Color,
    val isDark: Boolean = true
)

enum class AppThemeMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val previewColors: List<Color>
) {
    DARK_AURORA(
        id = "aurora",
        title = "Dark Aurora",
        subtitle = "Azul-noite com Indigo & Cyan",
        previewColors = listOf(Color(0xFF0B0F19), Color(0xFF6366F1), Color(0xFF06B6D4))
    ),
    AMOLED_BLACK(
        id = "amoled",
        title = "AMOLED Pitch Black",
        subtitle = "Preto puro + Esmeralda",
        previewColors = listOf(Color(0xFF000000), Color(0xFF10B981), Color(0xFF06B6D4))
    ),
    CYBER_CRIMSON(
        id = "crimson",
        title = "Cyber Crimson",
        subtitle = "Grafite + Rubi & Âmbar",
        previewColors = listOf(Color(0xFF0F080B), Color(0xFFF43F5E), Color(0xFFF59E0B))
    ),
    SOLAR_FROST(
        id = "frost",
        title = "Solar Frost",
        subtitle = "Modo claro alto contraste",
        previewColors = listOf(Color(0xFFF1F5F9), Color(0xFF4F46E5), Color(0xFF0891B2))
    ),
    MATERIAL_YOU(
        id = "dynamic",
        title = "Material You",
        subtitle = "Cores dinâmicas do sistema",
        previewColors = listOf(Color(0xFF1C1B1F), Color(0xFFD0BCFF), Color(0xFFCCC2DC))
    );

    companion object {
        fun fromId(id: String): AppThemeMode =
            entries.firstOrNull { it.id == id } ?: DARK_AURORA
    }
}

private val AuroraPalette = KliprPalette(
    bg = Color(0xFF0B0F19),
    surface = Color(0xFF151D2A),
    card = Color(0xFF1E293B),
    primary = Color(0xFF6366F1),
    secondary = Color(0xFF06B6D4),
    accent = Color(0xFF818CF8),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    border = Color(0xFF334155),
    success = Color(0xFF10B981),
    error = Color(0xFFEF4444),
    isDark = true
)

private val AmoledPalette = KliprPalette(
    bg = Color(0xFF000000),
    surface = Color(0xFF0A0A0D),
    card = Color(0xFF13141A),
    primary = Color(0xFF10B981),
    secondary = Color(0xFF06B6D4),
    accent = Color(0xFF34D399),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA1A1AA),
    border = Color(0xFF27272A),
    success = Color(0xFF22C55E),
    error = Color(0xFFEF4444),
    isDark = true
)

private val CrimsonPalette = KliprPalette(
    bg = Color(0xFF0F080B),
    surface = Color(0xFF1C1015),
    card = Color(0xFF29171E),
    primary = Color(0xFFF43F5E),
    secondary = Color(0xFFF59E0B),
    accent = Color(0xFFFB7185),
    textPrimary = Color(0xFFFFF1F2),
    textSecondary = Color(0xFFFDA4AF),
    border = Color(0xFF4C1D2E),
    success = Color(0xFF10B981),
    error = Color(0xFFEF4444),
    isDark = true
)

private val SolarFrostPalette = KliprPalette(
    bg = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFF4F46E5),
    secondary = Color(0xFF0891B2),
    accent = Color(0xFF6366F1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    border = Color(0xFFCBD5E1),
    success = Color(0xFF059669),
    error = Color(0xFFDC2626),
    isDark = false
)

val LocalKliprPalette = staticCompositionLocalOf { AuroraPalette }

val KliprBg: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.bg

val KliprSurface: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.surface

val KliprCard: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.card

val KliprPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.primary

val KliprSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.secondary

val KliprAccent: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.accent

val KliprTextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.textPrimary

val KliprTextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.textSecondary

val KliprBorder: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.border

val KliprSuccess: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.success

val KliprError: Color
    @Composable @ReadOnlyComposable get() = LocalKliprPalette.current.error

@Composable
fun KliprTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val themeId by KliprPreferences.themeModeFlow.collectAsState()
    val mode = AppThemeMode.fromId(themeId)

    val palette = when (mode) {
        AppThemeMode.DARK_AURORA -> AuroraPalette
        AppThemeMode.AMOLED_BLACK -> AmoledPalette
        AppThemeMode.CYBER_CRIMSON -> CrimsonPalette
        AppThemeMode.SOLAR_FROST -> SolarFrostPalette
        AppThemeMode.MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dyn = dynamicDarkColorScheme(context)
                KliprPalette(
                    bg = dyn.background,
                    surface = dyn.surfaceContainerLow,
                    card = dyn.surfaceContainerHigh,
                    primary = dyn.primary,
                    secondary = dyn.tertiary,
                    accent = dyn.secondary,
                    textPrimary = dyn.onBackground,
                    textSecondary = dyn.onSurfaceVariant,
                    border = dyn.outlineVariant,
                    success = Color(0xFF10B981),
                    error = dyn.error,
                    isDark = true
                )
            } else {
                AuroraPalette
            }
        }
    }

    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.accent,
            background = palette.bg,
            surface = palette.surface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.card
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.accent,
            background = palette.bg,
            surface = palette.surface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.card
        )
    }

    CompositionLocalProvider(LocalKliprPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
