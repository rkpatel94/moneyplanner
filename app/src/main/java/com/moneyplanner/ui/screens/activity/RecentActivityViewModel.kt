package com.moneyplanner.ui.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.data.repo.AnnualExpenseRepository
import com.moneyplanner.data.repo.BillRepository
import com.moneyplanner.data.repo.EmiRepository
import com.moneyplanner.data.repo.ExpenseRepository
import com.moneyplanner.data.repo.IncomeRepository
import com.moneyplanner.data.repo.PeopleRepository
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.SavingsRepository
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.di.DefaultDispatcher
import com.moneyplanner.domain.calc.ActivityItem
import com.moneyplanner.domain.calc.ActivityKind
import com.moneyplanner.domain.calc.RecentActivityCalculator
import com.moneyplanner.core.time.parsePeriodKey
import com.moneyplanner.domain.model.ExpenseLinkType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The last ten money movements, and the ability to put one right.
 *
 * Deleting is the part that carries risk. An expense created by marking a bill paid is one
 * half of a pair, and removing it on its own would leave the payment record behind: the
 * forecast would still believe the bill was settled while the money returned to the
 * balance, which is the double count the whole app is built to avoid. Every delete here
 * therefore routes to whichever repository owns the *event*, not to whichever table
 * happens to hold the row.
 */
@HiltViewModel
class RecentActivityViewModel @Inject constructor(
    snapshotRepository: SnapshotRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val peopleRepository: PeopleRepository,
    private val profileRepository: ProfileRepository,
    private val savingsRepository: SavingsRepository,
    private val billRepository: BillRepository,
    private val emiRepository: EmiRepository,
    private val annualExpenseRepository: AnnualExpenseRepository,
    @DefaultDispatcher computation: CoroutineDispatcher
) : ViewModel() {

    val state: StateFlow<RecentActivityState> = snapshotRepository.snapshot
        .map { snapshot ->
            RecentActivityState(
                items = RecentActivityCalculator.recent(snapshot),
                today = snapshot.today,
                isLoading = false
            )
        }
        .flowOn(computation)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecentActivityState()
        )

    private val _pendingDelete = MutableStateFlow<ActivityItem?>(null)
    val pendingDelete: StateFlow<ActivityItem?> = _pendingDelete.asStateFlow()

    /** Deletion is always confirmed: none of these records can be recovered afterwards. */
    fun askToDelete(item: ActivityItem) {
        _pendingDelete.value = item
    }

    fun dismissDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val item = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            when (item.kind) {
                ActivityKind.EXPENSE -> deleteExpense(item)
                ActivityKind.INCOME -> incomeRepository.deleteTransaction(item.recordId)
                ActivityKind.SETTLEMENT -> peopleRepository.deleteSettlement(item.recordId)
                ActivityKind.TRANSFER -> profileRepository.deleteTransfer(item.recordId)
                ActivityKind.SAVING -> savingsRepository.deleteContribution(item.recordId)
            }
        }
    }

    /**
     * Removes an expense, together with whatever it was half of.
     *
     * A linked expense is undone through its obligation so the payment record goes with
     * it and the occurrence returns to the forecast. Only a genuinely standalone expense
     * is deleted directly.
     */
    private suspend fun deleteExpense(item: ActivityItem) {
        val linkId = item.linkId
        val periodKey = item.linkPeriodKey

        when (item.linkType) {
            ExpenseLinkType.RECURRING_BILL -> {
                val month = periodKey?.let { parsePeriodKey(it) }
                if (linkId != null && month != null) {
                    billRepository.undoPayment(linkId, month)
                } else {
                    expenseRepository.delete(item.recordId)
                }
            }

            ExpenseLinkType.EMI -> {
                val installment = periodKey?.toIntOrNull()
                val emi = linkId?.let { emiRepository.getById(it) }
                if (emi != null && installment != null) {
                    emiRepository.undoInstallment(emi, installment)
                } else {
                    expenseRepository.delete(item.recordId)
                }
            }

            ExpenseLinkType.ANNUAL_EXPENSE -> {
                val year = periodKey?.toIntOrNull()
                if (linkId != null && year != null) {
                    annualExpenseRepository.undoPayment(linkId, year)
                } else {
                    expenseRepository.delete(item.recordId)
                }
            }

            // The out-of-pocket payment for a shared bill. Deleting it alone would leave
            // the split and everybody's share behind, so the shared expense itself goes,
            // which takes the balances it created with it.
            ExpenseLinkType.SHARED_EXPENSE ->
                if (linkId != null) {
                    peopleRepository.deleteSharedExpense(linkId)
                } else {
                    expenseRepository.delete(item.recordId)
                }

            else -> expenseRepository.delete(item.recordId)
        }
    }
}

data class RecentActivityState(
    val items: List<ActivityItem> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
