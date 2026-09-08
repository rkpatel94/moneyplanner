package com.moneyplanner.ui.screens.income

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.IncomeType
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DateField
import com.moneyplanner.ui.components.DayOfMonthField
import com.moneyplanner.ui.components.DropdownField
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.OptionalDateField
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.components.SwitchRow
import com.moneyplanner.ui.components.ordinalDay
import com.moneyplanner.ui.theme.MoneyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(
    onBack: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (Long) -> Unit,
    onAddReceipt: () -> Unit,
    onEditReceipt: (Long) -> Unit,
    viewModel: IncomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Income") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onAddReceipt) { Text("Record receipt") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSource) {
                Icon(Icons.Default.Add, contentDescription = "Add income source")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "This month") {
                    SummaryRow("Expected every month", state.expectedMonthly)
                    SummaryRow("Received so far", state.receivedThisMonth, colorBySign = true)
                }
            }

            if (state.sources.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Work,
                        title = "No income added yet",
                        message = "Add your salary and any other regular income so the app " +
                            "can work out what you will have each month.",
                        actionLabel = "Add income",
                        onAction = onAddSource
                    )
                }
            } else {
                item {
                    Text(
                        "Regular income",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(state.sources, key = { it.source.id }) { row ->
                    SectionCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditSource(row.source.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.source.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${row.source.type.label} · ${row.source.frequency.label} on the ${row.source.dayOfMonth.ordinalDay()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MoneyText(row.source.amount, style = MaterialTheme.typography.titleMedium)
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (row.receivedThisMonth) {
                                StatusPill(
                                    text = "Received this month",
                                    containerColor = MoneyTheme.colors.positiveContainer,
                                    contentColor = MoneyTheme.colors.positive
                                )
                            } else {
                                StatusPill(
                                    text = row.nextDate?.let { "Next on ${DateUtil.formatDayMonth(it)}" }
                                        ?: "No upcoming date"
                                )
                            }
                            if (!row.receivedThisMonth && row.source.isActive) {
                                TextButton(onClick = { viewModel.markReceived(row.source) }) {
                                    Text("Mark received")
                                }
                            }
                        }
                        if (!row.source.isActive) {
                            StatusPill(text = "Paused")
                        }
                    }
                }
            }

            if (state.recentReceipts.isNotEmpty()) {
                item {
                    Text(
                        "Recent receipts",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.recentReceipts, key = { it.id }) { receipt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditReceipt(receipt.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MoneyTheme.colors.positive
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(receipt.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                DateUtil.formatDate(receipt.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MoneyText(receipt.amount, colorBySign = true, showSign = true)
                        IconButton(onClick = { viewModel.deleteReceipt(receipt.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete receipt")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeSourceEditorScreen(
    onBack: () -> Unit,
    viewModel: IncomeSourceEditorViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) "Edit income" else "Add income") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (form.isEditing) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete income")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LabelledTextField(
                value = form.name,
                onValueChange = viewModel::updateName,
                label = "Name",
                isError = form.nameError != null,
                errorMessage = form.nameError,
                supportingText = "For example, Salary or Rent from shop"
            )

            AmountField(
                value = form.amountText,
                onValueChange = viewModel::updateAmount,
                isError = form.amountError != null,
                errorMessage = form.amountError
            )

            ChipSelector(
                label = "Type",
                options = IncomeType.entries,
                selected = form.type,
                onSelect = viewModel::updateType,
                optionLabel = { it.label }
            )

            DropdownField(
                value = form.frequency,
                options = Frequency.entries,
                onSelect = viewModel::updateFrequency,
                label = "How often",
                optionLabel = { it.label }
            )

            if (!form.frequency.isWeekly) {
                DayOfMonthField(
                    day = form.dayOfMonth,
                    onDayChange = viewModel::updateDay,
                    label = "Day it arrives"
                )
            }

            DateField(
                date = form.startDate,
                onDateChange = viewModel::updateStartDate,
                label = "Starting from"
            )

            OptionalDateField(
                date = form.endDate,
                onDateChange = viewModel::updateEndDate,
                label = "Has an end date",
                defaultWhenEnabled = form.startDate.plusYears(1)
            )

            if (accounts.isNotEmpty()) {
                DropdownField(
                    value = form.accountId,
                    options = accounts.map { it.id },
                    onSelect = viewModel::updateAccount,
                    label = "Paid into",
                    optionLabel = { id -> accounts.first { it.id == id }.name }
                )
            }

            LabelledTextField(
                value = form.incrementText,
                onValueChange = viewModel::updateIncrement,
                label = "Expected yearly increase (%)",
                keyboardType = KeyboardType.Decimal,
                supportingText = "Optional. Applied on each anniversary of the start date."
            )

            SwitchRow(
                label = "Currently active",
                checked = form.isActive,
                onCheckedChange = viewModel::updateActive,
                supporting = "Turn off to keep the record without including it in forecasts"
            )

            LabelledTextField(
                value = form.notes,
                onValueChange = viewModel::updateNotes,
                label = "Notes",
                singleLine = false,
                imeAction = ImeAction.Done
            )

            Spacer(Modifier.height(8.dp))
            PrimaryActionButton(
                text = if (form.isEditing) "Save changes" else "Add income",
                onClick = { viewModel.save(onBack) },
                enabled = form.canSave
            )
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete this income?",
            message = "Future months will no longer include it. Receipts you have already " +
                "recorded are kept.",
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { showDelete = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeReceiptScreen(
    onBack: () -> Unit,
    viewModel: IncomeReceiptViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var showDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (form.isEditing) "Edit receipt" else "Record money received")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (form.isEditing) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete receipt")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AmountField(
                value = form.amountText,
                onValueChange = viewModel::updateAmount,
                isError = form.amountError != null,
                errorMessage = form.amountError
            )

            LabelledTextField(
                value = form.name,
                onValueChange = viewModel::updateName,
                label = "What was it?"
            )

            ChipSelector(
                label = "Type",
                options = IncomeType.entries,
                selected = form.type,
                onSelect = viewModel::updateType,
                optionLabel = { it.label }
            )

            DateField(date = form.date, onDateChange = viewModel::updateDate)

            if (sources.isNotEmpty()) {
                DropdownField(
                    value = form.sourceId,
                    options = listOf<Long?>(null) + sources.map { it.id },
                    onSelect = viewModel::updateSource,
                    label = "Part of a regular income?",
                    optionLabel = { id ->
                        if (id == null) "One-off receipt"
                        else sources.first { it.id == id }.name
                    }
                )
                if (form.sourceId != null) {
                    Text(
                        "Linking this tells the forecast that this income has already " +
                            "arrived, so it will not be expected again this month.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (accounts.isNotEmpty()) {
                DropdownField(
                    value = form.accountId,
                    options = accounts.map { it.id },
                    onSelect = viewModel::updateAccount,
                    label = "Paid into",
                    optionLabel = { id -> accounts.first { it.id == id }.name }
                )
            }

            LabelledTextField(
                value = form.notes,
                onValueChange = viewModel::updateNotes,
                label = "Notes",
                singleLine = false,
                imeAction = ImeAction.Done
            )

            Spacer(Modifier.height(8.dp))
            PrimaryActionButton(
                text = if (form.isEditing) "Save changes" else "Record receipt",
                onClick = { viewModel.save(onBack) },
                enabled = form.canSave
            )
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete this receipt?",
            message = "The money it recorded comes back out of your balance. If it was " +
                "part of a regular income, that month will be expected again.",
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { showDelete = false }
        )
    }
}
