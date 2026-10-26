package com.moneyplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.ui.theme.MoneyTheme
import com.moneyplanner.ui.theme.PillLabelStyle
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
 * The visual rule is the app's: the past is solid and the future is light. A recorded fact
 * is a solid ink pill; anything still to come is outlined in lavender; an estimate's outline
 * is dashed and a projection's dotted, so the line itself says how firm the figure is. Late
 * money is the one solid warm pill. Every state also has its own icon and word, so none of
 * this depends on telling colours apart.
 */
@Composable
fun ConfidencePill(
    confidence: Confidence,
    modifier: Modifier = Modifier
) {
    val colors = MoneyTheme.colors

    val fill: Color
    val ink: Color
    val edge: Color?
    val dash: PillEdge
    val icon: ImageVector

    when (confidence) {
        Confidence.RECORDED -> {
            fill = colors.textPrimary; ink = colors.card
            edge = null; dash = PillEdge.SOLID; icon = Icons.Filled.CheckCircle
        }
        Confidence.UPCOMING -> {
            fill = colors.card; ink = colors.forecast
            edge = colors.forecast; dash = PillEdge.SOLID; icon = Icons.Filled.Schedule
        }
        Confidence.OVERDUE -> {
            fill = colors.warning; ink = colors.card
            edge = null; dash = PillEdge.SOLID; icon = Icons.Filled.PriorityHigh
        }
        Confidence.ESTIMATED -> {
            fill = colors.forecastContainer; ink = colors.forecast
            edge = colors.forecast; dash = PillEdge.DASHED; icon = Icons.Filled.Waves
        }
        Confidence.FORECAST -> {
            fill = colors.forecast.copy(alpha = 0.10f); ink = colors.forecast
            edge = colors.forecast; dash = PillEdge.DOTTED
            icon = Icons.AutoMirrored.Filled.TrendingUp
        }
        Confidence.ACCOUNT_UNKNOWN -> {
            fill = colors.neutralContainer; ink = colors.neutral
            edge = colors.neutral; dash = PillEdge.DASHED
            icon = Icons.AutoMirrored.Filled.HelpOutline
        }
    }

    Row(
        modifier = modifier
            .semantics { contentDescription = confidence.meaning }
            .background(fill, PillShape)
            .then(if (edge != null) Modifier.pillEdge(edge, dash) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(confidence.label, style = PillLabelStyle, color = ink, maxLines = 1)
    }
}

private val PillShape = RoundedCornerShape(50)

/** How firm a pill's outline is: solid for fact, dashed for an estimate, dotted for a projection. */
enum class PillEdge { SOLID, DASHED, DOTTED }

/**
 * A rounded outline that can be dashed or dotted, which a plain border cannot. Drawn inside
 * the bounds so the pill's size does not change with its edge.
 */
fun Modifier.pillEdge(color: Color, edge: PillEdge, width: Dp = 1.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val effect = when (edge) {
        PillEdge.SOLID -> null
        PillEdge.DASHED -> PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
        PillEdge.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 2.5.dp.toPx()))
    }
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius((size.height - stroke) / 2),
        style = Stroke(width = stroke, pathEffect = effect)
    )
}

/**
 * A dashed rounded outline for whole containers: a projected day on the calendar, an
 * estimated bill card. The same visual vocabulary as the pills, at container scale.
 */
fun Modifier.dashedOutline(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        )
    )
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
 * A pill holding "‹ October 2026 ›", with a marker on the right for the month being lived
 * through: a partial month's totals mean something different from a finished one's, and
 * somebody three months back should know where they are without counting taps. Given
 * [onJumpToCurrent], the marker becomes a "This month" button whenever another month is on
 * screen. The arrows stay full 48dp targets.
 */
@Composable
fun MonthSelector(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    currentMonth: YearMonth? = null,
    nextEnabled: Boolean = true,
    onJumpToCurrent: (() -> Unit)? = null
) {
    val colors = MoneyTheme.colors
    val isCurrent = currentMonth != null && month == currentMonth
    val title = DateUtil.formatMonth(month)
    val shape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(shape, elevation = 2.dp)
            .background(colors.card, shape)
            .border(1.dp, colors.cardBorder, shape)
            .padding(horizontal = 4.dp, vertical = 2.dp)
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
                contentDescription = "Previous month",
                tint = colors.textPrimary
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.clearAndSetSemantics { }
        )
        IconButton(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = colors.textPrimary
            )
        }
        Spacer(Modifier.weight(1f))

        val canJump = !isCurrent && currentMonth != null && onJumpToCurrent != null
        if (isCurrent || canJump) {
            Row(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .background(colors.accentSoft, shape)
                    .border(1.dp, colors.accent.copy(alpha = 0.6f), shape)
                    .then(
                        if (canJump) {
                            Modifier.clickable(onClickLabel = "Go to this month") {
                                onJumpToCurrent?.invoke()
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "This month",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onWarningContainer
                )
            }
        }
    }
}
