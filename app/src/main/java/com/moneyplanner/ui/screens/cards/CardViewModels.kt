package com.moneyplanner.ui.screens.cards

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.data.repo.CreditCardRepository
import com.moneyplanner.data.repo.ExpenseRepository
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.domain.calc.CreditCardCalculator
import com.moneyplanner.domain.calc.StatementBill
import com.moneyplanner.domain.calc.UpcomingCardBill
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.CreditCardPayment
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.ui.nav.Routes
import com.moneyplanner.ui.screens.expenses.toEditableText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Everything a card screen shows about one card, worked out once.
 *
 * The bill already issued and what is left of it, the spending since and which bill it will
 * be on, and the total really owed. Kept apart because they are different facts: paying the
 * total when only the statement is due is the mistake this layout exists to prevent.
 */
data class CardSummary(
    val card: CreditCard,
    val bill: StatementBill,
    val upcoming: List<UpcomingCardBill>,
    val unbilled: Money,
    val unbilledCount: Int,
    /** Statement still to pay plus spending since. Negative when the card is in credit. */
    val owed: Money,
    val utilisation: Float,
    /**
     * A statement the bank has cut since the one on file, if any. Its amount is the bank's
     * to say, so the card asks for it rather than guessing.
     */
    val newerStatement: LocalDate?
) {
    val isInCredit: Boolean get() = owed.isNegative

    /** The app's own figure for [newerStatement], from what it has recorded. */
    val newerStatementEstimate: Money?
        get() = newerStatement?.let { cut ->
            bill.remaining + upcoming.filter { !it.statementOn.isAfter(cut) }.sumOfMoney { it.amount }
        }

    companion object {
        fun of(
            card: CreditCard,
            payments: List<CreditCardPayment>,
            expenses: List<Expense>,
            today: LocalDate
        ): CardSummary {
            val bill = CreditCardCalculator.statementBill(card, payments, today)
            val latestCut = CreditCardCalculator.statementOnOrBefore(card, today)
            return CardSummary(
                card = card,
                bill = bill,
                upcoming = CreditCardCalculator.upcomingBills(card, expenses),
                unbilled = CreditCardCalculator.unbilledSpendOn(card, expenses),
                unbilledCount = CreditCardCalculator.purchasesOn(card, expenses).size,
                owed = CreditCardCalculator.projectedOutstanding(card, expenses),
                utilisation = CreditCardCalculator.projectedUtilisation(card, expenses),
                newerStatement = latestCut.takeIf { it.isAfter(bill.statementOn) }
            )
        }
    }
}

/** One quick-fill amount on the payment sheet. */
data class PaymentPreset(val label: String, val amount: Money)

data class CardPaymentForm(
    val cardId: Long,
    val cardName: String,
    val editingId: Long? = null,
    val amountText: String = "",
    val method: PaymentMethod = PaymentMethod.UPI,
    val accountId: Long? = null,
    /** Once the account is picked by hand, changing the method stops moving it. */
    val accountTouched: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val notes: String = "",
    val presets: List<PaymentPreset> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val error: String? = null,
    val isSaving: Boolean = false
) {
    val isEditing: Boolean get() = editingId != null
    val parsedAmount: Money? get() = Money.parseOrNull(amountText)?.takeIf { it.isPositive }
}

/**
 * The payment sheet's state and actions, shared by the card list and the card page.
 *
 * A card bill payment asks the same questions as any other payment — how much, how it was
 * paid, from which account and on what date — because it is real money leaving a real
 * account. It used to ask only for an amount, and was filed against no account at all, so
 * the bank balance on the accounts screen never came down when the card was paid.
 */
class CardPaymentController(
    private val scope: CoroutineScope,
    private val repository: CreditCardRepository,
    private val today: () -> LocalDate
) {
    private val _form = MutableStateFlow<CardPaymentForm?>(null)
    val form: StateFlow<CardPaymentForm?> = _form.asStateFlow()

    fun startPaying(summary: CardSummary, accounts: List<Account>) {
        val presets = presetsFor(summary)
        val method = PaymentMethod.UPI
        _form.value = CardPaymentForm(
            cardId = summary.card.id,
            cardName = summary.card.name,
            amountText = presets.firstOrNull()?.amount?.toEditableText().orEmpty(),
            method = method,
            accountId = defaultAccount(method, accounts),
            date = today(),
            presets = presets,
            accounts = accounts
        )
    }

    fun startEditing(payment: CreditCardPayment, cardName: String, accounts: List<Account>) {
        _form.value = CardPaymentForm(
            cardId = payment.cardId,
            cardName = cardName,
            editingId = payment.id,
            amountText = payment.amount.toEditableText(),
            method = payment.paymentMethod,
            accountId = payment.accountId,
            accountTouched = true,
            date = payment.paidDate,
            notes = payment.notes,
            accounts = accounts
        )
    }

    fun dismiss() {
        _form.value = null
    }

    fun updateAmount(text: String) = _form.update { it?.copy(amountText = text, error = null) }

    fun selectPreset(preset: PaymentPreset) =
        _form.update { it?.copy(amountText = preset.amount.toEditableText(), error = null) }

    /** Cash comes out of the cash account and everything else out of the bank, until the user picks. */
    fun selectMethod(method: PaymentMethod) = _form.update { form ->
        form ?: return@update null
        if (form.accountTouched) form.copy(method = method)
        else form.copy(method = method, accountId = defaultAccount(method, form.accounts))
    }

    fun selectAccount(id: Long?) = _form.update { it?.copy(accountId = id, accountTouched = true) }
    fun updateDate(date: LocalDate) = _form.update { it?.copy(date = date) }
    fun updateNotes(text: String) = _form.update { it?.copy(notes = text) }

    fun save() {
        val form = _form.value ?: return
        if (form.isSaving) return
        val amount = form.parsedAmount
        if (amount == null) {
            _form.update { it?.copy(error = "Enter an amount greater than zero") }
            return
        }
        _form.update { it?.copy(isSaving = true) }
        scope.launch {
            val editingId = form.editingId
            if (editingId != null) {
                repository.getPayment(editingId)?.let { existing ->
                    repository.updatePayment(
                        existing.copy(
                            amount = amount,
                            paidDate = form.date,
                            accountId = form.accountId,
                            notes = form.notes.trim(),
                            paymentMethod = form.method
                        )
                    )
                }
            } else {
                repository.getById(form.cardId)?.let { card ->
                    repository.payBill(
                        card = card,
                        amount = amount,
                        paidOn = form.date,
                        paymentMethod = form.method,
                        accountId = form.accountId,
                        notes = form.notes.trim()
                    )
                }
            }
            _form.value = null
        }
    }

    fun delete() {
        val id = _form.value?.editingId ?: return
        _form.value = null
        scope.launch { repository.deletePayment(id) }
    }

    companion object {
        /** Every method that moves cash. A card cannot be paid with a card. */
        val METHODS: List<PaymentMethod> = PaymentMethod.entries.filter { it != PaymentMethod.CREDIT_CARD }

        fun defaultAccount(method: PaymentMethod, accounts: List<Account>): Long? {
            val wanted = if (method == PaymentMethod.CASH) AccountType.CASH else AccountType.BANK
            return (accounts.firstOrNull { it.type == wanted } ?: accounts.firstOrNull())?.id
        }

        /**
         * The amounts someone actually pays a card with: the bill, the minimum, or
         * everything including spending not yet billed. Only the ones that differ are offered.
         */
        fun presetsFor(summary: CardSummary): List<PaymentPreset> = buildList {
            val bill = summary.bill
            if (bill.remaining.isPositive) add(PaymentPreset("Bill", bill.remaining))
            if (bill.minimumRemaining.isPositive && bill.minimumRemaining < bill.remaining) {
                add(PaymentPreset("Minimum", bill.minimumRemaining))
            }
            if (summary.owed.isPositive && summary.owed != bill.remaining) {
                add(PaymentPreset("Everything owed", summary.owed))
            }
        }
    }
}

// ---- List ----------------------------------------------------------------------------

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val cardRepository: CreditCardRepository,
    expenseRepository: ExpenseRepository,
    profileRepository: ProfileRepository,
    private val today: TodayProvider
) : ViewModel() {

    val state: StateFlow<CardsState> = combine(
        cardRepository.all,
        cardRepository.payments,
        expenseRepository.all,
        profileRepository.accounts,
        today.todayFlow
    ) { cards, payments, expenses, accounts, now ->
        val rows = cards.map { CardSummary.of(it, payments, expenses, now) }
        val active = rows.filter { it.card.isActive }
        CardsState(
            active = active,
            inactive = rows.filter { !it.card.isActive },
            accounts = accounts,
            totalOwed = active.sumOfMoney { it.owed.coerceAtLeastZero() },
            totalLimit = active.sumOfMoney { it.card.creditLimit },
            nextDue = active.filter { it.bill.remaining.isPositive }.minByOrNull { it.bill.dueOn },
            today = now,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardsState()
    )

    val payment = CardPaymentController(viewModelScope, cardRepository) { today.today() }
}

data class CardsState(
    val active: List<CardSummary> = emptyList(),
    val inactive: List<CardSummary> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val totalOwed: Money = Money.ZERO,
    val totalLimit: Money = Money.ZERO,
    /** The card whose bill falls due soonest, so the screen can lead with it. */
    val nextDue: CardSummary? = null,
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = !isLoading && active.isEmpty() && inactive.isEmpty()
    val available: Money get() = (totalLimit - totalOwed).coerceAtLeastZero()
}

// ---- One card --------------------------------------------------------------------------

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    private val cardRepository: CreditCardRepository,
    expenseRepository: ExpenseRepository,
    profileRepository: ProfileRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cardId: Long = savedStateHandle.get<String>(Routes.ARG_CARD_ID)?.toLongOrNull() ?: -1L

    val state: StateFlow<CardDetailState> = combine(
        cardRepository.byId(cardId),
        cardRepository.paymentsFor(cardId),
        expenseRepository.all,
        profileRepository.allAccounts,
        today.todayFlow
    ) { card, payments, expenses, allAccounts, now ->
        if (card == null) return@combine CardDetailState(isLoading = false, isMissing = true)
        val summary = CardSummary.of(card, payments, expenses, now)
        CardDetailState(
            summary = summary,
            payments = payments.sortedWith(
                compareByDescending<CreditCardPayment> { it.paidDate }.thenByDescending { it.id }
            ),
            purchasesSinceStatement = CreditCardCalculator.purchasesOn(card, expenses)
                .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id }),
            accounts = allAccounts.filterNot { it.isArchived },
            accountNames = allAccounts.associate { it.id to it.name },
            today = now,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardDetailState()
    )

    val payment = CardPaymentController(viewModelScope, cardRepository) { today.today() }

    private val _statement = MutableStateFlow<StatementForm?>(null)
    val statement: StateFlow<StatementForm?> = _statement.asStateFlow()

    /**
     * Opens the statement sheet on the most recent statement date the card's billing day
     * implies, with the app's own estimate shown beside the field for comparison only.
     */
    fun startStatement() {
        val summary = state.value.summary ?: return
        val cut = CreditCardCalculator.statementOnOrBefore(summary.card, today.today())
        _statement.value = StatementForm(
            date = cut,
            estimate = summary.newerStatementEstimate ?: summary.bill.remaining
        )
    }

    fun updateStatementDate(date: LocalDate) = _statement.update { it?.copy(date = date) }
    fun updateStatementAmount(text: String) = _statement.update { it?.copy(amountText = text, error = null) }
    fun updateStatementMinimum(text: String) = _statement.update { it?.copy(minimumText = text) }
    fun dismissStatement() {
        _statement.value = null
    }

    fun saveStatement() {
        val form = _statement.value ?: return
        val card = state.value.summary?.card ?: return
        val amount = Money.parseOrNull(form.amountText)
        if (amount == null || amount.isNegative) {
            _statement.update { it?.copy(error = "Enter the statement amount, or 0 if nothing is due") }
            return
        }
        if (form.date.isAfter(today.today())) {
            _statement.update { it?.copy(error = "A statement cannot be dated in the future") }
            return
        }
        val minimum = Money.parseOrNull(form.minimumText)?.takeIf { !it.isNegative } ?: Money.ZERO
        _statement.value = null
        viewModelScope.launch {
            cardRepository.updateStatement(card, form.date, amount, minOf(minimum, amount))
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            cardRepository.delete(cardId)
            onDeleted()
        }
    }
}

data class StatementForm(
    val date: LocalDate,
    val amountText: String = "",
    val minimumText: String = "",
    /** What the app's own records add up to, shown only as a check against the bank's figure. */
    val estimate: Money = Money.ZERO,
    val error: String? = null
)

data class CardDetailState(
    val summary: CardSummary? = null,
    val payments: List<CreditCardPayment> = emptyList(),
    val purchasesSinceStatement: List<Expense> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val accountNames: Map<Long, String> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val isMissing: Boolean = false
)

// ---- Add or edit -----------------------------------------------------------------------

@HiltViewModel
class CardEditorViewModel @Inject constructor(
    private val cardRepository: CreditCardRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cardId: Long? = savedStateHandle.get<String>(Routes.ARG_CARD_ID)?.toLongOrNull()

    private val _form = MutableStateFlow(CardForm(isEditing = cardId != null))
    val form: StateFlow<CardForm> = _form.asStateFlow()

    init {
        if (cardId != null) {
            viewModelScope.launch {
                cardRepository.getById(cardId)?.let { card ->
                    _form.value = CardForm(
                        name = card.name,
                        bank = card.bank,
                        lastFour = card.lastFourDigits.orEmpty(),
                        limitText = card.creditLimit.toEditableText(),
                        statementDay = card.statementDayOfMonth,
                        dueDay = card.dueDayOfMonth,
                        isActive = card.isActive,
                        notes = card.notes,
                        isEditing = true
                    )
                }
            }
        } else {
            _form.update { it.copy(statementDate = latestStatementFor(it.statementDay)) }
        }
    }

    /** The most recent date a statement could have been cut, given the billing day. */
    private fun latestStatementFor(statementDay: Int): LocalDate =
        CreditCardCalculator.statementOnOrBefore(
            placeholderCard(statementDay = statementDay, dueDay = _form.value.dueDay),
            today.today()
        )

    fun updateName(v: String) = _form.update { it.copy(name = v, nameError = null) }
    fun updateBank(v: String) = _form.update { it.copy(bank = v) }
    fun updateLastFour(v: String) = _form.update { it.copy(lastFour = v.filter { c -> c.isDigit() }.take(4)) }
    fun updateLimit(v: String) = _form.update { it.copy(limitText = v) }
    fun updateOutstanding(v: String) = _form.update { it.copy(outstandingText = v) }
    fun updateMinimumDue(v: String) = _form.update { it.copy(minimumDueText = v) }
    fun updateStatementDay(v: Int) = _form.update {
        // A new card's statement date follows its billing day until the user sets it.
        if (it.isEditing || it.statementDateTouched) it.copy(statementDay = v)
        else it.copy(statementDay = v, statementDate = latestStatementFor(v))
    }
    fun updateDueDay(v: Int) = _form.update { it.copy(dueDay = v) }
    fun updateStatementDate(v: LocalDate) = _form.update { it.copy(statementDate = v, statementDateTouched = true) }
    fun updateActive(v: Boolean) = _form.update { it.copy(isActive = v) }
    fun updateNotes(v: String) = _form.update { it.copy(notes = v) }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        if (current.isSaving) return
        if (current.name.isBlank()) {
            _form.update { it.copy(nameError = "Give this card a name") }
            return
        }
        _form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val outstanding = Money.parseOrNull(current.outstandingText) ?: Money.ZERO
            val card = CreditCard(
                id = cardId ?: 0,
                name = current.name.trim(),
                bank = current.bank.trim(),
                lastFourDigits = current.lastFour.ifBlank { null },
                creditLimit = Money.parseOrNull(current.limitText) ?: Money.ZERO,
                currentOutstanding = outstanding,
                minimumDue = minOf(Money.parseOrNull(current.minimumDueText) ?: Money.ZERO, outstanding),
                statementDayOfMonth = current.statementDay,
                dueDayOfMonth = current.dueDay,
                isActive = current.isActive,
                notes = current.notes.trim(),
                lastUpdated = today.today(),
                statementDate = current.statementDate
            )
            // An existing card's statement figure is not touched here: it changes through
            // "Enter new statement" and payments, so editing a name cannot disturb it.
            if (cardId != null) cardRepository.update(card) else cardRepository.add(card)
            onSaved()
        }
    }

    private fun placeholderCard(statementDay: Int, dueDay: Int) = CreditCard(
        id = 0, name = "", bank = "", lastFourDigits = null, creditLimit = Money.ZERO,
        currentOutstanding = Money.ZERO, minimumDue = Money.ZERO, statementDayOfMonth = statementDay,
        dueDayOfMonth = dueDay, isActive = true, notes = "", lastUpdated = null
    )
}

data class CardForm(
    val name: String = "",
    val bank: String = "",
    val lastFour: String = "",
    val limitText: String = "",
    /** Only asked when adding a card; afterwards the statement sheet owns these. */
    val outstandingText: String = "",
    val minimumDueText: String = "",
    val statementDate: LocalDate? = null,
    val statementDateTouched: Boolean = false,
    val statementDay: Int = 1,
    val dueDay: Int = 15,
    val isActive: Boolean = true,
    val notes: String = "",
    val nameError: String? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false
) {
    val canSave: Boolean get() = name.isNotBlank() && !isSaving
}
