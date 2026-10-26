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
import com.moneyplanner.domain.calc.ElapsedComparison
import com.moneyplanner.domain.calc.ReportsCalculator
import com.moneyplanner.domain.calc.elapsedComparison
import com.moneyplanner.domain.calc.MonthSummary
import com.moneyplanner.domain.calc.CategoryComparison
import com.moneyplanner.domain.calc.AccountFlow
import com.moneyplanner.domain.calc.SettlementCalculator
import com.moneyplanner.domain.calc.SpendingAnalyzer
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import com.moneyplanner.ui.nav.TabFocus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    snapshotRepository: SnapshotRepository,
    tabFocus: TabFocus,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    /**
     * Null until the user pages, so the month shown follows the snapshot's date, as the
     * calendar's does. Reading the system clock here fixed Reports to the month it was
     * opened in, so a screen left open past midnight on the 31st kept reporting the month
     * that had just ended as the one in progress.
     */
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)

    init {
        // A link names the month it is about, or null for the month in progress. The bar
        // alone returns to whichever month was last paged to here.
        viewModelScope.launch {
            tabFocus.reports.requests.collect { selectedMonth.value = it }
        }
    }

    val state: StateFlow<ReportsState> = combine(
        snapshotRepository.snapshot,
        selectedMonth
    ) { snapshot, chosen ->
        val month = chosen ?: snapshot.currentMonth
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

        // The six months up to and including the selected one, for the trend block.
        val trendMonths = (5 downTo 0).map { month.minusMonths(it.toLong()) }
        val trend = trendMonths
            .map { it to SpendingAnalyzer.totalSpendIn(snapshot, it) }
            .filter { it.second.isPositive }

        // Income against spending per month, each category against its own recent
        // average, and what moved through each account over the same six months.
        val monthsBack = ReportsCalculator.DEFAULT_TREND_MONTHS
        val summaries = ReportsCalculator.monthlyTrend(snapshot, monthsBack)
            .map { row -> if (row.month == month) row.copy(isPartial = row.isPartial) else row }
        val comparison = ReportsCalculator.categoryComparison(snapshot, month)
            .filter { it.thisMonth.isPositive || it.hasBaseline }
        val flows = ReportsCalculator.accountCashFlow(
            snapshot,
            from = month.minusMonths((monthsBack - 1).toLong()),
            to = month
        ).filter { it.hasActivity }

        ReportsState(
            month = month,
            today = snapshot.today,
            elapsed = elapsedComparison(snapshot, month),
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
            monthSummaries = summaries,
            categoryComparison = comparison,
            accountFlows = flows,
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

    fun previousMonth() = selectedMonth.update { (it ?: state.value.month).minusMonths(1) }
    fun nextMonth() = selectedMonth.update { (it ?: state.value.month).plusMonths(1) }
}

data class ReportsState(
    val month: YearMonth = YearMonth.now(),
    /** From the snapshot, so "this month" stays right when the app is left open past midnight. */
    val today: LocalDate = LocalDate.now(),
    /**
     * How far through the month, and how the same stretch of the previous one went. Null
     * only before the first snapshot arrives.
     */
    val elapsed: ElapsedComparison? = null,
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
    /** Income against spending, month by month. */
    val monthSummaries: List<MonthSummary> = emptyList(),
    /** Each category this month against its own recent average. */
    val categoryComparison: List<CategoryComparison> = emptyList(),
    /** What moved through each account over the trend window. */
    val accountFlows: List<AccountFlow> = emptyList(),
    val hasData: Boolean = false,
    val isLoading: Boolean = true
)
