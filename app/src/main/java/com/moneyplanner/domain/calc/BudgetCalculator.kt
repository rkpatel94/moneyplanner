package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.Budget
import com.moneyplanner.domain.model.FinancialSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Measures spending against the limits the user has set.
 *
 * Budgets answer a different question from the forecast, so they count differently and
 * the difference is deliberate:
 *
 *  - **The forecast tracks cash.** A credit card purchase is not counted until the card
 *    bill is paid, because that is when money actually leaves the account.
 *  - **A budget tracks spending.** A card purchase counts the day it is made, and a rent
 *    payment counts against rent even though it came from a scheduled bill. A limit on
 *    groceries means the groceries, however they were paid for.
 *
 * The projection is the useful part. Knowing you have spent 60% of the food budget is
 * only half the story; knowing that at this pace you will finish the month 20% over is
 * what actually changes behaviour, which is why [BudgetStatus.projectedSpend] exists.
 */
object BudgetCalculator {

    /** Past this share of the limit a budget is treated as close to the edge. */
    const val NEAR_LIMIT_FRACTION = 0.8f

    /**
     * How far back rollover will look.
     *
     * Each month's carry depends on the one before it, so the chain has to stop somewhere.
     * A year is well past the point where an unspent month is still meaningful, and it
     * bounds the work regardless of how long the budget has existed.
     */
    const val MAX_ROLLOVER_MONTHS = 12

    fun statusFor(
        budget: Budget,
        snapshot: FinancialSnapshot,
        month: YearMonth = snapshot.currentMonth
    ): BudgetStatus {
        val spent = spentIn(budget, snapshot, month)
        val carried = carriedInto(budget, snapshot, month)
        val limit = budget.amount + carried

        val remaining = (limit - spent).coerceAtLeastZero()
        val overspend = (spent - limit).coerceAtLeastZero()

        val fraction = if (limit.paise <= 0L) 0f
        else (spent.paise.toDouble() / limit.paise).toFloat()

        val projected = projectMonthEnd(spent, snapshot.today, month)
        val relevant = expensesFor(budget, snapshot, month)

        return BudgetStatus(
            budget = budget,
            categoryName = budget.categoryId
                ?.let { id -> snapshot.categoriesById[id]?.name }
                ?: "Everything",
            month = month,
            spent = spent,
            remaining = remaining,
            overspendAmount = overspend,
            usedFraction = fraction.coerceAtLeast(0f),
            projectedSpend = projected,
            transactionCount = relevant.size,
            dailyAllowanceLeft = dailyAllowance(remaining, snapshot.today, month),
            carriedOver = carried,
            effectiveLimit = limit,
            state = when {
                spent > limit -> BudgetState.OVER
                fraction >= budget.alertFraction -> BudgetState.NEAR_LIMIT
                projected > limit -> BudgetState.PROJECTED_OVER
                else -> BudgetState.ON_TRACK
            }
        )
    }

    private fun expensesFor(budget: Budget, snapshot: FinancialSnapshot, month: YearMonth) =
        SpendingAnalyzer.expensesIn(snapshot, month).let { expenses ->
            if (budget.categoryId == null) expenses
            else expenses.filter { it.categoryId == budget.categoryId }
        }

    private fun spentIn(budget: Budget, snapshot: FinancialSnapshot, month: YearMonth): Money =
        expensesFor(budget, snapshot, month).sumOfMoney { it.amount }

    /**
     * What an unspent run of earlier months adds to this month's limit.
     *
     * Walks forward from the oldest month still in range so each month's carry can build
     * on the one before it, which is what makes two quiet months worth more than one. A
     * month that went over contributes nothing rather than a negative, so overspending
     * costs the allowance it used and no more.
     *
     * Nothing before the budget was created counts: a budget set up today has no history
     * of restraint to be rewarded for.
     */
    fun carriedInto(budget: Budget, snapshot: FinancialSnapshot, month: YearMonth): Money {
        if (!budget.rolloverEnabled) return Money.ZERO

        val createdMonth = budget.createdAt?.let { YearMonth.from(it) }
        val earliest = month.minusMonths(MAX_ROLLOVER_MONTHS.toLong()).let { limit ->
            if (createdMonth != null && createdMonth.isAfter(limit)) createdMonth else limit
        }
        if (!earliest.isBefore(month)) return Money.ZERO

        var carried = Money.ZERO
        var cursor = earliest
        while (cursor.isBefore(month)) {
            val limit = budget.amount + carried
            val spent = spentIn(budget, snapshot, cursor)
            carried = (limit - spent).coerceAtLeastZero()
            cursor = cursor.plusMonths(1)
        }
        return carried
    }

    fun allStatuses(
        snapshot: FinancialSnapshot,
        month: YearMonth = snapshot.currentMonth
    ): List<BudgetStatus> = snapshot.budgets
        .filter { it.isActive }
        .map { statusFor(it, snapshot, month) }
        // The overall budget leads, then whichever category is under most pressure.
        .sortedWith(
            compareByDescending<BudgetStatus> { it.budget.categoryId == null }
                .thenByDescending { it.usedFraction }
        )

    /**
     * Where this month's spending lands if the current pace holds.
     *
     * Only meaningful for a month in progress: a finished month is already its own
     * answer, and a future month has no pace to extrapolate from.
     */
    private fun projectMonthEnd(spent: Money, today: LocalDate, month: YearMonth): Money {
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        if (today.isAfter(monthEnd)) return spent
        if (today.isBefore(monthStart)) return Money.ZERO

        val daysElapsed = ChronoUnit.DAYS.between(monthStart, today).toInt() + 1
        if (daysElapsed <= 0) return spent

        val totalDays = month.lengthOfMonth()
        val perDay = spent.paise.toDouble() / daysElapsed
        return Money(Math.round(perDay * totalDays))
    }

    /** What is left to spend per remaining day without breaching the limit. */
    private fun dailyAllowance(remaining: Money, today: LocalDate, month: YearMonth): Money {
        val monthEnd = month.atEndOfMonth()
        if (today.isAfter(monthEnd)) return Money.ZERO
        val daysLeft = ChronoUnit.DAYS.between(today, monthEnd).toInt() + 1
        if (daysLeft <= 0) return Money.ZERO
        return remaining.divideRounded(daysLeft)
    }
}

enum class BudgetState {
    ON_TRACK,
    /** On pace to exceed the limit, even though it has not been breached yet. */
    PROJECTED_OVER,
    NEAR_LIMIT,
    OVER
}

data class BudgetStatus(
    val budget: Budget,
    val categoryName: String,
    val month: YearMonth,
    val spent: Money,
    val remaining: Money,
    val overspendAmount: Money,
    val usedFraction: Float,
    val projectedSpend: Money,
    val transactionCount: Int,
    val dailyAllowanceLeft: Money,
    /** Unspent allowance brought forward from earlier months. Zero without rollover. */
    val carriedOver: Money = Money.ZERO,
    /** The limit actually in force this month: the budget plus anything carried in. */
    val effectiveLimit: Money = Money.ZERO,
    val state: BudgetState
) {
    val hasCarryOver: Boolean get() = carriedOver.isPositive
    val usedPercent: Int get() = (usedFraction * 100).toInt()
    val isOver: Boolean get() = state == BudgetState.OVER

    /**
     * A plain sentence describing where this budget stands.
     *
     * A carried-over allowance is named rather than folded silently into the remaining
     * figure, because a limit that is suddenly larger than the one the user set is
     * confusing until you know why.
     */
    fun summary(): String = when (state) {
        BudgetState.OVER ->
            "Over by ${com.moneyplanner.core.money.IndianFormat.format(overspendAmount)}"
        BudgetState.NEAR_LIMIT ->
            "${com.moneyplanner.core.money.IndianFormat.format(remaining)} left"
        BudgetState.PROJECTED_OVER ->
            "On pace for ${com.moneyplanner.core.money.IndianFormat.format(projectedSpend)}"
        BudgetState.ON_TRACK ->
            "${com.moneyplanner.core.money.IndianFormat.format(remaining)} left"
    }
}
