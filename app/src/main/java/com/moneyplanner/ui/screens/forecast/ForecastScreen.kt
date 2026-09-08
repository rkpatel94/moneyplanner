package com.moneyplanner.ui.screens.forecast

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.IconWash
import com.moneyplanner.ui.components.InOutBar
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.theme.MoneyTheme
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    private val selectedIndex = MutableStateFlow(0)

    val state: StateFlow<ForecastState> = combine(
        snapshotRepository.snapshot,
        settingsStore.settings,
        selectedIndex
    ) { snapshot, settings, index ->
        val months = ForecastCalculator.forecast(snapshot, settings.forecastMonths)
        ForecastState(
            months = months,
            selectedIndex = index.coerceIn(0, (months.size - 1).coerceAtLeast(0)),
            hasPlanningData = snapshot.incomeSources.isNotEmpty() ||
                snapshot.bills.isNotEmpty() ||
                snapshot.emis.isNotEmpty(),
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
    val hasPlanningData: Boolean = false,
    val isLoading: Boolean = true
) {
    val selected: MonthForecast? get() = months.getOrNull(selectedIndex)
}

/**
 * Money Ahead Forecast.
 *
 * A month is chosen from the chip row and then answered with a single figure: what is
 * expected to be left. The split bar underneath shows money in against money out at a
 * glance, and the breakdown says exactly which commitments produced it — because a
 * projection a person cannot interrogate is a projection they will not trust.
 */
@Composable
fun ForecastScreen(
    onOpenCalendar: () -> Unit,
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
            icon = Icons.Default.TrendingUp,
            title = "Nothing to forecast yet",
            message = "Add your salary, EMIs and regular bills. The app will then show " +
                "what you are expected to have left in each of the coming months."
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    "Money ahead forecast",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    "Built only from what you have entered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.months.forEachIndexed { index, month ->
                    FilterChip(
                        selected = index == state.selectedIndex,
                        onClick = { viewModel.selectMonth(index) },
                        label = {
                            Text(DateUtil.formatMonthShort(month.month).substringBefore(" "))
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }

        val month = state.selected
        if (month != null) {
            item { ExpectedRemainingCard(month) }
            item { BreakdownCard(month) }
            item { DatedItemsCard(month, onOpenCalendar) }

            item {
                SectionCard(
                    title = "Thinking about a purchase?",
                    modifier = Modifier.clickable(onClick = onOpenAffordability)
                ) {
                    Text(
                        "Check it against these months before you commit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                "Each month starts with the closing balance of the one before it, so a " +
                    "shortfall is carried forward rather than reset.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The headline figure, with the in-against-out split directly beneath it. */
@Composable
private fun ExpectedRemainingCard(month: MonthForecast) {
    val colors = MoneyTheme.colors
    val inflow = month.totalInflow
    val outflow = month.totalOutflow
    val total = inflow + outflow
    val inflowFraction = if (total.paise <= 0L) 0.5f
    else (inflow.paise.toDouble() / total.paise).toFloat()

    SectionCard(contentPadding = 20.dp) {
        Text(
            "Expected remaining",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        MoneyText(
            money = month.closingBalance,
            style = MaterialTheme.typography.displaySmall,
            color = if (month.isShortfall) colors.negative else MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))
        InOutBar(
            inflowFraction = inflowFraction,
            description = "${IndianFormat.format(inflow)} coming in against " +
                "${IndianFormat.format(outflow)} going out"
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowTile(
                label = "Money coming",
                amount = inflow,
                tint = colors.positive,
                positive = true,
                modifier = Modifier.weight(1f)
            )
            FlowTile(
                label = "Money going",
                amount = outflow,
                tint = colors.negative,
                positive = false,
                modifier = Modifier.weight(1f)
            )
        }

        if (month.isShortfall) {
            Spacer(Modifier.height(14.dp))
            StatusPill(
                text = "Projected to fall short by ${IndianFormat.format(month.closingBalance.abs())}",
                containerColor = colors.warningContainer,
                contentColor = colors.onWarningContainer
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Starts with ${IndianFormat.format(month.openingBalance)} carried in from " +
                "the month before.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FlowTile(
    label: String,
    amount: Money,
    tint: Color,
    positive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = (if (positive) "+" else "−") + IndianFormat.format(amount),
                style = MaterialTheme.typography.titleLarge,
                color = tint
            )
        }
    }
}

/** Which commitments make up the month, each with the icon its kind carries elsewhere. */
@Composable
private fun BreakdownCard(month: MonthForecast) {
    val colors = MoneyTheme.colors

    val rows = buildList {
        if (month.expectedIncome.isPositive) {
            add(BreakdownRow("Income", month.expectedIncome, Icons.Default.ArrowDownward, true))
        }
        if (month.expectedIncomingFromPeople.isPositive) {
            add(
                BreakdownRow(
                    "Money coming in",
                    month.expectedIncomingFromPeople,
                    Icons.Default.Groups,
                    true
                )
            )
        }
        if (month.emiOutflow.isPositive) {
            add(BreakdownRow("EMIs", month.emiOutflow, Icons.Default.AccountBalance, false))
        }
        if (month.billsOutflow.isPositive) {
            add(BreakdownRow("Bills", month.billsOutflow, Icons.Default.CalendarMonth, false))
        }
        if (month.annualOutflow.isPositive) {
            add(
                BreakdownRow(
                    "Yearly expenses",
                    month.annualOutflow,
                    Icons.Default.CalendarMonth,
                    false
                )
            )
        }
        if (month.creditCardOutflow.isPositive) {
            add(
                BreakdownRow(
                    "Credit card",
                    month.creditCardOutflow,
                    Icons.Default.CreditCard,
                    false
                )
            )
        }
        if (month.owedToPeopleOutflow.isPositive) {
            add(
                BreakdownRow(
                    "Money to repay",
                    month.owedToPeopleOutflow,
                    Icons.Default.Groups,
                    false
                )
            )
        }
        if (month.estimatedEverydaySpend.isPositive) {
            add(
                BreakdownRow(
                    "Everyday spending",
                    month.estimatedEverydaySpend,
                    Icons.Default.ShoppingBag,
                    false,
                    supporting = if (month.isCurrentMonth) {
                        "Estimated for the rest of the month"
                    } else {
                        "Estimated from your own history"
                    }
                )
            )
        }
    }

    SectionCard(title = "Expected breakdown") {
        if (rows.isEmpty()) {
            Text(
                "Nothing is scheduled for this month.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = colors.cardBorder
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconWash(
                    icon = row.icon,
                    tint = if (row.isInflow) colors.positive else colors.negative
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge)
                    if (row.supporting != null) {
                        Text(
                            row.supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                MoneyText(
                    money = row.amount,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.cardBorder)
        SummaryRow(
            label = "Expected at month end",
            amount = month.closingBalance,
            emphasise = true,
            amountColor = if (month.isShortfall) colors.negative else null
        )
        if (month.plannedSavings.isPositive) {
            SummaryRow(
                label = "After setting savings aside",
                amount = month.closingAfterPlannedSavings,
                supporting = "Your goals need ${IndianFormat.format(month.plannedSavings)} this month"
            )
        }
        if (!month.isProjectionBasedOnHistory && month.estimatedEverydaySpend.isZero) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Everyday spending is not projected yet. Once you have recorded a few " +
                    "weeks of expenses, this will include a realistic estimate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class BreakdownRow(
    val label: String,
    val amount: Money,
    val icon: ImageVector,
    val isInflow: Boolean,
    val supporting: String? = null
)

/** The dated lines behind the month, collapsed by default to keep the summary clean. */
@Composable
private fun DatedItemsCard(month: MonthForecast, onOpenCalendar: () -> Unit) {
    if (month.items.isEmpty()) return
    var expanded by remember(month.month) { mutableStateOf(false) }
    val colors = MoneyTheme.colors

    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dated payments", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${month.items.size} scheduled in ${DateUtil.formatMonth(month.month)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Hide dated payments" else "Show dated payments"
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                month.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconWash(
                            icon = iconFor(item.kind),
                            tint = if (item.isInflow) colors.positive else colors.negative,
                            size = 36.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${DateUtil.formatDayMonth(item.date)} · ${item.subtitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MoneyText(
                            money = item.signedAmount,
                            colorBySign = true,
                            showSign = true,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

private fun iconFor(kind: ForecastItemKind): ImageVector = when (kind) {
    ForecastItemKind.INCOME -> Icons.Default.ArrowDownward
    ForecastItemKind.PERSON_INCOMING, ForecastItemKind.PERSON_OUTGOING -> Icons.Default.Groups
    ForecastItemKind.EMI -> Icons.Default.AccountBalance
    ForecastItemKind.BILL, ForecastItemKind.ANNUAL -> Icons.Default.CalendarMonth
    ForecastItemKind.CREDIT_CARD -> Icons.Default.CreditCard
}
