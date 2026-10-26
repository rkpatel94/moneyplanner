package com.moneyplanner.ui.screens.expenses

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.ui.components.MonthSelector
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ColorAvatar
import com.moneyplanner.ui.components.Confidence
import com.moneyplanner.ui.components.ConfidencePill
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DateField
import com.moneyplanner.ui.components.DropdownField
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.util.categoryIcon

import java.time.YearMonth

/**
 * The month view of everything spent, with a category filter across the top.
 *
 * It is opened from Home and More rather than from the bottom bar, and the bar is hidden
 * here, so it carries its own back arrow. Opened from the dashboard's "expenses with no
 * account" row, it lists exactly those expenses from every month instead, each marked, so
 * the user can open one and say which account paid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.unassignedOnly) "Expenses with no account" else "All transactions")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        TransactionsContent(
            state = state,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onFilterByCategory = viewModel::filterByCategory,
            onAddExpense = onAddExpense,
            onEditExpense = onEditExpense,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun TransactionsContent(
    state: TransactionsState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onFilterByCategory: (Long?) -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.unassignedOnly) {
            // These can come from any month, so there is no month to page through.
            Text(
                "Recorded without an account or card, so the balance cannot say where " +
                    "this money came from. Open one to choose the account that paid.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            MonthSelector(
                month = state.month,
                // From the state rather than the wall clock, so "this month" comes from
                // TodayProvider like every other "today" and moves on at midnight with it.
                currentMonth = YearMonth.from(state.today),
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    state.selectedCategoryId != null -> "Filtered total"
                    state.unassignedOnly -> "Total with no account"
                    else -> "Total spent"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MoneyText(
                money = state.filteredTotal,
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (state.categories.isNotEmpty()) {
            ChipSelector(
                options = listOf<Long?>(null) + state.categories.map { it.id },
                selected = state.selectedCategoryId,
                onSelect = onFilterByCategory,
                optionLabel = { id ->
                    if (id == null) "All" else state.categoriesById[id]?.name ?: "Other"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (state.expenses.isEmpty() && state.unassignedOnly) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "Every expense has an account",
                message = "Nothing is left to assign. Each expense now says which account " +
                    "or card it was paid from."
            )
        } else if (state.expenses.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "Nothing recorded yet",
                message = "Expenses you add for ${DateUtil.formatMonth(state.month)} will appear here.",
                actionLabel = "Add an expense",
                onAction = onAddExpense
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(state.expenses, key = { it.id }) { expense ->
                    val category = state.categoriesById[expense.categoryId]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditExpense(expense.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorAvatar(
                            icon = categoryIcon(category?.name.orEmpty()),
                            colorHex = category?.colorHex ?: "#FF6E7A8A"
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                expense.description.ifBlank { category?.name ?: "Expense" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    // With every month in one list, the year matters too.
                                    (if (state.unassignedOnly) DateUtil.formatDate(expense.date)
                                    else DateUtil.formatDayMonth(expense.date)) +
                                        " · ${expense.paymentMethod.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (expense.linkType != com.moneyplanner.domain.model.ExpenseLinkType.NONE) {
                                    Spacer(Modifier.width(6.dp))
                                    StatusPill(text = "Scheduled")
                                }
                            }
                            if (state.unassignedOnly) {
                                Spacer(Modifier.height(4.dp))
                                ConfidencePill(Confidence.ACCOUNT_UNKNOWN)
                            }
                        }
                        MoneyText(
                            money = expense.amount,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

