package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.FinancialSnapshot
import java.time.YearMonth

/**
 * Turns spending history into the averages the forecast and the emergency fund rely on.
 *
 * Only everyday spending is averaged. Expenses recorded against an EMI, a bill or an
 * annual commitment are excluded, because those obligations are already projected from
 * their own schedules; averaging them in as well would count rent twice in every future
 * month. The exclusion is decided by the link on the expense, not by its category, so a
 * one-off doctor visit still counts as everyday spending while the linked insurance
 * premium does not.
 */
object SpendingAnalyzer {

    /** The default history window. Three months smooths out an unusual month. */
    const val DEFAULT_LOOKBACK_MONTHS = 3

    fun expensesIn(snapshot: FinancialSnapshot, month: YearMonth): List<Expense> =
        snapshot.expenses.filter { YearMonth.from(it.date) == month }

    /** Everything spent in a month, whatever it was for and however it was paid. */
    fun totalSpendIn(snapshot: FinancialSnapshot, month: YearMonth): Money =
        expensesIn(snapshot, month).sumOfMoney { it.amount }

    /**
     * Everything spent in a month up to and including [dayOfMonth].
     *
     * Used to compare a month still in progress against the same stretch of an earlier
     * one. Comparing a half-finished month against a whole one would report a saving
     * every time, which is a statement about the calendar rather than about spending.
     */
    fun spendUpToDayIn(
        snapshot: FinancialSnapshot,
        month: YearMonth,
        dayOfMonth: Int
    ): Money {
        val cutoff = com.moneyplanner.core.time.DateUtil.dayInMonth(month, dayOfMonth)
        return expensesIn(snapshot, month)
            .filter { !it.date.isAfter(cutoff) }
            .sumOfMoney { it.amount }
    }

    /**
     * Everyday spending that actually moved cash in a month.
     * This is the figure the cash-flow forecast projects forward.
     */
    fun discretionaryCashSpendIn(snapshot: FinancialSnapshot, month: YearMonth): Money =
        expensesIn(snapshot, month)
            .filter { it.isDiscretionary && it.movesCash }
            .sumOfMoney { it.amount }

    /**
     * The average everyday cash spending per month, over the completed months before
     * [upTo]. The current month is left out because a month that is only a week old
     * would drag the average down and make the forecast look better than it is.
     *
     * Returns zero when there is no completed history yet, which the UI reports honestly
     * rather than inventing a number.
     */
    fun averageMonthlyDiscretionaryCash(
        snapshot: FinancialSnapshot,
        upTo: YearMonth = snapshot.currentMonth,
        lookbackMonths: Int = DEFAULT_LOOKBACK_MONTHS
    ): Money {
        val months = completedMonthsWithData(snapshot, upTo, lookbackMonths)
        if (months.isEmpty()) return Money.ZERO
        val total = months.sumOfMoney { discretionaryCashSpendIn(snapshot, it) }
        return total.divideRounded(months.size)
    }

    /** The same average expressed per day, used to project the rest of the current month. */
    fun averageDailyDiscretionaryCash(
        snapshot: FinancialSnapshot,
        upTo: YearMonth = snapshot.currentMonth,
        lookbackMonths: Int = DEFAULT_LOOKBACK_MONTHS
    ): Money {
        val months = completedMonthsWithData(snapshot, upTo, lookbackMonths)
        if (months.isEmpty()) return Money.ZERO
        val total = months.sumOfMoney { discretionaryCashSpendIn(snapshot, it) }
        val days = months.sumOf { it.lengthOfMonth() }
        if (days == 0) return Money.ZERO
        return total.divideRounded(days)
    }

    /**
     * Average monthly spending in categories marked essential.
     *
     * Used for the emergency fund, which asks what the household must keep paying rather
     * than what it usually spends. Card purchases count here because groceries still have
     * to be bought regardless of how they were paid for.
     */
    fun averageMonthlyEssentialSpend(
        snapshot: FinancialSnapshot,
        lookbackMonths: Int = DEFAULT_LOOKBACK_MONTHS
    ): Money {
        val essentialCategoryIds = snapshot.categories
            .filter { it.isEssential }
            .map { it.id }
            .toSet()
        if (essentialCategoryIds.isEmpty()) return Money.ZERO

        val months = completedMonthsWithData(snapshot, snapshot.currentMonth, lookbackMonths)
        if (months.isEmpty()) return Money.ZERO

        val total = months.sumOfMoney { month ->
            expensesIn(snapshot, month)
                .filter { it.isDiscretionary && it.categoryId in essentialCategoryIds }
                .sumOfMoney { it.amount }
        }
        return total.divideRounded(months.size)
    }

    /** Spending grouped by category for a month, largest first. */
    fun categoryBreakdown(
        snapshot: FinancialSnapshot,
        month: YearMonth
    ): List<CategorySpend> {
        val byId = snapshot.categoriesById
        val total = totalSpendIn(snapshot, month)
        return expensesIn(snapshot, month)
            .groupBy { it.categoryId }
            .map { (categoryId, expenses) ->
                val amount = expenses.sumOfMoney { it.amount }
                CategorySpend(
                    categoryId = categoryId,
                    categoryName = byId[categoryId]?.name ?: "Uncategorised",
                    colorHex = byId[categoryId]?.colorHex ?: "#FF6E7A8A",
                    amount = amount,
                    transactionCount = expenses.size,
                    shareOfTotal = if (total.paise <= 0L) 0f
                    else (amount.paise.toDouble() / total.paise).toFloat()
                )
            }
            .sortedByDescending { it.amount.paise }
    }

    /**
     * The completed months before [upTo] that actually contain expenses.
     *
     * Skipping empty months matters for a new install: a user with two weeks of history
     * gets an average built from the data they have, not one diluted by months in which
     * they were not using the app at all.
     */
    private fun completedMonthsWithData(
        snapshot: FinancialSnapshot,
        upTo: YearMonth,
        lookbackMonths: Int
    ): List<YearMonth> {
        val candidates = (1..lookbackMonths).map { upTo.minusMonths(it.toLong()) }
        val withData = candidates.filter { month ->
            snapshot.expenses.any { YearMonth.from(it.date) == month }
        }
        if (withData.isNotEmpty()) return withData

        // Nothing complete yet. Fall back to the current month so a brand new user still
        // sees a projection based on their own spending rather than a blank.
        val currentHasData = snapshot.expenses.any { YearMonth.from(it.date) == upTo }
        return if (currentHasData) listOf(upTo) else emptyList()
    }
}

data class CategorySpend(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val amount: Money,
    val transactionCount: Int,
    val shareOfTotal: Float
)
