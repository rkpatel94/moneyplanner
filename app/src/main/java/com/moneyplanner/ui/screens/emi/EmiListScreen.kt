package com.moneyplanner.ui.screens.emi

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.GoalProgressBar
import com.moneyplanner.ui.components.IconWash
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.theme.MoneyTheme
import com.moneyplanner.ui.util.categoryIcon

/**
 * Your Plans: everything the user has committed to paying.
 *
 * Loans lead because they are the largest and least escapable commitment, with bills,
 * cards and yearly expenses reachable underneath. When [onBack] is null the screen is
 * being shown as a bottom-bar tab and so carries no back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmiListScreen(
    onBack: (() -> Unit)?,
    onAddEmi: () -> Unit,
    onOpenEmi: (Long) -> Unit,
    onOpenBills: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenAnnual: () -> Unit,
    viewModel: EmiListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onBack == null) "Your plans" else "EMIs and loans") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddEmi,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add EMI") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (onBack == null) {
                item {
                    Text(
                        "Manage your EMIs and upcoming commitments.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(contentPadding = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconWash(
                            Icons.Default.AccountBalance,
                            MaterialTheme.colorScheme.primary,
                            size = 36.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Monthly EMI",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    MoneyText(
                        money = state.monthlyBurden,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (state.totalOutstanding.isPositive) {
                        Text(
                            "${IndianFormat.format(state.totalOutstanding)} still to repay in total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.active.isNotEmpty()) {
                item {
                    Text(
                        "Active commitments",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(state.active, key = { it.emi.id }) { row ->
                    SectionCard(modifier = Modifier.clickable { onOpenEmi(row.emi.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconWash(
                                icon = categoryIcon(row.emi.name),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    row.emi.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (row.emi.accountReference.isNotBlank()) {
                                    Text(
                                        row.emi.accountReference,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(
                                    money = row.emi.emiAmount,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    perPeriodLabel(row),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                row.nextDueDate?.let { "Next: ${DateUtil.formatDayMonth(it)}" }
                                    ?: "No date scheduled",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${row.remainingInstallments} left",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        GoalProgressBar(
                            fraction = row.progressFraction,
                            label = "${row.paidInstallments} of ${row.emi.totalInstallments} " +
                                "installments paid"
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${(row.progressFraction * 100).toInt()}% paid",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            } else {
                item {
                    EmptyState(
                        icon = Icons.Default.AccountBalance,
                        title = "No loans recorded",
                        message = "Add your EMIs so every future month knows what has to be paid.",
                        actionLabel = "Add an EMI",
                        onAction = onAddEmi
                    )
                }
            }

            if (state.closed.isNotEmpty()) {
                item {
                    Text(
                        "Closed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(state.closed, key = { it.emi.id }) { row ->
                    SectionCard(modifier = Modifier.clickable { onOpenEmi(row.emi.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconWash(Icons.Default.CheckCircle, MoneyTheme.colors.positive)
                            Spacer(Modifier.width(12.dp))
                            Text(row.emi.name, modifier = Modifier.weight(1f))
                            StatusPill(
                                text = if (row.isCompleted) "Fully repaid" else "Paused",
                                containerColor = MoneyTheme.colors.positiveContainer,
                                contentColor = MoneyTheme.colors.positive
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Other commitments") {
                    CommitmentLink(
                        Icons.Default.EventRepeat,
                        "Recurring bills",
                        "Rent, electricity, subscriptions",
                        onOpenBills
                    )
                    CommitmentLink(
                        Icons.Default.CreditCard,
                        "Credit cards",
                        "Outstanding and due dates",
                        onOpenCards
                    )
                    CommitmentLink(
                        Icons.Default.CalendarMonth,
                        "Yearly expenses",
                        "Insurance, school fees, property tax",
                        onOpenAnnual
                    )
                }
            }
        }
    }
}

private fun perPeriodLabel(row: EmiRow): String =
    if (row.emi.frequency.isWeekly) "/week" else "/mo"

@Composable
private fun CommitmentLink(
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
