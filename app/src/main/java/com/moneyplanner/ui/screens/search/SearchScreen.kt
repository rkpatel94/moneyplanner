package com.moneyplanner.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.InitialsAvatar
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.theme.MoneyTheme

/**
 * One search box across everything.
 *
 * People do not think in terms of which screen a record lives on. They think "what did I
 * spend with Amit" or "show me fuel", so expenses, people, loans and bills are all
 * searched together and grouped in the results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onOpenEmi: (Long) -> Unit,
    onOpenBills: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val colors = MoneyTheme.colors

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
                label = { Text("Search your money") },
                placeholder = { Text("Try fuel, Amit, or bike EMI") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            if (state.suggestions.isNotEmpty() && query.isBlank()) {
                ChipSelector(
                    label = "Common searches",
                    options = state.suggestions,
                    selected = null,
                    onSelect = viewModel::updateQuery,
                    optionLabel = { it },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (query.isNotBlank() && state.isEmpty) {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "Nothing found",
                    message = "No expenses, people, loans or bills match \"$query\"."
                )
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (state.people.isNotEmpty()) {
                    item { GroupHeader("People") }
                    items(state.people, key = { "person-${it.person.id}" }) { summary ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPerson(summary.person.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialsAvatar(summary.person.name, size = 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(summary.person.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    summary.person.relation.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MoneyText(
                                money = summary.displayAmount,
                                color = when {
                                    summary.theyOweMe -> colors.positive
                                    summary.iOweThem -> colors.negative
                                    else -> colors.neutral
                                }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (state.emis.isNotEmpty()) {
                    item { GroupHeader("Loans") }
                    items(state.emis, key = { "emi-${it.id}" }) { emi ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenEmi(emi.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(emi.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${emi.frequency.label} installment",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MoneyText(emi.emiAmount)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (state.bills.isNotEmpty()) {
                    item { GroupHeader("Bills") }
                    items(state.bills, key = { "bill-${it.id}" }) { bill ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenBills() }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bill.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${bill.frequency.label} bill",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MoneyText(bill.amount)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (state.expenses.isNotEmpty()) {
                    item {
                        GroupHeader(
                            "Expenses" + if (state.expenseTotal.isPositive) {
                                " · ${com.moneyplanner.core.money.IndianFormat.format(state.expenseTotal)}"
                            } else {
                                ""
                            }
                        )
                    }
                    items(state.expenses, key = { "expense-${it.expense.id}" }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenExpense(row.expense.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    row.expense.description.ifBlank { row.categoryName },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${DateUtil.formatDate(row.expense.date)} · ${row.categoryName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (row.personName != null) {
                                        Spacer(Modifier.width(6.dp))
                                        StatusPill(text = row.personName)
                                    }
                                }
                            }
                            MoneyText(row.expense.amount)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}
