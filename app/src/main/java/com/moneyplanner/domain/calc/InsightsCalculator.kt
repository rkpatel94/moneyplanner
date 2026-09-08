package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.FinancialSnapshot
import java.time.YearMonth

/**
 * Notices things worth mentioning.
 *
 * The app holds months of a person's financial history and, until now, never once remarked
 * on it. These are the observations a careful friend looking at the same records would
 * make: a category well above its usual level, a loan about to finish, a card creeping
 * towards its limit.
 *
 * Every insight is derived arithmetic with a stated threshold, not a judgement. Nothing
 * here tells the user what to do with their money; it points at something and gives the
 * figures. Insights are also suppressed unless there is enough history to justify them,
 * because a confident claim from two weeks of data is noise dressed as intelligence.
 */
object InsightsCalculator {

    /** How far above the recent average a category has to sit before it is worth saying. */
    private const val SPIKE_THRESHOLD = 1.4f

    /** Card utilisation past this point is worth flagging. */
    private const val HIGH_UTILISATION = 0.7f

    /** Loans within this many installments of the end. */
    private const val NEARLY_DONE_INSTALLMENTS = 3

    fun generate(snapshot: FinancialSnapshot): List<Insight> {
        val insights = mutableListOf<Insight>()

        insights += categorySpikes(snapshot)
        insights += loansNearlyFinished(snapshot)
        insights += highCardUtilisation(snapshot)
        insights += savingsRate(snapshot)
        insights += quietWin(snapshot)

        return insights.sortedByDescending { it.priority }
    }

    /**
     * Categories running well above their own recent average.
     *
     * Compared against the same category's previous months rather than any external
     * benchmark, since what counts as a lot of grocery spending is entirely personal.
     */
    private fun categorySpikes(snapshot: FinancialSnapshot): List<Insight> {
        val month = snapshot.currentMonth
        val previous = (1..3).map { month.minusMonths(it.toLong()) }
            .filter { past -> snapshot.expenses.any { YearMonth.from(it.date) == past } }

        if (previous.isEmpty()) return emptyList()

        val current = SpendingAnalyzer.categoryBreakdown(snapshot, month)

        return current.mapNotNull { row ->
            val history = previous.map { past ->
                snapshot.expenses
                    .filter { YearMonth.from(it.date) == past && it.categoryId == row.categoryId }
                    .sumOfMoney { it.amount }
            }
            val average = history.sumOfMoney { it }.divideRounded(history.size)
            if (!average.isPositive) return@mapNotNull null

            val ratio = row.amount.paise.toFloat() / average.paise
            if (ratio < SPIKE_THRESHOLD) return@mapNotNull null

            val percentAbove = ((ratio - 1f) * 100).toInt()
            Insight(
                kind = InsightKind.SPENDING_SPIKE,
                title = "${row.categoryName} is up $percentAbove%",
                detail = "${IndianFormat.format(row.amount)} this month against a recent " +
                    "average of ${IndianFormat.format(average)}.",
                priority = 70 + percentAbove.coerceAtMost(25)
            )
        }
    }

    private fun loansNearlyFinished(snapshot: FinancialSnapshot): List<Insight> =
        snapshot.emis
            .filter { it.isActive }
            .mapNotNull { emi ->
                val left = EmiCalculator.remainingInstallments(emi, snapshot.emiPayments)
                if (left == 0 || left > NEARLY_DONE_INSTALLMENTS) return@mapNotNull null

                Insight(
                    kind = InsightKind.LOAN_ENDING,
                    title = "${emi.name} is nearly paid off",
                    detail = "$left " + (if (left == 1) "installment" else "installments") +
                        " left. After that, ${IndianFormat.format(emi.emiAmount)} a month " +
                        "frees up.",
                    priority = 85
                )
            }

    private fun highCardUtilisation(snapshot: FinancialSnapshot): List<Insight> =
        snapshot.creditCards
            .filter { it.isActive && it.creditLimit.isPositive }
            .mapNotNull { card ->
                if (card.utilisation < HIGH_UTILISATION) return@mapNotNull null
                Insight(
                    kind = InsightKind.CARD_UTILISATION,
                    title = "${card.name} is ${(card.utilisation * 100).toInt()}% used",
                    detail = "${IndianFormat.format(card.currentOutstanding)} of " +
                        "${IndianFormat.format(card.creditLimit)}.",
                    priority = 75
                )
            }

    /**
     * How much of the month's income survived. Only shown once a month is far enough
     * along that the answer means something.
     */
    private fun savingsRate(snapshot: FinancialSnapshot): List<Insight> {
        val month = snapshot.currentMonth
        if (snapshot.today.dayOfMonth < 20) return emptyList()

        val received = snapshot.incomeTransactions
            .filter { YearMonth.from(it.date) == month }
            .sumOfMoney { it.amount }
        if (!received.isPositive) return emptyList()

        val spent = SpendingAnalyzer.totalSpendIn(snapshot, month)
        val kept = received - spent
        if (!kept.isPositive) {
            return listOf(
                Insight(
                    kind = InsightKind.SAVINGS_RATE,
                    title = "You have spent more than you received",
                    detail = "${IndianFormat.format(spent)} spent against " +
                        "${IndianFormat.format(received)} received this month.",
                    priority = 90
                )
            )
        }

        val percent = (kept.paise.toDouble() / received.paise * 100).toInt()
        return listOf(
            Insight(
                kind = InsightKind.SAVINGS_RATE,
                title = "You have kept $percent% of what came in",
                detail = "${IndianFormat.format(kept)} of " +
                    "${IndianFormat.format(received)} so far this month.",
                priority = 40
            )
        )
    }

    /**
     * Something going right. An app that only ever reports problems stops being read.
     */
    private fun quietWin(snapshot: FinancialSnapshot): List<Insight> {
        val month = snapshot.currentMonth
        val previous = month.minusMonths(1)

        val hasPrevious = snapshot.expenses.any { YearMonth.from(it.date) == previous }
        if (!hasPrevious || snapshot.today.dayOfMonth < 25) return emptyList()

        // Compare like with like. The current month is still running, so it is measured
        // against the same number of days of the previous month rather than against all
        // of it — otherwise a partial month always looks like a saving, and the app would
        // congratulate the user every single month for nothing more than the calendar.
        val dayOfMonth = snapshot.today.dayOfMonth
        val nowSpend = SpendingAnalyzer.totalSpendIn(snapshot, month)
        val thenSpend = SpendingAnalyzer.spendUpToDayIn(snapshot, previous, dayOfMonth)
        if (!thenSpend.isPositive || nowSpend >= thenSpend) return emptyList()

        val saved = thenSpend - nowSpend
        val percent = (saved.paise.toDouble() / thenSpend.paise * 100).toInt()
        if (percent < 10) return emptyList()

        return listOf(
            Insight(
                kind = InsightKind.GOOD_NEWS,
                title = "You spent $percent% less than last month",
                detail = "${IndianFormat.format(saved)} less than you had by the " +
                    "$dayOfMonth${ordinalSuffix(dayOfMonth)} of last month.",
                priority = 50
            )
        )
    }

    /** "1st", "22nd", "3rd" — the teens are all "th", which the modulo check catches. */
    private fun ordinalSuffix(day: Int): String = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
}

enum class InsightKind {
    SPENDING_SPIKE,
    LOAN_ENDING,
    CARD_UTILISATION,
    SAVINGS_RATE,
    GOOD_NEWS
}

data class Insight(
    val kind: InsightKind,
    val title: String,
    val detail: String,
    /** Higher sorts first; roughly how much the user would want to know this. */
    val priority: Int
) {
    val isPositive: Boolean
        get() = kind == InsightKind.GOOD_NEWS ||
            (kind == InsightKind.SAVINGS_RATE && priority < 80)
}
