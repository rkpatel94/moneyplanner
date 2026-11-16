package com.moneyplanner.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.data.repo.AnnualExpenseRepository
import com.moneyplanner.data.repo.BillRepository
import com.moneyplanner.data.repo.CreditCardRepository
import com.moneyplanner.data.repo.EmiRepository
import com.moneyplanner.data.repo.ExpenseRepository
import com.moneyplanner.data.repo.IncomeRepository
import com.moneyplanner.data.repo.PeopleRepository
import com.moneyplanner.data.repo.RecordRemover
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.di.DefaultDispatcher
import com.moneyplanner.domain.calc.NoAccountRecord
import com.moneyplanner.domain.calc.PaymentSuggestion
import com.moneyplanner.domain.calc.SuggestionTarget
import com.moneyplanner.domain.calc.UnlinkedCalculator
import com.moneyplanner.domain.calc.UnlinkedKind
import com.moneyplanner.domain.calc.UnlinkedRecords
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.ui.components.ChipSelector
import com.moneyplanner.ui.components.ConfirmDialog
import com.moneyplanner.ui.components.MoneyText
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.theme.MoneyTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The home screen's list of records the app cannot place, and what can be done with each.
 *
 * Settle means giving the record its missing link: the account the money moved through,
 * the card a purchase went on, or the bill, EMI, yearly expense or card bill an everyday
 * expense was really the payment of. Delete removes it, through whichever repository owns
 * the event. A suggestion can also be answered "not a payment", and is not offered again.
 */
@HiltViewModel
class UnlinkedViewModel @Inject constructor(
    snapshotRepository: SnapshotRepository,
    private val settingsStore: SettingsStore,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val peopleRepository: PeopleRepository,
    private val cardRepository: CreditCardRepository,
    private val billRepository: BillRepository,
    private val emiRepository: EmiRepository,
    private val annualRepository: AnnualExpenseRepository,
    private val recordRemover: RecordRemover,
    @DefaultDispatcher computation: CoroutineDispatcher
) : ViewModel() {

    val state: StateFlow<UnlinkedState> = combine(
        snapshotRepository.snapshot,
        settingsStore.dismissedLinkSuggestions
    ) { snapshot, dismissed ->
        UnlinkedState(
            records = UnlinkedCalculator.find(snapshot, dismissed),
            accounts = snapshot.accounts.filterNot { it.isArchived },
            cards = snapshot.creditCards.filter { it.isActive },
            snapshot = snapshot
        )
    }
        .flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnlinkedState())

    private val _linking = MutableStateFlow<NoAccountRecord?>(null)
    val linking: StateFlow<NoAccountRecord?> = _linking.asStateFlow()

    private val _deleting = MutableStateFlow<PendingDelete?>(null)
    val deleting: StateFlow<PendingDelete?> = _deleting.asStateFlow()

    private val busy = mutableSetOf<String>()

    private fun once(key: String, block: suspend () -> Unit) {
        if (!busy.add(key)) return
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy.remove(key)
            }
        }
    }

    /** Gives an everyday expense the payment it looks like, so it is not counted twice. */
    fun settle(suggestion: PaymentSuggestion) = once(suggestion.key) {
        val expense = suggestion.expense
        when (val target = suggestion.target) {
            is SuggestionTarget.BillOccurrence -> billRepository.settleWithExpense(target.bill, target.dueDate, expense)
            is SuggestionTarget.EmiInstallment -> emiRepository.settleWithExpense(target.emi, target.installment, expense)
            is SuggestionTarget.AnnualOccurrence -> annualRepository.settleWithExpense(target.annual, target.year, expense)
            is SuggestionTarget.CardBill -> cardRepository.settleExpenseAsPayment(target.card, expense)
        }
    }

    fun notAPayment(suggestion: PaymentSuggestion) = once(suggestion.key) {
        settingsStore.dismissLinkSuggestion(suggestion.key)
    }

    fun startLinking(record: NoAccountRecord) {
        _linking.value = record
    }

    fun dismissLinking() {
        _linking.value = null
    }

    /**
     * Files [record] under the account it came from, or the card it went on. With [toAll],
     * every other record still missing an account is filed under the same one, which is
     * the common case of a run of payments the app wrote without asking.
     */
    fun link(record: NoAccountRecord, accountId: Long?, cardId: Long?, toAll: Boolean) {
        _linking.value = null
        val snapshot = state.value.snapshot ?: return
        val targets = if (toAll && !record.needsCard) {
            state.value.records.noAccount.filterNot { it.needsCard }
        } else {
            listOf(record)
        }
        once("link:${record.key}") {
            targets.forEach { target -> linkOne(snapshot, target, accountId, cardId) }
        }
    }

    private suspend fun linkOne(snapshot: FinancialSnapshot, record: NoAccountRecord, accountId: Long?, cardId: Long?) {
        when (record.kind) {
            UnlinkedKind.EXPENSE -> snapshot.expenses.firstOrNull { it.id == record.id }?.let { expense ->
                expenseRepository.update(
                    if (record.needsCard) expense.copy(creditCardId = cardId ?: return)
                    else expense.copy(accountId = accountId ?: return)
                )
            }
            UnlinkedKind.INCOME -> snapshot.incomeTransactions.firstOrNull { it.id == record.id }?.let {
                incomeRepository.updateTransaction(it.copy(accountId = accountId ?: return))
            }
            UnlinkedKind.SETTLEMENT -> snapshot.settlements.firstOrNull { it.id == record.id }?.let {
                peopleRepository.updateSettlement(it.copy(accountId = accountId ?: return))
            }
            UnlinkedKind.CARD_PAYMENT -> cardRepository.getPayment(record.id)?.let {
                cardRepository.updatePayment(it.copy(accountId = accountId ?: return))
            }
        }
    }

    fun askToDelete(record: NoAccountRecord) {
        _deleting.value = PendingDelete(record = record)
    }

    fun askToDelete(suggestion: PaymentSuggestion) {
        _deleting.value = PendingDelete(suggestion = suggestion)
    }

    fun dismissDelete() {
        _deleting.value = null
    }

    fun confirmDelete() {
        val pending = _deleting.value ?: return
        _deleting.value = null
        val snapshot = state.value.snapshot ?: return
        pending.suggestion?.let { suggestion ->
            once("delete:${suggestion.key}") { recordRemover.deleteExpense(suggestion.expense) }
            return
        }
        val record = pending.record ?: return
        once("delete:${record.key}") {
            when (record.kind) {
                UnlinkedKind.EXPENSE -> snapshot.expenses.firstOrNull { it.id == record.id }
                    ?.let { recordRemover.deleteExpense(it) }
                UnlinkedKind.INCOME -> incomeRepository.deleteTransaction(record.id)
                UnlinkedKind.SETTLEMENT -> peopleRepository.deleteSettlement(record.id)
                UnlinkedKind.CARD_PAYMENT -> cardRepository.deletePayment(record.id)
            }
        }
    }
}

data class UnlinkedState(
    val records: UnlinkedRecords = UnlinkedRecords(),
    val accounts: List<Account> = emptyList(),
    val cards: List<CreditCard> = emptyList(),
    val snapshot: FinancialSnapshot? = null
)

data class PendingDelete(
    val record: NoAccountRecord? = null,
    val suggestion: PaymentSuggestion? = null
) {
    val title: String get() = suggestion?.expense?.description?.ifBlank { null } ?: record?.title ?: "this record"
}

/** Rows shown before the card offers to show the rest. */
private const val COLLAPSED_ROWS = 3

@Composable
fun UnlinkedCard(
    onOpenExpense: (Long) -> Unit,
    viewModel: UnlinkedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val linking by viewModel.linking.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val records = state.records
    if (records.isEmpty) return

    val colors = MoneyTheme.colors
    val total = records.count

    SectionCard(
        title = "Not linked yet",
        subtitle = if (total == 1) "1 record needs sorting out" else "$total records need sorting out"
    ) {
        Text(
            "Until these are linked, a balance or the forecast is off by their amount.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )

        var shown = 0
        val limit = if (expanded) Int.MAX_VALUE else COLLAPSED_ROWS

        records.suggestions.forEach { suggestion ->
            if (shown >= limit) return@forEach
            shown++
            HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(vertical = 8.dp))
            SuggestionRow(
                suggestion = suggestion,
                onOpen = { onOpenExpense(suggestion.expense.id) },
                onSettle = { viewModel.settle(suggestion) },
                onNotPayment = { viewModel.notAPayment(suggestion) },
                onDelete = { viewModel.askToDelete(suggestion) }
            )
        }

        records.noAccount.forEach { record ->
            if (shown >= limit) return@forEach
            shown++
            HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(vertical = 8.dp))
            NoAccountRow(
                record = record,
                onOpen = { if (record.kind == UnlinkedKind.EXPENSE) onOpenExpense(record.id) },
                onLink = { viewModel.startLinking(record) },
                onDelete = { viewModel.askToDelete(record) }
            )
        }

        if (total > COLLAPSED_ROWS) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show fewer" else "Show all $total")
            }
        }
    }

    linking?.let { record ->
        LinkDialog(
            record = record,
            accounts = state.accounts,
            cards = state.cards,
            othersWithoutAccount = records.noAccount.count { !it.needsCard && it.key != record.key },
            onLink = { accountId, cardId, toAll -> viewModel.link(record, accountId, cardId, toAll) },
            onDismiss = viewModel::dismissLinking
        )
    }

    deleting?.let { pending ->
        ConfirmDialog(
            title = "Delete ${pending.title}?",
            message = "It is removed from your records and every balance is worked out again " +
                "without it. This cannot be undone.",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
}

@Composable
private fun SuggestionRow(
    suggestion: PaymentSuggestion,
    onOpen: () -> Unit,
    onSettle: () -> Unit,
    onNotPayment: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MoneyTheme.colors
    val expense = suggestion.expense
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.description.ifBlank { "Expense" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${DateUtil.formatDayMonth(expense.date)} · looks like ${suggestion.target.title}, " +
                        "due ${DateUtil.formatDayMonth(suggestion.target.dueDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onWarningContainer
                )
            }
            MoneyText(expense.amount, color = colors.negative)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSettle) {
                Text(
                    when (suggestion.target) {
                        is SuggestionTarget.CardBill -> "It's the card bill"
                        else -> "Yes, it's this payment"
                    }
                )
            }
            TextButton(onClick = onNotPayment) { Text("Not a payment") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete this expense")
            }
        }
    }
}

@Composable
private fun NoAccountRow(
    record: NoAccountRecord,
    onOpen: () -> Unit,
    onLink: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MoneyTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    DateUtil.formatDayMonth(record.date) + " · " +
                        if (record.needsCard) "on a card, but which one?" else "no account",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            MoneyText(
                if (record.isInflow) record.amount else -record.amount,
                showSign = true,
                colorBySign = true
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLink) { Text(if (record.needsCard) "Choose card" else "Link account") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${record.title}")
            }
        }
    }
}

@Composable
private fun LinkDialog(
    record: NoAccountRecord,
    accounts: List<Account>,
    cards: List<CreditCard>,
    othersWithoutAccount: Int,
    onLink: (accountId: Long?, cardId: Long?, toAll: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var accountId by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id) }
    var cardId by rememberSaveable { mutableStateOf(cards.singleOrNull()?.id) }
    var toAll by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(if (record.needsCard) "Which card was it on?" else "Which account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${record.title} · ${IndianFormat.format(record.amount)} on ${DateUtil.formatDayMonth(record.date)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (record.needsCard) {
                    if (cards.isEmpty()) {
                        Text("Add the card first, under Credit cards.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        ChipSelector(
                            options = cards,
                            selected = cards.firstOrNull { it.id == cardId },
                            onSelect = { cardId = it.id },
                            optionLabel = { it.name }
                        )
                    }
                } else {
                    ChipSelector(
                        options = accounts,
                        selected = accounts.firstOrNull { it.id == accountId },
                        onSelect = { accountId = it.id },
                        optionLabel = { it.name }
                    )
                    if (othersWithoutAccount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = toAll, onCheckedChange = { toAll = it })
                            Text(
                                if (othersWithoutAccount == 1) "Also link the 1 other record with no account"
                                else "Also link the $othersWithoutAccount others with no account",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onLink(accountId, cardId, toAll) },
                enabled = if (record.needsCard) cardId != null else accountId != null
            ) { Text("Link") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
