package com.wolfeleo2.fika.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 60-30-10 palette: calm slate/neutral canvas (60%), washed secondary structure (30%),
// and punchy ember accent for CTAs and high-value focal points (10%).

internal val LightColors: ColorScheme = lightColorScheme(
    // Punchy Ember Accent (CTAs, active orders, key focus)
    primary = Color(0xFFD44417),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFEBE6),
    onPrimaryContainer = Color(0xFF8C2200),

    // Washed Slate Secondary (structural chips, subdued chrome)
    secondary = Color(0xFF4B5563),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF1E293B),

    // Money & Cash Tertiary (amber/gold for KES collection)
    tertiary = Color(0xFFAD5009),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),

    // Error
    error = Color(0xFFCC2121),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),

    // Clean neutral canvas (60%) — eliminates glare and pastel pink flood
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFF9CA3AF),
    outlineVariant = Color(0xFFE5E7EB),

    // Elevated surface tiers
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFF1F3F5),
    surfaceContainerHigh = Color(0xFFE9ECEF),
    surfaceContainerHighest = Color(0xFFDEE2E6),
)

internal val DarkColors: ColorScheme = darkColorScheme(
    // Punchy Ember Accent (luminous for dark mode readability)
    primary = Color(0xFFFF7247),
    onPrimary = Color(0xFF430D00),
    primaryContainer = Color(0xFF8C2200),
    onPrimaryContainer = Color(0xFFFFDBCD),

    // Washed Slate Secondary
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF262D38),
    onSecondaryContainer = Color(0xFFE2E8F0),

    // Money & Cash Tertiary
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),

    // Error
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),

    // Deep Charcoal / Slate Canvas
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF222730),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF4B5563),
    outlineVariant = Color(0xFF2E3541),

    // Elevated dark surface tiers
    surfaceContainerLowest = Color(0xFF0B0D11),
    surfaceContainerLow = Color(0xFF161B22),
    surfaceContainer = Color(0xFF1F242C),
    surfaceContainerHigh = Color(0xFF262C36),
    surfaceContainerHighest = Color(0xFF2E3541),
)

/** Success green (Delivered, "Paid and delivered"); M3 has no slot for it. Read via `FikaTheme.success`. */
@Immutable
class SuccessColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

internal val LightSuccess = SuccessColors(
    success = Color(0xFF1E6B3C),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFA6F2B6),
    onSuccessContainer = Color(0xFF00210E),
)

internal val DarkSuccess = SuccessColors(
    success = Color(0xFF8BD89B),
    onSuccess = Color(0xFF00391B),
    successContainer = Color(0xFF1E6B3C),
    onSuccessContainer = Color(0xFFA6F2B6),
)

internal val LocalSuccessColors = staticCompositionLocalOf { LightSuccess }
