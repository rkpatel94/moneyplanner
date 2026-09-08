package com.moneyplanner.ui.screens.commitments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.core.time.periodKey
import com.moneyplanner.data.repo.AnnualExpenseRepository
import com.moneyplanner.data.repo.BillRepository
import com.moneyplanner.data.repo.CategoryRepository
import com.moneyplanner.data.repo.CreditCardRepository
import com.moneyplanner.data.repo.ExpenseRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.data.repo.VehicleRepository
import com.moneyplanner.domain.calc.RecurrenceCalculator
import com.moneyplanner.domain.calc.CreditCardCalculator
import com.moneyplanner.domain.model.AnnualExpense
import com.moneyplanner.domain.model.BillAmountType
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.RecurringBill
import com.moneyplanner.domain.model.Vehicle
import com.moneyplanner.ui.nav.Routes
import com.moneyplanner.ui.screens.expenses.toEditableText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// ---- Credit cards ----------------------------------------------------------------

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val cardRepository: CreditCardRepository,
    expenseRepository: ExpenseRepository,
    private val today: TodayProvider
) : ViewModel() {

    val state: StateFlow<CardsState> = combine(
        cardRepository.all,
        expenseRepository.all
    ) { cards, expenses ->
        val now = today.today()
        CardsState(
            cards = cards.map { card ->
                CardRow(
                    card = card,
                    nextDueDate = RecurrenceCalculator.nextOccurrenceOnOrAfter(
                        from = now,
                        start = now.minusYears(5),
                        end = null,
                        dayOfMonth = card.dueDayOfMonth,
                        frequency = Frequency.MONTHLY
                    ),
                    unbilledSpend = CreditCardCalculator.unbilledSpendOn(card, expenses)
                )
            },
            totalOutstanding = cards.filter { it.isActive }.sumOfMoney { it.currentOutstanding },
            totalLimit = cards.filter { it.isActive }.sumOfMoney { it.creditLimit },
            today = now,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardsState()
    )

    private val _payAmount = MutableStateFlow("")
    val payAmount: StateFlow<String> = _payAmount.asStateFlow()

    fun updatePayAmount(value: String) {
        _payAmount.value = value
    }

    fun payBill(card: CreditCard) {
        val amount = Money.parseOrNull(_payAmount.value) ?: return
        if (!amount.isPositive) return
        viewModelScope.launch {
            cardRepository.payBill(card, amount)
            _payAmount.value = ""
        }
    }

    fun payFull(card: CreditCard) {
        viewModelScope.launch { cardRepository.payBill(card, card.currentOutstanding) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { cardRepository.delete(id) }
    }
}

data class CardRow(
    val card: CreditCard,
    val nextDueDate: LocalDate?,
    /** Charged to the card since its statement figure was last entered. */
    val unbilledSpend: Money = Money.ZERO
) {
    val hasUnbilled: Boolean get() = unbilledSpend.isPositive

    /** What is really owed today: the statement figure plus what has gone on since. */
    val projectedOutstanding: Money get() = card.currentOutstanding + unbilledSpend
}

data class CardsState(
    val cards: List<CardRow> = emptyList(),
    val totalOutstanding: Money = Money.ZERO,
    val totalLimit: Money = Money.ZERO,
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CardEditorViewModel @Inject constructor(
    private val cardRepository: CreditCardRepository,
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
                        outstandingText = card.currentOutstanding.toEditableText(),
                        minimumDueText = card.minimumDue.toEditableText(),
                        statementDay = card.statementDayOfMonth,
                        dueDay = card.dueDayOfMonth,
                        isActive = card.isActive,
                        notes = card.notes,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun updateName(v: String) = _form.update { it.copy(name = v, nameError = null) }
    fun updateBank(v: String) = _form.update { it.copy(bank = v) }
    fun updateLastFour(v: String) = _form.update { it.copy(lastFour = v.filter { c -> c.isDigit() }.take(4)) }
    fun updateLimit(v: String) = _form.update { it.copy(limitText = v) }
    fun updateOutstanding(v: String) = _form.update { it.copy(outstandingText = v) }
    fun updateMinimumDue(v: String) = _form.update { it.copy(minimumDueText = v) }
    fun updateStatementDay(v: Int) = _form.update { it.copy(statementDay = v) }
    fun updateDueDay(v: Int) = _form.update { it.copy(dueDay = v) }
    fun updateActive(v: Boolean) = _form.update { it.copy(isActive = v) }
    fun updateNotes(v: String) = _form.update { it.copy(notes = v) }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        if (current.name.isBlank()) {
            _form.update { it.copy(nameError = "Give this card a name") }
            return
        }
        viewModelScope.launch {
            val card = CreditCard(
                id = cardId ?: 0,
                name = current.name.trim(),
                bank = current.bank.trim(),
                lastFourDigits = current.lastFour.ifBlank { null },
                creditLimit = Money.parseOrNull(current.limitText) ?: Money.ZERO,
                currentOutstanding = Money.parseOrNull(current.outstandingText) ?: Money.ZERO,
                minimumDue = Money.parseOrNull(current.minimumDueText) ?: Money.ZERO,
                statementDayOfMonth = current.statementDay,
                dueDayOfMonth = current.dueDay,
                isActive = current.isActive,
                notes = current.notes.trim(),
                lastUpdated = null
            )
            if (cardId != null) cardRepository.update(card) else cardRepository.add(card)
            onSaved()
        }
    }
}

data class CardForm(
    val name: String = "",
    val bank: String = "",
    val lastFour: String = "",
    val limitText: String = "",
    val outstandingText: String = "",
    val minimumDueText: String = "",
    val statementDay: Int = 1,
    val dueDay: Int = 15,
    val isActive: Boolean = true,
    val notes: String = "",
    val nameError: String? = null,
    val isEditing: Boolean = false
) {
    val canSave: Boolean get() = name.isNotBlank()
}

// ---- Recurring bills -------------------------------------------------------------

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val today: TodayProvider
) : ViewModel() {

    val state: StateFlow<BillsState> = combine(
        billRepository.all,
        billRepository.payments
    ) { bills, payments ->
        val now = today.today()
        val month = YearMonth.from(now)
        val paidKeys = payments.map { it.billId to it.periodKey }.toSet()

        val rows = bills.map { bill ->
            val dueThisMonth = RecurrenceCalculator
                .occurrencesIn(month, bill.startDate, bill.endDate, bill.dueDayOfMonth, bill.frequency)
                .firstOrNull()
            BillRow(
                bill = bill,
                dueThisMonth = dueThisMonth,
                isPaidThisMonth = (bill.id to month.periodKey()) in paidKeys,
                nextDueDate = RecurrenceCalculator.nextOccurrenceOnOrAfter(
                    now, bill.startDate, bill.endDate, bill.dueDayOfMonth, bill.frequency
                )
            )
        }

        BillsState(
            active = rows.filter { it.bill.isActive },
            inactive = rows.filter { !it.bill.isActive },
            monthlyTotal = rows
                .filter { it.bill.isActive && it.dueThisMonth != null }
                .sumOfMoney { it.bill.amount },
            unpaidThisMonth = rows
                .filter { it.bill.isActive && it.dueThisMonth != null && !it.isPaidThisMonth }
                .sumOfMoney { it.bill.amount },
            month = month,
            today = now,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BillsState()
    )

    private val _paidAmounts = MutableStateFlow<Map<Long, String>>(emptyMap())
    val paidAmounts: StateFlow<Map<Long, String>> = _paidAmounts.asStateFlow()

    fun updatePaidAmount(billId: Long, value: String) {
        _paidAmounts.update { it + (billId to value) }
    }

    /**
     * Marks a bill paid. Variable bills such as electricity are recorded at the amount
     * actually billed rather than at the estimate, which is what keeps the history
     * accurate enough to improve next month's estimate.
     */
    fun markPaid(row: BillRow) {
        val typed = Money.parseOrNull(_paidAmounts.value[row.bill.id].orEmpty())
        val amount = typed ?: row.bill.amount
        val dueDate = row.dueThisMonth ?: row.nextDueDate ?: today.today()

        viewModelScope.launch {
            billRepository.markPaid(
                bill = row.bill,
                month = YearMonth.from(dueDate),
                amount = amount,
                dueDate = dueDate
            )
            _paidAmounts.update { it - row.bill.id }
        }
    }

    fun undoPaid(row: BillRow) {
        val dueDate = row.dueThisMonth ?: return
        viewModelScope.launch {
            billRepository.undoPayment(row.bill.id, YearMonth.from(dueDate))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { billRepository.delete(id) }
    }
}

data class BillRow(
    val bill: RecurringBill,
    val dueThisMonth: LocalDate?,
    val isPaidThisMonth: Boolean,
    val nextDueDate: LocalDate?
)

data class BillsState(
    val active: List<BillRow> = emptyList(),
    val inactive: List<BillRow> = emptyList(),
    val monthlyTotal: Money = Money.ZERO,
    val unpaidThisMonth: Money = Money.ZERO,
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true
)

@HiltViewModel
class BillEditorViewModel @Inject constructor(
    private val billRepository: BillRepository,
    categoryRepository: CategoryRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val billId: Long? = savedStateHandle.get<String>(Routes.ARG_BILL_ID)?.toLongOrNull()

    private val _form = MutableStateFlow(
        BillForm(startDate = today.today(), isEditing = billId != null)
    )
    val form: StateFlow<BillForm> = _form.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.expenseCategories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        if (billId != null) {
            viewModelScope.launch {
                billRepository.getById(billId)?.let { bill ->
                    _form.value = BillForm(
                        name = bill.name,
                        amountText = bill.amount.toEditableText(),
                        amountType = bill.amountType,
                        minText = bill.minAmount?.toEditableText().orEmpty(),
                        maxText = bill.maxAmount?.toEditableText().orEmpty(),
                        dueDay = bill.dueDayOfMonth,
                        frequency = bill.frequency,
                        startDate = bill.startDate,
                        endDate = bill.endDate,
                        categoryId = bill.categoryId,
                        paymentMethod = bill.paymentMethod,
                        autoDebit = bill.autoDebit,
                        isEssential = bill.isEssential,
                        isActive = bill.isActive,
                        notes = bill.notes,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun updateName(v: String) = _form.update { it.copy(name = v, nameError = null) }
    fun updateAmount(v: String) = _form.update { it.copy(amountText = v, amountError = null) }
    fun updateAmountType(v: BillAmountType) = _form.update { it.copy(amountType = v) }
    fun updateMin(v: String) = _form.update { it.copy(minText = v) }
    fun updateMax(v: String) = _form.update { it.copy(maxText = v) }
    fun updateDueDay(v: Int) = _form.update { it.copy(dueDay = v) }
    fun updateFrequency(v: Frequency) = _form.update { it.copy(frequency = v) }
    fun updateStartDate(v: LocalDate) = _form.update { it.copy(startDate = v) }
    fun updateEndDate(v: LocalDate?) = _form.update { it.copy(endDate = v) }
    fun updateCategory(v: Long?) = _form.update { it.copy(categoryId = v) }
    fun updatePaymentMethod(v: PaymentMethod) = _form.update { it.copy(paymentMethod = v) }
    fun updateAutoDebit(v: Boolean) = _form.update { it.copy(autoDebit = v) }
    fun updateEssential(v: Boolean) = _form.update { it.copy(isEssential = v) }
    fun updateActive(v: Boolean) = _form.update { it.copy(isActive = v) }
    fun updateNotes(v: String) = _form.update { it.copy(notes = v) }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        val amount = Money.parseOrNull(current.amountText)
        if (current.name.isBlank()) {
            _form.update { it.copy(nameError = "Give this bill a name") }
            return
        }
        if (amount == null || !amount.isPositive) {
            _form.update { it.copy(amountError = "Enter an amount greater than zero") }
            return
        }
        viewModelScope.launch {
            val bill = RecurringBill(
                id = billId ?: 0,
                name = current.name.trim(),
                categoryId = current.categoryId,
                amountType = current.amountType,
                amount = amount,
                minAmount = Money.parseOrNull(current.minText),
                maxAmount = Money.parseOrNull(current.maxText),
                dueDayOfMonth = current.dueDay,
                frequency = current.frequency,
                startDate = current.startDate,
                endDate = current.endDate,
                paymentMethod = current.paymentMethod,
                autoDebit = current.autoDebit,
                isEssential = current.isEssential,
                isActive = current.isActive,
                notes = current.notes.trim()
            )
            if (billId != null) billRepository.update(bill) else billRepository.add(bill)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = billId ?: return
        viewModelScope.launch {
            billRepository.delete(id)
            onDeleted()
        }
    }
}

data class BillForm(
    val name: String = "",
    val amountText: String = "",
    val amountType: BillAmountType = BillAmountType.FIXED,
    val minText: String = "",
    val maxText: String = "",
    val dueDay: Int = 5,
    val frequency: Frequency = Frequency.MONTHLY,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val categoryId: Long? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val autoDebit: Boolean = false,
    val isEssential: Boolean = true,
    val isActive: Boolean = true,
    val notes: String = "",
    val nameError: String? = null,
    val amountError: String? = null,
    val isEditing: Boolean = false
) {
    val canSave: Boolean
        get() = name.isNotBlank() && Money.parseOrNull(amountText)?.isPositive == true
}

// ---- Annual expenses -------------------------------------------------------------

@HiltViewModel
class AnnualViewModel @Inject constructor(
    private val annualRepository: AnnualExpenseRepository,
    private val today: TodayProvider
) : ViewModel() {

    val state: StateFlow<AnnualState> = combine(
        annualRepository.all,
        annualRepository.payments
    ) { expenses, payments ->
        val now = today.today()
        val paidThisYear = payments.filter { it.year == now.year }
            .map { it.annualExpenseId }
            .toSet()

        val rows = expenses.map { expense ->
            val dueThisYear = LocalDate.of(
                now.year,
                expense.dueMonth,
                minOf(expense.dueDayOfMonth, YearMonth.of(now.year, expense.dueMonth).lengthOfMonth())
            )
            AnnualRow(
                expense = expense,
                dueDate = if (dueThisYear.isBefore(now) && expense.id !in paidThisYear) {
                    dueThisYear
                } else if (dueThisYear.isBefore(now)) {
                    dueThisYear.plusYears(1)
                } else {
                    dueThisYear
                },
                isPaidThisYear = expense.id in paidThisYear,
                isOverdue = dueThisYear.isBefore(now) && expense.id !in paidThisYear
            )
        }

        AnnualState(
            expenses = rows.filter { it.expense.isActive }.sortedBy { it.dueDate },
            inactive = rows.filter { !it.expense.isActive },
            yearlyTotal = expenses.filter { it.isActive }.sumOfMoney { it.amount },
            monthlyReserve = expenses.filter { it.isActive }.sumOfMoney { it.monthlyReserve },
            today = now,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnnualState()
    )

    fun markPaid(row: AnnualRow) {
        viewModelScope.launch {
            annualRepository.markPaid(row.expense, today.today().year, row.expense.amount)
        }
    }

    fun undoPaid(row: AnnualRow) {
        viewModelScope.launch {
            annualRepository.undoPayment(row.expense.id, today.today().year)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { annualRepository.delete(id) }
    }
}

data class AnnualRow(
    val expense: AnnualExpense,
    val dueDate: LocalDate,
    val isPaidThisYear: Boolean,
    val isOverdue: Boolean
)

data class AnnualState(
    val expenses: List<AnnualRow> = emptyList(),
    val inactive: List<AnnualRow> = emptyList(),
    val yearlyTotal: Money = Money.ZERO,
    /** What should be set aside every month so these never arrive as a shock. */
    val monthlyReserve: Money = Money.ZERO,
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AnnualEditorViewModel @Inject constructor(
    private val annualRepository: AnnualExpenseRepository,
    categoryRepository: CategoryRepository,
    vehicleRepository: VehicleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val annualId: Long? = savedStateHandle.get<String>(Routes.ARG_ANNUAL_ID)?.toLongOrNull()

    private val _form = MutableStateFlow(AnnualForm(isEditing = annualId != null))
    val form: StateFlow<AnnualForm> = _form.asStateFlow()

    val options: StateFlow<AnnualOptions> = combine(
        categoryRepository.expenseCategories,
        vehicleRepository.all
    ) { categories, vehicles -> AnnualOptions(categories, vehicles) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnnualOptions()
        )

    init {
        if (annualId != null) {
            viewModelScope.launch {
                annualRepository.getById(annualId)?.let { expense ->
                    _form.value = AnnualForm(
                        name = expense.name,
                        amountText = expense.amount.toEditableText(),
                        dueMonth = expense.dueMonth,
                        dueDay = expense.dueDayOfMonth,
                        categoryId = expense.categoryId,
                        vehicleId = expense.vehicleId,
                        isActive = expense.isActive,
                        notes = expense.notes,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun updateName(v: String) = _form.update { it.copy(name = v, nameError = null) }
    fun updateAmount(v: String) = _form.update { it.copy(amountText = v, amountError = null) }
    fun updateDueMonth(v: Int) = _form.update { it.copy(dueMonth = v) }
    fun updateDueDay(v: Int) = _form.update { it.copy(dueDay = v) }
    fun updateCategory(v: Long?) = _form.update { it.copy(categoryId = v) }
    fun updateVehicle(v: Long?) = _form.update { it.copy(vehicleId = v) }
    fun updateActive(v: Boolean) = _form.update { it.copy(isActive = v) }
    fun updateNotes(v: String) = _form.update { it.copy(notes = v) }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        val amount = Money.parseOrNull(current.amountText)
        if (current.name.isBlank()) {
            _form.update { it.copy(nameError = "Give this expense a name") }
            return
        }
        if (amount == null || !amount.isPositive) {
            _form.update { it.copy(amountError = "Enter an amount greater than zero") }
            return
        }
        viewModelScope.launch {
            val expense = AnnualExpense(
                id = annualId ?: 0,
                name = current.name.trim(),
                categoryId = current.categoryId,
                amount = amount,
                dueMonth = current.dueMonth,
                dueDayOfMonth = current.dueDay,
                vehicleId = current.vehicleId,
                isActive = current.isActive,
                notes = current.notes.trim()
            )
            if (annualId != null) annualRepository.update(expense)
            else annualRepository.add(expense)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = annualId ?: return
        viewModelScope.launch {
            annualRepository.delete(id)
            onDeleted()
        }
    }
}

data class AnnualForm(
    val name: String = "",
    val amountText: String = "",
    val dueMonth: Int = 1,
    val dueDay: Int = 15,
    val categoryId: Long? = null,
    val vehicleId: Long? = null,
    val isActive: Boolean = true,
    val notes: String = "",
    val nameError: String? = null,
    val amountError: String? = null,
    val isEditing: Boolean = false
) {
    val canSave: Boolean
        get() = name.isNotBlank() && Money.parseOrNull(amountText)?.isPositive == true

    /** Shown live while typing, so the monthly cost of a yearly bill is never a surprise. */
    val monthlyReserve: Money
        get() = Money.parseOrNull(amountText)?.divideRounded(12) ?: Money.ZERO
}

data class AnnualOptions(
    val categories: List<Category> = emptyList(),
    val vehicles: List<Vehicle> = emptyList()
)
