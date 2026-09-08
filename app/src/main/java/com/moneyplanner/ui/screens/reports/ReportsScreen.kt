package com.moneyplanner.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.GoalProgressBar
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.components.parseColorOrDefault
import com.moneyplanner.ui.screens.expenses.MonthSelector
import com.moneyplanner.ui.theme.MoneyTheme
import androidx.compose.material.icons.filled.Assessment

/**
 * Plain reports rather than a wall of charts.
 *
 * Each block answers one question a person would actually ask, and a bar is only used
 * where comparing lengths genuinely helps. Every figure has its number printed beside it,
 * so nothing depends on reading a chart accurately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MoneyTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        if (!state.hasData) {
            EmptyState(
                icon = Icons.Default.Assessment,
                title = "Nothing to report yet",
                message = "Once you have recorded some income and expenses, this screen " +
                    "will show where your money is going.",
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MonthSelector(
                    label = DateUtil.formatMonth(state.month),
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth
                )
            }

            item {
                SectionCard(title = "Income against spending") {
                    SummaryRow("Received", state.income, colorBySign = true, showSign = true)
                    SummaryRow("Spent", -state.spent, colorBySign = true, showSign = true)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow(
                        label = if (state.net.isNegative) "Spent more than received" else "Kept",
                        amount = state.net,
                        colorBySign = true,
                        showSign = true,
                        emphasise = true
                    )
                    if (state.income.isPositive) {
                        Spacer(Modifier.height(10.dp))
                        val savedFraction = (state.net.paise.toFloat() / state.income.paise)
                            .coerceIn(0f, 1f)
                        GoalProgressBar(
                            fraction = savedFraction,
                            color = colors.positive,
                            label = "Kept ${(savedFraction * 100).toInt()} percent of what came in"
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "You kept ${(savedFraction * 100).toInt()}% of what came in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.categoryBreakdown.isNotEmpty()) {
                item {
                    SectionCard(title = "Where it went") {
                        state.categoryBreakdown.forEach { row ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                parseColorOrDefault(
                                                    row.colorHex,
                                                    MaterialTheme.colorScheme.primary
                                                ),
                                                CircleShape
                                            )
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            row.categoryName,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            "${row.transactionCount} " +
                                                if (row.transactionCount == 1) "entry" else "entries",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        MoneyText(row.amount)
                                        Text(
                                            "${(row.shareOfTotal * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                CategoryBar(
                                    fraction = row.shareOfTotal,
                                    color = parseColorOrDefault(
                                        row.colorHex,
                                        MaterialTheme.colorScheme.primary
                                    ),
                                    description = "${row.categoryName}: " +
                                        "${IndianFormat.format(row.amount)}, " +
                                        "${(row.shareOfTotal * 100).toInt()} percent of spending"
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "What your commitments cost",
                    subtitle = "The part of your month that is already spoken for"
                ) {
                    if (state.emiBurden.isPositive) SummaryRow("EMIs", state.emiBurden)
                    if (state.billsBurden.isPositive) SummaryRow("Recurring bills", state.billsBurden)
                    if (state.annualReserve.isPositive) {
                        SummaryRow(
                            label = "Yearly expenses",
                            amount = state.annualReserve,
                            supporting = "Their monthly share"
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("Committed every month", state.totalCommitted, emphasise = true)
                    if (state.monthlyIncome.isPositive) {
                        Spacer(Modifier.height(10.dp))
                        val burden = (state.totalCommitted.paise.toFloat() /
                            state.monthlyIncome.paise).coerceIn(0f, 1f)
                        GoalProgressBar(
                            fraction = burden,
                            color = if (burden > 0.5f) colors.warning else MaterialTheme.colorScheme.primary,
                            label = "Commitments take ${(burden * 100).toInt()} percent of your income"
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "That is ${(burden * 100).toInt()}% of your regular income.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.peopleBalances.isNotEmpty()) {
                item {
                    SectionCard(title = "People") {
                        state.peopleBalances.forEach { summary ->
                            SummaryRow(
                                label = summary.person.name,
                                amount = summary.balance,
                                supporting = if (summary.theyOweMe) "owes you" else "you owe",
                                colorBySign = true,
                                showSign = true
                            )
                        }
                    }
                }
            }

            if (state.goalProgress.isNotEmpty()) {
                item {
                    SectionCard(title = "Savings progress") {
                        state.goalProgress.forEach { progress ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        progress.goal.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "${progress.progressPercent}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                GoalProgressBar(
                                    fraction = progress.progressFraction,
                                    label = "${progress.goal.name} is " +
                                        "${progress.progressPercent} percent funded"
                                )
                            }
                        }
                    }
                }
            }

            if (state.monthlyTrend.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "Recent months",
                        subtitle = "Spending over the last few months"
                    ) {
                        val peak = state.monthlyTrend.maxOf { it.second.paise }.coerceAtLeast(1L)
                        state.monthlyTrend.forEach { (month, amount) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    DateUtil.formatMonthShort(month),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(56.dp)
                                )
                                CategoryBar(
                                    fraction = amount.paise.toFloat() / peak,
                                    color = MaterialTheme.colorScheme.primary,
                                    description = "${DateUtil.formatMonth(month)}: " +
                                        IndianFormat.format(amount),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(10.dp))
                                MoneyText(
                                    amount,
                                    style = MaterialTheme.typography.bodySmall,
                                    compact = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A single proportional bar. The number is always printed next to it. */
@Composable
private fun CategoryBar(
    fraction: Float,
    color: Color,
    description: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}
