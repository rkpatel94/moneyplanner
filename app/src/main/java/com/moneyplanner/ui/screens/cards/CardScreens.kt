package com.moneyplanner.ui.screens.cards

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.calc.StatementBill
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DateField
import com.moneyplanner.ui.components.DayOfMonthField
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.GoalProgressBar
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.components.SwitchRow
import com.moneyplanner.ui.theme.MoneyTheme
import java.time.LocalDate

// ---- List ----------------------------------------------------------------------------

/**
 * Every card, leading with what is due and when.
 *
 * Each card says three things apart: the bill the bank has issued and what is left of it,
 * what has been spent since, and the two together. "Pay bill" asks how and from where.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    onBack: () -> Unit,
    onAddCard: () -> Unit,
    onOpenCard: (Long) -> Unit,
    viewModel: CardsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val paymentForm by viewModel.payment.form.collectAsStateWithLifecycle()

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
        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Default.CreditCard,
                title = "No cards added",
                message = "Add your credit cards so each bill appears in your forecast on " +
                    "the day it is really due, and you get a reminder before it.",
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
            item(key = "overview") { CardsOverview(state) }

            items(state.active, key = { "card-${it.card.id}" }) { summary ->
                CardTile(
                    summary = summary,
                    today = state.today,
                    onOpen = { onOpenCard(summary.card.id) },
                    onPay = { viewModel.payment.startPaying(summary, state.accounts) }
                )
            }

            if (state.inactive.isNotEmpty()) {
                item(key = "inactive-header") {
                    Text(
                        "Not in use",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.inactive, key = { "inactive-${it.card.id}" }) { summary ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCard(summary.card.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(summary.card.name, modifier = Modifier.weight(1f))
                        MoneyText(summary.owed.coerceAtLeastZero())
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            item(key = "footnote") {
                Text(
                    "Spending on a card raises what it owes. The money leaves your bank " +
                        "balance when you pay the card bill, which is when your forecast counts it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    paymentForm?.let { CardPaymentDialog(form = it, controller = viewModel.payment) }
}

@Composable
private fun CardsOverview(state: CardsState) {
    SectionCard(title = "All cards") {
        SummaryRow("Owed across your cards", state.totalOwed, emphasise = true)
        state.nextDue?.let { next ->
            SummaryRow(
                label = "Next bill: ${next.card.name}",
                amount = next.bill.remaining,
                supporting = "Due " + DateUtil.formatDayMonth(next.bill.dueOn)
            )
        }
        if (state.totalLimit.isPositive) {
            SummaryRow("Still available to spend", state.available)
        }
    }
}

@Composable
private fun CardTile(
    summary: CardSummary,
    today: LocalDate,
    onOpen: () -> Unit,
    onPay: () -> Unit
) {
    val colors = MoneyTheme.colors
    val card = summary.card
    SectionCard(onClick = onOpen) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    cardSubtitle(card.bank, card.lastFourDigits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open ${card.name}")
        }

        Spacer(Modifier.height(10.dp))
        BillLine(summary.bill, today)

        if (summary.unbilled.isPositive) {
            val nextDue = summary.upcoming.lastOrNull()?.dueOn
            SummaryRow(
                label = "Spent since the statement",
                amount = summary.unbilled,
                supporting = buildString {
                    append(if (summary.unbilledCount == 1) "1 purchase" else "${summary.unbilledCount} purchases")
                    if (nextDue != null) append(" · on the bill due ${DateUtil.formatDayMonth(nextDue)}")
                }
            )
        }

        HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(vertical = 6.dp))
        OwedRow(summary)

        if (card.creditLimit.isPositive) {
            Spacer(Modifier.height(6.dp))
            GoalProgressBar(
                fraction = summary.utilisation,
                color = if (summary.utilisation > 0.7f) colors.warning else MaterialTheme.colorScheme.primary,
                label = "${(summary.utilisation * 100).toInt()} percent of the limit used"
            )
        }

        summary.newerStatement?.let { cut ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Your ${DateUtil.formatDayMonth(cut)} statement is out. Open the card to enter its amount.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onWarningContainer
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Details") }
            PrimaryActionButton(text = "Pay bill", onClick = onPay, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OwedRow(summary: CardSummary) {
    if (summary.isInCredit) {
        SummaryRow(
            label = "In credit",
            amount = summary.owed.abs(),
            supporting = "Paid more than was owed; it comes off the next bill",
            amountColor = MoneyTheme.colors.positive
        )
    } else {
        SummaryRow("Owed in total", summary.owed, emphasise = true)
    }
}

/** The bill already issued: what is left of it, and when it is due. */
@Composable
private fun BillLine(bill: StatementBill, today: LocalDate) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Bill", style = MaterialTheme.typography.labelMedium, color = MoneyTheme.colors.textSecondary)
            MoneyText(bill.remaining, style = MaterialTheme.typography.titleLarge)
            if (bill.minimumRemaining.isPositive && bill.minimumRemaining < bill.remaining) {
                Text(
                    "Minimum ${IndianFormat.format(bill.minimumRemaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DuePill(bill, today)
    }
}

@Composable
private fun DuePill(bill: StatementBill, today: LocalDate) {
    val colors = MoneyTheme.colors
    if (bill.isPaid) {
        StatusPill(text = "Paid", containerColor = colors.positiveContainer, contentColor = colors.positive)
        return
    }
    val days = bill.daysUntilDue(today)
    val text = when {
        days < -1L -> "Overdue ${-days} days"
        days == -1L -> "Overdue 1 day"
        days == 0L -> "Due today"
        days == 1L -> "Due tomorrow"
        else -> "Due ${DateUtil.formatDayMonth(bill.dueOn)} · $days days"
    }
    val urgent = days <= 3L
    StatusPill(
        text = text,
        containerColor = if (days < 0) colors.negativeContainer else if (urgent) colors.warningContainer else colors.neutralContainer,
        contentColor = if (days < 0) colors.negative else if (urgent) colors.onWarningContainer else colors.textSecondary
    )
}

private fun cardSubtitle(bank: String, lastFour: String?): String = buildString {
    append(bank.ifBlank { "Credit card" })
    lastFour?.let { append(" · ending $it") }
}

// ---- One card --------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    onBack: () -> Unit,
    onEditCard: (Long) -> Unit,
    onOpenExpense: (Long) -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val paymentForm by viewModel.payment.form.collectAsStateWithLifecycle()
    val statementForm by viewModel.statement.collectAsStateWithLifecycle()
    var confirmingDelete by remember { mutableStateOf(false) }
    val summary = state.summary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.card?.name ?: "Card", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    summary?.let {
                        IconButton(onClick = { onEditCard(it.card.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit card")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (summary == null) {
            if (state.isMissing) {
                EmptyState(
                    icon = Icons.Default.CreditCard,
                    title = "This card was removed",
                    message = "It is no longer in your records.",
                    actionLabel = "Back",
                    onAction = onBack,
                    modifier = Modifier.padding(padding)
                )
            }
            return@Scaffold
        }
        val card = summary.card
        val bill = summary.bill
        val colors = MoneyTheme.colors

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "statement") {
                SectionCard(title = "Bill") {
                    Text(
                        "From your statement of ${DateUtil.formatDate(bill.statementOn)}" +
                            if (card.statementDate == null) ", worked out from your billing day" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    BillLine(bill, state.today)
                    Text(
                        "Payable by ${DateUtil.formatDate(bill.dueOn)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bill.paidSinceStatement.isPositive) {
                        SummaryRow("Paid since the statement", bill.paidSinceStatement)
                    }
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                    OwedRow(summary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::startStatement, modifier = Modifier.weight(1f)) {
                            Text("New statement")
                        }
                        PrimaryActionButton(
                            text = "Pay bill",
                            onClick = { viewModel.payment.startPaying(summary, state.accounts) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            summary.newerStatement?.let { cut ->
                item(key = "newer") {
                    SectionCard(containerColor = colors.warningContainer, borderColor = colors.warningBorder) {
                        Text(
                            "Your ${DateUtil.formatDayMonth(cut)} statement is out",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onWarningContainer
                        )
                        Text(
                            "Enter its amount from the bank so the bill, the forecast and your " +
                                "reminder use the bank's figure. Your own records add up to about " +
                                IndianFormat.format(summary.newerStatementEstimate ?: Money.ZERO) + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onWarningContainer
                        )
                        TextButton(onClick = viewModel::startStatement) { Text("Enter statement") }
                    }
                }
            }

            item(key = "unbilled") {
                SectionCard(title = "Spent since the statement") {
                    if (state.purchasesSinceStatement.isEmpty()) {
                        Text(
                            "Nothing charged to this card since ${DateUtil.formatDayMonth(bill.statementOn)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        summary.upcoming.forEach { upcoming ->
                            SummaryRow(
                                label = "On the ${DateUtil.formatDayMonth(upcoming.statementOn)} statement",
                                amount = upcoming.amount,
                                supporting = "Due ${DateUtil.formatDayMonth(upcoming.dueOn)} · " +
                                    if (upcoming.purchaseCount == 1) "1 purchase" else "${upcoming.purchaseCount} purchases"
                            )
                        }
                        HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                        state.purchasesSinceStatement.take(30).forEach { expense ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenExpense(expense.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(expense.description.ifBlank { "Card purchase" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        DateUtil.formatDayMonth(expense.date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                MoneyText(expense.amount)
                            }
                        }
                    }
                }
            }

            item(key = "payments") {
                SectionCard(title = "Payments") {
                    if (state.payments.isEmpty()) {
                        Text(
                            "No payments recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.payments.take(24).forEach { payment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.payment.startEditing(payment, card.name, state.accounts) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(DateUtil.formatDate(payment.paidDate))
                                Text(
                                    buildString {
                                        append(payment.paymentMethod.label)
                                        val account = payment.accountId?.let { state.accountNames[it] }
                                        append(if (account != null) " · from $account" else " · no account")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MoneyText(payment.amount, color = colors.negative)
                        }
                    }
                    if (state.payments.isNotEmpty()) {
                        Text(
                            "Tap a payment to correct or remove it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }
                }
            }

            item(key = "about") {
                SectionCard(title = "Card") {
                    if (card.creditLimit.isPositive) {
                        SummaryRow("Credit limit", card.creditLimit)
                        SummaryRow("Still available", (card.creditLimit - summary.owed).coerceAtLeastZero())
                        GoalProgressBar(
                            fraction = summary.utilisation,
                            color = if (summary.utilisation > 0.7f) colors.warning else MaterialTheme.colorScheme.primary,
                            label = "${(summary.utilisation * 100).toInt()} percent of the limit used"
                        )
                    }
                    Text(
                        "Statement on the ${ordinal(card.statementDayOfMonth)}, payable by the " +
                            "${ordinal(card.dueDayOfMonth)}." +
                            card.lastFourDigits?.let { " Ending $it." }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            item(key = "delete") {
                TextButton(onClick = { confirmingDelete = true }) {
                    Text("Delete this card", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    paymentForm?.let { CardPaymentDialog(form = it, controller = viewModel.payment) }
    statementForm?.let { StatementDialog(form = it, viewModel = viewModel) }
    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete ${summary?.card?.name ?: "this card"}?",
            message = "Its payments are deleted with it. Purchases made on it stay in your " +
                "expenses. To keep its history, switch it off in Edit instead.",
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmingDelete = false }
        )
    }
}

private fun ordinal(day: Int): String {
    val suffix = if (day in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

// ---- Sheets ----------------------------------------------------------------------------

/**
 * How much, how, from where and when. The same four questions as any other payment, since
 * a card bill is real money leaving a real account.
 */
@Composable
fun CardPaymentDialog(form: CardPaymentForm, controller: CardPaymentController) {
    AlertDialog(
        onDismissRequest = controller::dismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(if (form.isEditing) "Edit payment" else "Pay ${form.cardName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (form.presets.isNotEmpty()) {
                    ChipSelector(
                        label = "Pay",
                        options = form.presets,
                        selected = form.presets.firstOrNull { it.amount == form.parsedAmount },
                        onSelect = controller::selectPreset,
                        optionLabel = { "${it.label} ${IndianFormat.format(it.amount)}" }
                    )
                }
                AmountField(
                    value = form.amountText,
                    onValueChange = controller::updateAmount,
                    label = "Amount paid",
                    isError = form.error != null,
                    errorMessage = form.error,
                    imeAction = ImeAction.Done
                )
                ChipSelector(
                    label = "Paid by",
                    options = CardPaymentController.METHODS,
                    selected = form.method,
                    onSelect = controller::selectMethod,
                    optionLabel = { it.label }
                )
                if (form.accounts.isNotEmpty()) {
                    ChipSelector(
                        label = "From account",
                        options = form.accounts,
                        selected = form.accounts.firstOrNull { it.id == form.accountId },
                        onSelect = { controller.selectAccount(it.id) },
                        optionLabel = { it.name }
                    )
                }
                DateField(date = form.date, onDateChange = controller::updateDate, label = "Paid on")
                LabelledTextField(
                    value = form.notes,
                    onValueChange = controller::updateNotes,
                    label = "Note (optional)",
                    imeAction = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(onClick = controller::save, enabled = !form.isSaving) {
                Text(
                    if (form.isEditing) "Save"
                    else form.parsedAmount?.let { "Record ${IndianFormat.format(it)}" } ?: "Record payment"
                )
            }
        },
        dismissButton = {
            Row {
                if (form.isEditing) {
                    TextButton(onClick = controller::delete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = controller::dismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun StatementDialog(form: StatementForm, viewModel: CardDetailViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissStatement,
        shape = MaterialTheme.shapes.large,
        title = { Text("New statement") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Copy the figures from your bank's statement. Payments you record after " +
                        "the statement date come off it here automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DateField(date = form.date, onDateChange = viewModel::updateStatementDate, label = "Statement date")
                AmountField(
                    value = form.amountText,
                    onValueChange = viewModel::updateStatementAmount,
                    label = "Statement amount",
                    isError = form.error != null,
                    errorMessage = form.error
                )
                AmountField(
                    value = form.minimumText,
                    onValueChange = viewModel::updateStatementMinimum,
                    label = "Minimum due (optional)",
                    imeAction = ImeAction.Done
                )
                if (form.estimate.isPositive) {
                    Text(
                        "Your own records add up to about ${IndianFormat.format(form.estimate)}. " +
                            "Use the bank's figure: it includes any interest and fees.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveStatement) { Text("Save statement") } },
        dismissButton = { TextButton(onClick = viewModel::dismissStatement) { Text("Cancel") } }
    )
}

// ---- Add or edit -----------------------------------------------------------------------

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
            LabelledTextField(value = form.bank, onValueChange = viewModel::updateBank, label = "Bank")
            LabelledTextField(
                value = form.lastFour,
                onValueChange = viewModel::updateLastFour,
                label = "Last four digits (optional)",
                keyboardType = KeyboardType.Number,
                supportingText = "Only the last four digits are stored. They match bank SMS alerts to this card."
            )
            AmountField(value = form.limitText, onValueChange = viewModel::updateLimit, label = "Credit limit")
            DayOfMonthField(day = form.statementDay, onDayChange = viewModel::updateStatementDay, label = "Statement date each month")
            DayOfMonthField(day = form.dueDay, onDayChange = viewModel::updateDueDay, label = "Payment due date each month")

            if (!form.isEditing) {
                Text(
                    "Last statement",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "From your most recent bill. Leave it at zero if nothing is due.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AmountField(value = form.outstandingText, onValueChange = viewModel::updateOutstanding, label = "Statement amount")
                AmountField(value = form.minimumDueText, onValueChange = viewModel::updateMinimumDue, label = "Minimum due")
                form.statementDate?.let { date ->
                    DateField(date = date, onDateChange = viewModel::updateStatementDate, label = "Statement date")
                }
            } else {
                Text(
                    "To change what the card owes, use New statement on the card's page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SwitchRow(label = "Card is in use", checked = form.isActive, onCheckedChange = viewModel::updateActive)
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
