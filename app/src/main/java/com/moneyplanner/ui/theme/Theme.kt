package com.moneyplanner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.moneyplanner.data.prefs.ThemeMode

/**
 * The Bharat Wealth Framework palette.
 *
 * Deep navy anchors the app because it reads as institutional and stable, which is what a
 * person wants from something holding their financial position. Surfaces are a crisp
 * off-white with pure white reserved for elevated cards, so hierarchy comes from tonal
 * shift rather than from heavy borders.
 *
 * Dynamic colour is deliberately not offered. A wallpaper-derived palette would repaint
 * "money in" and "money out" in whatever hues the device picked, and those two colours
 * carry meaning here rather than decoration.
 */

// Core brand
private val DeepNavy = Color(0xFF000666)
private val NavyContainer = Color(0xFF1A237E)
private val OnNavyContainer = Color(0xFF8690EE)
private val SecondaryIndigo = Color(0xFF4555B7)
private val SoftBlue = Color(0xFFE8EAF6)

// Surfaces
private val SurfaceBase = Color(0xFFF8F9FA)
private val SurfaceElevated = Color(0xFFFFFFFF)
private val SurfaceContainer = Color(0xFFEDEEEF)
private val SurfaceContainerHigh = Color(0xFFE7E8E9)
private val OnSurface = Color(0xFF191C1D)
private val OnSurfaceVariant = Color(0xFF454652)
private val OutlineColor = Color(0xFF767683)
private val OutlineVariantColor = Color(0xFFC6C5D4)

/** The 1px hairline that defines a card boundary on a white surface. */
val CardBorderColor = SoftBlue

private val LightScheme = lightColorScheme(
    primary = DeepNavy,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = OnNavyContainer,
    inversePrimary = Color(0xFFBDC2FF),
    secondary = SecondaryIndigo,
    onSecondary = Color.White,
    secondaryContainer = SoftBlue,
    onSecondaryContainer = Color(0xFF182A8E),
    tertiary = Color(0xFF181B23),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E2EE),
    onTertiaryContainer = Color(0xFF181B24),
    background = SurfaceBase,
    onBackground = OnSurface,
    surface = SurfaceBase,
    onSurface = OnSurface,
    surfaceVariant = Color(0xFFE1E3E4),
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = SurfaceElevated,
    surfaceContainerLow = Color(0xFFF3F4F5),
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFE1E3E4),
    inverseSurface = Color(0xFF2E3132),
    inverseOnSurface = Color(0xFFF0F1F2),
    outline = OutlineColor,
    outlineVariant = OutlineVariantColor,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A)
)

/**
 * The dark counterpart. The framework specifies a light palette, so this keeps its
 * relationships rather than inventing a second identity: navy becomes the light-on-dark
 * accent, and the same soft indigo carries containers.
 */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFBDC2FF),
    onPrimary = Color(0xFF000767),
    primaryContainer = Color(0xFF343D96),
    onPrimaryContainer = Color(0xFFE0E0FF),
    inversePrimary = DeepNavy,
    secondary = Color(0xFFBBC3FF),
    onSecondary = Color(0xFF000E5E),
    secondaryContainer = Color(0xFF2C3C9E),
    onSecondaryContainer = Color(0xFFDEE0FF),
    tertiary = Color(0xFFC4C6D2),
    onTertiary = Color(0xFF2C3039),
    tertiaryContainer = Color(0xFF434750),
    onTertiaryContainer = Color(0xFFE0E2EE),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D4),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    outline = Color(0xFF90909D),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * Colours that carry meaning rather than style, held outside the Material scheme so they
 * keep that meaning in both themes.
 *
 * Emerald for growth and money arriving, amber for a limit being approached, rose for
 * something genuinely wrong. Amber is reserved strictly for warnings so that it still
 * registers as one when it appears.
 */
data class MoneyColors(
    val positive: Color,
    val positiveContainer: Color,
    val negative: Color,
    val negativeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val neutral: Color,
    /** Card boundary hairline. */
    val cardBorder: Color,
    /** Tint behind a semantic icon, at roughly ten percent of its colour. */
    val iconWash: Color
)

private val LightMoneyColors = MoneyColors(
    positive = Color(0xFF065F46),
    positiveContainer = Color(0xFFD9F2E7),
    negative = Color(0xFFBE123C),
    negativeContainer = Color(0xFFFFE2E7),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFDF0DC),
    onWarningContainer = Color(0xFF3D2A00),
    neutral = Color(0xFF6B6C78),
    cardBorder = SoftBlue,
    iconWash = SoftBlue
)

private val DarkMoneyColors = MoneyColors(
    positive = Color(0xFF6FD6A6),
    positiveContainer = Color(0xFF12372A),
    negative = Color(0xFFFF9DAF),
    negativeContainer = Color(0xFF4A1122),
    warning = Color(0xFFF2C464),
    warningContainer = Color(0xFF3E2E00),
    onWarningContainer = Color(0xFFFDF0DC),
    neutral = Color(0xFF9A9AA6),
    cardBorder = Color(0xFF2B2E45),
    iconWash = Color(0xFF23263A)
)

val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }

/**
 * Elevation as tonal layers with a soft navy-tinted shadow, never harsh black. Level 1 is
 * a card, level 2 is something active or floating above the page.
 */
object Elevation {
    val level1 = 2.dp
    val level2 = 8.dp
}

object MoneyTheme {
    val colors: MoneyColors
        @Composable get() = LocalMoneyColors.current
}

@Composable
fun MoneyPlannerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val moneyColors = if (darkTheme) DarkMoneyColors else LightMoneyColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalMoneyColors provides moneyColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MoneyTypography,
            shapes = MoneyShapes,
            content = content
        )
    }
}
