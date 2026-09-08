package com.moneyplanner.ui.screens.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.data.repo.CategoryRepository
import com.moneyplanner.data.repo.CreditCardRepository
import com.moneyplanner.data.repo.ExpenseRepository
import com.moneyplanner.data.repo.ExpenseMetadataRepository
import com.moneyplanner.data.repo.LinkedPaymentSync
import com.moneyplanner.data.repo.FamilyRepository
import com.moneyplanner.data.repo.PeopleRepository
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.data.repo.VehicleRepository
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.FamilyMember
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.Person
import com.moneyplanner.domain.model.Vehicle
import com.moneyplanner.domain.nlp.SpokenExpenseParser
import com.moneyplanner.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The month-by-month list of everything spent. */
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    categoryRepository: CategoryRepository,
    private val today: TodayProvider
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.from(today.today()))
    private val categoryFilter = MutableStateFlow<Long?>(null)

    val state: StateFlow<TransactionsState> = combine(
        selectedMonth,
        categoryFilter,
        expenseRepository.all,
        categoryRepository.all
    ) { month, filter, expenses, categories ->
        val inMonth = expenses.filter { YearMonth.from(it.date) == month }
        val filtered = if (filter == null) inMonth else inMonth.filter { it.categoryId == filter }

        TransactionsState(
            month = month,
            expenses = filtered.sortedWith(
                compareByDescending<Expense> { it.date }.thenByDescending { it.id }
            ),
            categories = categories,
            selectedCategoryId = filter,
            monthTotal = inMonth.sumOfMoney { it.amount },
            filteredTotal = filtered.sumOfMoney { it.amount },
            today = today.today(),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsState(month = YearMonth.from(today.today()))
    )

    fun selectMonth(month: YearMonth) {
        selectedMonth.value = month
    }

    fun previousMonth() {
        selectedMonth.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        selectedMonth.update { it.plusMonths(1) }
    }

    fun filterByCategory(categoryId: Long?) {
        categoryFilter.value = categoryId
    }

    fun delete(expenseId: Long) {
        viewModelScope.launch { expenseRepository.delete(expenseId) }
    }
}

data class TransactionsState(
    val month: YearMonth,
    val expenses: List<Expense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val monthTotal: Money = Money.ZERO,
    val filteredTotal: Money = Money.ZERO,
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true
) {
    val categoriesById: Map<Long, Category> get() = categories.associateBy { it.id }
}

/**
 * Adding or editing a single expense.
 *
 * The form is deliberately short: amount, category and date are the only things needed to
 * save, and everything else is optional, so a routine entry takes a few seconds rather
 * than a full page of fields.
 */
@HiltViewModel
class ExpenseEditorViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val metadataRepository: ExpenseMetadataRepository,
    private val linkedPaymentSync: LinkedPaymentSync,
    categoryRepository: CategoryRepository,
    peopleRepository: PeopleRepository,
    vehicleRepository: VehicleRepository,
    familyRepository: FamilyRepository,
    profileRepository: ProfileRepository,
    creditCardRepository: CreditCardRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val expenseId: Long? = savedStateHandle.get<String>(Routes.ARG_EXPENSE_ID)?.toLongOrNull()

    private val _form = MutableStateFlow(
        ExpenseForm(date = today.today(), isEditing = expenseId != null)
    )
    val form: StateFlow<ExpenseForm> = _form.asStateFlow()

    /** Attachments are available after the expense exists; new expenses save first. */
    val attachments = (expenseId?.let(metadataRepository::attachments) ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val options: StateFlow<ExpenseEditorOptions> = combine(
        categoryRepository.expenseCategories,
        peopleRepository.people,
        vehicleRepository.all,
        familyRepository.all,
        profileRepository.accounts,
        creditCardRepository.all
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ExpenseEditorOptions(
            categories = values[0] as List<Category>,
            people = values[1] as List<Person>,
            vehicles = values[2] as List<Vehicle>,
            familyMembers = values[3] as List<FamilyMember>,
            accounts = values[4] as List<Account>,
            creditCards = (values[5] as List<CreditCard>).filter { it.isActive }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExpenseEditorOptions()
    )

    init {
        if (expenseId != null) {
            viewModelScope.launch {
                expenseRepository.getById(expenseId)?.let { existing ->
                    val tags = metadataRepository.tagsForExpense(expenseId)
                    _form.value = ExpenseForm(
                        amountText = existing.amount.toEditableText(),
                        description = existing.description,
                        categoryId = existing.categoryId,
                        date = existing.date,
                        paymentMethod = existing.paymentMethod,
                        personId = existing.personId,
                        vehicleId = existing.vehicleId,
                        familyMemberId = existing.familyMemberId,
                        accountId = existing.accountId,
                        accountTouched = true,
                        creditCardId = existing.creditCardId,
                        notes = existing.notes,
                        tagsText = tags.joinToString(", "),
                        isEditing = true,
                        isLinked = existing.linkType != com.moneyplanner.domain.model.ExpenseLinkType.NONE
                    )
                }
            }
        } else {
            // A new entry starts on the account its payment method implies, once the
            // accounts have loaded. Left unset, every expense would file against no
            // account and the per-account balances could not account for the money.
            viewModelScope.launch {
                options.filterNot { it.accounts.isEmpty() }.first()
                _form.update { form ->
                    if (form.accountTouched || form.accountId != null) form
                    else form.copy(accountId = defaultAccountFor(form.paymentMethod))
                }
            }
        }
    }

    fun updateAmount(text: String) = _form.update { it.copy(amountText = text, amountError = null) }
    fun updateDescription(text: String) = _form.update { it.copy(description = text) }
    fun selectCategory(id: Long) = _form.update { it.copy(categoryId = id, categoryError = null) }
    fun updateDate(date: LocalDate) = _form.update { it.copy(date = date) }
    /**
     * Changing how it was paid also moves the account, unless the user has set one.
     *
     * Paying by cash almost always means the money came out of a pocket rather than
     * straight out of the bank, and asking somebody to restate that on every entry is how
     * the field ends up wrong or ignored. The moment the account is set by hand the guess
     * stops, because at that point the user has said something the app should not overrule.
     */
    fun selectPaymentMethod(method: PaymentMethod) = _form.update { form ->
        val card = if (method == PaymentMethod.CREDIT_CARD) {
            // One card is the common case, so choosing "credit card" is already the whole
            // answer. With several, the user picks.
            form.creditCardId ?: options.value.creditCards.singleOrNull()?.id
        } else {
            // The card is cleared when the method changes away from it, so an expense
            // cannot claim to be on a card it was not paid with.
            null
        }
        if (form.accountTouched) {
            form.copy(paymentMethod = method, creditCardId = card)
        } else {
            form.copy(
                paymentMethod = method,
                accountId = defaultAccountFor(method),
                creditCardId = card
            )
        }
    }

    /** Which card a card purchase went on. */
    fun selectCreditCard(id: Long?) = _form.update { it.copy(creditCardId = id) }

    /**
     * Cash comes out of the cash account, everything else out of the first bank account.
     * A card purchase touches no account at all until the bill is paid.
     */
    private fun defaultAccountFor(method: PaymentMethod): Long? {
        val accounts = options.value.accounts
        if (method == PaymentMethod.CREDIT_CARD) return null
        val wanted = if (method == PaymentMethod.CASH) AccountType.CASH else AccountType.BANK
        return (accounts.firstOrNull { it.type == wanted } ?: accounts.firstOrNull())?.id
    }
    fun selectPerson(id: Long?) = _form.update { it.copy(personId = id) }
    fun selectVehicle(id: Long?) = _form.update { it.copy(vehicleId = id) }
    fun selectFamilyMember(id: Long?) = _form.update { it.copy(familyMemberId = id) }

    /** Which account the money left. Optional: an entry with none is still recorded. */
    fun selectAccount(id: Long?) =
        _form.update { it.copy(accountId = id, accountTouched = true) }
    fun updateNotes(text: String) = _form.update { it.copy(notes = text) }
    fun updateTags(text: String) = _form.update { it.copy(tagsText = text) }

    fun addAttachment(uri: String, displayName: String, mimeType: String) {
        val id = expenseId ?: return
        viewModelScope.launch { metadataRepository.addAttachment(id, uri, displayName, mimeType) }
    }

    fun deleteAttachment(id: Long) {
        viewModelScope.launch { metadataRepository.deleteAttachment(id) }
    }

    /**
     * Fills the form from a spoken sentence.
     *
     * Only fields the parser actually recognised are written, so speaking "500 lunch"
     * after already picking a date does not silently reset that date. Nothing is saved:
     * the user still reviews and confirms, because speech recognition mishears numbers
     * and a wrong amount written straight to the ledger is a corrupted record.
     */
    fun applySpoken(spoken: String) {
        val parsed = SpokenExpenseParser.parse(
            spoken = spoken,
            categories = options.value.categories,
            today = today.today()
        )
        _form.update { current ->
            current.copy(
                amountText = parsed.amount?.toEditableText() ?: current.amountText,
                description = parsed.description.ifBlank { current.description },
                categoryId = parsed.categoryId ?: current.categoryId,
                date = parsed.date ?: current.date,
                paymentMethod = parsed.paymentMethod ?: current.paymentMethod,
                amountError = null,
                categoryError = null,
                heardText = spoken,
                heardSummary = parsed.understoodSummary(),
                heardNothingUsable = !parsed.isUsable
            )
        }
    }

    fun dismissHeard() = _form.update {
        it.copy(heardText = null, heardSummary = null, heardNothingUsable = false)
    }

    /** Saves, or reports what is missing. Returns true when the entry was stored. */
    fun save(onSaved: () -> Unit) {
        val current = _form.value
        val amount = Money.parseOrNull(current.amountText)

        if (amount == null || !amount.isPositive) {
            _form.update { it.copy(amountError = "Enter an amount greater than zero") }
            return
        }
        if (current.categoryId == null) {
            _form.update { it.copy(categoryError = "Choose a category") }
            return
        }

        viewModelScope.launch {
            val expense = Expense(
                id = expenseId ?: 0,
                amount = amount,
                description = current.description.trim(),
                categoryId = current.categoryId,
                date = current.date,
                paymentMethod = current.paymentMethod,
                personId = current.personId,
                vehicleId = current.vehicleId,
                familyMemberId = current.familyMemberId,
                accountId = current.accountId,
                creditCardId = current.creditCardId,
                notes = current.notes.trim()
            )
            if (expenseId != null) {
                // Preserve the link so an expense created by paying a bill keeps its
                // connection to that bill after being edited.
                val existing = expenseRepository.getById(expenseId)
                val updated = expense.copy(
                    linkType = existing?.linkType ?: com.moneyplanner.domain.model.ExpenseLinkType.NONE,
                    linkId = existing?.linkId,
                    linkPeriodKey = existing?.linkPeriodKey
                )
                expenseRepository.update(updated)
                metadataRepository.replaceTags(expenseId, current.tagsText.split(','))
                // The payment record behind a linked expense describes the same event, so
                // a corrected amount has to reach it too or the two will disagree.
                linkedPaymentSync.syncFromExpense(updated)
            } else {
                val id = expenseRepository.add(expense)
                metadataRepository.replaceTags(id, current.tagsText.split(','))
            }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = expenseId ?: return
        viewModelScope.launch {
            expenseRepository.delete(id)
            onDeleted()
        }
    }
}

data class ExpenseForm(
    val amountText: String = "",
    val description: String = "",
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val personId: Long? = null,
    val vehicleId: Long? = null,
    val familyMemberId: Long? = null,
    /**
     * Carried through the form untouched. The editor does not offer these yet, and
     * rebuilding the record without them would silently blank whichever account or card
     * the expense was originally filed against.
     */
    val accountId: Long? = null,
    /** True once the user has picked an account themselves, which stops the app guessing. */
    val accountTouched: Boolean = false,
    val creditCardId: Long? = null,
    val notes: String = "",
    /** Comma-separated in the compact editor, stored as normalized individual labels. */
    val tagsText: String = "",
    val amountError: String? = null,
    val categoryError: String? = null,
    val isEditing: Boolean = false,
    /** True when this expense was created by paying an EMI, a bill or a yearly expense. */
    val isLinked: Boolean = false,
    /** What the speech recogniser heard, shown back so a misheard entry is obvious. */
    val heardText: String? = null,
    val heardSummary: String? = null,
    val heardNothingUsable: Boolean = false
) {
    val canSave: Boolean
        get() = Money.parseOrNull(amountText)?.isPositive == true && categoryId != null
}

data class ExpenseEditorOptions(
    val categories: List<Category> = emptyList(),
    val people: List<Person> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val familyMembers: List<FamilyMember> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val creditCards: List<CreditCard> = emptyList()
)

/**
 * Renders an amount back into the plain text an amount field expects.
 *
 * The sign is written out separately rather than taken from the rupee part, because
 * integer division truncates towards zero: -50 paise gives 0 rupees, so relying on that
 * to carry the sign would turn a 50 paise correction into a positive one.
 */
fun Money.toEditableText(): String {
    val sign = if (paise < 0) "-" else ""
    val absolute = kotlin.math.abs(paise)
    val rupees = absolute / 100
    val remainder = absolute % 100
    return if (remainder == 0L) "$sign$rupees"
    else "$sign$rupees.${remainder.toString().padStart(2, '0')}"
}
