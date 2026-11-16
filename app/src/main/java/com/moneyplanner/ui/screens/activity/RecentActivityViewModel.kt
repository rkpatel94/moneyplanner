package com.moneyplanner.ui.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.data.repo.IncomeRepository
import com.moneyplanner.data.repo.PeopleRepository
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.RecordRemover
import com.moneyplanner.data.repo.SavingsRepository
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.di.DefaultDispatcher
import com.moneyplanner.domain.calc.ActivityItem
import com.moneyplanner.domain.calc.ActivityKind
import com.moneyplanner.domain.calc.RecentActivityCalculator
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
    private val incomeRepository: IncomeRepository,
    private val peopleRepository: PeopleRepository,
    private val profileRepository: ProfileRepository,
    private val savingsRepository: SavingsRepository,
    private val recordRemover: RecordRemover,
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
     * Removes an expense, together with whatever it was half of. The rule lives in
     * [RecordRemover], shared with the expense editor so the two can never differ.
     */
    private suspend fun deleteExpense(item: ActivityItem) =
        recordRemover.deleteExpense(
            expenseId = item.recordId,
            linkType = item.linkType,
            linkId = item.linkId,
            periodKey = item.linkPeriodKey
        )
}

data class RecentActivityState(
    val items: List<ActivityItem> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
