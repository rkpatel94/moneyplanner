package com.moneyplanner.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.calc.BalanceBreakdown
import com.moneyplanner.domain.calc.EmergencyFundCalculator
import com.moneyplanner.domain.calc.EmergencyFundStatus
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.ForecastItem
import com.moneyplanner.domain.calc.Insight
import com.moneyplanner.domain.calc.InsightsCalculator
import com.moneyplanner.domain.calc.MonthForecast
import com.moneyplanner.domain.calc.SettlementCalculator
import com.moneyplanner.domain.calc.BudgetCalculator
import com.moneyplanner.domain.calc.BudgetState
import com.moneyplanner.domain.calc.BudgetStatus
import com.moneyplanner.domain.calc.RunwayCalculator
import com.moneyplanner.domain.calc.RunwayProjection
import com.moneyplanner.domain.calc.SpendingAnalyzer
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * The dashboard answers the questions a person actually opens the app for: what have I
 * got, what is coming in, what has to go out, and what will be left.
 *
 * All of it is derived from one snapshot so the figures cannot disagree with one another,
 * and the work happens off the main thread — see the flowOn below — because building this
 * runs a three month projection, a day-by-day runway walk, every budget and every insight
 * over the user's whole history.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    val state: StateFlow<DashboardState> = snapshotRepository.snapshot
        .map { snapshot -> buildState(snapshot) }
        .flowOn(computation)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardState.Loading
        )

    private fun buildState(snapshot: FinancialSnapshot): DashboardState {
        val forecast = ForecastCalculator.forecast(snapshot, 3)
        val thisMonth = forecast.first()
        val nextMonth = forecast.getOrNull(1)

        val breakdown = BalanceCalculator.breakdown(snapshot)

        // The day-by-day walk catches a mid-month squeeze a month-end figure hides.
        val runway = RunwayCalculator.project(snapshot, monthsAhead = 3)
        val budgets = BudgetCalculator.allStatuses(snapshot)
        val insights = InsightsCalculator.generate(snapshot)
        val emergency = EmergencyFundCalculator.calculate(snapshot)

        val receivable = SettlementCalculator.totalReceivable(
            snapshot.ledgerEntries, snapshot.settlements
        )
        val payable = SettlementCalculator.totalPayable(
            snapshot.ledgerEntries, snapshot.settlements
        )

        // The next fortnight is what people plan around day to day.
        val horizonEnd = snapshot.today.plusDays(14)
        val upcoming = forecast
            .flatMap { it.items }
            .filter { !it.date.isBefore(snapshot.today) && !it.date.isAfter(horizonEnd) }
            .sortedBy { it.date }
            .take(6)

        val cardDue = snapshot.creditCards
            .filter { it.isActive }
            .sumOfMoney { it.currentOutstanding }

        return DashboardState.Ready(
            today = snapshot.today,
            userName = snapshot.profile.displayName,
            breakdown = breakdown,
            thisMonth = thisMonth,
            nextMonth = nextMonth,
            upcoming = upcoming,
            receivable = receivable,
            payable = payable,
            creditCardOutstanding = cardDue,
            spentThisMonth = SpendingAnalyzer.totalSpendIn(snapshot, snapshot.currentMonth),
            receivedThisMonth = snapshot.incomeTransactions
                .filter { java.time.YearMonth.from(it.date) == snapshot.currentMonth }
                .sumOfMoney { it.amount },
            savedThisMonth = snapshot.contributions
                .filter { java.time.YearMonth.from(it.date) == snapshot.currentMonth }
                .sumOfMoney { it.amount },
            emergencyFund = emergency,
            insights = insights.take(3),
            runway = runway,
            runwayHeadline = runway.headline(snapshot.today),
            budgets = budgets,
            budgetsNeedingAttention = budgets.filter {
                it.state == BudgetState.OVER ||
                    it.state == BudgetState.NEAR_LIMIT ||
                    it.state == BudgetState.PROJECTED_OVER
            },
            recentExpenses = snapshot.expenses.sortedWith(
                compareByDescending<Expense> { it.date }.thenByDescending { it.id }
            ).take(5),
            categoryNames = snapshot.categories.associate { it.id to it.name },
            categoryColors = snapshot.categories.associate { it.id to it.colorHex },
            hasAnyData = snapshot.expenses.isNotEmpty() ||
                snapshot.incomeTransactions.isNotEmpty() ||
                snapshot.incomeSources.isNotEmpty() ||
                snapshot.emis.isNotEmpty() ||
                snapshot.bills.isNotEmpty()
        )
    }
}

sealed interface DashboardState {
    data object Loading : DashboardState

    data class Ready(
        val today: LocalDate,
        val userName: String,
        val breakdown: BalanceBreakdown,
        val thisMonth: MonthForecast,
        val nextMonth: MonthForecast?,
        val upcoming: List<ForecastItem>,
        val receivable: Money,
        val payable: Money,
        val creditCardOutstanding: Money,
        val spentThisMonth: Money,
        val receivedThisMonth: Money,
        val savedThisMonth: Money,
        val emergencyFund: EmergencyFundStatus,
        val insights: List<Insight>,
        val runway: RunwayProjection,
        /** One sentence about when money gets tight, or null when it does not. */
        val runwayHeadline: String?,
        val budgets: List<BudgetStatus>,
        val budgetsNeedingAttention: List<BudgetStatus>,
        val recentExpenses: List<Expense>,
        val categoryNames: Map<Long, String>,
        val categoryColors: Map<Long, String>,
        /** False on a brand new install, which switches the screen to a guided start. */
        val hasAnyData: Boolean
    ) : DashboardState {
        val availableBalance: Money get() = breakdown.available
        val expectedRemaining: Money get() = thisMonth.closingBalance
    }
}
