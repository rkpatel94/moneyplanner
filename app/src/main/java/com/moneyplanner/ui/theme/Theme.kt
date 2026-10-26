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
 * The "Kal" palette: midnight indigo meets marigold.
 *
 * In Hindi and Gujarati, kal means both yesterday and tomorrow, and that is what this app
 * does: what was recorded projects what will be left. The palette carries the idea. Indigo
 * and ink are the solid colour of settled fact; lavender is reserved for everything
 * projected; marigold marks what is active, chosen or worth celebrating. Surfaces are a warm
 * porcelain with pure white cards, so hierarchy comes from tone rather than heavy borders.
 *
 * Dynamic colour is deliberately not offered. A wallpaper-derived palette would repaint
 * "money in" and "money out" in whatever hues the device picked, and those two colours
 * carry meaning here rather than decoration.
 */

// Brand
private val Indigo = Color(0xFF2B2F8F)
private val IndigoDeep = Color(0xFF11137A)
private val IndigoTint = Color(0xFFE1E0FF)
private val Marigold = Color(0xFFFFB21C)
private val MarigoldSoft = Color(0xFFFFF1D1)
private val Ink = Color(0xFF12142B)

// Light surfaces
private val Porcelain = Color(0xFFF6F4EF)
private val Sunken = Color(0xFFEEEBE4)
private val Hairline = Color(0xFFE2DED5)

// Dark surfaces
private val Midnight = Color(0xFF0B0D2B)
private val NightCard = Color(0xFF15183D)
private val NightRaised = Color(0xFF1D2150)
private val NightSunken = Color(0xFF0F1133)
private val NightHairline = Color(0xFF2C3166)

/** The 1px hairline that defines a card boundary on a white surface. */
val CardBorderColor = Hairline

private val LightScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = IndigoTint,
    onPrimaryContainer = IndigoDeep,
    inversePrimary = Color(0xFFBFC1FF),
    // Secondary is marigold. The container is the soft wash, because Material uses it for
    // highlight cards and selected segments; full marigold is spent sparingly, through
    // MoneyColors.accent, on the active dock tab, chosen chips and the add button.
    secondary = Color(0xFF805600),
    onSecondary = Color.White,
    secondaryContainer = MarigoldSoft,
    onSecondaryContainer = Color(0xFF3D2A00),
    tertiary = Color(0xFF0B7A6B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF3EE),
    onTertiaryContainer = Color(0xFF00201B),
    background = Porcelain,
    onBackground = Ink,
    surface = Porcelain,
    onSurface = Ink,
    surfaceVariant = Sunken,
    onSurfaceVariant = Color(0xFF4A4F66),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFF3F1EC),
    surfaceContainerHigh = Sunken,
    surfaceContainerHighest = Color(0xFFE8E4DB),
    inverseSurface = Color(0xFF2D2E47),
    inverseOnSurface = Color(0xFFF1EFFF),
    // Outline bounds something pressable and has to stay visible; the hairline between
    // rows and around cards is the quieter variant.
    outline = Color(0xFF7C8196),
    outlineVariant = Hairline,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A)
)

/**
 * Midnight rather than grey. The same relationships as the light palette, with indigo
 * lifted to a lavender blue so it still reads as the brand on a dark page.
 */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8A8FFF),
    onPrimary = Midnight,
    primaryContainer = Indigo,
    onPrimaryContainer = IndigoTint,
    inversePrimary = Indigo,
    secondary = Marigold,
    onSecondary = Color(0xFF281800),
    secondaryContainer = Color(0xFF3A2E12),
    onSecondaryContainer = Color(0xFFFFE7B3),
    tertiary = Color(0xFF3FD3B9),
    onTertiary = Color(0xFF00201B),
    tertiaryContainer = Color(0xFF0F3B36),
    onTertiaryContainer = Color(0xFF98F3E0),
    background = Midnight,
    onBackground = Color(0xFFF4F3FF),
    surface = Midnight,
    onSurface = Color(0xFFF4F3FF),
    surfaceVariant = NightRaised,
    onSurfaceVariant = Color(0xFFB9BCE0),
    // Cards sit one step above the page, so the "lowest" container, which cards use, is
    // the card tone rather than something darker than the background.
    surfaceContainerLowest = NightCard,
    surfaceContainerLow = Color(0xFF181B44),
    surfaceContainer = Color(0xFF1B1F4A),
    surfaceContainerHigh = NightRaised,
    surfaceContainerHighest = Color(0xFF262A5C),
    inverseSurface = Color(0xFFF4F3FF),
    inverseOnSurface = Color(0xFF181A31),
    outline = Color(0xFF858AB8),
    outlineVariant = NightHairline,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * Colours that carry meaning rather than style, held outside the Material scheme so they
 * keep that meaning in both themes.
 *
 * Peacock teal for money arriving, kumkum rose for money leaving, turmeric for something
 * late or near a limit, lavender for anything projected rather than recorded. Each always
 * travels with an icon and a word, never as colour alone.
 */
data class MoneyColors(
    val positive: Color,
    val positiveContainer: Color,
    /** Hairline around a positive pill. Meaning never rests on fill colour alone. */
    val positiveBorder: Color,
    val negative: Color,
    val negativeContainer: Color,
    val negativeBorder: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val warningBorder: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val neutralBorder: Color,
    /**
     * Three levels of text emphasis, so a figure and the date under it are separated by
     * weight and colour rather than by size alone.
     */
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Card boundary hairline. */
    val cardBorder: Color,
    /** The quieter divider used inside a card, between rows of one list. */
    val borderSubtle: Color,
    /** Nested containers and table headers, one step up from a plain card. */
    val cardElevated: Color,
    /** Tint behind a semantic icon, at roughly ten percent of its colour. */
    val iconWash: Color,
    /** Lavender: the colour of every figure that is projected rather than recorded. */
    val forecast: Color,
    val forecastContainer: Color,
    /** Marigold, for the active, the chosen and the celebrated. Always carries [onAccent]. */
    val accent: Color,
    val onAccent: Color,
    /** The soft marigold wash behind gentle highlights. */
    val accentSoft: Color,
    /** Hero figures and screen titles: the deepest indigo, a step beyond primary. */
    val display: Color,
    /** Inset wells: inputs, keypad keys, progress tracks. */
    val sunken: Color,
    /** The card colour, for marks drawn on a card that must match its tone. */
    val card: Color
)

private val LightMoneyColors = MoneyColors(
    positive = Color(0xFF0B7A6B),
    positiveContainer = Color(0xFFDDF3EE),
    positiveBorder = Color(0xFF9ED9CC),
    negative = Color(0xFFC2334D),
    negativeContainer = Color(0xFFFBE4E8),
    negativeBorder = Color(0xFFF2B3BF),
    warning = Color(0xFF9A5B00),
    warningContainer = MarigoldSoft,
    onWarningContainer = Color(0xFF3D2A00),
    warningBorder = Color(0xFFF5CF85),
    neutral = Color(0xFF5B6275),
    neutralContainer = Sunken,
    neutralBorder = Color(0xFFD6D1C6),
    textPrimary = Ink,
    textSecondary = Color(0xFF4A4F66),
    textTertiary = Color(0xFF7C8196),
    cardBorder = Hairline,
    borderSubtle = Sunken,
    cardElevated = Color(0xFFF3F1EC),
    iconWash = Sunken,
    forecast = Color(0xFF6B5BD6),
    forecastContainer = Color(0xFFEEEBFF),
    accent = Marigold,
    onAccent = Ink,
    accentSoft = MarigoldSoft,
    display = IndigoDeep,
    sunken = Sunken,
    card = Color.White
)

private val DarkMoneyColors = MoneyColors(
    positive = Color(0xFF3FD3B9),
    positiveContainer = Color(0xFF0F3B36),
    positiveBorder = Color(0xFF1F5E55),
    negative = Color(0xFFFF7A8F),
    negativeContainer = Color(0xFF47162A),
    negativeBorder = Color(0xFF7A2840),
    warning = Color(0xFFFFC048),
    warningContainer = Color(0xFF3A2E12),
    onWarningContainer = Color(0xFFFFE7B3),
    warningBorder = Color(0xFF6E5317),
    neutral = Color(0xFFA3AABB),
    neutralContainer = NightRaised,
    neutralBorder = Color(0xFF343A70),
    textPrimary = Color(0xFFF4F3FF),
    textSecondary = Color(0xFFB9BCE0),
    textTertiary = Color(0xFF858AB8),
    cardBorder = NightHairline,
    borderSubtle = Color(0xFF22265A),
    cardElevated = NightRaised,
    iconWash = Color(0xFF22265A),
    forecast = Color(0xFFA99BFF),
    forecastContainer = Color(0xFF2A2660),
    accent = Marigold,
    onAccent = Ink,
    accentSoft = Color(0xFF3A2E12),
    display = Color(0xFFC9CBFF),
    sunken = NightSunken,
    card = NightCard
)

val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }

/**
 * Elevation as tonal layers with a soft indigo-tinted shadow, never harsh black. Level 1 is
 * a card, level 2 is something floating above the page, such as the dock.
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
