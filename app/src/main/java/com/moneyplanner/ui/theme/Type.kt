package com.moneyplanner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moneyplanner.R

/**
 * Typography for the "Kal" design.
 *
 * Two bundled faces, because the app has no internet permission and a downloadable font
 * would simply never arrive. Both ship as variable fonts, so every weight comes from one
 * file's `wght` axis rather than a file per weight.
 *
 * - **Anek Latin**, from Ek Type in India, carries character: hero balances, screen titles,
 *   card titles, verdicts. Its sister faces, Anek Devanagari and Anek Gujarati, are drawn to
 *   match, which is where Hindi and Gujarati will come from when the app is translated.
 * - **Inter** carries everything read rather than glanced at: body text, labels, and every
 *   amount in a list. Those styles set `tnum`, switching Inter to tabular figures so a column
 *   of rupee values lines up digit for digit instead of shuffling as the numbers change.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun interWeight(weight: Int) = Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Inter = FontFamily(
    interWeight(400),
    interWeight(500),
    interWeight(600),
    interWeight(700)
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun anekWeight(weight: Int) = Font(
    resId = R.font.anek_latin,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(100f)
    )
)

val Anek = FontFamily(
    anekWeight(500),
    anekWeight(600),
    anekWeight(700),
    anekWeight(800)
)

/** Applied to anything showing money, so figures align vertically. */
const val TABULAR_FIGURES = "tnum"

val MoneyTypography = Typography(
    // The hero balance, and nothing else at this size.
    displayLarge = TextStyle(
        fontFamily = Anek,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.02).em
    ),
    // A screen's headline figure, such as a month's expected remainder.
    displayMedium = TextStyle(
        fontFamily = Anek,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.01).em
    ),
    // The standard figure size on a tile.
    displaySmall = TextStyle(
        fontFamily = Anek,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp
    ),
    // Screen titles.
    headlineLarge = TextStyle(
        fontFamily = Anek,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01).em
    ),
    headlineMedium = TextStyle(
        fontFamily = Anek,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Anek,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // Card titles, set in the display face so every card reads as one family.
    titleLarge = TextStyle(
        fontFamily = Anek,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = TABULAR_FIGURES
    ),
    titleSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    // The accessibility floor for readable text.
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.1.sp
    )
)

/**
 * The uppercase micro-label: confidence pills, overlines such as "AVAILABLE TO SPEND", and
 * section captions. Spaced out a little so small capitals stay legible.
 */
val PillLabelStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.08.em
)

/**
 * Rounded, expressive shapes. Cards use 24dp and hero cards and sheets 28dp; inputs sit at
 * 16dp; buttons, chips and pills are fully rounded wherever they are drawn.
 */
val MoneyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Amount styling used where a figure needs to be scanned rather than read. */
val AmountTextStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    fontFeatureSettings = TABULAR_FIGURES
)
