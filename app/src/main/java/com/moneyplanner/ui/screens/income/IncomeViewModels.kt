package com.moneyplanner.ui.screens.income

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.core.time.periodKey
import com.moneyplanner.data.repo.IncomeRepository
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.domain.calc.IncomeReceiptPeriod
import com.moneyplanner.domain.calc.RecurrenceCalculator
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.IncomeSource
import com.moneyplanner.domain.model.IncomeTransaction
import com.moneyplanner.domain.model.IncomeType
import com.moneyplanner.ui.nav.Routes
import com.moneyplanner.ui.screens.expenses.toEditableText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class IncomeViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val today: TodayProvider
) : ViewModel() {

    val state: StateFlow<IncomeState> = combine(
        incomeRepository.sources,
        incomeRepository.transactions
    ) { sources, transactions ->
        val now = today.today()
        val month = YearMonth.from(now)

        val rows = sources.map { source ->
            val receivedThisMonth = transactions.any {
                it.sourceId == source.id && YearMonth.from(it.date) == month
            }
            IncomeSourceRow(
                source = source,
                nextDate = RecurrenceCalculator.nextOccurrenceOnOrAfter(
                    from = now,
                    start = source.startDate,
                    end = source.endDate,
                    dayOfMonth = source.dayOfMonth,
                    frequency = source.frequency
                ),
                receivedThisMonth = receivedThisMonth
            )
        }

        IncomeState(
            sources = rows,
            recentReceipts = transactions.take(20),
            expectedMonthly = sources
                .filter { it.isActive && it.frequency == Frequency.MONTHLY }
                .sumOfMoney { it.amount },
            receivedThisMonth = transactions
                .filter { YearMonth.from(it.date) == month }
                .sumOfMoney { it.amount },
            today = now,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IncomeState()
    )

    /** Records that a scheduled income has arrived, at the amount that was expected. */
    fun markReceived(source: IncomeSource) {
        viewModelScope.launch {
            incomeRepository.markSourceReceived(source, source.amount)
        }
    }

    fun deleteReceipt(id: Long) {
        viewModelScope.launch { incomeRepository.deleteTransaction(id) }
    }

    fun deleteSource(id: Long) {
        viewModelScope.launch { incomeRepository.deleteSource(id) }
    }
}

data class IncomeSourceRow(
    val source: IncomeSource,
    val nextDate: LocalDate?,
    val receivedThisMonth: Boolean
)

data class IncomeState(
    val sources: List<IncomeSourceRow> = emptyList(),
    val recentReceipts: List<IncomeTransaction> = emptyList(),
    val expectedMonthly: Money = Money.ZERO,
    val receivedThisMonth: Money = Money.ZERO,
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true
)

/** Creating or editing a recurring income such as a salary. */
@HiltViewModel
class IncomeSourceEditorViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    profileRepository: ProfileRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sourceId: Long? = savedStateHandle.get<String>(Routes.ARG_SOURCE_ID)?.toLongOrNull()

    private val _form = MutableStateFlow(
        IncomeSourceForm(startDate = today.today(), isEditing = sourceId != null)
    )
    val form: StateFlow<IncomeSourceForm> = _form.asStateFlow()

    /** Where this income lands, so each receipt can default to the right account. */
    val accounts: StateFlow<List<Account>> = profileRepository.accounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        if (sourceId != null) {
            viewModelScope.launch {
                incomeRepository.getSource(sourceId)?.let { existing ->
                    _form.value = IncomeSourceForm(
                        name = existing.name,
                        amountText = existing.amount.toEditableText(),
                        type = existing.type,
                        dayOfMonth = existing.dayOfMonth,
                        frequency = existing.frequency,
                        startDate = existing.startDate,
                        endDate = existing.endDate,
                        incrementText = if (existing.annualIncrementPercent > 0.0) {
                            existing.annualIncrementPercent.toString()
                        } else {
                            ""
                        },
                        accountId = existing.accountId,
                        isActive = existing.isActive,
                        notes = existing.notes,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun updateName(value: String) = _form.update { it.copy(name = value, nameError = null) }
    fun updateAmount(value: String) = _form.update { it.copy(amountText = value, amountError = null) }
    fun updateType(value: IncomeType) = _form.update { it.copy(type = value) }
    fun updateDay(value: Int) = _form.update { it.copy(dayOfMonth = value) }
    fun updateFrequency(value: Frequency) = _form.update { it.copy(frequency = value) }
    fun updateStartDate(value: LocalDate) = _form.update { it.copy(startDate = value) }
    fun updateEndDate(value: LocalDate?) = _form.update { it.copy(endDate = value) }
    fun updateIncrement(value: String) = _form.update { it.copy(incrementText = value) }
    fun updateActive(value: Boolean) = _form.update { it.copy(isActive = value) }
    fun updateNotes(value: String) = _form.update { it.copy(notes = value) }

    /** Which account this income is paid into. */
    fun updateAccount(value: Long?) = _form.update { it.copy(accountId = value) }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        val amount = Money.parseOrNull(current.amountText)

        if (current.name.isBlank()) {
            _form.update { it.copy(nameError = "Give this income a name") }
            return
        }
        if (amount == null || !amount.isPositive) {
            _form.update { it.copy(amountError = "Enter an amount greater than zero") }
            return
        }

        viewModelScope.launch {
            val source = IncomeSource(
                id = sourceId ?: 0,
                name = current.name.trim(),
                type = current.type,
                amount = amount,
                dayOfMonth = current.dayOfMonth,
                frequency = current.frequency,
                startDate = current.startDate,
                endDate = current.endDate,
                annualIncrementPercent = current.incrementText.toDoubleOrNull() ?: 0.0,
                accountId = current.accountId,
                isActive = current.isActive,
                notes = current.notes.trim()
            )
            if (sourceId != null) incomeRepository.updateSource(source)
            else incomeRepository.addSource(source)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = sourceId ?: return
        viewModelScope.launch {
            incomeRepository.deleteSource(id)
            onDeleted()
        }
    }
}

data class IncomeSourceForm(
    val name: String = "",
    val amountText: String = "",
    val type: IncomeType = IncomeType.SALARY,
    val dayOfMonth: Int = 1,
    val frequency: Frequency = Frequency.MONTHLY,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val incrementText: String = "",
    val accountId: Long? = null,
    val isActive: Boolean = true,
    val notes: String = "",
    val nameError: String? = null,
    val amountError: String? = null,
    val isEditing: Boolean = false
) {
    val canSave: Boolean
        get() = name.isNotBlank() && Money.parseOrNull(amountText)?.isPositive == true
}

/** Recording a one-off receipt: a bonus, a gift, or a freelance payment. */
@HiltViewModel
class IncomeReceiptViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    profileRepository: ProfileRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val receiptId: Long? =
        savedStateHandle.get<String>(Routes.ARG_RECEIPT_ID)?.toLongOrNull()

    private val _form = MutableStateFlow(
        IncomeReceiptForm(date = today.today(), isEditing = receiptId != null)
    )
    val form: StateFlow<IncomeReceiptForm> = _form.asStateFlow()

    val sources: StateFlow<List<IncomeSource>> = incomeRepository.sources.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val accounts: StateFlow<List<Account>> = profileRepository.accounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        if (receiptId != null) {
            viewModelScope.launch {
                incomeRepository.getTransaction(receiptId)?.let { existing ->
                    _form.value = IncomeReceiptForm(
                        name = existing.name,
                        amountText = existing.amount.toEditableText(),
                        type = existing.type,
                        date = existing.date,
                        sourceId = existing.sourceId,
                        accountId = existing.accountId,
                        notes = existing.notes,
                        isEditing = true,
                        // Held so an edit that does not move the receipt cannot silently
                        // re-point it at a different month. See save().
                        loadedPeriodKey = existing.periodKey,
                        loadedSourceId = existing.sourceId,
                        loadedDate = existing.date
                    )
                }
            }
        } else {
            // Land on the first account rather than on nothing. Money that arrives has to
            // go somewhere, and a receipt filed against no account is money the
            // per-account balances cannot explain, which shows up as an unassigned
            // difference the user then has to work out for themselves.
            viewModelScope.launch {
                val first = accounts.filterNot { it.isEmpty() }.first()
                _form.update { form ->
                    if (form.accountId != null) form else form.copy(accountId = first.first().id)
                }
            }
        }
    }

    fun updateName(value: String) = _form.update { it.copy(name = value) }
    fun updateAmount(value: String) = _form.update { it.copy(amountText = value, amountError = null) }
    fun updateType(value: IncomeType) = _form.update { it.copy(type = value) }
    fun updateDate(value: LocalDate) = _form.update { it.copy(date = value) }
    /**
     * Picking a regular income also moves the receipt to the account that income is paid
     * into, since that is nearly always where it landed. The user can still change it.
     */
    fun updateSource(value: Long?) = _form.update { form ->
        val sourceAccount = value?.let { id -> sources.value.firstOrNull { it.id == id }?.accountId }
        form.copy(sourceId = value, accountId = sourceAccount ?: form.accountId)
    }

    /** Which account the money arrived in. */
    fun updateAccount(value: Long?) = _form.update { it.copy(accountId = value) }
    fun updateNotes(value: String) = _form.update { it.copy(notes = value) }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        val amount = Money.parseOrNull(current.amountText)
        if (amount == null || !amount.isPositive) {
            _form.update { it.copy(amountError = "Enter an amount greater than zero") }
            return
        }
        viewModelScope.launch {
            val receipt = IncomeTransaction(
                id = receiptId ?: 0,
                sourceId = current.sourceId,
                name = current.name.ifBlank { current.type.label },
                type = current.type,
                amount = amount,
                date = current.date,
                accountId = current.accountId,
                periodKey = periodKeyFor(current),
                notes = current.notes.trim()
            )
            if (receiptId != null) {
                incomeRepository.updateTransaction(receipt)
            } else {
                incomeRepository.addTransaction(receipt)
            }
            onSaved()
        }
    }

    private fun periodKeyFor(form: IncomeReceiptForm): String? =
        IncomeReceiptPeriod.resolve(
            isNewReceipt = receiptId == null,
            sourceId = form.sourceId,
            date = form.date,
            originalSourceId = form.loadedSourceId,
            originalDate = form.loadedDate,
            originalPeriodKey = form.loadedPeriodKey
        )

    /** Removing a receipt that should not have been recorded at all. */
    fun delete(onDeleted: () -> Unit) {
        val id = receiptId ?: return
        viewModelScope.launch {
            incomeRepository.deleteTransaction(id)
            onDeleted()
        }
    }
}

data class IncomeReceiptForm(
    val name: String = "",
    val amountText: String = "",
    val type: IncomeType = IncomeType.OTHER,
    val date: LocalDate = LocalDate.now(),
    val sourceId: Long? = null,
    val accountId: Long? = null,
    val notes: String = "",
    val amountError: String? = null,
    val isEditing: Boolean = false,
    /** What the record already said, so an unrelated edit does not re-point its month. */
    val loadedPeriodKey: String? = null,
    val loadedSourceId: Long? = null,
    val loadedDate: LocalDate? = null
) {
    val canSave: Boolean get() = Money.parseOrNull(amountText)?.isPositive == true
}
