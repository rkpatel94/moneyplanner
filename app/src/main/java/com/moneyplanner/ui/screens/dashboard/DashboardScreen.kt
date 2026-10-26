package com.moneyplanner.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moneyplanner.ui.components.softShadow
import com.moneyplanner.ui.theme.PillLabelStyle
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.calc.ForecastItem
import com.moneyplanner.domain.calc.BudgetState
import com.moneyplanner.ui.components.ColorAvatar
import com.moneyplanner.ui.components.GoalProgressBar
import com.moneyplanner.ui.components.IconWash
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.domain.calc.AttentionSummary
import com.moneyplanner.ui.components.Confidence
import com.moneyplanner.ui.components.ConfidencePill
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.theme.MoneyTheme
import com.moneyplanner.ui.util.categoryIcon

/**
 * The screen the app opens to.
 *
 * Ordered by the question people ask first: how much have I got, what is still to come
 * this month, and what does that leave. The balance is the largest thing on the page and
 * the quick actions sit directly beneath it, so the two most common tasks — checking the
 * number and recording a spend — are both reachable without scrolling.
 */
@Composable
fun DashboardScreen(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenEmis: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenEmergencyFund: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenTransactions: () -> Unit,
    /** Every expense with no account or card, from every month, ready to be assigned. */
    onOpenUnassigned: () -> Unit,
    onOpenAffordability: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenBudgets: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    when (val current = viewModel.state.collectAsStateWithLifecycle().value) {
        is DashboardState.Loading -> LoadingState()
        is DashboardState.Ready -> DashboardContent(
            state = current,
            onAddExpense = onAddExpense,
            onAddIncome = onAddIncome,
            onOpenEmis = onOpenEmis,
            onOpenSearch = onOpenSearch,
            onOpenForecast = onOpenForecast,
            onOpenPeople = onOpenPeople,
            onOpenCards = onOpenCards,
            onOpenGoals = onOpenGoals,
            onOpenEmergencyFund = onOpenEmergencyFund,
            onOpenCalendar = onOpenCalendar,
            onOpenTransactions = onOpenTransactions,
            onOpenUnassigned = onOpenUnassigned,
            onOpenAffordability = onOpenAffordability,
            onOpenAssistant = onOpenAssistant,
            onOpenBudgets = onOpenBudgets
        )
    }
}

@Composable
private fun DashboardContent(
    state: DashboardState.Ready,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenEmis: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenEmergencyFund: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenUnassigned: () -> Unit,
    onOpenAffordability: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenBudgets: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        // 20dp side margins per the mobile grid, and room at the end so the last card
        // clears the floating add button.
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { GreetingRow(state, onOpenSearch) }

        // The promise, stated where it is seen every day rather than buried in Settings.
        item { PrivacyChip() }

        item {
            HeroBalanceCard(
                state = state,
                onAddExpense = onAddExpense,
                onAddIncome = onAddIncome,
                onOpenEmis = onOpenEmis,
                onOpenGoals = onOpenGoals
            )
        }

        // Order is the argument this screen makes: what you have, what is wrong, what is
        // about to leave, and only then how the month is expected to end. Anything the
        // user cannot act on today waits below that.
        if (!state.attention.isEmpty) {
            item {
                AttentionCard(
                    summary = state.attention,
                    onOpenUnassigned = onOpenUnassigned,
                    onOpenCalendar = onOpenCalendar
                )
            }
        }

        // Held back until there is something recorded. A brand-new install has no balance
        // yet, so it sits below the comfort floor from the first day, and the card used to
        // warn that the balance "dips low" directly above a forecast calling the month
        // comfortable: scolding somebody for not having started yet.
        if (state.hasAnyData) {
            state.runwayHeadline?.let { headline ->
                item { RunwayWarningCard(headline, state, onOpenForecast) }
            }
        }

        if (!state.hasAnyData) {
            item { GettingStartedCard(onAddExpense) }
        }

        if (!state.comingUp.isEmpty) {
            item { ComingUpCard(state, onOpenCalendar) }
        }

        item { ForecastCard(state, onOpenForecast) }

        // The bento: small facts read side by side, two to a row.
        val tiles = buildList<@Composable (Modifier) -> Unit> {
            add { SpentTile(state, onOpenTransactions, it) }
            if (state.emergencyFund.targetAmount.isPositive) {
                add { EmergencyFundTile(state, onOpenEmergencyFund, it) }
            }
            add { IncomeTile(state, onAddIncome, it) }
            add { SavedTile(state, onOpenGoals, it) }
        }
        items(tiles.chunked(2)) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (state.receivable.isPositive || state.payable.isPositive) {
            item { PeopleCard(state, onOpenPeople) }
        }

        if (state.creditCardOutstanding.isPositive) {
            item { CardsCard(state, onOpenCards) }
        }

        if (state.insights.isNotEmpty()) {
            item { InsightsCard(state) }
        }

        if (state.budgets.isNotEmpty()) {
            item { BudgetsCard(state, onOpenBudgets) }
        }

        if (state.recentExpenses.isNotEmpty()) {
            item { RecentSpendingCard(state, onOpenTransactions) }
        }

        item {
            PlanAheadCard(
                onOpenAffordability, onOpenAssistant, onOpenBudgets,
                onOpenGoals, onOpenCalendar
            )
        }
    }
}

@Composable
private fun GreetingRow(state: DashboardState.Ready, onOpenSearch: () -> Unit) {
    val colors = MoneyTheme.colors
    val initial = state.userName.trim().firstOrNull()?.uppercaseChar()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                DateUtil.formatWeekdayDate(state.today),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary
            )
            Text(
                greeting(state),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.display
            )
        }
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.textSecondary)
        }
        Spacer(Modifier.width(4.dp))
        // A monogram rather than a photo: there is no account, so there is nothing to fetch
        // a picture from, and a letter is enough to make the screen feel like theirs.
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (initial != null) {
                Text(
                    initial.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * A greeting that matches the time of day, falling back to a neutral heading before the
 * user has told the app their name.
 */
private fun greeting(state: DashboardState.Ready): String {
    val partOfDay = when (java.time.LocalTime.now().hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val name = state.userName.trim().split(" ").firstOrNull().orEmpty()
    return if (name.isEmpty()) partOfDay else "$partOfDay, $name"
}

@Composable
private fun PrivacyChip() {
    val colors = MoneyTheme.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .background(colors.sunken, shape)
            .border(1.dp, colors.cardBorder, shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Works offline · Everything stays on this phone",
            style = PillLabelStyle.copy(letterSpacing = 0.03.em),
            color = colors.textSecondary
        )
    }
}

/**
 * The hero, and the app's central claim.
 *
 * The headline is what is left after money already promised to a goal is set aside, not the
 * bank total. Showing the total invites somebody to spend savings they had mentally
 * committed; hiding the savings makes it look like money went missing. Both figures appear,
 * with the spendable one at display size, because both are true and only one of them
 * answers "can I buy this today".
 */
@Composable
private fun HeroBalanceCard(
    state: DashboardState.Ready,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenEmis: () -> Unit,
    onOpenGoals: () -> Unit
) {
    val colors = MoneyTheme.colors
    val earmarked = state.breakdown.earmarkedForGoals
    val spendable = state.breakdown.unallocated
    val total = state.availableBalance
    val shape = MaterialTheme.shapes.extraLarge
    var explaining by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(shape)
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.cardBorder, shape)
    ) {
        HorizonArcs(Modifier.align(Alignment.TopEnd))

        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (earmarked.isPositive) "AVAILABLE TO SPEND" else "AVAILABLE NOW",
                    style = PillLabelStyle,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { explaining = !explaining },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = if (explaining) {
                            "Hide what this figure means"
                        } else {
                            "What this figure means"
                        },
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            MoneyText(
                money = spendable,
                style = MaterialTheme.typography.displayLarge,
                color = if (spendable.isNegative) colors.negative else colors.display
            )
            if (explaining) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (earmarked.isPositive) {
                        "Your bank and cash, minus what you have set aside for goals. The " +
                            "money in goals is still yours; it is just not counted as free."
                    } else {
                        "Everything in your bank accounts and cash, from what you have recorded."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            // The two components, shown only when they differ from the headline. With nothing
            // set aside the breakdown would just repeat the number above it.
            if (earmarked.isPositive) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = colors.cardBorder)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Total bank & cash",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                        MoneyText(
                            money = total,
                            style = HeroSplitAmount,
                            color = colors.textPrimary
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClickLabel = "Open savings goals", onClick = onOpenGoals)
                    ) {
                        Text(
                            "Set aside for goals",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                        MoneyText(
                            money = -earmarked,
                            style = HeroSplitAmount,
                            showSign = true,
                            color = colors.warning
                        )
                    }
                }

                val free = if (total.isPositive && !spendable.isNegative) {
                    (spendable.paise.toDouble() / total.paise).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                Spacer(Modifier.height(14.dp))
                SplitTrack(free)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    LegendDot(colors.display, "${(free * 100).roundToInt()}% free to spend")
                    Spacer(Modifier.weight(1f))
                    LegendDot(colors.accent, "${100 - (free * 100).roundToInt()}% set aside")
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionTile(Icons.Default.AddCircleOutline, "Expense", onAddExpense, Modifier.weight(1f))
                QuickActionTile(Icons.Default.Payments, "Income", onAddIncome, Modifier.weight(1f))
                QuickActionTile(Icons.Default.EventRepeat, "Pay EMI", onOpenEmis, Modifier.weight(1f))
                QuickActionTile(Icons.Default.Savings, "Set aside", onOpenGoals, Modifier.weight(1f))
            }
        }
    }
}

/** The balance split's figures: Inter, so the two columns align digit for digit. */
private val HeroSplitAmount
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp)

/**
 * The horizon motif: two soft arcs rising past the card's corner, the line between what has
 * happened and what is still to come. Decoration only, so it is invisible to accessibility.
 */
@Composable
private fun HorizonArcs(modifier: Modifier = Modifier) {
    val colors = MoneyTheme.colors
    Canvas(
        modifier = modifier
            .offset(x = 70.dp, y = (-90).dp)
            .size(240.dp)
    ) {
        val outer = 28.dp.toPx()
        drawCircle(
            color = colors.forecastContainer.copy(alpha = 0.7f),
            radius = size.minDimension / 2 - outer / 2,
            style = Stroke(width = outer)
        )
        val inner = 14.dp.toPx()
        drawCircle(
            color = colors.accentSoft,
            radius = size.minDimension / 2 - outer - inner,
            style = Stroke(width = inner)
        )
    }
}

/** Free money against money set aside, as two segments of one track. */
@Composable
private fun SplitTrack(free: Float) {
    val colors = MoneyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(colors.sunken, RoundedCornerShape(50))
            .padding(2.dp)
            .clearAndSetSemantics {
                contentDescription = "${(free * 100).roundToInt()} percent free to spend, " +
                    "the rest set aside for goals"
            },
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (free > 0f) {
            Box(
                Modifier
                    .weight(free)
                    .fillMaxHeight()
                    .background(colors.display, RoundedCornerShape(50))
            )
        }
        if (free < 1f) {
            Box(
                Modifier
                    .weight(1f - free)
                    .fillMaxHeight()
                    .background(colors.accent, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = PillLabelStyle, color = MoneyTheme.colors.textTertiary)
    }
}

/** One of the four shortcuts under the balance: a soft well with an icon and a word. */
@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MoneyTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunken)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

/** Overdue rows shown in full; the rest are counted, and every one is in the total line. */
private const val OVERDUE_ROWS = 3

/**
 * The two kinds of problem worth interrupting for.
 *
 * Deliberately quiet. Overdue money and unlabelled records are ordinary parts of running a
 * household, and a screen that shouts about them stops being opened. It uses the soft
 * marigold surface rather than the error one, names the count so the size of the job is
 * obvious before tapping, and every row leads somewhere the problem can actually be fixed.
 */
@Composable
private fun AttentionCard(
    summary: AttentionSummary,
    onOpenUnassigned: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    val colors = MoneyTheme.colors

    SectionCard(containerColor = colors.accentSoft, borderColor = colors.warningBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Needs attention",
                style = MaterialTheme.typography.titleMedium,
                color = colors.warning,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (summary.actionCount == 1) "1 ACTION" else "${summary.actionCount} ACTIONS",
                style = PillLabelStyle,
                color = colors.warning
            )
        }

        summary.overdue.take(OVERDUE_ROWS).forEach { item ->
            Spacer(Modifier.height(10.dp))
            AttentionRow(
                leading = item.title.trim().firstOrNull()?.uppercase() ?: "!",
                title = item.title + " · " + IndianFormat.format(item.amount),
                // The day it was really due. A late balance is shown on today's date to
                // keep it in view, so without this it would read as due today. A fixed
                // bill's own subtitle is just "Due", which this line already says.
                subtitle = "Due " + DateUtil.formatDayMonth(item.dueDate) +
                    item.subtitle.takeIf { it.isNotBlank() && !it.equals("Due", ignoreCase = true) }
                        ?.let { " · $it" }.orEmpty(),
                confidence = Confidence.OVERDUE,
                onClick = onOpenCalendar
            )
        }

        // Every late rupee appears in a figure, even the rows cut for space, and the Next 7
        // days total deliberately leaves late items out, so this line is their only total.
        // Money to pay and money to receive are kept apart: one does not cancel the other.
        if (summary.overdue.size > 1) {
            val hidden = summary.overdue.size - OVERDUE_ROWS
            val totals = listOfNotNull(
                summary.overdueToPay.takeIf { it.isPositive }
                    ?.let { IndianFormat.format(it) + " to pay" },
                summary.overdueToReceive.takeIf { it.isPositive }
                    ?.let { IndianFormat.format(it) + " to receive" }
            ).joinToString(" and ")
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    if (hidden > 0) append("and $hidden more overdue. ")
                    append("Late in all: ")
                    append(totals)
                    append(".")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.warning
            )
        }

        if (summary.hasUnassigned) {
            Spacer(Modifier.height(10.dp))
            AttentionRow(
                leading = "?",
                title = if (summary.unassignedCount == 1) {
                    "1 expense with no account"
                } else {
                    "${summary.unassignedCount} expenses with no account"
                },
                subtitle = IndianFormat.format(summary.unassignedTotal) + " in total",
                confidence = Confidence.ACCOUNT_UNKNOWN,
                // The list of exactly those expenses, from every month, rather than this
                // month's spending, which could not even show which ones they were.
                onClick = onOpenUnassigned,
                pillBelow = true
            )
        }
    }
}

/**
 * One problem, as a white inset on the attention card, with its confidence on the right, or
 * under the text when the pill is too long to sit beside it without squeezing the title.
 */
@Composable
private fun AttentionRow(
    leading: String,
    title: String,
    subtitle: String,
    confidence: Confidence,
    onClick: () -> Unit,
    pillBelow: Boolean = false
) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.accentSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(leading, style = MaterialTheme.typography.titleSmall, color = colors.warning)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2
            )
            if (pillBelow) {
                Spacer(Modifier.height(6.dp))
                ConfidencePill(confidence)
            }
        }
        if (!pillBelow) {
            Spacer(Modifier.width(8.dp))
            ConfidencePill(confidence)
        }
    }
}

/**
 * A small fact in a bento tile: an icon chip, a label, the figure, and a line of context.
 * Tiles in one row stretch to the tallest, so the grid stays even.
 */
@Composable
private fun BentoTile(
    icon: ImageVector,
    iconTint: Color,
    iconWell: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    footer: @Composable () -> Unit,
    value: @Composable () -> Unit
) {
    SectionCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconWell, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoneyTheme.colors.textSecondary)
        value()
        Spacer(Modifier.height(4.dp))
        footer()
    }
}

private val TileFigure
    @Composable get() = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, lineHeight = 34.sp)

@Composable
private fun SpentTile(state: DashboardState.Ready, onOpen: () -> Unit, modifier: Modifier) {
    val colors = MoneyTheme.colors
    val comparison = state.spentComparison
    val change = comparison.changePercent

    BentoTile(
        icon = Icons.Default.CreditCard,
        iconTint = colors.forecast,
        iconWell = colors.forecastContainer,
        label = "Spent this month",
        onClick = onOpen,
        modifier = modifier,
        footer = {
            if (change != null && change != 0) {
                // Measured to the same day of last month, never against its whole total,
                // so a half-finished month is not congratulated for being unfinished.
                val sameDay = DateUtil.formatDayMonth(
                    comparison.previousMonth.atDay(comparison.comparableDay)
                )
                val less = change < 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (less) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = if (less) colors.positive else colors.negative,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${kotlin.math.abs(change)}% ${if (less) "less" else "more"} than by $sameDay",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (less) colors.positive else colors.negative
                    )
                }
            } else {
                Text("So far this month", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
            }
        }
    ) {
        MoneyText(state.spentThisMonth, style = TileFigure, color = colors.display)
    }
}

@Composable
private fun EmergencyFundTile(state: DashboardState.Ready, onOpen: () -> Unit, modifier: Modifier) {
    val colors = MoneyTheme.colors
    val fund = state.emergencyFund

    BentoTile(
        icon = Icons.Default.Shield,
        iconTint = colors.warning,
        iconWell = colors.accentSoft,
        label = "Emergency fund",
        onClick = onOpen,
        modifier = modifier,
        trailing = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                CircularProgressIndicator(
                    progress = { fund.progressFraction },
                    modifier = Modifier.fillMaxSize(),
                    color = colors.accent,
                    trackColor = colors.sunken,
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp
                )
                Text("${fund.progressPercent}%", style = PillLabelStyle.copy(letterSpacing = 0.em), color = colors.textPrimary)
            }
        },
        footer = {
            Text(
                "Target: ${fund.monthsOfCover} months",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
        }
    ) {
        Text(
            String.format(java.util.Locale.ENGLISH, "%.1f mo", fund.monthsCovered),
            style = TileFigure,
            color = colors.display,
            modifier = Modifier.semantics {
                contentDescription = String.format(
                    java.util.Locale.ENGLISH, "%.1f months of essential expenses covered", fund.monthsCovered
                )
            }
        )
    }
}

@Composable
private fun IncomeTile(state: DashboardState.Ready, onOpen: () -> Unit, modifier: Modifier) {
    val colors = MoneyTheme.colors
    BentoTile(
        icon = Icons.Default.ArrowDownward,
        iconTint = colors.positive,
        iconWell = colors.positiveContainer,
        label = "Income this month",
        onClick = onOpen,
        modifier = modifier,
        footer = {
            Text("Received so far", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
        }
    ) {
        MoneyText(state.receivedThisMonth, style = TileFigure, color = colors.display)
    }
}

@Composable
private fun SavedTile(state: DashboardState.Ready, onOpen: () -> Unit, modifier: Modifier) {
    val colors = MoneyTheme.colors
    BentoTile(
        icon = Icons.Default.Savings,
        iconTint = colors.warning,
        iconWell = colors.accentSoft,
        label = "Saved this month",
        onClick = onOpen,
        modifier = modifier,
        footer = {
            Text("Set aside for goals", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
        }
    ) {
        MoneyText(state.savedThisMonth, style = TileFigure, color = colors.display)
    }
}

/**
 * The month's outlook, led by a plain-language verdict.
 *
 * The verdict is the point: a person should learn whether this month is comfortable before
 * they read a single figure. The bar is lavender because everything on it is projected.
 */
@Composable
private fun ForecastCard(state: DashboardState.Ready, onOpenForecast: () -> Unit) {
    val colors = MoneyTheme.colors
    val month = state.thisMonth

    val (statusLabel, statusColor, statusWell, statusIcon) = when {
        month.isShortfall -> Quad(
            "Tight", colors.negative, colors.negativeContainer, Icons.Default.WarningAmber
        )
        month.safeToSpend < month.estimatedEverydaySpend -> Quad(
            "Careful", colors.warning, colors.accentSoft, Icons.Default.WarningAmber
        )
        else -> Quad("Comfortable", colors.positive, colors.positiveContainer, Icons.Default.Check)
    }

    val expected = month.openingBalance + month.totalInflow
    val committed = month.totalOutflow
    val fraction = if (expected.paise <= 0L) 0f
    else (committed.paise.toDouble() / expected.paise).toFloat().coerceIn(0f, 1f)

    SectionCard(onClick = onOpenForecast) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${DateUtil.formatMonth(month.month).substringBefore(" ")} forecast",
                style = MaterialTheme.typography.titleLarge,
                color = colors.display,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .background(statusWell, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(statusLabel, style = MaterialTheme.typography.labelLarge, color = statusColor)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${(fraction * 100).toInt()}% of expected money is already committed",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary
        )
        Text(
            buildAnnotatedString {
                append("Safe to spend ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.display)) {
                    append(IndianFormat.format(month.safeToSpend))
                }
                append(" after every commitment you have recorded")
                // Named, so the figure reconciles with the money shown going out and
                // expected just below, which still includes the goal money.
                if (month.setAsideForGoals.isPositive) {
                    append(", with ${IndianFormat.format(month.setAsideForGoals)} kept aside for your goals")
                }
                append(".")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Spacer(Modifier.height(12.dp))
        GoalProgressBar(
            fraction = fraction,
            color = colors.forecast,
            label = "${(fraction * 100).toInt()} percent of expected money is already committed"
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "${IndianFormat.format(committed)} going out",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${IndianFormat.format(expected)} expected",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/**
 * What is about to leave, and how much of it.
 *
 * The title, the total and the rows all describe one window, so the figure can be checked
 * against the list beneath it rather than quietly covering a different stretch of days.
 */
@Composable
private fun ComingUpCard(state: DashboardState.Ready, onOpenCalendar: () -> Unit) {
    val colors = MoneyTheme.colors
    val window = state.comingUp

    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Next ${window.days} days",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.display
                )
                // The total is the point of the section. A list alone tells somebody there
                // are things ahead; the figure tells them whether today has room in it. It
                // names the last day it counts, because "next 7 days" leaves open whether
                // today is one.
                if (window.committedOutflow.isPositive) {
                    Text(
                        IndianFormat.format(window.committedOutflow) + " due by " +
                            DateUtil.formatDayMonth(window.lastDay),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
            TextButton(onClick = onOpenCalendar) {
                Text("View in Calendar", style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        state.upcoming.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = colors.borderSubtle)
            ComingUpRow(item)
        }

        // Said rather than left to be noticed. Without it the rows look like the whole
        // window, and a total larger than the rows add up to looks like a mistake.
        if (state.upcomingNotShown > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "and ${state.upcomingNotShown} more",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun ComingUpRow(item: ForecastItem) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1
            )
            Text(
                DateUtil.formatDayMonth(item.date) +
                    item.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // An estimate says so in its figure too, not only in its pill.
                if (item.isEstimate) {
                    Text("~", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                }
                MoneyText(
                    money = item.amount,
                    style = MaterialTheme.typography.titleMedium,
                    showSign = item.isInflow,
                    color = if (item.isInflow) colors.positive else colors.textPrimary
                )
            }
            Spacer(Modifier.height(4.dp))
            // How much the app trusts this figure, said out loud. A scheduled EMI and a
            // guessed electricity bill are not the same kind of number.
            ConfidencePill(
                when {
                    item.isOverdue -> Confidence.OVERDUE
                    item.isEstimate -> Confidence.ESTIMATED
                    else -> Confidence.UPCOMING
                }
            )
        }
    }
}

@Composable
private fun PeopleCard(state: DashboardState.Ready, onOpenPeople: () -> Unit) {
    SectionCard(
        title = "People",
        action = {
            IconButton(onClick = onOpenPeople) {
                Icon(Icons.Default.Groups, contentDescription = "Open people")
            }
        }
    ) {
        if (state.receivable.isPositive) {
            SummaryRow("Owed to you", state.receivable, colorBySign = true, showSign = true)
        }
        if (state.payable.isPositive) {
            SummaryRow("You owe", -state.payable, colorBySign = true, showSign = true)
        }
    }
}

@Composable
private fun CardsCard(state: DashboardState.Ready, onOpenCards: () -> Unit) {
    SectionCard(
        title = "Credit cards",
        action = {
            IconButton(onClick = onOpenCards) {
                Icon(Icons.Default.CreditCard, contentDescription = "Open cards")
            }
        }
    ) {
        SummaryRow(
            label = "Total outstanding",
            amount = state.creditCardOutstanding,
            supporting = "Leaves your account when the bill is paid"
        )
    }
}

@Composable
private fun RecentSpendingCard(state: DashboardState.Ready, onOpenTransactions: () -> Unit) {
    SectionCard(
        title = "Recent spending",
        action = { TextButton(onClick = onOpenTransactions) { Text("See all") } }
    ) {
        state.recentExpenses.forEachIndexed { index, expense ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MoneyTheme.colors.cardBorder
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorAvatar(
                    icon = categoryIcon(state.categoryNames[expense.categoryId].orEmpty()),
                    colorHex = state.categoryColors[expense.categoryId] ?: "#FF4555B7"
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        expense.description.ifBlank {
                            state.categoryNames[expense.categoryId] ?: "Expense"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                    Text(
                        DateUtil.relativeDayLabel(expense.date, state.today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MoneyText(
                    money = expense.amount,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun GettingStartedCard(onAddExpense: () -> Unit) {
    SectionCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            "Start with what you know",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Add your salary, your EMIs and your regular bills, and this screen will " +
                "start showing what you will actually have left each month.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onAddExpense) { Text("Add your first entry") }
    }
}

@Composable
private fun PlanAheadCard(
    onOpenAffordability: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    SectionCard(title = "Plan ahead") {
        PlanRow(
            Icons.AutoMirrored.Filled.Chat,
            "Ask about your money",
            "Answered on your phone from your own records",
            onOpenAssistant
        )
        PlanRow(
            Icons.Default.ShoppingCart,
            "Can I afford it?",
            "Check a purchase against what is coming",
            onOpenAffordability
        )
        PlanRow(
            Icons.Default.PieChart,
            "Budgets",
            "Set a limit and see if you are on pace",
            onOpenBudgets
        )
        PlanRow(
            Icons.Default.Savings,
            "Savings goals",
            "See what each goal needs every month",
            onOpenGoals
        )
        PlanRow(
            Icons.Default.CalendarMonth,
            "Money calendar",
            "Every dated payment in one place",
            onOpenCalendar
        )
    }
}

@Composable
private fun PlanRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconWash(icon, MaterialTheme.colorScheme.primary, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * When money gets tight.
 *
 * A month-end balance can look healthy while rent, an EMI and a card bill all land in the
 * same week before the salary arrives. This says the date, because a date is something a
 * person can actually act on.
 */
@Composable
private fun RunwayWarningCard(
    headline: String,
    state: DashboardState.Ready,
    onOpenForecast: () -> Unit
) {
    val colors = MoneyTheme.colors
    val severe = state.runway.hasShortfall
    val tint = if (severe) colors.negative else colors.onWarningContainer

    SectionCard(
        containerColor = if (severe) colors.negativeContainer else colors.warningContainer,
        modifier = Modifier.clickable(onClick = onOpenForecast)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, color = tint)
                state.runway.lowestPoint?.let { low ->
                    Text(
                        "Lowest point " + IndianFormat.format(low.closingBalance) + " on " +
                            DateUtil.formatDayMonth(low.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = tint
                    )
                }
            }
        }
    }
}

/** Budgets needing attention lead; when all are fine the rest are just summarised. */
@Composable
private fun BudgetsCard(state: DashboardState.Ready, onOpenBudgets: () -> Unit) {
    val colors = MoneyTheme.colors
    val attention = state.budgetsNeedingAttention

    SectionCard(
        title = "Budgets",
        action = {
            IconButton(onClick = onOpenBudgets) {
                Icon(Icons.Default.PieChart, contentDescription = "Open budgets")
            }
        }
    ) {
        val shown = attention.ifEmpty { state.budgets.take(2) }
        shown.forEach { status ->
            val tint = when (status.state) {
                BudgetState.OVER -> colors.negative
                BudgetState.ON_TRACK -> colors.positive
                else -> colors.warning
            }
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(status.categoryName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        status.summary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tint
                    )
                }
                Spacer(Modifier.height(6.dp))
                GoalProgressBar(
                    fraction = status.usedFraction,
                    color = tint,
                    label = status.categoryName + " is " + status.usedPercent + " percent used"
                )
            }
        }
        if (attention.isEmpty() && state.budgets.size > 2) {
            Text(
                "All " + state.budgets.size + " budgets are on track.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Things worth noticing.
 *
 * The app has held months of history and never once remarked on it. These are the
 * observations a careful friend would make looking at the same records — each one derived
 * arithmetic with a stated threshold, never a judgement about how the money should be spent.
 */
@Composable
private fun InsightsCard(state: DashboardState.Ready) {
    val colors = MoneyTheme.colors

    SectionCard(title = "Worth noticing") {
        state.insights.forEachIndexed { index, insight ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = colors.cardBorder
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                IconWash(
                    icon = if (insight.isPositive) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.WarningAmber
                    },
                    tint = if (insight.isPositive) colors.positive else colors.warning,
                    size = 34.dp
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(insight.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        insight.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
