package com.moneyplanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.ui.theme.MoneyTheme
import java.time.YearMonth

/**
 * How certain the app is about a figure.
 *
 * The whole product rests on not pretending to know things it does not, so every amount
 * that is not a recorded fact carries one of these. Each has an icon and a word as well as
 * a colour, because a state that reads only as "the amber one" is invisible to anyone who
 * cannot separate amber from green, and unreadable in bright sunlight.
 */
enum class Confidence(val label: String, val meaning: String) {
    /** Entered by the user or read off a bank message. A fact. */
    RECORDED("RECORDED", "Directly entered or taken from a bank message"),

    /** Scheduled, dated, and not yet due. */
    UPCOMING("UPCOMING", "Scheduled and not yet due"),

    /** Scheduled, dated, and the date has passed. */
    OVERDUE("OVERDUE", "Past its due date"),

    /** A figure the user gave as an approximation. */
    ESTIMATED("ESTIMATED", "An amount you estimated"),

    /** Derived from history rather than stated by anybody. */
    FORECAST("FORECAST", "Projected from your recorded history"),

    /** Real money, but the app does not know which account it moved through. */
    ACCOUNT_UNKNOWN("ACCOUNT UNKNOWN", "Recorded before accounts were set up")
}

/**
 * The confidence marker itself.
 *
 * Uppercase and bordered so it reads as a label rather than as decoration, and small
 * enough to sit inline beside a row title without competing with the amount.
 */
@Composable
fun ConfidencePill(
    confidence: Confidence,
    modifier: Modifier = Modifier
) {
    val colors = MoneyTheme.colors
    val scheme = MaterialTheme.colorScheme

    val fill: Color
    val ink: Color
    val edge: Color
    val icon: ImageVector

    when (confidence) {
        Confidence.RECORDED -> {
            fill = colors.positiveContainer; ink = colors.positive
            edge = colors.positiveBorder; icon = Icons.Filled.Check
        }
        Confidence.UPCOMING -> {
            fill = colors.warningContainer; ink = colors.warning
            edge = colors.warningBorder; icon = Icons.Filled.Schedule
        }
        Confidence.OVERDUE -> {
            fill = colors.negativeContainer; ink = colors.negative
            edge = colors.negativeBorder; icon = Icons.Filled.PriorityHigh
        }
        Confidence.ESTIMATED -> {
            fill = colors.neutralContainer; ink = colors.neutral
            edge = colors.neutralBorder; icon = Icons.Filled.Timeline
        }
        Confidence.FORECAST -> {
            fill = scheme.primaryContainer; ink = scheme.primary
            edge = scheme.primary.copy(alpha = 0.35f)
            icon = Icons.AutoMirrored.Filled.TrendingUp
        }
        Confidence.ACCOUNT_UNKNOWN -> {
            fill = colors.neutralContainer; ink = colors.neutral
            edge = colors.neutralBorder; icon = Icons.Filled.HelpOutline
        }
    }

    Surface(
        modifier = modifier.semantics { contentDescription = confidence.meaning },
        shape = RoundedCornerShape(6.dp),
        color = fill,
        border = BorderStroke(1.dp, edge)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                confidence.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = ink
            )
        }
    }
}

/**
 * Progress with both figures spelled out.
 *
 * A bare percentage is the kind of number that feels informative and answers nothing: 74%
 * of what, and how much is left? The caption above the track carries the two amounts that
 * actually decide whether somebody can spend today, and the percentage trails them.
 */
@Composable
fun AmountProgressBar(
    spent: Money,
    total: Money,
    modifier: Modifier = Modifier,
    label: String? = null,
    track: Color? = null
) {
    val colors = MoneyTheme.colors
    val fraction = if (total.paise <= 0L) {
        0f
    } else {
        (spent.paise.toFloat() / total.paise.toFloat()).coerceIn(0f, 1f)
    }
    val percent = (fraction * 100).toInt()
    val bar = track ?: MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                IndianFormat.format(spent) + " / " + IndianFormat.format(total) +
                    " · " + percent + "%",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(colors.cardElevated, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .background(bar, RoundedCornerShape(4.dp))
            )
        }
    }
}

/**
 * Two tracks, drawn to one shared scale.
 *
 * Used where the honest answer needs a reference point: this month against the same day
 * last month, or spending against its budget. Both bars are measured against the larger of
 * the two figures, so the shorter bar is shorter because the number is smaller and not
 * because each was scaled to its own maximum.
 */
@Composable
fun ComparisonBars(
    topLabel: String,
    topValue: Money,
    bottomLabel: String,
    bottomValue: Money,
    modifier: Modifier = Modifier
) {
    val colors = MoneyTheme.colors
    val ceiling = maxOf(topValue.paise, bottomValue.paise, 1L)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ComparisonTrack(
            label = topLabel,
            value = topValue,
            fraction = (topValue.paise.toFloat() / ceiling).coerceIn(0f, 1f),
            color = MaterialTheme.colorScheme.primary,
            trackColor = colors.cardElevated
        )
        ComparisonTrack(
            label = bottomLabel,
            value = bottomValue,
            fraction = (bottomValue.paise.toFloat() / ceiling).coerceIn(0f, 1f),
            color = colors.textTertiary,
            trackColor = colors.cardElevated
        )
    }
}

@Composable
private fun ComparisonTrack(
    label: String,
    value: Money,
    fraction: Float,
    color: Color,
    trackColor: Color
) {
    val colors = MoneyTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            MoneyText(
                money = value,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(trackColor, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
    }
}

/**
 * Moving between months.
 *
 * The arrows are full 48dp targets, and the current month is named rather than implied, so
 * somebody three months back knows where they are without counting taps. When the month on
 * screen is the one being lived through, it says so: a partial month's totals mean
 * something different from a finished one's.
 */
@Composable
fun MonthSelector(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    currentMonth: YearMonth? = null,
    nextEnabled: Boolean = true
) {
    val colors = MoneyTheme.colors
    val isCurrent = currentMonth != null && month == currentMonth
    val title = DateUtil.formatMonth(month)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (isCurrent) {
                    "Showing $title, the month currently in progress"
                } else {
                    "Showing $title"
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month"
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary
            )
            if (isCurrent) {
                Text(
                    "This month",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
        }

        IconButton(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month"
            )
        }
    }
}
