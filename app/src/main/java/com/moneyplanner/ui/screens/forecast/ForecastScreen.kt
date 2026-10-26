package com.moneyplanner.ui.screens.forecast

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.moneyplanner.ui.theme.PillLabelStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.ForecastItemKind
import com.moneyplanner.domain.calc.MonthForecast
import com.moneyplanner.domain.calc.ShortfallExplainer
import com.moneyplanner.domain.calc.ShortfallExplanation
import com.moneyplanner.ui.components.Confidence
import com.moneyplanner.ui.components.ConfidencePill
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.InOutBar
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.theme.MoneyTheme
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import com.moneyplanner.ui.nav.TabFocus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class ForecastViewModel @Inject constructor(
    snapshotRepository: SnapshotRepository,
    settingsStore: SettingsStore,
    tabFocus: TabFocus,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    private val selectedIndex = MutableStateFlow(0)

    init {
        // A link from elsewhere is about the month in progress; the bar alone returns to
        // whichever month was last chosen here.
        viewModelScope.launch {
            tabFocus.forecast.requests.collect { selectedIndex.value = 0 }
        }
    }

    val state: StateFlow<ForecastState> = combine(
        snapshotRepository.snapshot,
        settingsStore.settings,
        selectedIndex
    ) { snapshot, settings, index ->
        val months = ForecastCalculator.forecast(snapshot, settings.forecastMonths)
        ForecastState(
            months = months,
            selectedIndex = index.coerceIn(0, (months.size - 1).coerceAtLeast(0)),
            shortfall = ShortfallExplainer.explain(months),
            basis = ForecastBasis(
                // Only completed months of history count. The month in progress would drag
                // the average down for no reason other than the date.
                monthsOfHistory = snapshot.expenses
                    .map { YearMonth.from(it.date) }
                    .filter { it < YearMonth.from(snapshot.today) }
                    .distinct()
                    .size,
                recurringCommitments = snapshot.emis.count { it.isActive } +
                    snapshot.bills.size +
                    snapshot.annualExpenses.size,
                estimates = snapshot.bills.count { it.amountType.name == "ESTIMATED" },
                usesSpendingHistory = months.firstOrNull()?.isProjectionBasedOnHistory ?: false
            ),
            hasPlanningData = snapshot.incomeSources.isNotEmpty() ||
                snapshot.bills.isNotEmpty() ||
                snapshot.emis.isNotEmpty(),
            today = snapshot.today,
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ForecastState()
    )

    fun selectMonth(index: Int) {
        selectedIndex.value = index
    }
}

data class ForecastState(
    val months: List<MonthForecast> = emptyList(),
    val selectedIndex: Int = 0,
    /** The first month projected to end below zero, and the payment that causes it. */
    val shortfall: ShortfallExplanation? = null,
    /** What the projection is built from, so its confidence can be judged. */
    val basis: ForecastBasis = ForecastBasis(),
    val hasPlanningData: Boolean = false,
    /** From the snapshot, so "in 41 days" stays right across midnight. */
    val today: java.time.LocalDate = java.time.LocalDate.now(),
    val isLoading: Boolean = true
) {
    val selected: MonthForecast? get() = months.getOrNull(selectedIndex)
}

/**
 * Money Ahead Forecast.
 *
 * A month is chosen from the horizon strip and then answered with a single figure: what is
 * expected to be left. Under it, money in against money out, and then every line that
 * produced it, each saying how sure the app is about it — because a projection a person
 * cannot interrogate is a projection they will not trust.
 */
@Composable
fun ForecastScreen(
    /** Opens the calendar on the given month. */
    onOpenCalendar: (YearMonth) -> Unit,
    onOpenAffordability: () -> Unit,
    viewModel: ForecastViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MoneyTheme.colors

    if (state.isLoading) {
        LoadingState()
        return
    }

    if (!state.hasPlanningData) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            title = "Nothing to forecast yet",
            message = "Add your salary, EMIs and regular bills. The app will then show " +
                "what you are expected to have left in each of the coming months."
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ForecastHeader(
                months = state.months,
                selectedIndex = state.selectedIndex,
                onSelect = viewModel::selectMonth
            )
        }

        item {
            MonthHorizonStrip(
                months = state.months,
                selectedIndex = state.selectedIndex,
                onSelect = viewModel::selectMonth
            )
        }

        // The alarm comes before the detail. Somebody who is going to run short in
        // December should not have to tap through eleven month chips to find out.
        state.shortfall?.let { shortfall ->
            item { ShortfallCard(shortfall, state.today, onOpenCalendar) }
        }

        val month = state.selected
        if (month != null) {
            item { ForecastHeroCard(month) }
            item { ForecastBasisCard(state.basis) }
            item { AffordBanner(onOpenAffordability) }
            item { BreakdownCard(month) }
        } else {
            item { ForecastBasisCard(state.basis) }
        }

        item {
            Text(
                "Each month starts with the closing balance of the one before it, so a " +
                    "shortfall is carried forward rather than reset.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
        }
    }
}

/**
 * The screen title, with the chosen month in a pill beside it. The pill opens the full
 * list of months, for somebody who would rather pick than scroll the strip.
 */
@Composable
private fun ForecastHeader(
    months: List<MonthForecast>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val colors = MoneyTheme.colors
    var choosing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Forecast",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.display,
            modifier = Modifier.weight(1f)
        )
        months.getOrNull(selectedIndex)?.let { selected ->
            Box {
                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(colors.sunken)
                        .border(1.dp, colors.cardBorder, shape)
                        .clickable(onClickLabel = "Choose a month") { choosing = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        DateUtil.formatMonth(selected.month),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    months.forEachIndexed { index, month ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        DateUtil.formatMonth(month.month),
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    MoneyText(
                                        money = month.closingBalance,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (month.isShortfall) {
                                            colors.negative
                                        } else {
                                            colors.textSecondary
                                        }
                                    )
                                }
                            },
                            onClick = {
                                onSelect(index)
                                choosing = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The months ahead as a strip of chips, each carrying its own projected closing balance, so
 * the shape of the next year is readable before any chip is tapped. Every one of these is a
 * projection, so unchosen months are drawn light; the chosen one is marigold.
 */
@Composable
private fun MonthHorizonStrip(
    months: List<MonthForecast>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val colors = MoneyTheme.colors

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${months.size}-MONTH HORIZON",
                style = PillLabelStyle,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f)
            )
            months.firstOrNull()?.takeIf { it.isCurrentMonth }?.let { current ->
                Box(
                    Modifier
                        .size(7.dp)
                        .background(colors.accent, CircleShape)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    DateUtil.formatMonthShort(current.month).substringBefore(" ").uppercase() +
                        " IN PROGRESS",
                    style = PillLabelStyle,
                    color = colors.warning
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            months.forEachIndexed { index, month ->
                val selected = index == selectedIndex
                val shape = RoundedCornerShape(18.dp)
                Column(
                    modifier = Modifier
                        .clip(shape)
                        .background(if (selected) colors.accent else colors.card)
                        .then(
                            if (selected) {
                                Modifier
                            } else {
                                Modifier.border(1.dp, colors.cardBorder, shape)
                            }
                        )
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(index) }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        DateUtil.formatMonthShort(month.month),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.onAccent else colors.textPrimary
                    )
                    MoneyText(
                        money = month.closingBalance,
                        style = MaterialTheme.typography.labelMedium,
                        compact = true,
                        color = when {
                            selected -> colors.onAccent
                            month.isShortfall -> colors.negative
                            else -> colors.forecast
                        }
                    )
                }
            }
        }
    }
}

/**
 * The month the money is projected to run out, and the payment that takes it there.
 *
 * Calm on purpose. A red full-bleed panel about a date months away is how somebody decides
 * the app is stressful and stops opening it. This states the date, the figure and the
 * cause, then offers the screen where it can be dealt with. No score, no exclamation, no
 * advice about spending less.
 */
@Composable
private fun ShortfallCard(
    shortfall: ShortfallExplanation,
    today: java.time.LocalDate,
    onOpenCalendar: (YearMonth) -> Unit
) {
    val colors = MoneyTheme.colors
    val monthName = DateUtil.formatMonth(shortfall.month)
    val daysAway = java.time.temporal.ChronoUnit.DAYS.between(
        today,
        shortfall.culpritDate ?: shortfall.month.atDay(1)
    )

    SectionCard(containerColor = colors.negativeContainer, borderColor = colors.negativeBorder) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.negative.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = colors.negative,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PROJECTED SHORTFALL",
                        style = PillLabelStyle,
                        color = colors.negative,
                        modifier = Modifier.weight(1f)
                    )
                    if (daysAway > 0) {
                        Text(
                            "in $daysAway days",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.negative,
                            modifier = Modifier
                                .background(colors.card.copy(alpha = 0.7f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "$monthName is projected to end at",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
                MoneyText(
                    money = shortfall.projectedBalance,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.negative
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        if (shortfall.hasCulprit) {
                            append(shortfall.culpritTitle)
                            append(" (")
                            append(IndianFormat.format(shortfall.culpritAmount))
                            append(")")
                            val relief = shortfall.daysUntilRelief
                            if (relief != null && relief > 0) {
                                append(" falls ")
                                append(relief)
                                append(if (relief == 1L) " day" else " days")
                                append(" before the next money is expected.")
                            } else {
                                append(" is the largest payment that month.")
                            }
                        } else {
                            // No single payment dominates, so naming one would point at
                            // the wrong thing. Ordinary spending adding up is a different
                            // problem.
                            append(
                                "No single payment causes this. The month's ordinary " +
                                    "spending is projected to outrun what is expected to come in."
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                TextButton(
                    onClick = { onOpenCalendar(shortfall.month) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(
                        "See what is dated in ${monthName.substringBefore(" ")}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.negative
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = colors.negative,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * The headline figure for the chosen month, labelled as a forecast, with money in against
 * money out in an inset beneath it.
 */
@Composable
private fun ForecastHeroCard(month: MonthForecast) {
    val colors = MoneyTheme.colors
    val inflow = month.totalInflow
    val outflow = month.totalOutflow
    val total = inflow + outflow
    val inflowFraction = if (total.paise <= 0L) 0.5f
    else (inflow.paise.toDouble() / total.paise).toFloat()
    val monthName = DateUtil.formatMonth(month.month).substringBefore(" ")

    SectionCard(contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$monthName forecast",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            ConfidencePill(Confidence.FORECAST)
        }
        Spacer(Modifier.height(6.dp))
        MoneyText(
            money = month.closingBalance,
            style = MaterialTheme.typography.displayMedium,
            color = if (month.isShortfall) colors.negative else colors.display
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (month.isShortfall) colors.negative else colors.positive,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Projected closing balance on " +
                    DateUtil.formatDayMonth(month.month.atEndOfMonth()),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.sunken, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                FlowFigure("Expected in", inflow, colors.positive, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                FlowFigure("Committed out", outflow, colors.negative, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            InOutBar(
                inflowFraction = inflowFraction,
                height = 8.dp,
                description = "${IndianFormat.format(inflow)} coming in against " +
                    "${IndianFormat.format(outflow)} going out"
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Net this month",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f)
                )
                MoneyText(
                    money = inflow - outflow,
                    style = MaterialTheme.typography.labelLarge,
                    showSign = true,
                    color = if ((inflow - outflow).isNegative) colors.negative else colors.textPrimary
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Starts with ${IndianFormat.format(month.openingBalance)} carried in from " +
                "the month before.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary
        )
    }
}

@Composable
private fun FlowFigure(label: String, amount: Money, tint: Color, modifier: Modifier = Modifier) {
    val colors = MoneyTheme.colors
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(tint, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        MoneyText(
            money = amount,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = tint
        )
    }
}

/**
 * What the projection rests on.
 *
 * The forecast is only as good as its inputs, and the user is the only person who can tell
 * whether three months of history is enough. Stating the inputs is how a projection stays a
 * projection rather than becoming an oracle.
 */
@Composable
private fun ForecastBasisCard(basis: ForecastBasis) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.sunken, RoundedCornerShape(20.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.forecastContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Insights,
                contentDescription = null,
                tint = colors.forecast,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            basis.describe(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun AffordBanner(onOpenAffordability: () -> Unit) {
    val colors = MoneyTheme.colors
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accentSoft)
            .border(1.dp, colors.warningBorder, shape)
            .clickable(onClick = onOpenAffordability)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.ShoppingCart,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Thinking about a purchase? Check it with Can I afford it?",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onWarningContainer,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.onWarningContainer
        )
    }
}

/** Rows shown before the breakdown offers to show the rest. */
private const val BREAKDOWN_ROWS = 8

/**
 * Every line behind the month's figure, each with its confidence. Scheduled payments are
 * UPCOMING, guesses are ESTIMATED and anything late is OVERDUE; everyday spending, which no
 * schedule describes, is always an estimate and says so.
 */
@Composable
private fun BreakdownCard(month: MonthForecast) {
    val colors = MoneyTheme.colors
    var showAll by remember(month.month) { mutableStateOf(false) }

    val rows = buildList {
        month.items.forEach { item ->
            add(
                BreakdownLine(
                    title = item.title,
                    // A late balance is carried forward to today to stay in view, but the
                    // row says the day it was really due, beside its OVERDUE pill.
                    subtitle = DateUtil.formatDayMonth(item.originallyDue ?: item.date) +
                        item.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    amount = item.amount,
                    isInflow = item.isInflow,
                    icon = iconFor(item.kind),
                    confidence = when {
                        item.isOverdue -> Confidence.OVERDUE
                        item.isEstimate -> Confidence.ESTIMATED
                        else -> Confidence.UPCOMING
                    }
                )
            )
        }
        if (month.estimatedEverydaySpend.isPositive) {
            add(
                BreakdownLine(
                    title = "Everyday spending",
                    subtitle = if (month.isCurrentMonth) {
                        "Estimated for the rest of the month"
                    } else {
                        "Estimated from your own history"
                    },
                    amount = month.estimatedEverydaySpend,
                    isInflow = false,
                    icon = Icons.Default.ShoppingBasket,
                    confidence = Confidence.ESTIMATED,
                    isEveryday = true
                )
            )
        }
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Expected breakdown",
                style = MaterialTheme.typography.titleLarge,
                color = colors.display,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (rows.size == 1) "1 ENTRY" else "${rows.size} ENTRIES",
                style = PillLabelStyle,
                color = colors.textTertiary
            )
        }
        Spacer(Modifier.height(8.dp))

        if (rows.isEmpty()) {
            Text(
                "Nothing is scheduled for this month.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }

        val visible = if (showAll) rows else rows.take(BREAKDOWN_ROWS)
        visible.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = colors.borderSubtle)
            BreakdownLineRow(row)
        }
        if (rows.size > BREAKDOWN_ROWS) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(
                    if (showAll) "Show fewer" else "Show all ${rows.size}",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = colors.cardBorder)
        Spacer(Modifier.height(4.dp))
        // From the real month-end balance down to what is free, one deduction per row: the
        // money already set aside for goals, then what the goals need this month.
        val setAside = month.setAsideForGoals
        SummaryRow(
            label = "Expected at month end",
            amount = month.closingBalance,
            supporting = setAside.takeIf { it.isPositive }
                ?.let { "Includes ${IndianFormat.format(it)} set aside for goals" },
            emphasise = true,
            amountColor = if (month.isShortfall) colors.negative else colors.display
        )
        if (setAside.isPositive) {
            SummaryRow(
                label = "Safe to spend",
                amount = month.safeToSpend,
                supporting = "Leaving the money set aside for goals alone"
            )
        }
        if (month.plannedSavings.isPositive) {
            SummaryRow(
                label = "After setting savings aside",
                amount = month.leftAfterPlannedSavings,
                supporting = "Your goals need ${IndianFormat.format(month.plannedSavings)}" +
                    (if (setAside.isPositive) " more" else "") + " this month",
                amountColor = if (month.leftAfterPlannedSavings.isNegative) colors.negative else null
            )
        }
        if (!month.isProjectionBasedOnHistory && month.estimatedEverydaySpend.isZero) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Everyday spending is not projected yet. Once you have recorded a few " +
                    "weeks of expenses, this will include a realistic estimate.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
        }
    }
}

private data class BreakdownLine(
    val title: String,
    val subtitle: String,
    val amount: Money,
    val isInflow: Boolean,
    val icon: ImageVector,
    val confidence: Confidence,
    val isEveryday: Boolean = false
)

@Composable
private fun BreakdownLineRow(row: BreakdownLine) {
    val colors = MoneyTheme.colors
    val (tint, well) = when {
        row.isInflow -> colors.positive to colors.positiveContainer
        row.isEveryday -> colors.warning to colors.accentSoft
        else -> colors.forecast to colors.forecastContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(well, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(row.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                money = if (row.isInflow) row.amount else -row.amount,
                style = MaterialTheme.typography.titleMedium,
                showSign = true,
                color = if (row.isInflow) colors.positive else colors.negative
            )
            Spacer(Modifier.height(4.dp))
            ConfidencePill(row.confidence)
        }
    }
}

private fun iconFor(kind: ForecastItemKind): ImageVector = when (kind) {
    ForecastItemKind.INCOME -> Icons.Default.Payments
    ForecastItemKind.PERSON_INCOMING, ForecastItemKind.PERSON_OUTGOING -> Icons.Default.Groups
    ForecastItemKind.EMI -> Icons.Default.AccountBalance
    ForecastItemKind.BILL -> Icons.AutoMirrored.Filled.ReceiptLong
    ForecastItemKind.ANNUAL -> Icons.Default.CalendarMonth
    ForecastItemKind.CREDIT_CARD -> Icons.Default.CreditCard
}

/**
 * The inputs behind the projection, counted rather than characterised.
 *
 * Written as a sentence rather than a row of statistics because the point is a reader
 * judging whether to trust the numbers above, and "based on two months" does that better
 * than a dashboard tile labelled 2.
 */
data class ForecastBasis(
    val monthsOfHistory: Int = 0,
    val recurringCommitments: Int = 0,
    val estimates: Int = 0,
    /** False until there is enough history for the everyday spending figure to mean anything. */
    val usesSpendingHistory: Boolean = false
) {
    fun describe(): String {
        val parts = mutableListOf<String>()

        parts += when (monthsOfHistory) {
            0 -> "no completed months of spending yet"
            1 -> "1 completed month of spending"
            else -> "$monthsOfHistory completed months of spending"
        }
        if (recurringCommitments > 0) {
            parts += if (recurringCommitments == 1) {
                "1 recurring commitment"
            } else {
                "$recurringCommitments recurring commitments"
            }
        }
        if (estimates > 0) {
            parts += if (estimates == 1) {
                "1 amount you estimated"
            } else {
                "$estimates amounts you estimated"
            }
        }

        val list = when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] + " and " + parts[1]
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }

        val caveat = if (!usesSpendingHistory) {
            " Everyday spending is not projected yet: there is not enough history for it " +
                "to be worth a guess."
        } else {
            ""
        }
        return "Based on " + list + "." + caveat
    }
}
