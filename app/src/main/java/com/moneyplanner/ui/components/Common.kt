package com.moneyplanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.ui.theme.Elevation
import com.moneyplanner.ui.theme.MoneyTheme

/**
 * Shared building blocks, styled to the Bharat Wealth Framework.
 *
 * Cards are pure white on the off-white page with a 24dp radius, a soft-blue hairline and
 * a navy-tinted shadow, so hierarchy is established by tonal shift rather than by weight.
 */

/**
 * A currency amount.
 *
 * The rupee symbol is rendered a step lighter than the digits, which keeps the eye on the
 * value rather than on the currency marker, and the whole thing is announced to a screen
 * reader as spoken money rather than as a run of grouped digits.
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    colorBySign: Boolean = false,
    showSign: Boolean = false,
    color: Color? = null,
    compact: Boolean = false
) {
    val moneyColors = MoneyTheme.colors
    val resolved = color ?: when {
        !colorBySign -> MaterialTheme.colorScheme.onSurface
        money.isPositive -> moneyColors.positive
        money.isNegative -> moneyColors.negative
        else -> moneyColors.neutral
    }
    val text = when {
        showSign -> IndianFormat.formatSigned(money)
        compact -> IndianFormat.formatCompact(money)
        else -> IndianFormat.format(money)
    }

    Text(
        text = text,
        modifier = modifier.semantics { contentDescription = spokenAmount(money) },
        style = style,
        color = resolved,
        maxLines = 1
    )
}

private fun spokenAmount(money: Money): String {
    val rupees = money.paise / 100
    val paise = kotlin.math.abs(money.paise % 100)
    val sign = if (money.isNegative) "minus " else ""
    val paiseText = if (paise > 0) " and $paise paise" else ""
    return "$sign${kotlin.math.abs(rupees)} rupees$paiseText"
}

/**
 * The standard elevated card: white, 24dp corners, soft-blue hairline, gentle navy shadow.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.level1),
        border = BorderStroke(1.dp, MoneyTheme.colors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            if (title != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    action?.invoke()
                }
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

/** A label and amount on one line, the workhorse of every summary in the app. */
@Composable
fun SummaryRow(
    label: String,
    amount: Money,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    colorBySign: Boolean = false,
    showSign: Boolean = false,
    emphasise: Boolean = false,
    amountColor: Color? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = if (emphasise) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = MaterialTheme.colorScheme.onSurface
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        MoneyText(
            money = amount,
            style = if (emphasise) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            colorBySign = colorBySign,
            showSign = showSign,
            color = amountColor
        )
    }
}

/**
 * A semantic icon in a 40dp circle washed at about ten percent of its own colour, which is
 * the framework's treatment for transaction and category rows.
 */
@Composable
fun ColorAvatar(
    icon: ImageVector,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val tint = parseColorOrDefault(colorHex, MaterialTheme.colorScheme.primary)
    IconWash(icon = icon, tint = tint, modifier = modifier, size = size)
}

/** The same treatment when the colour is already known rather than stored as hex. */
@Composable
fun IconWash(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** Initials avatar for people, so a list stays readable without any photos. */
@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

fun parseColorOrDefault(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: IllegalArgumentException) {
    fallback
}

/** Shown when a list is genuinely empty, with the action that fills it. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Loading" },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

/** A progress bar carrying its own accessible description of how far along it is. */
@Composable
fun GoalProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    label: String? = null,
    height: Dp = 8.dp
) {
    val safe = fraction.coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { safe },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {
                contentDescription = label ?: "${(safe * 100).toInt()} percent complete"
            },
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/**
 * A two-colour bar showing money in against money out, as used on the forecast header.
 * Both sides are drawn in one track so the split reads as a single quantity.
 */
@Composable
fun InOutBar(
    inflowFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    description: String? = null
) {
    val colors = MoneyTheme.colors
    val safe = inflowFraction.coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .clearAndSetSemantics {
                contentDescription = description
                    ?: "${(safe * 100).toInt()} percent of the month is money coming in"
            }
    ) {
        if (safe > 0f) {
            Box(
                modifier = Modifier
                    .weight(safe)
                    .fillMaxSize()
                    .background(colors.positive)
            )
        }
        if (safe < 1f) {
            Box(
                modifier = Modifier
                    .weight(1f - safe)
                    .fillMaxSize()
                    .background(colors.negative)
            )
        }
    }
}

/** A small status pill, used for due dates, paid markers and warnings. */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leadingIcon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

/**
 * A pill-shaped quick action, as used under the dashboard balance. Outlined rather than
 * filled so the balance above it stays the loudest thing on the screen.
 */
@Composable
fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Confirmation for anything that removes a financial record. The dialog names what will be
 * removed rather than asking a generic "are you sure".
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
