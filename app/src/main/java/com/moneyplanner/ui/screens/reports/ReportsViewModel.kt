package com.moneyplanner.ui.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.domain.calc.CategorySpend
import com.moneyplanner.domain.calc.EmiCalculator
import com.moneyplanner.domain.calc.GoalProgress
import com.moneyplanner.domain.calc.PersonBalanceSummary
import com.moneyplanner.domain.calc.RecurrenceCalculator
import com.moneyplanner.domain.calc.SavingsCalculator
import com.moneyplanner.domain.calc.SettlementCalculator
import com.moneyplanner.domain.calc.SpendingAnalyzer
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val state: StateFlow<ReportsState> = combine(
        snapshotRepository.snapshot,
        selectedMonth
    ) { snapshot, month ->
        val income = snapshot.incomeTransactions
            .filter { YearMonth.from(it.date) == month }
            .sumOfMoney { it.amount }
        val spent = SpendingAnalyzer.totalSpendIn(snapshot, month)

        val emiBurden = snapshot.emis
            .filter { it.isActive }
            .filterNot { EmiCalculator.isCompleted(it, snapshot.emiPayments) }
            .sumOfMoney { emi ->
                if (emi.frequency.isWeekly) emi.emiAmount * 4
                else emi.emiAmount.divideRounded(emi.frequency.monthsPerPeriod)
            }

        val billsBurden = snapshot.bills
            .filter { it.isActive }
            .sumOfMoney { bill ->
                val occurrences = RecurrenceCalculator.occurrencesIn(
                    month, bill.startDate, bill.endDate, bill.dueDayOfMonth, bill.frequency
                ).size
                bill.amount * occurrences
            }

        val annualReserve = snapshot.annualExpenses
            .filter { it.isActive }
            .sumOfMoney { it.monthlyReserve }

        val monthlyIncome = snapshot.incomeSources
            .filter { it.isActive && it.frequency == Frequency.MONTHLY }
            .sumOfMoney { it.amount }

        // The six months before the selected one, for the trend block.
        val trendMonths = (5 downTo 0).map { month.minusMonths(it.toLong()) }
        val trend = trendMonths
            .map { it to SpendingAnalyzer.totalSpendIn(snapshot, it) }
            .filter { it.second.isPositive }

        ReportsState(
            month = month,
            income = income,
            spent = spent,
            net = income - spent,
            categoryBreakdown = SpendingAnalyzer.categoryBreakdown(snapshot, month),
            emiBurden = emiBurden,
            billsBurden = billsBurden,
            annualReserve = annualReserve,
            totalCommitted = emiBurden + billsBurden + annualReserve,
            monthlyIncome = monthlyIncome,
            peopleBalances = SettlementCalculator
                .summaries(snapshot.people, snapshot.ledgerEntries, snapshot.settlements)
                .filterNot { it.isSettled },
            goalProgress = SavingsCalculator.allProgress(snapshot),
            monthlyTrend = trend,
            hasData = snapshot.expenses.isNotEmpty() || snapshot.incomeTransactions.isNotEmpty(),
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportsState()
    )

    fun previousMonth() = selectedMonth.update { it.minusMonths(1) }
    fun nextMonth() = selectedMonth.update { it.plusMonths(1) }
}

data class ReportsState(
    val month: YearMonth = YearMonth.now(),
    val income: Money = Money.ZERO,
    val spent: Money = Money.ZERO,
    val net: Money = Money.ZERO,
    val categoryBreakdown: List<CategorySpend> = emptyList(),
    val emiBurden: Money = Money.ZERO,
    val billsBurden: Money = Money.ZERO,
    val annualReserve: Money = Money.ZERO,
    val totalCommitted: Money = Money.ZERO,
    val monthlyIncome: Money = Money.ZERO,
    val peopleBalances: List<PersonBalanceSummary> = emptyList(),
    val goalProgress: List<GoalProgress> = emptyList(),
    val monthlyTrend: List<Pair<YearMonth, Money>> = emptyList(),
    val hasData: Boolean = false,
    val isLoading: Boolean = true
)
