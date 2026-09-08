package com.moneyplanner.ui.screens.emi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.data.repo.CategoryRepository
import com.moneyplanner.data.repo.EmiRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.data.repo.VehicleRepository
import com.moneyplanner.domain.calc.EmiCalculator
import com.moneyplanner.domain.calc.EmiScheduleRow
import com.moneyplanner.domain.calc.PrepaymentCalculator
import com.moneyplanner.domain.calc.PrepaymentResult
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.Vehicle
import com.moneyplanner.ui.nav.Routes
import com.moneyplanner.ui.screens.expenses.toEditableText
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class EmiListViewModel @Inject constructor(
    private val emiRepository: EmiRepository,
    private val today: TodayProvider
) : ViewModel() {

    val state: StateFlow<EmiListState> = combine(
        emiRepository.all,
        emiRepository.payments
    ) { emis, payments ->
        val rows = emis.map { emi ->
            EmiRow(
                emi = emi,
                paidInstallments = EmiCalculator.paidInstallments(emi, payments),
                remainingInstallments = EmiCalculator.remainingInstallments(emi, payments),
                outstanding = EmiCalculator.outstandingAmount(emi, payments),
                nextDueDate = EmiCalculator.nextDueDate(emi, payments),
                isCompleted = EmiCalculator.isCompleted(emi, payments)
            )
        }
        EmiListState(
            active = rows.filter { !it.isCompleted && it.emi.isActive },
            closed = rows.filter { it.isCompleted || !it.emi.isActive },
            monthlyBurden = rows
                .filter { !it.isCompleted && it.emi.isActive }
                .sumOfMoney { row ->
                    if (row.emi.frequency.isWeekly) row.emi.emiAmount * 4
                    else row.emi.emiAmount.divideRounded(row.emi.frequency.monthsPerPeriod)
                },
            totalOutstanding = rows
                .filter { !it.isCompleted && it.emi.isActive }
                .sumOfMoney { it.outstanding },
            today = today.today(),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EmiListState()
    )
}

data class EmiRow(
    val emi: Emi,
    val paidInstallments: Int,
    val remainingInstallments: Int,
    val outstanding: Money,
    val nextDueDate: LocalDate?,
    val isCompleted: Boolean
) {
    val progressFraction: Float
        get() = if (emi.totalInstallments == 0) 0f
        else (paidInstallments.toFloat() / emi.totalInstallments).coerceIn(0f, 1f)
}

data class EmiListState(
    val active: List<EmiRow> = emptyList(),
    val closed: List<EmiRow> = emptyList(),
    val monthlyBurden: Money = Money.ZERO,
    val totalOutstanding: Money = Money.ZERO,
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true
)

@HiltViewModel
class EmiDetailViewModel @Inject constructor(
    private val emiRepository: EmiRepository,
    categoryRepository: CategoryRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val emiId: Long = savedStateHandle.get<String>(Routes.ARG_EMI_ID)?.toLongOrNull() ?: 0L

    /** What the user is considering paying on top, before they commit to it. */
    private val extraPerMonth = MutableStateFlow("")
    val extraInput: StateFlow<String> = extraPerMonth.asStateFlow()

    val state: StateFlow<EmiDetailState> = combine(
        emiRepository.byId(emiId),
        emiRepository.paymentsFor(emiId),
        categoryRepository.expenseCategories,
        extraPerMonth
    ) { emi, payments, categories, extraText ->
        if (emi == null) {
            EmiDetailState(isLoading = false)
        } else {
            EmiDetailState(
                emi = emi,
                schedule = EmiCalculator.schedule(emi, payments),
                paidInstallments = EmiCalculator.paidInstallments(emi, payments),
                remainingInstallments = EmiCalculator.remainingInstallments(emi, payments),
                outstanding = EmiCalculator.outstandingAmount(emi, payments),
                totalPayable = EmiCalculator.totalPayable(emi),
                totalInterest = EmiCalculator.totalInterest(emi),
                nextInstallment = EmiCalculator.nextInstallmentNumber(emi, payments),
                nextDueDate = EmiCalculator.nextDueDate(emi, payments),
                categories = categories,
                today = today.today(),
                prepayment = PrepaymentCalculator.evaluate(
                    emi = emi,
                    payments = payments,
                    extraPerMonth = Money.parseOrNull(extraText) ?: Money.ZERO
                ),
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EmiDetailState()
    )

    /**
     * Records the next installment as paid.
     *
     * The matching expense is filed under the EMI category when one exists, so the
     * payment shows up in the month's spending without being mistaken for everyday
     * spending in the forecast.
     */
    fun payNextInstallment() {
        val current = state.value
        val emi = current.emi ?: return
        val installment = current.nextInstallment ?: return
        val category = current.categories.firstOrNull { it.name.equals("EMI", ignoreCase = true) }
            ?: current.categories.firstOrNull { it.id == emi.categoryId }

        viewModelScope.launch {
            emiRepository.payInstallment(
                emi = emi,
                installmentNumber = installment,
                amount = emi.emiAmount,
                paidOn = today.today(),
                categoryId = category?.id
            )
        }
    }

    fun updateExtraPerMonth(value: String) {
        extraPerMonth.value = value
    }

    fun undoInstallment(installmentNumber: Int) {
        val emi = state.value.emi ?: return
        viewModelScope.launch { emiRepository.undoInstallment(emi, installmentNumber) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            emiRepository.delete(emiId)
            onDeleted()
        }
    }
}

data class EmiDetailState(
    val emi: Emi? = null,
    val schedule: List<EmiScheduleRow> = emptyList(),
    val paidInstallments: Int = 0,
    val remainingInstallments: Int = 0,
    val outstanding: Money = Money.ZERO,
    val totalPayable: Money = Money.ZERO,
    val totalInterest: Money = Money.ZERO,
    val nextInstallment: Int? = null,
    val nextDueDate: LocalDate? = null,
    val categories: List<Category> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** What paying extra would save, recalculated as the user types. */
    val prepayment: PrepaymentResult? = null,
    val isLoading: Boolean = true
)

/**
 * Creating or editing a loan.
 *
 * If the user knows the principal, rate and tenure but not the installment, the standard
 * formula fills it in as a suggestion. What they type always wins, because the amount the
 * bank actually collects is the number the forecast has to use.
 */
@HiltViewModel
class EmiEditorViewModel @Inject constructor(
    private val emiRepository: EmiRepository,
    categoryRepository: CategoryRepository,
    vehicleRepository: VehicleRepository,
    private val today: TodayProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val emiId: Long? = savedStateHandle.get<String>(Routes.ARG_EMI_ID)?.toLongOrNull()

    private val _form = MutableStateFlow(
        EmiForm(
            startDate = today.today(),
            firstDueDate = today.today().withDayOfMonth(minOf(5, today.today().lengthOfMonth())),
            isEditing = emiId != null
        )
    )
    val form: StateFlow<EmiForm> = _form.asStateFlow()

    val options: StateFlow<EmiEditorOptions> = combine(
        categoryRepository.expenseCategories,
        vehicleRepository.all
    ) { categories, vehicles ->
        EmiEditorOptions(categories, vehicles)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EmiEditorOptions()
    )

    init {
        if (emiId != null) {
            viewModelScope.launch {
                emiRepository.getById(emiId)?.let { existing ->
                    _form.value = EmiForm(
                        name = existing.name,
                        emiAmountText = existing.emiAmount.toEditableText(),
                        principalText = existing.principal.toEditableText(),
                        interestRateText = existing.interestRatePercent?.toString().orEmpty(),
                        startDate = existing.startDate,
                        firstDueDate = existing.firstDueDate,
                        frequency = existing.frequency,
                        totalInstallmentsText = existing.totalInstallments.toString(),
                        openingPaidText = existing.openingPaidInstallments.toString(),
                        categoryId = existing.categoryId,
                        vehicleId = existing.vehicleId,
                        accountReference = existing.accountReference,
                        autoDebit = existing.autoDebit,
                        isActive = existing.isActive,
                        notes = existing.notes,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun updateName(v: String) = _form.update { it.copy(name = v, nameError = null) }
    fun updateEmiAmount(v: String) = _form.update { it.copy(emiAmountText = v, amountError = null) }
    fun updatePrincipal(v: String) = _form.update { it.copy(principalText = v) }
    fun updateInterestRate(v: String) = _form.update { it.copy(interestRateText = v) }
    fun updateStartDate(v: LocalDate) = _form.update { it.copy(startDate = v) }
    fun updateFirstDueDate(v: LocalDate) = _form.update { it.copy(firstDueDate = v) }
    fun updateFrequency(v: Frequency) = _form.update { it.copy(frequency = v) }
    fun updateTotalInstallments(v: String) = _form.update {
        it.copy(totalInstallmentsText = v.filter { c -> c.isDigit() }, tenureError = null)
    }
    fun updateOpeningPaid(v: String) = _form.update {
        it.copy(openingPaidText = v.filter { c -> c.isDigit() })
    }
    fun updateCategory(v: Long?) = _form.update { it.copy(categoryId = v) }
    fun updateVehicle(v: Long?) = _form.update { it.copy(vehicleId = v) }
    fun updateAccountReference(v: String) = _form.update { it.copy(accountReference = v) }
    fun updateAutoDebit(v: Boolean) = _form.update { it.copy(autoDebit = v) }
    fun updateActive(v: Boolean) = _form.update { it.copy(isActive = v) }
    fun updateNotes(v: String) = _form.update { it.copy(notes = v) }

    /** Fills the installment field from the principal, rate and tenure. */
    fun suggestInstallment() {
        val current = _form.value
        val principal = Money.parseOrNull(current.principalText) ?: return
        val rate = current.interestRateText.toDoubleOrNull() ?: return
        val tenure = current.totalInstallmentsText.toIntOrNull() ?: return
        if (tenure <= 0) return

        val suggested = EmiCalculator.calculateInstallment(principal, rate, tenure)
        _form.update { it.copy(emiAmountText = suggested.toEditableText(), amountError = null) }
    }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        val amount = Money.parseOrNull(current.emiAmountText)
        val tenure = current.totalInstallmentsText.toIntOrNull()

        if (current.name.isBlank()) {
            _form.update { it.copy(nameError = "Give this loan a name") }
            return
        }
        if (amount == null || !amount.isPositive) {
            _form.update { it.copy(amountError = "Enter the installment amount") }
            return
        }
        if (tenure == null || tenure <= 0) {
            _form.update { it.copy(tenureError = "Enter how many installments there are") }
            return
        }

        viewModelScope.launch {
            val emi = Emi(
                id = emiId ?: 0,
                name = current.name.trim(),
                categoryId = current.categoryId,
                principal = Money.parseOrNull(current.principalText) ?: Money.ZERO,
                emiAmount = amount,
                interestRatePercent = current.interestRateText.toDoubleOrNull(),
                startDate = current.startDate,
                firstDueDate = current.firstDueDate,
                frequency = current.frequency,
                totalInstallments = tenure,
                openingPaidInstallments = (current.openingPaidText.toIntOrNull() ?: 0)
                    .coerceIn(0, tenure),
                vehicleId = current.vehicleId,
                accountReference = current.accountReference.trim(),
                autoDebit = current.autoDebit,
                isActive = current.isActive,
                notes = current.notes.trim()
            )
            if (emiId != null) emiRepository.update(emi) else emiRepository.add(emi)
            onSaved()
        }
    }
}

data class EmiForm(
    val name: String = "",
    val emiAmountText: String = "",
    val principalText: String = "",
    val interestRateText: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val firstDueDate: LocalDate = LocalDate.now(),
    val frequency: Frequency = Frequency.MONTHLY,
    val totalInstallmentsText: String = "",
    val openingPaidText: String = "0",
    val categoryId: Long? = null,
    val vehicleId: Long? = null,
    val accountReference: String = "",
    val autoDebit: Boolean = false,
    val isActive: Boolean = true,
    val notes: String = "",
    val nameError: String? = null,
    val amountError: String? = null,
    val tenureError: String? = null,
    val isEditing: Boolean = false
) {
    val canSuggestInstallment: Boolean
        get() = Money.parseOrNull(principalText)?.isPositive == true &&
            (interestRateText.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (totalInstallmentsText.toIntOrNull() ?: 0) > 0

    val canSave: Boolean
        get() = name.isNotBlank() &&
            Money.parseOrNull(emiAmountText)?.isPositive == true &&
            (totalInstallmentsText.toIntOrNull() ?: 0) > 0
}

data class EmiEditorOptions(
    val categories: List<Category> = emptyList(),
    val vehicles: List<Vehicle> = emptyList()
)
