package com.moneyplanner.ui.screens.commitments

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.BillAmountType
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DateField
import com.moneyplanner.ui.components.DayOfMonthField
import com.moneyplanner.ui.components.DropdownField
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.GoalProgressBar
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
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

// ---- Credit cards ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    onBack: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (Long) -> Unit,
    viewModel: CardsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val payText by viewModel.payAmount.collectAsStateWithLifecycle()
    var payingCardId by remember { mutableStateOf<Long?>(null) }
    val colors = MoneyTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credit cards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCard,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add card") }
            )
        }
    ) { padding ->
        if (state.cards.isEmpty()) {
            EmptyState(
                icon = Icons.Default.CreditCard,
                title = "No cards added",
                message = "Add your credit cards so their dues appear in your forecast " +
                    "and you get a reminder before the payment date.",
                actionLabel = "Add a card",
                onAction = onAddCard,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "All cards") {
                    SummaryRow("Total outstanding", state.totalOutstanding, emphasise = true)
                    SummaryRow("Total limit", state.totalLimit)
                }
            }

            items(state.cards, key = { it.card.id }) { row ->
                val card = row.card
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append(card.bank)
                                    card.lastFourDigits?.let { append(" · ending $it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onEditCard(card.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit card")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    SummaryRow("Outstanding", card.currentOutstanding, emphasise = true)
                    if (card.minimumDue.isPositive) {
                        SummaryRow("Minimum due", card.minimumDue)
                    }

                    if (card.creditLimit.isPositive) {
                        Spacer(Modifier.height(6.dp))
                        GoalProgressBar(
                            fraction = card.utilisation,
                            color = if (card.utilisation > 0.7f) colors.warning else MaterialTheme.colorScheme.primary,
                            label = "${(card.utilisation * 100).toInt()} percent of the limit used"
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${IndianFormat.format(card.availableLimit)} still available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    row.nextDueDate?.let { due ->
                        StatusPill(
                            text = "Payment due ${DateUtil.relativeDayLabel(due, state.today).lowercase()}",
                            containerColor = if (
                                DateUtil.daysBetween(state.today, due) <= 3
                            ) colors.warningContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (
                                DateUtil.daysBetween(state.today, due) <= 3
                            ) colors.onWarningContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (card.currentOutstanding.isPositive) {
                        Spacer(Modifier.height(10.dp))
                        if (payingCardId == card.id) {
                            AmountField(
                                value = payText,
                                onValueChange = viewModel::updatePayAmount,
                                label = "Amount paid",
                                imeAction = ImeAction.Done
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryActionButton(
                                    text = "Record payment",
                                    onClick = {
                                        viewModel.payBill(card)
                                        payingCardId = null
                                    },
                                    enabled = Money.parseOrNull(payText)?.isPositive == true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { payingCardId = null },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancel") }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { payingCardId = card.id },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Pay part") }
                                PrimaryActionButton(
                                    text = "Pay full",
                                    onClick = { viewModel.payFull(card) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Spending on a card raises its outstanding. The money leaves your " +
                        "bank balance when you pay the card bill, which is when your " +
                        "forecast counts it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorScreen(
    onBack: () -> Unit,
    viewModel: CardEditorViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) "Edit card" else "Add card") },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LabelledTextField(
                value = form.name,
                onValueChange = viewModel::updateName,
                label = "Card name",
                isError = form.nameError != null,
                errorMessage = form.nameError
            )
            LabelledTextField(
                value = form.bank,
                onValueChange = viewModel::updateBank,
                label = "Bank"
            )
            LabelledTextField(
                value = form.lastFour,
                onValueChange = viewModel::updateLastFour,
                label = "Last four digits (optional)",
                keyboardType = KeyboardType.Number,
                supportingText = "Only the last four digits are stored, never the full number"
            )
            AmountField(
                value = form.limitText,
                onValueChange = viewModel::updateLimit,
                label = "Credit limit"
            )
            AmountField(
                value = form.outstandingText,
                onValueChange = viewModel::updateOutstanding,
                label = "Current outstanding"
            )
            AmountField(
                value = form.minimumDueText,
                onValueChange = viewModel::updateMinimumDue,
                label = "Minimum due"
            )
            DayOfMonthField(
                day = form.statementDay,
                onDayChange = viewModel::updateStatementDay,
                label = "Statement date"
            )
            DayOfMonthField(
                day = form.dueDay,
                onDayChange = viewModel::updateDueDay,
                label = "Payment due date"
            )
            SwitchRow(
                label = "Card is in use",
                checked = form.isActive,
                onCheckedChange = viewModel::updateActive
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
                text = if (form.isEditing) "Save changes" else "Add card",
                onClick = { viewModel.save(onBack) },
                enabled = form.canSave
            )
        }
    }
}

// ---- Recurring bills -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    onBack: () -> Unit,
    onAddBill: () -> Unit,
    onEditBill: (Long) -> Unit,
    viewModel: BillsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amounts by viewModel.paidAmounts.collectAsStateWithLifecycle()
    val colors = MoneyTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring bills") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBill,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add bill") }
            )
        }
    ) { padding ->
        if (state.active.isEmpty() && state.inactive.isEmpty()) {
            EmptyState(
                icon = Icons.Default.EventRepeat,
                title = "No recurring bills",
                message = "Add your rent, electricity, internet and other regular bills " +
                    "so every month knows what has to be paid.",
                actionLabel = "Add a bill",
                onAction = onAddBill,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = DateUtil.formatMonth(state.month)) {
                    SummaryRow("Bills due this month", state.monthlyTotal)
                    SummaryRow("Still to pay", state.unpaidThisMonth, emphasise = true)
                }
            }

            items(state.active, key = { it.bill.id }) { row ->
                val bill = row.bill
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = row.isPaidThisMonth,
                            onCheckedChange = { checked ->
                                if (checked) viewModel.markPaid(row) else viewModel.undoPaid(row)
                            },
                            enabled = row.dueThisMonth != null
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bill.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append("${bill.frequency.label} on the ${bill.dueDayOfMonth.ordinalDay()}")
                                    if (bill.amountType == BillAmountType.ESTIMATED) append(" · estimated")
                                    if (bill.autoDebit) append(" · auto debit")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MoneyText(bill.amount, style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { onEditBill(bill.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit bill")
                            }
                        }
                    }

                    if (row.isPaidThisMonth) {
                        StatusPill(
                            text = "Paid this month",
                            containerColor = colors.positiveContainer,
                            contentColor = colors.positive
                        )
                    } else if (bill.amountType == BillAmountType.ESTIMATED && row.dueThisMonth != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = amounts[bill.id].orEmpty(),
                            onValueChange = { text ->
                                val filtered = text.filter { it.isDigit() || it == '.' }
                                viewModel.updatePaidAmount(bill.id, filtered)
                            },
                            label = { Text("Actual amount when you pay") },
                            prefix = { Text("₹") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            )
                        )
                        bill.minAmount?.let { min ->
                            bill.maxAmount?.let { max ->
                                Text(
                                    "Usually between ${IndianFormat.format(min)} and ${IndianFormat.format(max)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    row.nextDueDate?.takeIf { !row.isPaidThisMonth }?.let { due ->
                        Spacer(Modifier.height(6.dp))
                        StatusPill(text = "Due ${DateUtil.relativeDayLabel(due, state.today).lowercase()}")
                    }
                }
            }

            if (state.inactive.isNotEmpty()) {
                item {
                    Text(
                        "Not active",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.inactive, key = { it.bill.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditBill(row.bill.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(row.bill.name, modifier = Modifier.weight(1f))
                        MoneyText(row.bill.amount)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditorScreen(
    onBack: () -> Unit,
    viewModel: BillEditorViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) "Edit bill" else "Add bill") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (form.isEditing) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete bill")
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
                label = "Bill name",
                supportingText = "For example, Rent, Electricity or School fees",
                isError = form.nameError != null,
                errorMessage = form.nameError
            )

            ChipSelector(
                label = "Amount",
                options = BillAmountType.entries,
                selected = form.amountType,
                onSelect = viewModel::updateAmountType,
                optionLabel = { it.label }
            )

            AmountField(
                value = form.amountText,
                onValueChange = viewModel::updateAmount,
                label = if (form.amountType == BillAmountType.ESTIMATED) {
                    "Typical amount"
                } else {
                    "Amount"
                },
                isError = form.amountError != null,
                errorMessage = form.amountError
            )

            if (form.amountType == BillAmountType.ESTIMATED) {
                Text(
                    "Bills like electricity change every month. The forecast uses your " +
                        "typical amount, and you enter the real figure when you pay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField(
                        value = form.minText,
                        onValueChange = viewModel::updateMin,
                        label = "Lowest",
                        modifier = Modifier.weight(1f)
                    )
                    AmountField(
                        value = form.maxText,
                        onValueChange = viewModel::updateMax,
                        label = "Highest",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            DropdownField(
                value = form.frequency,
                options = Frequency.entries,
                onSelect = viewModel::updateFrequency,
                label = "How often",
                optionLabel = { it.label }
            )

            if (!form.frequency.isWeekly) {
                DayOfMonthField(
                    day = form.dueDay,
                    onDayChange = viewModel::updateDueDay,
                    label = "Due day"
                )
            }

            if (categories.isNotEmpty()) {
                DropdownField(
                    value = form.categoryId,
                    options = listOf<Long?>(null) + categories.map { it.id },
                    onSelect = viewModel::updateCategory,
                    label = "Category",
                    optionLabel = { id ->
                        if (id == null) "No category"
                        else categories.first { it.id == id }.name
                    }
                )
            }

            DropdownField(
                value = form.paymentMethod,
                options = PaymentMethod.entries,
                onSelect = viewModel::updatePaymentMethod,
                label = "Usually paid by",
                optionLabel = { it.label }
            )

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

            SwitchRow(
                label = "Essential",
                checked = form.isEssential,
                onCheckedChange = viewModel::updateEssential,
                supporting = "Essential bills count towards your emergency fund target"
            )

            SwitchRow(
                label = "Paid by auto debit",
                checked = form.autoDebit,
                onCheckedChange = viewModel::updateAutoDebit
            )

            SwitchRow(
                label = "Currently active",
                checked = form.isActive,
                onCheckedChange = viewModel::updateActive
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
                text = if (form.isEditing) "Save changes" else "Add bill",
                onClick = { viewModel.save(onBack) },
                enabled = form.canSave
            )
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete this bill?",
            message = "It will stop appearing in future months. Payments you have already " +
                "recorded are kept.",
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { showDelete = false }
        )
    }
}

// ---- Annual expenses -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualScreen(
    onBack: () -> Unit,
    onAddAnnual: () -> Unit,
    onEditAnnual: (Long) -> Unit,
    viewModel: AnnualViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MoneyTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yearly expenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddAnnual,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add expense") }
            )
        }
    ) { padding ->
        if (state.expenses.isEmpty() && state.inactive.isEmpty()) {
            EmptyState(
                icon = Icons.Default.CalendarMonth,
                title = "No yearly expenses",
                message = "Insurance, school fees and property tax arrive once a year and " +
                    "can break a monthly budget. Add them and the app will show what to " +
                    "set aside each month.",
                actionLabel = "Add a yearly expense",
                onAction = onAddAnnual,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "Across the year") {
                    SummaryRow("Total for the year", state.yearlyTotal)
                    SummaryRow(
                        label = "Set aside each month",
                        amount = state.monthlyReserve,
                        supporting = "So these never arrive as a shock",
                        emphasise = true
                    )
                }
            }

            items(state.expenses, key = { it.expense.id }) { row ->
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.expense.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Due ${DateUtil.formatDate(row.dueDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MoneyText(row.expense.amount, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${IndianFormat.format(row.expense.monthlyReserve)} a month",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onEditAnnual(row.expense.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit expense")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            row.isPaidThisYear -> StatusPill(
                                text = "Paid for ${state.today.year}",
                                containerColor = colors.positiveContainer,
                                contentColor = colors.positive
                            )
                            row.isOverdue -> StatusPill(
                                text = "Overdue",
                                containerColor = colors.warningContainer,
                                contentColor = colors.onWarningContainer
                            )
                            else -> StatusPill(
                                text = DateUtil.relativeDayLabel(row.dueDate, state.today)
                            )
                        }
                        if (row.isPaidThisYear) {
                            TextButton(onClick = { viewModel.undoPaid(row) }) { Text("Undo") }
                        } else {
                            TextButton(onClick = { viewModel.markPaid(row) }) { Text("Mark paid") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualEditorScreen(
    onBack: () -> Unit,
    viewModel: AnnualEditorViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) "Edit yearly expense" else "Add yearly expense") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (form.isEditing) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete expense")
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
                supportingText = "For example, Vehicle insurance or School admission",
                isError = form.nameError != null,
                errorMessage = form.nameError
            )

            AmountField(
                value = form.amountText,
                onValueChange = viewModel::updateAmount,
                label = "Amount for the year",
                isError = form.amountError != null,
                errorMessage = form.amountError
            )

            if (form.monthlyReserve.isPositive) {
                SectionCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "Set aside ${IndianFormat.format(form.monthlyReserve)} every month",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "That is this expense spread across twelve months.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            DropdownField(
                value = form.dueMonth,
                options = (1..12).toList(),
                onSelect = viewModel::updateDueMonth,
                label = "Month it is due",
                optionLabel = { Month.of(it).getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
            )

            DayOfMonthField(
                day = form.dueDay,
                onDayChange = viewModel::updateDueDay,
                label = "Day it is due"
            )

            if (options.categories.isNotEmpty()) {
                DropdownField(
                    value = form.categoryId,
                    options = listOf<Long?>(null) + options.categories.map { it.id },
                    onSelect = viewModel::updateCategory,
                    label = "Category",
                    optionLabel = { id ->
                        if (id == null) "No category"
                        else options.categories.first { it.id == id }.name
                    }
                )
            }

            if (options.vehicles.isNotEmpty()) {
                DropdownField(
                    value = form.vehicleId,
                    options = listOf<Long?>(null) + options.vehicles.map { it.id },
                    onSelect = viewModel::updateVehicle,
                    label = "Related vehicle",
                    optionLabel = { id ->
                        if (id == null) "Not vehicle related"
                        else options.vehicles.first { it.id == id }.name
                    }
                )
            }

            SwitchRow(
                label = "Currently active",
                checked = form.isActive,
                onCheckedChange = viewModel::updateActive
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
                text = if (form.isEditing) "Save changes" else "Add expense",
                onClick = { viewModel.save(onBack) },
                enabled = form.canSave
            )
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete this yearly expense?",
            message = "It will no longer appear in your forecast.",
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { showDelete = false }
        )
    }
}
