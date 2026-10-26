package com.moneyplanner.ui.screens.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Remove
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
import com.moneyplanner.domain.calc.ForecastItemKind
import com.moneyplanner.ui.components.ColorAvatar
import com.moneyplanner.ui.components.GoalProgressBar
import com.moneyplanner.ui.components.IconWash
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.QuickActionChip
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
        // 20dp side margins per the framework's mobile grid.
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { GreetingRow(state, onOpenSearch) }

        item {
            AvailableNowCard(
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

        item { IncomeCard(state) }

        item { SpentSavedRow(state) }

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

        if (state.emergencyFund.targetAmount.isPositive) {
            item { EmergencyFundCard(state, onOpenEmergencyFund) }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                greeting(state),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                DateUtil.formatWeekdayDate(state.today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Default.Search, contentDescription = "Search")
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
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
private fun AvailableNowCard(
    state: DashboardState.Ready,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenEmis: () -> Unit,
    onOpenGoals: () -> Unit
) {
    val colors = MoneyTheme.colors
    val earmarked = state.breakdown.earmarkedForGoals
    val spendable = state.breakdown.unallocated

    SectionCard(contentPadding = 20.dp) {
        Text(
            if (earmarked.isPositive) "AVAILABLE TO SPEND" else "AVAILABLE NOW",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary
        )
        Spacer(Modifier.height(4.dp))
        MoneyText(
            money = spendable,
            style = MaterialTheme.typography.displaySmall,
            color = if (spendable.isNegative) {
                colors.negative
            } else {
                MaterialTheme.colorScheme.primary
            }
        )

        // The two components, shown only when they differ from the headline. With nothing
        // set aside the breakdown would just repeat the number above it.
        if (earmarked.isPositive) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.borderSubtle)
            Spacer(Modifier.height(12.dp))
            BreakdownRow(
                label = "Total bank and cash",
                money = state.availableBalance,
                dotColor = colors.textTertiary
            )
            Spacer(Modifier.height(8.dp))
            BreakdownRow(
                label = "Set aside for goals",
                money = -earmarked,
                dotColor = colors.negative,
                onClick = onOpenGoals
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionChip(Icons.Default.Remove, "Expense", onAddExpense)
            QuickActionChip(Icons.Default.Add, "Income", onAddIncome)
            QuickActionChip(Icons.Default.AccountBalance, "EMI", onOpenEmis)
            QuickActionChip(Icons.Default.Savings, "Save", onOpenGoals)
        }
    }
}

/** Overdue rows shown in full; the rest are counted, and every one is in the total line. */
private const val OVERDUE_ROWS = 3

/**
 * The two kinds of problem worth interrupting for.
 *
 * Deliberately quiet. Overdue money and unlabelled records are ordinary parts of running a
 * household, and a screen that shouts about them stops being opened. It uses the warning
 * surface rather than the error one, names the count so the size of the job is obvious
 * before tapping, and every row leads somewhere the problem can actually be fixed.
 */
@Composable
private fun AttentionCard(
    summary: AttentionSummary,
    onOpenUnassigned: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    val colors = MoneyTheme.colors

    SectionCard(containerColor = colors.warningContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "NEEDS ATTENTION",
                style = MaterialTheme.typography.labelMedium,
                color = colors.warning,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (summary.actionCount == 1) "1 thing" else "${summary.actionCount} things",
                style = MaterialTheme.typography.labelMedium,
                color = colors.warning
            )
        }

        summary.overdue.take(OVERDUE_ROWS).forEach { item ->
            Spacer(Modifier.height(12.dp))
            AttentionRow(
                title = item.title,
                // The day it was really due. A late balance is shown on today's date to
                // keep it in view, so without this it would read as due today.
                subtitle = "Due " + DateUtil.formatDayMonth(item.dueDate) +
                    item.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                money = item.amount,
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
            Spacer(Modifier.height(12.dp))
            AttentionRow(
                title = if (summary.unassignedCount == 1) {
                    "1 expense with no account"
                } else {
                    "${summary.unassignedCount} expenses with no account"
                },
                subtitle = "Recorded, but the app cannot say which account paid",
                money = summary.unassignedTotal,
                confidence = Confidence.ACCOUNT_UNKNOWN,
                // The list of exactly those expenses, from every month, rather than this
                // month's spending, which could not even show which ones they were.
                onClick = onOpenUnassigned
            )
        }
    }
}

@Composable
private fun AttentionRow(
    title: String,
    subtitle: String,
    money: Money,
    confidence: Confidence,
    onClick: () -> Unit
) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(3.dp))
            ConfidencePill(confidence)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        MoneyText(
            money = money,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary
        )
    }
}

/**
 * One line of the balance breakdown: a coloured dot, what it is, and how much.
 *
 * The dot carries no meaning on its own; the label beside it does. It is there to tie the
 * row to the figure above without another line of prose.
 */
@Composable
private fun BreakdownRow(
    label: String,
    money: Money,
    dotColor: Color,
    onClick: (() -> Unit)? = null
) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        MoneyText(
            money = money,
            style = MaterialTheme.typography.titleMedium,
            color = if (money.isNegative) colors.negative else colors.textPrimary,
            showSign = money.isNegative
        )
    }
}

@Composable
private fun IncomeCard(state: DashboardState.Ready) {
    val colors = MoneyTheme.colors
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconWash(Icons.Default.ArrowDownward, colors.positive)
            Spacer(Modifier.width(12.dp))
            Text(
                "Income",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        MoneyText(
            money = state.receivedThisMonth,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Received this month",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Spent and saved, side by side, because they are read as a pair. */
@Composable
private fun SpentSavedRow(state: DashboardState.Ready) {
    val colors = MoneyTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatTile(
            icon = Icons.Default.ArrowUpward,
            tint = colors.negative,
            label = "Spent",
            amount = state.spentThisMonth,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            icon = Icons.Default.Savings,
            tint = MaterialTheme.colorScheme.secondary,
            label = "Saved",
            amount = state.savedThisMonth,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    amount: Money,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconWash(icon, tint, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        MoneyText(money = amount, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * The month's outlook, led by a plain-language verdict.
 *
 * The status line is the point: a person should learn whether this month is comfortable
 * before they read a single figure.
 */
@Composable
private fun ForecastCard(state: DashboardState.Ready, onOpenForecast: () -> Unit) {
    val colors = MoneyTheme.colors
    val month = state.thisMonth

    val (statusLabel, statusDetail, statusColor, statusIcon) = when {
        month.isShortfall -> Quad(
            "Tight",
            "Your commitments come to more than you have coming in.",
            colors.negative,
            Icons.Default.WarningAmber
        )
        month.safeToSpend < month.estimatedEverydaySpend -> Quad(
            "Careful",
            "There is not much room left once your payments are made.",
            colors.warning,
            Icons.Default.WarningAmber
        )
        else -> Quad(
            "Comfortable",
            "Your buffer is healthy for this month.",
            colors.positive,
            Icons.Default.CheckCircle
        )
    }

    SectionCard(
        title = "${DateUtil.formatMonth(month.month).substringBefore(" ")} forecast",
        action = {
            IconButton(onClick = onOpenForecast) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open forecast")
            }
        }
    ) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp)) {
                Icon(statusIcon, contentDescription = null, tint = statusColor)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor
                    )
                    Text(
                        statusDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        val expected = month.openingBalance + month.totalInflow
        val committed = month.totalOutflow
        val fraction = if (expected.paise <= 0L) 0f
        else (committed.paise.toDouble() / expected.paise).toFloat().coerceIn(0f, 1f)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Expected in against payments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${(fraction * 100).toInt()}% committed",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        GoalProgressBar(
            fraction = fraction,
            color = statusColor,
            label = "${(fraction * 100).toInt()} percent of expected money is already committed"
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${IndianFormat.format(committed)} going out",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${IndianFormat.format(expected)} expected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MoneyTheme.colors.cardBorder)
        Spacer(Modifier.height(8.dp))
        SummaryRow(
            label = "Safe to spend",
            amount = month.safeToSpend,
            supporting = "After every commitment you have recorded",
            emphasise = true
        )
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
    val window = state.comingUp

    SectionCard(
        title = "Next ${window.days} days",
        // The total is the point of the section. A list alone tells somebody there are
        // things ahead; the figure tells them whether today has room in it. It names the
        // last day it counts, because "next 7 days" leaves open whether today is one.
        subtitle = if (window.committedOutflow.isPositive) {
            IndianFormat.format(window.committedOutflow) + " due by " +
                DateUtil.formatDayMonth(window.lastDay)
        } else {
            null
        },
        action = { TextButton(onClick = onOpenCalendar) { Text("View all") } }
    ) {
        state.upcoming.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MoneyTheme.colors.cardBorder
                )
            }
            ForecastItemRow(item, state)
        }

        // Said rather than left to be noticed. Without it the rows look like the whole
        // window, and a total larger than the rows add up to looks like a mistake.
        if (state.upcomingNotShown > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "and ${state.upcomingNotShown} more",
                style = MaterialTheme.typography.bodySmall,
                color = MoneyTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun ForecastItemRow(item: ForecastItem, state: DashboardState.Ready) {
    val colors = MoneyTheme.colors
    val tint = if (item.isInflow) colors.positive else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconWash(icon = iconFor(item.kind), tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                // How much the app trusts this figure, said out loud. A scheduled EMI and
                // a guessed electricity bill are not the same kind of number, and a row
                // that showed both the same way would be overstating one of them.
                ConfidencePill(
                    when {
                        item.isOverdue -> Confidence.OVERDUE
                        item.isEstimate -> Confidence.ESTIMATED
                        else -> Confidence.UPCOMING
                    }
                )
            }
            Text(
                DateUtil.relativeDayLabel(item.date, state.today) + " · " +
                    DateUtil.formatDayMonth(item.date),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
        MoneyText(
            money = if (item.isInflow) item.amount else -item.amount,
            style = MaterialTheme.typography.titleMedium,
            showSign = true,
            color = if (item.isInflow) colors.positive else colors.textPrimary
        )
    }
}

private fun iconFor(kind: ForecastItemKind) = when (kind) {
    ForecastItemKind.INCOME -> Icons.Default.ArrowDownward
    ForecastItemKind.PERSON_INCOMING, ForecastItemKind.PERSON_OUTGOING -> Icons.Default.Groups
    ForecastItemKind.EMI -> Icons.Default.AccountBalance
    ForecastItemKind.BILL -> Icons.Default.CalendarMonth
    ForecastItemKind.ANNUAL -> Icons.Default.CalendarMonth
    ForecastItemKind.CREDIT_CARD -> Icons.Default.CreditCard
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
private fun EmergencyFundCard(state: DashboardState.Ready, onOpen: () -> Unit) {
    val fund = state.emergencyFund
    val colors = MoneyTheme.colors

    SectionCard(
        title = "Emergency fund",
        subtitle = "${fund.monthsOfCover} months of essential expenses",
        action = {
            IconButton(onClick = onOpen) {
                Icon(Icons.Default.Shield, contentDescription = "Open emergency fund")
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            MoneyText(fund.currentAmount, style = MaterialTheme.typography.titleLarge)
            Text(
                "of ${IndianFormat.format(fund.targetAmount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        GoalProgressBar(
            fraction = fund.progressFraction,
            color = if (fund.isFunded) colors.positive else MaterialTheme.colorScheme.primary,
            label = "Emergency fund is ${fund.progressPercent} percent funded"
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (fund.isFunded) "Fully funded." else "${IndianFormat.format(fund.remainingAmount)} to go.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
