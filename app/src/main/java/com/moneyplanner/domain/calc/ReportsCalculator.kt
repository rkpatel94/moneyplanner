package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.SettlementDirection
import java.time.YearMonth

/**
 * The three questions a month's figures cannot answer on their own.
 *
 * A single month tells you what happened. It does not tell you whether that is normal,
 * which is the question people actually have. These compare a month against its
 * neighbours, a category against its own past, and an account against the others.
 *
 * Everything here is a pure function of the snapshot and reports only what the records
 * say. Where there is not enough history to make a comparison honest, the comparison is
 * marked as such rather than being drawn against a zero that would read as a collapse.
 */
object ReportsCalculator {

    const val DEFAULT_TREND_MONTHS = 6

    // ---- Trend by month ------------------------------------------------------------

    /**
     * Income, spending and the gap between them, month by month, oldest first.
     *
     * The current month is included and flagged, because a month still running is not
     * comparable with the finished ones beside it: on the 3rd it will always look like a
     * collapse in spending, which is a fact about the calendar rather than the household.
     */
    fun monthlyTrend(
        snapshot: FinancialSnapshot,
        months: Int = DEFAULT_TREND_MONTHS
    ): List<MonthSummary> {
        val current = snapshot.currentMonth
        return (months.coerceAtLeast(1) - 1 downTo 0).map { offset ->
            val month = current.minusMonths(offset.toLong())
            val income = snapshot.incomeTransactions
                .filter { YearMonth.from(it.date) == month }
                .sumOfMoney { it.amount }
            val spent = SpendingAnalyzer.totalSpendIn(snapshot, month)
            MonthSummary(
                month = month,
                income = income,
                spent = spent,
                isPartial = month == current
            )
        }
    }

    // ---- Category comparison -------------------------------------------------------

    /**
     * Each category this month against its own average over the months before it.
     *
     * Compared against itself rather than against other categories or any outside
     * benchmark, because what counts as a lot of grocery spending is entirely personal.
     *
     * Only completed months form the baseline. Including the current one would compare a
     * month against an average it is itself dragging down, which flatters every figure.
     */
    fun categoryComparison(
        snapshot: FinancialSnapshot,
        month: YearMonth = snapshot.currentMonth,
        lookbackMonths: Int = 3
    ): List<CategoryComparison> {
        val baselineMonths = (1..lookbackMonths.coerceAtLeast(1))
            .map { month.minusMonths(it.toLong()) }
            .filter { past -> snapshot.expenses.any { YearMonth.from(it.date) == past } }

        val current = SpendingAnalyzer.categoryBreakdown(snapshot, month)
        val names = snapshot.categoriesById

        // Categories that were spent on before but not this month still matter: dropping
        // them would hide the fact that something stopped, which is as interesting as
        // something starting.
        val earlierIds = baselineMonths.flatMap { past ->
            snapshot.expenses.filter { YearMonth.from(it.date) == past }.map { it.categoryId }
        }.toSet()
        val allIds = (current.map { it.categoryId } + earlierIds).distinct()

        return allIds.map { categoryId ->
            val now = current.firstOrNull { it.categoryId == categoryId }?.amount ?: Money.ZERO
            val baseline = if (baselineMonths.isEmpty()) {
                Money.ZERO
            } else {
                baselineMonths.sumOfMoney { past ->
                    snapshot.expenses
                        .filter { YearMonth.from(it.date) == past && it.categoryId == categoryId }
                        .sumOfMoney { it.amount }
                }.divideRounded(baselineMonths.size)
            }

            CategoryComparison(
                categoryId = categoryId,
                categoryName = names[categoryId]?.name ?: "Uncategorised",
                colorHex = names[categoryId]?.colorHex ?: "#FF6E7A8A",
                thisMonth = now,
                baseline = baseline,
                hasBaseline = baselineMonths.isNotEmpty() && baseline.isPositive
            )
        }.sortedByDescending { it.thisMonth.paise }
    }

    // ---- Account cash flow ---------------------------------------------------------

    /**
     * What flowed through each account over a range of months.
     *
     * Transfers are counted, because from one account's point of view money moving to
     * another really did leave it. They cancel across the accounts rather than within
     * one, which is why the totals here can exceed what the household earned or spent.
     */
    fun accountCashFlow(
        snapshot: FinancialSnapshot,
        from: YearMonth,
        to: YearMonth
    ): List<AccountFlow> {
        fun inRange(date: java.time.LocalDate): Boolean {
            val month = YearMonth.from(date)
            return !month.isBefore(from) && !month.isAfter(to)
        }

        return snapshot.accounts.map { account ->
            val id = account.id

            val income = snapshot.incomeTransactions
                .filter { it.accountId == id && inRange(it.date) }
                .sumOfMoney { it.amount }
            val received = snapshot.settlements
                .filter {
                    it.accountId == id && inRange(it.date) &&
                        it.direction == SettlementDirection.RECEIVED_FROM_THEM
                }
                .sumOfMoney { it.amount }
            val transferredIn = snapshot.transfers
                .filter { it.toAccountId == id && inRange(it.date) }
                .sumOfMoney { it.amount }

            val spent = snapshot.expenses
                .filter { it.accountId == id && inRange(it.date) && it.movesCash }
                .sumOfMoney { it.amount }
            val cardBills = snapshot.creditCardPayments
                .filter { it.accountId == id && inRange(it.paidDate) }
                .sumOfMoney { it.amount }
            val paidOut = snapshot.settlements
                .filter {
                    it.accountId == id && inRange(it.date) &&
                        it.direction == SettlementDirection.PAID_TO_THEM
                }
                .sumOfMoney { it.amount }
            val transferredOut = snapshot.transfers
                .filter { it.fromAccountId == id && inRange(it.date) }
                .sumOfMoney { it.amount }

            AccountFlow(
                account = account,
                moneyIn = income + received + transferredIn,
                moneyOut = spent + cardBills + paidOut + transferredOut,
                closingBalance = BalanceCalculator.accountBalancesAsOf(
                    snapshot,
                    minOf(to.atEndOfMonth(), snapshot.today)
                ).rows.firstOrNull { it.account.id == id }?.balance ?: Money.ZERO
            )
        }.sortedBy { it.account.sortOrder }
    }
}

data class MonthSummary(
    val month: YearMonth,
    val income: Money,
    val spent: Money,
    /** True for a month still running, whose figures are not yet comparable. */
    val isPartial: Boolean
) {
    val net: Money get() = income - spent
    val isNegative: Boolean get() = net.isNegative
}

data class CategoryComparison(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val thisMonth: Money,
    /** The average of the completed months before this one. */
    val baseline: Money,
    /** False when there is not enough history for the comparison to mean anything. */
    val hasBaseline: Boolean
) {
    val difference: Money get() = thisMonth - baseline

    /** How far above or below the usual level, as a fraction. Null without a baseline. */
    val changeFraction: Float?
        get() = if (!hasBaseline) null
        else ((thisMonth.paise - baseline.paise).toDouble() / baseline.paise).toFloat()

    val changePercent: Int? get() = changeFraction?.let { (it * 100).toInt() }
    val isUp: Boolean get() = difference.isPositive
    val isDown: Boolean get() = difference.isNegative
}

data class AccountFlow(
    val account: Account,
    val moneyIn: Money,
    val moneyOut: Money,
    val closingBalance: Money
) {
    val net: Money get() = moneyIn - moneyOut
    val hasActivity: Boolean get() = moneyIn.isPositive || moneyOut.isPositive
}
