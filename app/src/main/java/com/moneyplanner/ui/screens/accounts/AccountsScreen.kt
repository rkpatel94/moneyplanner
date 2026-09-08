package com.moneyplanner.ui.screens.accounts

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.moneyplanner.domain.calc.AccountBalance
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.ui.components.AmountField
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.DateField
import com.moneyplanner.ui.components.EmptyState
import com.moneyplanner.ui.components.LabelledTextField
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.PrimaryActionButton
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.components.StatusPill
import com.moneyplanner.ui.theme.MoneyTheme

/**
 * Accounts and what each one holds.
 *
 * The total at the top is the same number the dashboard shows, split by where the money
 * is. Anything the app knows about but cannot place in an account is shown as unassigned
 * rather than folded into the first one, so the two figures always reconcile and the gap
 * is something the user can go and fix.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val transferForm by viewModel.transferForm.collectAsStateWithLifecycle()
    var archiving by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.hasTwoAccounts) {
                        IconButton(onClick = viewModel::startWithdrawal) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = "Withdraw cash"
                            )
                        }
                        IconButton(onClick = viewModel::startDeposit) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Deposit cash"
                            )
                        }
                        IconButton(onClick = viewModel::startTransfer) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Move money")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startAdding) {
                Icon(Icons.Default.Add, contentDescription = "Add account")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.balances?.let { balances ->
                item {
                    SectionCard(
                        title = "Total across your accounts",
                        subtitle = "As of ${DateUtil.formatDate(balances.asOf)}"
                    ) {
                        MoneyText(
                            money = balances.total,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        if (balances.hasUnassigned) {
                            Spacer(Modifier.height(10.dp))
                            StatusPill(
                                text = "${IndianFormat.format(balances.unassigned)} not " +
                                    "linked to any account",
                                containerColor = MoneyTheme.colors.warningContainer,
                                contentColor = MoneyTheme.colors.onWarningContainer
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Entries recorded without choosing an account. Your total " +
                                    "is still right — this only says the app does not know " +
                                    "which account the money sits in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (balances.rows.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "No accounts yet",
                            message = "Add your bank accounts, cash in hand and wallets to " +
                                "see where your money actually is."
                        )
                    }
                }

                items(balances.rows, key = { it.account.id }) { row ->
                    AccountCard(
                        row = row,
                        onEdit = { viewModel.startEditing(row.account) },
                        onArchive = { archiving = row.account }
                    )
                }
            }

            if (state.transfers.isNotEmpty()) {
                item {
                    Text(
                        "Money moved between accounts",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.transfers, key = { it.id }) { transfer ->
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${state.accountNames[transfer.fromAccountId] ?: "?"} → " +
                                        (state.accountNames[transfer.toAccountId] ?: "?"),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    DateUtil.formatDate(transfer.date) +
                                        transfer.notes.takeIf { it.isNotBlank() }
                                            ?.let { " · $it" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MoneyText(
                                money = transfer.amount,
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { viewModel.deleteTransfer(transfer.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete transfer")
                            }
                        }
                    }
                }
            }
        }
    }

    if (form.isOpen) {
        AccountDialog(form = form, viewModel = viewModel)
    }

    if (transferForm.isOpen) {
        TransferDialog(
            form = transferForm,
            accounts = state.accounts,
            viewModel = viewModel
        )
    }

    archiving?.let { account ->
        ConfirmDialog(
            title = "Close ${account.name}?",
            message = "It stops appearing when you record something new. Everything already " +
                "recorded against it is kept, so your history and your balances stay right.",
            confirmLabel = "Close account",
            onConfirm = { viewModel.archive(account) },
            onDismiss = { archiving = null }
        )
    }
}

@Composable
private fun AccountCard(
    row: AccountBalance,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    SectionCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.account.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    row.account.type.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MoneyText(money = row.balance, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Opened with",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    IndianFormat.format(row.openingBalance),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "In",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    IndianFormat.format(row.moneyIn),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoneyTheme.colors.positive
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Out",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    IndianFormat.format(row.moneyOut),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoneyTheme.colors.negative
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text(" Edit")
            }
            OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f)) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun AccountDialog(form: AccountForm, viewModel: AccountsViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissForm,
        shape = MaterialTheme.shapes.large,
        title = { Text(if (form.isEditing) "Edit account" else "Add an account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabelledTextField(
                    value = form.name,
                    onValueChange = viewModel::updateName,
                    label = "Name",
                    isError = form.nameError != null,
                    errorMessage = form.nameError,
                    supportingText = "For example HDFC Savings, or Cash in hand"
                )
                ChipSelector(
                    label = "Type",
                    options = AccountType.entries,
                    selected = form.type,
                    onSelect = viewModel::updateType,
                    optionLabel = { it.label }
                )
                AmountField(
                    value = form.openingBalanceText,
                    onValueChange = viewModel::updateOpeningBalance,
                    label = "Balance on the opening date",
                    isError = form.amountError != null,
                    errorMessage = form.amountError,
                    imeAction = ImeAction.Done
                )
                DateField(
                    date = form.openingDate,
                    onDateChange = viewModel::updateOpeningDate,
                    label = "Opening date"
                )
                Text(
                    "Everything recorded from the opening date onwards is added to this " +
                        "starting figure, so the balance always explains itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveAccount) { Text("Save") } },
        dismissButton = { TextButton(onClick = viewModel::dismissForm) { Text("Cancel") } }
    )
}

@Composable
private fun TransferDialog(
    form: TransferForm,
    accounts: List<Account>,
    viewModel: AccountsViewModel
) {
    AlertDialog(
        onDismissRequest = viewModel::dismissTransfer,
        shape = MaterialTheme.shapes.large,
        title = { Text(form.mode.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when (form.mode) {
                        TransferMode.WITHDRAW ->
                            "Taking cash out is not spending it. Your total does not " +
                                "change; the money moves from the bank into your cash."
                        TransferMode.DEPOSIT ->
                            "Paying cash in is not income. Your total does not change; " +
                                "the money moves from your cash into the bank."
                        TransferMode.MOVE ->
                            "Moving money between your own accounts is neither income " +
                                "nor an expense. Your total does not change; only where " +
                                "it sits does."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChipSelector(
                    label = "From",
                    options = accounts,
                    selected = accounts.firstOrNull { it.id == form.fromId },
                    onSelect = { viewModel.updateTransferFrom(it.id) },
                    optionLabel = { it.name }
                )
                ChipSelector(
                    label = "To",
                    options = accounts,
                    selected = accounts.firstOrNull { it.id == form.toId },
                    onSelect = { viewModel.updateTransferTo(it.id) },
                    optionLabel = { it.name }
                )
                AmountField(
                    value = form.amountText,
                    onValueChange = viewModel::updateTransferAmount,
                    label = "Amount",
                    isError = form.error != null,
                    errorMessage = form.error,
                    imeAction = ImeAction.Done
                )
                DateField(date = form.date, onDateChange = viewModel::updateTransferDate)
                LabelledTextField(
                    value = form.notes,
                    onValueChange = viewModel::updateTransferNotes,
                    label = "Note (optional)",
                    imeAction = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::saveTransfer) { Text(form.mode.action) }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissTransfer) { Text("Cancel") } }
    )
}
