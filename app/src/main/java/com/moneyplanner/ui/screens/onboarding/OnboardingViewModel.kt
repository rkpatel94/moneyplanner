package com.moneyplanner.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.data.repo.BillRepository
import com.moneyplanner.data.repo.CategoryRepository
import com.moneyplanner.data.repo.EmiRepository
import com.moneyplanner.data.repo.IncomeRepository
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.model.BillAmountType
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.IncomeSource
import com.moneyplanner.domain.model.IncomeType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.RecurringBill
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Collects just enough to make the dashboard meaningful on first open.
 *
 * Nothing is written until the user finishes or skips, and each answer is optional. What
 * is written goes through exactly the same repositories as manual entry, so an onboarding
 * salary is an ordinary income source the user can edit or delete like any other — there
 * is no special first-run data hiding anywhere.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val incomeRepository: IncomeRepository,
    private val billRepository: BillRepository,
    private val emiRepository: EmiRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsStore: SettingsStore,
    private val snapshotRepository: SnapshotRepository,
    private val today: TodayProvider,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    companion object {
        const val TOTAL_STEPS = 4
    }

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun updateName(value: String) = _state.update { it.copy(name = value) }
    fun updateBalance(value: String) = _state.update { it.copy(balanceText = value) }
    fun updateSalary(value: String) = _state.update { it.copy(salaryText = value) }
    fun updateSalaryDay(value: Int) = _state.update { it.copy(salaryDay = value) }
    fun updateRent(value: String) = _state.update { it.copy(rentText = value) }
    fun updateEmi(value: String) = _state.update { it.copy(emiText = value) }

    fun next(onFinished: () -> Unit) {
        val current = _state.value
        if (!current.isLastStep) {
            _state.update { it.copy(step = it.step + 1) }
            return
        }
        finish(onFinished)
    }

    /** Saves whatever was entered and marks setup done. */
    fun skipAll(onFinished: () -> Unit) = finish(onFinished)

    private fun finish(onFinished: () -> Unit) {
        val form = _state.value
        viewModelScope.launch {
            profileRepository.saveProfile(
                profileRepository.profile.first().copy(
                    displayName = form.name.trim(),
                    onboardingCompleted = true
                )
            )

            // The stated balance becomes a reconciliation against the computed zero, which
            // is the same path the settings screen uses. No special case, and the
            // adjustment stays visible afterwards.
            Money.parseOrNull(form.balanceText)?.takeIf { it.isPositive }?.let { stated ->
                val computed = withContext(computation) {
                    BalanceCalculator.currentBalance(snapshotRepository.snapshot.first())
                }
                profileRepository.reconcileBalance(
                    computedBalance = computed,
                    statedBalance = stated,
                    reason = "Opening balance set during setup"
                )
            }

            Money.parseOrNull(form.salaryText)?.takeIf { it.isPositive }?.let { salary ->
                incomeRepository.addSource(
                    IncomeSource(
                        id = 0,
                        name = "Salary",
                        type = IncomeType.SALARY,
                        amount = salary,
                        dayOfMonth = form.salaryDay,
                        frequency = Frequency.MONTHLY,
                        startDate = today.today().withDayOfMonth(1),
                        endDate = null,
                        annualIncrementPercent = 0.0,
                        accountId = null,
                        isActive = true,
                        notes = ""
                    )
                )
            }

            Money.parseOrNull(form.rentText)?.takeIf { it.isPositive }?.let { rent ->
                val category = categoryRepository.expenseCategories.first()
                    .firstOrNull { it.name.equals("Rent", ignoreCase = true) }
                billRepository.add(
                    RecurringBill(
                        id = 0,
                        name = "Rent",
                        categoryId = category?.id,
                        amountType = BillAmountType.FIXED,
                        amount = rent,
                        minAmount = null,
                        maxAmount = null,
                        dueDayOfMonth = 5,
                        frequency = Frequency.MONTHLY,
                        startDate = today.today().withDayOfMonth(1),
                        endDate = null,
                        paymentMethod = PaymentMethod.UPI,
                        autoDebit = false,
                        isEssential = true,
                        isActive = true,
                        notes = ""
                    )
                )
            }

            // Recorded as a single combined loan, since asking someone to itemise every
            // EMI before they have seen the app would lose most people at step four. The
            // tenure is a placeholder the user is told to correct on the Plans screen.
            Money.parseOrNull(form.emiText)?.takeIf { it.isPositive }?.let { emiAmount ->
                emiRepository.add(
                    Emi(
                        id = 0,
                        name = "Loan installments",
                        categoryId = null,
                        principal = Money.ZERO,
                        emiAmount = emiAmount,
                        interestRatePercent = null,
                        startDate = today.today().withDayOfMonth(1),
                        firstDueDate = today.today().withDayOfMonth(
                            minOf(5, today.today().lengthOfMonth())
                        ),
                        frequency = Frequency.MONTHLY,
                        totalInstallments = 12,
                        openingPaidInstallments = 0,
                        vehicleId = null,
                        accountReference = "",
                        autoDebit = false,
                        isActive = true,
                        notes = "Added during setup. Open this to set the real tenure and rate."
                    )
                )
            }

            settingsStore.setOnboarded(true)
            onFinished()
        }
    }
}

data class OnboardingState(
    val step: Int = 0,
    val name: String = "",
    val balanceText: String = "",
    val salaryText: String = "",
    val salaryDay: Int = 1,
    val rentText: String = "",
    val emiText: String = ""
) {
    val isLastStep: Boolean get() = step >= OnboardingViewModel.TOTAL_STEPS - 1

    private val salary: Money get() = Money.parseOrNull(salaryText) ?: Money.ZERO
    private val rent: Money get() = Money.parseOrNull(rentText) ?: Money.ZERO
    private val emi: Money get() = Money.parseOrNull(emiText) ?: Money.ZERO

    val previewAvailable: Boolean get() = salary.isPositive && (rent.isPositive || emi.isPositive)

    /** A rough figure shown during setup, before any spending history exists. */
    val roughMonthlyLeft: Money get() = (salary - rent - emi).coerceAtLeastZero()
}
