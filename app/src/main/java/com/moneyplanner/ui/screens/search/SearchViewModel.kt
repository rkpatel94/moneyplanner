package com.moneyplanner.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.domain.calc.PersonBalanceSummary
import com.moneyplanner.domain.calc.SettlementCalculator
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.RecurringBill
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Searches every kind of record at once.
 *
 * Matching is done in memory against the snapshot rather than with a query per table,
 * because the whole point is a single result list. Typing is debounced so the projection
 * is not rebuilt on every keystroke.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<SearchState> = combine(
        snapshotRepository.snapshot,
        _query.debounce(180)
    ) { snapshot, rawQuery ->
        val needle = rawQuery.trim().lowercase()

        // Category names make good starting points because they are what people
        // actually search for most often.
        val suggestions = snapshot.categories
            .filter { !it.isArchived }
            .take(8)
            .map { it.name }

        if (needle.isBlank()) {
            return@combine SearchState(suggestions = suggestions, isLoading = false)
        }

        val categoriesById = snapshot.categoriesById
        val peopleById = snapshot.peopleById

        val matchingCategoryIds = snapshot.categories
            .filter { it.name.lowercase().contains(needle) }
            .map { it.id }
            .toSet()

        val matchingPeople = snapshot.people.filter { it.name.lowercase().contains(needle) }
        val matchingPersonIds = matchingPeople.map { it.id }.toSet()

        val expenses = snapshot.expenses
            .filter { expense ->
                expense.description.lowercase().contains(needle) ||
                    expense.notes.lowercase().contains(needle) ||
                    expense.categoryId in matchingCategoryIds ||
                    expense.personId in matchingPersonIds
            }
            .sortedByDescending { it.date }
            .take(60)
            .map { expense ->
                ExpenseResult(
                    expense = expense,
                    categoryName = categoriesById[expense.categoryId]?.name ?: "Uncategorised",
                    personName = expense.personId?.let { peopleById[it]?.name }
                )
            }

        val balances = SettlementCalculator.summaries(
            matchingPeople, snapshot.ledgerEntries, snapshot.settlements
        )

        SearchState(
            expenses = expenses,
            expenseTotal = expenses.sumOfMoney { it.expense.amount },
            people = balances,
            emis = snapshot.emis.filter { it.name.lowercase().contains(needle) },
            bills = snapshot.bills.filter { it.name.lowercase().contains(needle) },
            suggestions = suggestions,
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchState()
    )

    fun updateQuery(value: String) {
        _query.value = value
    }
}

data class ExpenseResult(
    val expense: Expense,
    val categoryName: String,
    val personName: String?
)

data class SearchState(
    val expenses: List<ExpenseResult> = emptyList(),
    val expenseTotal: Money = Money.ZERO,
    val people: List<PersonBalanceSummary> = emptyList(),
    val emis: List<Emi> = emptyList(),
    val bills: List<RecurringBill> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean
        get() = expenses.isEmpty() && people.isEmpty() && emis.isEmpty() && bills.isEmpty()
}
