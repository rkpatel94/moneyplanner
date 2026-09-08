package com.moneyplanner.ui.screens.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.repo.BudgetRepository
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.domain.calc.BudgetCalculator
import com.moneyplanner.domain.calc.BudgetStatus
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.CategoryType
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    val state: StateFlow<BudgetsState> = snapshotRepository.snapshot.map { snapshot ->
        BudgetsState(
            statuses = BudgetCalculator.allStatuses(snapshot),
            month = snapshot.currentMonth,
            availableCategories = snapshot.categories
                .filter { it.type == CategoryType.EXPENSE && !it.isArchived },
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetsState()
    )

    fun setBudget(categoryId: Long?, amount: Money) {
        viewModelScope.launch { budgetRepository.setBudget(categoryId, amount) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { budgetRepository.delete(id) }
    }
}

data class BudgetsState(
    val statuses: List<BudgetStatus> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val availableCategories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)
