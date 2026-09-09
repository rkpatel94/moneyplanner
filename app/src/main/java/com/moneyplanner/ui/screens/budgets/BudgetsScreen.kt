package com.moneyplanner.ui.screens.budgets

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.Budget
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.calc.BudgetState
import com.moneyplanner.domain.calc.BudgetStatus
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DropdownField
import com.moneyplanner.ui.components.SwitchRow
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.GoalProgressBar
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.theme.MoneyTheme

/**
 * Spending limits.
 *
 * The forecast says what will happen; a budget is how you change it. Each row leads with
 * where you stand and, when the pace is a problem, says so before the limit is actually
 * breached — a warning that arrives after the money is spent is not much of a warning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BudgetStatus?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditor = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Set a budget") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (state.statuses.isEmpty()) {
            EmptyState(
                icon = Icons.Default.PieChart,
                title = "No budgets set",
                message = "A budget is the other half of planning: the forecast tells you " +
                    "what will happen, a limit is how you change it.",
                actionLabel = "Set your first budget",
                onAction = { showEditor = true },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        DateUtil.formatMonth(state.month),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(state.statuses, key = { it.budget.id }) { status ->
                    BudgetCard(status, onDelete = { pendingDelete = status })
                }

                item {
                    Text(
                        "Budgets count what you spend, however you paid for it — including " +
                            "card purchases, which the cash forecast deliberately leaves out " +
                            "until the bill is due.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showEditor) {
        BudgetEditorDialog(
            categories = state.availableCategories,
            onDismiss = { showEditor = false },
            onSave = { categoryId, amount, rollover, threshold ->
                viewModel.setBudget(categoryId, amount, rollover, threshold)
                showEditor = false
            }
        )
    }

    pendingDelete?.let { status ->
        ConfirmDialog(
            title = "Remove this budget?",
            message = "The limit on ${status.categoryName} will be removed. Your expenses " +
                "are not affected.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.delete(status.budget.id) },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun BudgetCard(status: BudgetStatus, onDelete: () -> Unit) {
    val colors = MoneyTheme.colors

    val (barColor, pill) = when (status.state) {
        BudgetState.OVER -> colors.negative to ("Over budget" to colors.negativeContainer)
        BudgetState.NEAR_LIMIT -> colors.warning to ("Close to the limit" to colors.warningContainer)
        BudgetState.PROJECTED_OVER ->
            colors.warning to ("On pace to go over" to colors.warningContainer)
        BudgetState.ON_TRACK -> colors.positive to ("On track" to colors.positiveContainer)
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(status.categoryName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${status.transactionCount} " +
                        if (status.transactionCount == 1) "entry" else "entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove budget")
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            MoneyText(status.spent, style = MaterialTheme.typography.titleLarge)
            Text(
                "of ${IndianFormat.format(status.budget.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
        GoalProgressBar(
            fraction = status.usedFraction,
            color = barColor,
            label = "${status.categoryName}: ${status.usedPercent} percent of the budget used"
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = pill.first,
                containerColor = pill.second,
                contentColor = if (status.state == BudgetState.ON_TRACK) {
                    colors.positive
                } else if (status.state == BudgetState.OVER) {
                    colors.negative
                } else {
                    colors.onWarningContainer
                }
            )
            Text(
                status.summary(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // A limit larger than the one the user set needs explaining, or it reads as a bug.
        if (status.hasCarryOver) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${IndianFormat.format(status.carriedOver)} carried over, so this month's " +
                    "limit is ${IndianFormat.format(status.effectiveLimit)}.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.positive
            )
        }

        if (status.state == BudgetState.PROJECTED_OVER) {
            Spacer(Modifier.height(8.dp))
            Text(
                "At this pace you will finish the month around " +
                    "${IndianFormat.format(status.projectedSpend)}.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.warning
            )
        }

        if (!status.isOver && status.dailyAllowanceLeft.isPositive) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${IndianFormat.format(status.dailyAllowanceLeft)} a day for the rest of the month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetEditorDialog(
    categories: List<com.moneyplanner.domain.model.Category>,
    onDismiss: () -> Unit,
    onSave: (Long?, Money, Boolean, Int) -> Unit
) {
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var amountText by remember { mutableStateOf("") }
    var rollover by remember { mutableStateOf(false) }
    var threshold by remember { mutableIntStateOf(Budget.DEFAULT_ALERT_THRESHOLD) }
    val amount = Money.parseOrNull(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Set a monthly budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(
                    value = categoryId,
                    options = listOf<Long?>(null) + categories.map { it.id },
                    onSelect = { categoryId = it },
                    label = "Applies to",
                    optionLabel = { id ->
                        if (id == null) "Everything" else categories.first { it.id == id }.name
                    }
                )
                AmountField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "Monthly limit",
                    imeAction = ImeAction.Done
                )
                ChipSelector(
                    label = "Warn me at",
                    options = listOf(50, 70, 80, 90),
                    selected = threshold,
                    onSelect = { threshold = it },
                    optionLabel = { "$it%" }
                )

                SwitchRow(
                    label = "Carry unspent money forward",
                    checked = rollover,
                    onCheckedChange = { rollover = it }
                )
                Text(
                    if (rollover) {
                        "Whatever you do not spend in a month raises the next month's " +
                            "limit. Going over does not carry the other way."
                    } else {
                        "Each month starts at the same limit, whatever last month did."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "Setting a limit for something that already has one replaces it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let { onSave(categoryId, it, rollover, threshold) } },
                enabled = amount?.isPositive == true
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
