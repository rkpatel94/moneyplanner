package com.moneyplanner.ui.screens.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.repo.CategoryRepository
import com.moneyplanner.data.repo.PeopleRepository
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.domain.calc.AllocationResult
import com.moneyplanner.domain.calc.PersonBalanceSummary
import com.moneyplanner.domain.calc.SettlementCalculator
import com.moneyplanner.domain.calc.SplitCalculator
import com.moneyplanner.domain.calc.SplitParticipant
import com.moneyplanner.domain.calc.SplitValidation
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.Person
import com.moneyplanner.domain.model.Relation
import com.moneyplanner.domain.model.Settlement
import com.moneyplanner.domain.model.SettlementDirection
import com.moneyplanner.domain.model.SplitType
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
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository
) : ViewModel() {

    val state: StateFlow<PeopleState> = combine(
        peopleRepository.people,
        peopleRepository.ledgerEntries,
        peopleRepository.settlements
    ) { people, entries, settlements ->
        val summaries = SettlementCalculator.summaries(people, entries, settlements)
        PeopleState(
            owedToMe = summaries.filter { it.theyOweMe }.sortedByDescending { it.balance.paise },
            iOwe = summaries.filter { it.iOweThem }.sortedBy { it.balance.paise },
            settled = summaries.filter { it.isSettled },
            totalReceivable = SettlementCalculator.totalReceivable(entries, settlements),
            totalPayable = SettlementCalculator.totalPayable(entries, settlements),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PeopleState()
    )
}

data class PeopleState(
    val owedToMe: List<PersonBalanceSummary> = emptyList(),
    val iOwe: List<PersonBalanceSummary> = emptyList(),
    val settled: List<PersonBalanceSummary> = emptyList(),
    val totalReceivable: Money = Money.ZERO,
    val totalPayable: Money = Money.ZERO,
    val isLoading: Boolean = true
) {
    val hasAnyone: Boolean get() = owedToMe.isNotEmpty() || iOwe.isNotEmpty() || settled.isNotEmpty()
}

/**
 * One person: their running balance, every obligation behind it, and the settlements
 * that have chipped away at it.
 */
@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository,
    profileRepository: ProfileRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val personId: Long = savedStateHandle.get<String>(Routes.ARG_PERSON_ID)?.toLongOrNull() ?: 0L

    val state: StateFlow<PersonDetailState> = combine(
        peopleRepository.person(personId),
        peopleRepository.entriesFor(personId),
        peopleRepository.settlementsFor(personId)
    ) { person, entries, settlements ->
        PersonDetailState(
            person = person,
            allocation = SettlementCalculator.allocate(entries, settlements),
            settlements = settlements.sortedWith(
                compareByDescending<Settlement> { it.date }.thenByDescending { it.id }
            ),
            today = today.today(),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PersonDetailState()
    )

    private val _settleForm = MutableStateFlow(SettleForm())
    val settleForm: StateFlow<SettleForm> = _settleForm.asStateFlow()

    init {
        // Arrived here from the activity feed, which names a settlement to correct. Wait
        // for the records to load before opening the sheet, since the outstanding figure
        // it shows has to be worked out from them.
        val requested = savedStateHandle.get<String>(Routes.ARG_SETTLEMENT_ID)?.toLongOrNull()
        if (requested != null) {
            viewModelScope.launch {
                state.filterNot { it.isLoading }.first()
                    .settlements.firstOrNull { it.id == requested }
                    ?.let(::startEditingSettlement)
            }
        }
    }

    /** Accounts, so a settlement can say where the money actually came from or went. */
    val accounts: StateFlow<List<Account>> = profileRepository.accounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    /**
     * Opens the settlement sheet.
     *
     * Recording a payment against a person is a real cash movement, so it asks the same
     * questions as any other: how much, from where, how, and when. Filing it against no
     * account would drop the money into the unassigned bucket, where the per-account
     * balances can no longer explain a figure the overall balance has already changed.
     *
     * @param full pre-fills the whole outstanding balance rather than leaving it blank.
     */
    fun startSettlement(balance: Money, full: Boolean) {
        if (balance.isZero) return
        val method = PaymentMethod.UPI
        _settleForm.value = SettleForm(
            isOpen = true,
            amountText = if (full) balance.abs().toEditableText() else "",
            paymentMethod = method,
            accountId = defaultAccountFor(method),
            date = today.today(),
            direction = if (balance.isPositive) {
                SettlementDirection.RECEIVED_FROM_THEM
            } else {
                SettlementDirection.PAID_TO_THEM
            },
            outstanding = balance.abs()
        )
    }

    /**
     * Opens the sheet on a settlement already recorded.
     *
     * The outstanding figure shown is the balance *without* this settlement, not the
     * balance as it stands. The current balance already has this payment taken off it, so
     * showing that alongside the amount being edited would describe a debt that has been
     * reduced twice, and every "what would still be owed" line under it would be wrong.
     */
    fun startEditingSettlement(settlement: Settlement) {
        val entries = state.value.allocation?.rows?.map { it.entry }.orEmpty()
        val others = state.value.settlements.filterNot { it.id == settlement.id }
        val balanceWithoutThis = SettlementCalculator.balanceFor(entries, others)

        _settleForm.value = SettleForm(
            isOpen = true,
            editingId = settlement.id,
            amountText = settlement.amount.toEditableText(),
            paymentMethod = settlement.paymentMethod,
            accountId = settlement.accountId,
            // The account came off the record, so the payment method must not override it.
            accountTouched = true,
            date = settlement.date,
            notes = settlement.notes,
            direction = settlement.direction,
            outstanding = balanceWithoutThis.abs()
        )
    }

    fun dismissSettlement() {
        _settleForm.value = SettleForm()
    }

    fun updateSettleAmount(value: String) =
        _settleForm.update { it.copy(amountText = value, error = null) }

    fun updateSettleMethod(method: PaymentMethod) = _settleForm.update { form ->
        if (form.accountTouched) form.copy(paymentMethod = method)
        else form.copy(paymentMethod = method, accountId = defaultAccountFor(method))
    }

    fun updateSettleAccount(id: Long?) =
        _settleForm.update { it.copy(accountId = id, accountTouched = true) }

    fun updateSettleDate(value: LocalDate) = _settleForm.update { it.copy(date = value) }

    fun updateSettleNotes(value: String) = _settleForm.update { it.copy(notes = value) }

    /**
     * Cash leaves the cash account, everything else the first bank account. The same rule
     * the expense form uses, so the two never disagree about where cash lives.
     */
    private fun defaultAccountFor(method: PaymentMethod): Long? {
        val available = accounts.value
        val wanted = if (method == PaymentMethod.CASH) AccountType.CASH else AccountType.BANK
        return (available.firstOrNull { it.type == wanted } ?: available.firstOrNull())?.id
    }

    /**
     * Records the settlement.
     *
     * Paying more than is outstanding is allowed rather than blocked: an overpayment is a
     * real thing that happens, and the allocator already reports it instead of absorbing
     * it. The form only warns, so the user can see it before deciding.
     */
    fun saveSettlement() {
        val current = _settleForm.value
        val amount = Money.parseOrNull(current.amountText)
        if (amount == null || !amount.isPositive) {
            _settleForm.update { it.copy(error = "Enter an amount greater than zero") }
            return
        }
        viewModelScope.launch {
            val settlement = Settlement(
                id = current.editingId ?: 0,
                personId = personId,
                amount = amount,
                direction = current.direction,
                date = current.date,
                paymentMethod = current.paymentMethod,
                accountId = current.accountId,
                notes = current.notes.trim()
            )
            if (current.editingId != null) {
                peopleRepository.updateSettlement(settlement)
            } else {
                peopleRepository.addSettlement(settlement)
            }
            _settleForm.value = SettleForm()
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { peopleRepository.deleteEntry(id) }
    }

    fun deleteSettlement(id: Long) {
        viewModelScope.launch { peopleRepository.deleteSettlement(id) }
    }

    fun deletePerson(onDeleted: () -> Unit) {
        viewModelScope.launch {
            peopleRepository.deletePerson(personId)
            onDeleted()
        }
    }
}

data class PersonDetailState(
    val person: Person? = null,
    val allocation: AllocationResult? = null,
    val settlements: List<Settlement> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true
) {
    val balance: Money get() = allocation?.balance ?: Money.ZERO
}

/** Adding a person. */
@HiltViewModel
class AddPersonViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository
) : ViewModel() {

    private val _form = MutableStateFlow(AddPersonForm())
    val form: StateFlow<AddPersonForm> = _form.asStateFlow()

    fun updateName(value: String) = _form.update { it.copy(name = value, nameError = null) }
    fun updateRelation(value: Relation) = _form.update { it.copy(relation = value) }
    fun updatePhone(value: String) = _form.update { it.copy(phone = value) }
    fun updateNotes(value: String) = _form.update { it.copy(notes = value) }

    fun save(onSaved: (Long) -> Unit) {
        val current = _form.value
        if (current.name.isBlank()) {
            _form.update { it.copy(nameError = "Enter a name") }
            return
        }
        viewModelScope.launch {
            val id = peopleRepository.addPerson(
                current.name, current.relation, current.phone, current.notes
            )
            onSaved(id)
        }
    }
}

data class AddPersonForm(
    val name: String = "",
    val relation: Relation = Relation.FRIEND,
    val phone: String = "",
    val notes: String = "",
    val nameError: String? = null
) {
    val canSave: Boolean get() = name.isNotBlank()
}

/** Recording a plain "they owe me" or "I owe them" entry. */
@HiltViewModel
class ObligationEditorViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val personId: Long = savedStateHandle.get<String>(Routes.ARG_PERSON_ID)?.toLongOrNull() ?: 0L

    private val _form = MutableStateFlow(ObligationForm(date = today.today()))
    val form: StateFlow<ObligationForm> = _form.asStateFlow()

    val person: StateFlow<Person?> = peopleRepository.person(personId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    fun updateAmount(value: String) = _form.update { it.copy(amountText = value, amountError = null) }
    fun updateDirection(value: LedgerDirection) = _form.update { it.copy(direction = value) }
    fun updateDescription(value: String) = _form.update { it.copy(description = value) }
    fun updateDate(value: LocalDate) = _form.update { it.copy(date = value) }
    fun updateExpectedDate(value: LocalDate?) = _form.update { it.copy(expectedDate = value) }
    fun updateNotes(value: String) = _form.update { it.copy(notes = value) }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        val amount = Money.parseOrNull(current.amountText)
        if (amount == null || !amount.isPositive) {
            _form.update { it.copy(amountError = "Enter an amount greater than zero") }
            return
        }
        viewModelScope.launch {
            peopleRepository.addSimpleObligation(
                personId = personId,
                amount = amount,
                direction = current.direction,
                description = current.description,
                date = current.date,
                expectedDate = current.expectedDate,
                notes = current.notes
            )
            onSaved()
        }
    }
}

data class ObligationForm(
    val amountText: String = "",
    val direction: LedgerDirection = LedgerDirection.THEY_OWE_ME,
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    /** When set, the forecast expects the money in that month. */
    val expectedDate: LocalDate? = null,
    val notes: String = "",
    val amountError: String? = null
) {
    val canSave: Boolean get() = Money.parseOrNull(amountText)?.isPositive == true
}

/**
 * Splitting a bill with other people.
 *
 * The screen previews each person's share live, because a split that does not add up to
 * the bill is the easiest way to end up with balances nobody can explain later.
 */
@HiltViewModel
class SharedExpenseViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository,
    categoryRepository: CategoryRepository,
    private val today: TodayProvider
) : ViewModel() {

    private val _form = MutableStateFlow(SharedExpenseForm(date = today.today()))
    val form: StateFlow<SharedExpenseForm> = _form.asStateFlow()

    val options: StateFlow<SharedExpenseOptions> = combine(
        peopleRepository.people,
        categoryRepository.expenseCategories
    ) { people, categories ->
        SharedExpenseOptions(people, categories)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SharedExpenseOptions()
    )

    fun updateDescription(value: String) = _form.update { it.copy(description = value) }
    fun updateAmount(value: String) = _form.update { it.copy(amountText = value, amountError = null) }
    fun updateDate(value: LocalDate) = _form.update { it.copy(date = value) }
    fun updateCategory(value: Long?) = _form.update { it.copy(categoryId = value, categoryError = null) }
    fun updateSplitType(value: SplitType) = _form.update { it.copy(splitType = value) }
    fun updatePaidBy(value: Long?) = _form.update { it.copy(paidByPersonId = value) }

    fun toggleParticipant(personId: Long) = _form.update { current ->
        val updated = if (personId in current.participantIds) {
            current.participantIds - personId
        } else {
            current.participantIds + personId
        }
        current.copy(participantIds = updated)
    }

    fun updateExactAmount(personId: Long?, value: String) = _form.update { current ->
        current.copy(exactAmounts = current.exactAmounts + (personId to value))
    }

    fun updatePercent(personId: Long?, value: String) = _form.update { current ->
        current.copy(percentages = current.percentages + (personId to value))
    }

    /** The live preview of who owes what, recalculated on every keystroke. */
    fun preview(): SharedExpensePreview {
        val current = _form.value
        val total = Money.parseOrNull(current.amountText) ?: return SharedExpensePreview()
        if (!total.isPositive || current.participantIds.isEmpty()) return SharedExpensePreview()

        val participants = buildList {
            add(
                SplitParticipant(
                    personId = null,
                    exactAmount = Money.parseOrNull(current.exactAmounts[null].orEmpty()),
                    percent = current.percentages[null]?.toDoubleOrNull(),
                    bearsFullAmount = current.fullPayerId == null && current.splitType == SplitType.FULL_ON_ONE
                )
            )
            current.participantIds.forEach { id ->
                add(
                    SplitParticipant(
                        personId = id,
                        exactAmount = Money.parseOrNull(current.exactAmounts[id].orEmpty()),
                        percent = current.percentages[id]?.toDoubleOrNull(),
                        bearsFullAmount = current.fullPayerId == id
                    )
                )
            }
        }

        val validation = when (current.splitType) {
            SplitType.EXACT -> SplitCalculator.validateExact(total, participants)
            SplitType.PERCENTAGE -> SplitCalculator.validatePercentages(participants)
            else -> SplitValidation.Valid
        }

        val result = SplitCalculator.split(total, current.splitType, participants, current.paidByPersonId)
        return SharedExpensePreview(
            shares = result.shares.associate { it.personId to it.amount },
            myShare = result.myShare,
            validation = validation,
            isValid = validation is SplitValidation.Valid
        )
    }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        val total = Money.parseOrNull(current.amountText)
        if (total == null || !total.isPositive) {
            _form.update { it.copy(amountError = "Enter an amount greater than zero") }
            return
        }
        if (current.participantIds.isEmpty()) return
        // The money the user pays out becomes a real expense, and an expense has to be
        // filed under something, so the category stops being optional here.
        if (current.categoryId == null) {
            _form.update { it.copy(categoryError = "Choose a category") }
            return
        }
        if (!preview().isValid) return

        val participants = buildList {
            add(
                SplitParticipant(
                    personId = null,
                    exactAmount = Money.parseOrNull(current.exactAmounts[null].orEmpty()),
                    percent = current.percentages[null]?.toDoubleOrNull(),
                    bearsFullAmount = current.fullPayerId == null && current.splitType == SplitType.FULL_ON_ONE
                )
            )
            current.participantIds.forEach { id ->
                add(
                    SplitParticipant(
                        personId = id,
                        exactAmount = Money.parseOrNull(current.exactAmounts[id].orEmpty()),
                        percent = current.percentages[id]?.toDoubleOrNull(),
                        bearsFullAmount = current.fullPayerId == id
                    )
                )
            }
        }

        viewModelScope.launch {
            peopleRepository.saveSharedExpense(
                description = current.description.ifBlank { "Shared expense" },
                totalAmount = total,
                date = current.date,
                categoryId = current.categoryId,
                splitType = current.splitType,
                participants = participants,
                paidByPersonId = current.paidByPersonId,
                notes = current.notes
            )
            onSaved()
        }
    }
}

data class SharedExpenseForm(
    val description: String = "",
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val categoryId: Long? = null,
    val splitType: SplitType = SplitType.EQUAL,
    val participantIds: Set<Long> = emptySet(),
    /** Null means the user paid the bill themselves. */
    val paidByPersonId: Long? = null,
    val fullPayerId: Long? = null,
    val exactAmounts: Map<Long?, String> = emptyMap(),
    val percentages: Map<Long?, String> = emptyMap(),
    val notes: String = "",
    val amountError: String? = null,
    val categoryError: String? = null
)

data class SharedExpenseOptions(
    val people: List<Person> = emptyList(),
    val categories: List<Category> = emptyList()
)

data class SharedExpensePreview(
    val shares: Map<Long?, Money> = emptyMap(),
    val myShare: Money = Money.ZERO,
    val validation: SplitValidation = SplitValidation.Valid,
    val isValid: Boolean = false
)

/**
 * A settlement being recorded: how much, which way, from where, how and when.
 *
 * [outstanding] is carried so the sheet can show what is still owed and flag an amount
 * that goes past it, without the caller having to recompute the balance.
 */
data class SettleForm(
    val isOpen: Boolean = false,
    /** Set when correcting a settlement already recorded, rather than adding one. */
    val editingId: Long? = null,
    val amountText: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val accountId: Long? = null,
    /** Set once the user picks an account, after which the method stops overriding it. */
    val accountTouched: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val notes: String = "",
    val error: String? = null,
    val direction: SettlementDirection = SettlementDirection.PAID_TO_THEM,
    val outstanding: Money = Money.ZERO
) {
    val amount: Money? get() = Money.parseOrNull(amountText)
    val canSave: Boolean get() = amount?.isPositive == true
    val isEditing: Boolean get() = editingId != null
    val isPayment: Boolean get() = direction == SettlementDirection.PAID_TO_THEM

    /** True when the amount entered is more than is actually owed. */
    val isOverpayment: Boolean
        get() = amount?.let { it > outstanding } == true

    /** What would still be owed after this payment, floored at zero. */
    val remainingAfter: Money
        get() = amount?.let { (outstanding - it).coerceAtLeastZero() } ?: outstanding
}
