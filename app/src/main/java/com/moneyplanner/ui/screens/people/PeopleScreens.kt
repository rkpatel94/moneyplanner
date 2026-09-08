package com.moneyplanner.ui.screens.people

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.calc.PersonBalanceSummary
import com.moneyplanner.domain.calc.SplitValidation
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.Relation
import com.moneyplanner.domain.model.SettlementDirection
import com.moneyplanner.domain.model.SplitType
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DateField
import com.moneyplanner.ui.components.DropdownField
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.InitialsAvatar
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.LoadingState
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.OptionalDateField
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.components.SummaryRow
import com.moneyplanner.ui.theme.MoneyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onBack: () -> Unit,
    onAddPerson: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onAddSharedExpense: () -> Unit,
    viewModel: PeopleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddSharedExpense,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Split a bill") }
            )
        }
    ) { padding ->
        if (!state.hasAnyone) {
            EmptyState(
                icon = Icons.Default.Groups,
                title = "No people yet",
                message = "Add the people you lend to, borrow from, or split bills with, " +
                    "and the app will keep track of who owes what.",
                actionLabel = "Add a person",
                onAction = onAddPerson,
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
                SectionCard(title = "Overall") {
                    SummaryRow("Owed to you", state.totalReceivable, colorBySign = true, showSign = true)
                    SummaryRow("You owe", -state.totalPayable, colorBySign = true, showSign = true)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SummaryRow(
                        label = "Net position",
                        amount = state.totalReceivable - state.totalPayable,
                        colorBySign = true,
                        showSign = true,
                        emphasise = true
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = onAddPerson,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add a person")
                }
            }

            if (state.owedToMe.isNotEmpty()) {
                item { GroupHeader("Owe you") }
                items(state.owedToMe, key = { it.person.id }) { summary ->
                    PersonRow(summary, onClick = { onOpenPerson(summary.person.id) })
                }
            }

            if (state.iOwe.isNotEmpty()) {
                item { GroupHeader("You owe") }
                items(state.iOwe, key = { it.person.id }) { summary ->
                    PersonRow(summary, onClick = { onOpenPerson(summary.person.id) })
                }
            }

            if (state.settled.isNotEmpty()) {
                item { GroupHeader("All settled") }
                items(state.settled, key = { it.person.id }) { summary ->
                    PersonRow(summary, onClick = { onOpenPerson(summary.person.id) })
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
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun PersonRow(summary: PersonBalanceSummary, onClick: () -> Unit) {
    val colors = MoneyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(summary.person.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.person.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary.person.relation.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                money = summary.displayAmount,
                color = when {
                    summary.theyOweMe -> colors.positive
                    summary.iOweThem -> colors.negative
                    else -> colors.neutral
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                when {
                    summary.theyOweMe -> "owes you"
                    summary.iOweThem -> "you owe"
                    else -> "settled"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One person's full history.
 *
 * Each obligation shows how much of it has been settled, so a part payment is visible
 * against the specific loan it went towards rather than only in a single net figure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onAddObligation: () -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settleForm by viewModel.settleForm.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var showDeletePerson by remember { mutableStateOf(false) }
    val colors = MoneyTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current

    if (state.isLoading) {
        LoadingState()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.person?.name ?: "Person") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.balance.isPositive) {
                        IconButton(onClick = {
                            shareReminder(
                                context = context,
                                name = state.person?.name.orEmpty(),
                                amount = state.balance
                            )
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Send a reminder")
                        }
                    }
                    IconButton(onClick = { showDeletePerson = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete person")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddObligation,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add entry") }
            )
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
                SectionCard {
                    Text(
                        when {
                            state.balance.isPositive -> "${state.person?.name} owes you"
                            state.balance.isNegative -> "You owe ${state.person?.name}"
                            else -> "All settled"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        money = state.balance.abs(),
                        style = MaterialTheme.typography.displaySmall,
                        color = when {
                            state.balance.isPositive -> colors.positive
                            state.balance.isNegative -> colors.negative
                            else -> colors.neutral
                        }
                    )

                    if (state.allocation?.hasOverpayment == true) {
                        Spacer(Modifier.height(8.dp))
                        StatusPill(
                            text = "More has been settled than was owed. Check the entries below.",
                            containerColor = colors.warningContainer,
                            contentColor = colors.onWarningContainer
                        )
                    }

                    if (!state.balance.isZero) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.startSettlement(state.balance, full = false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (state.balance.isPositive) "Record part payment"
                                    else "Record a payment"
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.startSettlement(state.balance, full = true) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Settle in full") }
                        }
                    }
                }
            }

            val rows = state.allocation?.rows.orEmpty()
            if (rows.isNotEmpty()) {
                item { GroupHeader("Entries") }
                items(rows, key = { it.entry.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                row.entry.description.ifBlank {
                                    if (row.entry.direction == LedgerDirection.THEY_OWE_ME) {
                                        "Money lent"
                                    } else {
                                        "Money borrowed"
                                    }
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                buildString {
                                    append(DateUtil.formatDate(row.entry.date))
                                    row.entry.expectedDate?.let {
                                        append(" · expected ${DateUtil.formatDayMonth(it)}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (row.settledAmount.isPositive && !row.isFullySettled) {
                                Text(
                                    "${IndianFormat.format(row.settledAmount)} of ${IndianFormat.format(row.entry.amount)} settled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.neutral
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MoneyText(
                                money = row.outstandingAmount,
                                color = if (row.entry.direction == LedgerDirection.THEY_OWE_ME) {
                                    colors.positive
                                } else {
                                    colors.negative
                                }
                            )
                            if (row.isFullySettled) StatusPill(text = "Settled")
                        }
                        IconButton(onClick = { viewModel.deleteEntry(row.entry.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete entry")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            if (state.settlements.isNotEmpty()) {
                item { GroupHeader("Settlement history") }
                items(state.settlements, key = { it.id }) { settlement ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.startEditingSettlement(settlement) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (settlement.direction == SettlementDirection.RECEIVED_FROM_THEM) {
                                    "Received"
                                } else {
                                    "Paid"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "${DateUtil.formatDate(settlement.date)} · " +
                                    settlement.paymentMethod.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MoneyText(
                            money = settlement.amount,
                            color = if (settlement.direction == SettlementDirection.RECEIVED_FROM_THEM) {
                                colors.positive
                            } else {
                                colors.negative
                            }
                        )
                        IconButton(onClick = { viewModel.deleteSettlement(settlement.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete settlement")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showDeletePerson) {
        ConfirmDialog(
            title = "Delete ${state.person?.name}?",
            message = "Every entry and settlement recorded against them will be removed too.",
            onConfirm = { viewModel.deletePerson(onBack) },
            onDismiss = { showDeletePerson = false }
        )
    }

    if (settleForm.isOpen) {
        SettleDialog(
            form = settleForm,
            accounts = accounts,
            personName = state.person?.name.orEmpty(),
            viewModel = viewModel
        )
    }
}

/**
 * Recording money actually changing hands with a person.
 *
 * A settlement moves real cash, so it asks the same questions the expense form asks:
 * how much, how, from which account, and on what date. Answering them is what lets the
 * per-account balances still add up afterwards.
 */
@Composable
private fun SettleDialog(
    form: SettleForm,
    accounts: List<Account>,
    personName: String,
    viewModel: PersonDetailViewModel
) {
    val colors = MoneyTheme.colors

    AlertDialog(
        onDismissRequest = viewModel::dismissSettlement,
        shape = MaterialTheme.shapes.large,
        title = {
            val who = personName.ifBlank { "them" }
            Text(
                when {
                    form.isEditing && form.isPayment -> "Edit payment to $who"
                    form.isEditing -> "Edit money received from $who"
                    form.isPayment -> "Pay $who"
                    else -> "Receive from $who"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "${IndianFormat.format(form.outstanding)} " +
                        if (form.isPayment) "still to pay" else "still to come",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AmountField(
                    value = form.amountText,
                    onValueChange = viewModel::updateSettleAmount,
                    label = if (form.isPayment) "Amount paid" else "Amount received",
                    isError = form.error != null,
                    errorMessage = form.error
                )

                // Only the methods that actually move cash. Settling a debt on a credit
                // card would leave the balance falling with no account behind it, because
                // card spending does not reduce cash until the card bill is paid.
                ChipSelector(
                    label = if (form.isPayment) "Paid by" else "Received by",
                    options = PaymentMethod.entries.filter { it.reducesCashImmediately },
                    selected = form.paymentMethod,
                    onSelect = viewModel::updateSettleMethod,
                    optionLabel = { it.label }
                )

                if (accounts.isNotEmpty()) {
                    DropdownField(
                        value = form.accountId,
                        options = accounts.map { it.id },
                        onSelect = viewModel::updateSettleAccount,
                        label = if (form.isPayment) "Paid from" else "Received into",
                        optionLabel = { id -> accounts.first { it.id == id }.name }
                    )
                }

                DateField(date = form.date, onDateChange = viewModel::updateSettleDate)

                LabelledTextField(
                    value = form.notes,
                    onValueChange = viewModel::updateSettleNotes,
                    label = "Note (optional)",
                    imeAction = ImeAction.Done
                )

                if (form.isOverpayment) {
                    StatusPill(
                        text = "That is more than is owed. It will be recorded and the " +
                            "balance will swing the other way.",
                        containerColor = colors.warningContainer,
                        contentColor = colors.onWarningContainer
                    )
                } else if (form.canSave) {
                    Text(
                        if (form.remainingAfter.isZero) {
                            "This clears the balance."
                        } else {
                            "${IndianFormat.format(form.remainingAfter)} would still be " +
                                if (form.isPayment) "owed." else "expected."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::saveSettlement, enabled = form.canSave) {
                Text(if (form.isEditing) "Save" else "Record")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissSettlement) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: AddPersonViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add person") },
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
                label = "Name",
                isError = form.nameError != null,
                errorMessage = form.nameError
            )
            ChipSelector(
                label = "Relationship",
                options = Relation.entries.filterNot { it == Relation.SELF },
                selected = form.relation,
                onSelect = viewModel::updateRelation,
                optionLabel = { it.label }
            )
            LabelledTextField(
                value = form.phone,
                onValueChange = viewModel::updatePhone,
                label = "Phone (optional)",
                keyboardType = KeyboardType.Phone
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
                text = "Add person",
                onClick = { viewModel.save(onSaved) },
                enabled = form.canSave
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObligationEditorScreen(
    onBack: () -> Unit,
    viewModel: ObligationEditorViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val person by viewModel.person.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add entry") },
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
            ChipSelector(
                label = "Direction",
                options = LedgerDirection.entries,
                selected = form.direction,
                onSelect = viewModel::updateDirection,
                optionLabel = { direction ->
                    val name = person?.name ?: "They"
                    if (direction == LedgerDirection.THEY_OWE_ME) "$name owes me" else "I owe $name"
                }
            )
            AmountField(
                value = form.amountText,
                onValueChange = viewModel::updateAmount,
                isError = form.amountError != null,
                errorMessage = form.amountError
            )
            LabelledTextField(
                value = form.description,
                onValueChange = viewModel::updateDescription,
                label = "What is it for?"
            )
            DateField(date = form.date, onDateChange = viewModel::updateDate, label = "Date")
            OptionalDateField(
                date = form.expectedDate,
                onDateChange = viewModel::updateExpectedDate,
                label = "Expected to be settled by",
                defaultWhenEnabled = form.date.plusMonths(1)
            )
            if (form.expectedDate != null) {
                Text(
                    "Setting a date includes this in your forecast for that month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                text = "Save entry",
                onClick = { viewModel.save(onBack) },
                enabled = form.canSave
            )
        }
    }
}

/** Splitting a bill, with a live preview of what each person will owe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedExpenseScreen(
    onBack: () -> Unit,
    onAddPerson: () -> Unit,
    viewModel: SharedExpenseViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val preview = viewModel.preview()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split a bill") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (options.people.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Groups,
                title = "Add someone first",
                message = "You need at least one other person before a bill can be split.",
                actionLabel = "Add a person",
                onAction = onAddPerson,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

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
                label = "Total bill",
                isError = form.amountError != null,
                errorMessage = form.amountError
            )
            LabelledTextField(
                value = form.description,
                onValueChange = viewModel::updateDescription,
                label = "What was it for?"
            )
            DateField(date = form.date, onDateChange = viewModel::updateDate)

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

            DropdownField(
                value = form.paidByPersonId,
                options = listOf<Long?>(null) + options.people.map { it.id },
                onSelect = viewModel::updatePaidBy,
                label = "Who paid the bill",
                optionLabel = { id ->
                    if (id == null) "I paid" else options.people.first { it.id == id }.name
                }
            )

            Text("Who shared it", style = MaterialTheme.typography.titleSmall)
            Text(
                "You are always included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            options.people.forEach { person ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleParticipant(person.id) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = person.id in form.participantIds,
                        onCheckedChange = { viewModel.toggleParticipant(person.id) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(person.name, modifier = Modifier.weight(1f))
                    preview.shares[person.id]?.let { share ->
                        MoneyText(share, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            ChipSelector(
                label = "How to split",
                options = SplitType.entries,
                selected = form.splitType,
                onSelect = viewModel::updateSplitType,
                optionLabel = { it.label }
            )

            if (form.splitType == SplitType.EXACT || form.splitType == SplitType.PERCENTAGE) {
                SectionCard(title = "Each share") {
                    ShareInputRow(
                        label = "You",
                        value = if (form.splitType == SplitType.EXACT) {
                            form.exactAmounts[null].orEmpty()
                        } else {
                            form.percentages[null].orEmpty()
                        },
                        isPercent = form.splitType == SplitType.PERCENTAGE,
                        onValueChange = { text ->
                            if (form.splitType == SplitType.EXACT) {
                                viewModel.updateExactAmount(null, text)
                            } else {
                                viewModel.updatePercent(null, text)
                            }
                        }
                    )
                    options.people.filter { it.id in form.participantIds }.forEach { person ->
                        ShareInputRow(
                            label = person.name,
                            value = if (form.splitType == SplitType.EXACT) {
                                form.exactAmounts[person.id].orEmpty()
                            } else {
                                form.percentages[person.id].orEmpty()
                            },
                            isPercent = form.splitType == SplitType.PERCENTAGE,
                            onValueChange = { text ->
                                if (form.splitType == SplitType.EXACT) {
                                    viewModel.updateExactAmount(person.id, text)
                                } else {
                                    viewModel.updatePercent(person.id, text)
                                }
                            }
                        )
                    }
                }
            }

            when (val validation = preview.validation) {
                is SplitValidation.Short -> WarningText(
                    "The shares are ${IndianFormat.format(validation.by)} short of the total."
                )
                is SplitValidation.Over -> WarningText(
                    "The shares are ${IndianFormat.format(validation.by)} more than the total."
                )
                is SplitValidation.ShortPercent -> WarningText(
                    "The percentages add up to ${100 - validation.by} instead of 100."
                )
                is SplitValidation.OverPercent -> WarningText(
                    "The percentages add up to ${100 + validation.by} instead of 100."
                )
                SplitValidation.Valid -> Unit
            }

            if (preview.isValid && form.participantIds.isNotEmpty()) {
                SectionCard(title = "What this means") {
                    SummaryRow("Your share", preview.myShare)
                    if (form.paidByPersonId == null) {
                        Text(
                            "You paid the bill, so everyone else will owe you their share.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val payer = options.people.firstOrNull { it.id == form.paidByPersonId }?.name
                        Text(
                            "$payer paid, so you will owe them your share.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            PrimaryActionButton(
                text = "Save split",
                onClick = { viewModel.save(onBack) },
                enabled = preview.isValid && form.participantIds.isNotEmpty()
            )
        }
    }
}

@Composable
private fun ShareInputRow(
    label: String,
    value: String,
    isPercent: Boolean,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = { text ->
                val filtered = text.filter { it.isDigit() || it == '.' }
                if (filtered.count { it == '.' } <= 1) onValueChange(filtered)
            },
            modifier = Modifier.width(140.dp),
            singleLine = true,
            prefix = { Text(if (isPercent) "" else "₹") },
            suffix = { Text(if (isPercent) "%" else "") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            )
        )
    }
}

@Composable
private fun WarningText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MoneyTheme.colors.warning
    )
}

/**
 * Hands a polite reminder to whatever messaging app the user prefers.
 *
 * The app composes the text and passes it to the system share sheet; it never sends
 * anything itself, has no contacts access, and needs no permission. The user picks the
 * app and the recipient, and can edit the message before it goes.
 */
private fun shareReminder(
    context: android.content.Context,
    name: String,
    amount: com.moneyplanner.core.money.Money
) {
    val message = buildString {
        append("Hi ")
        append(name)
        append(", a small reminder about the ")
        append(com.moneyplanner.core.money.IndianFormat.format(amount))
        append(" pending between us. No rush at all.")
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, message)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Send a reminder"))
}
