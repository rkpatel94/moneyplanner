package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * Why a month is projected to run short, in terms of the records that cause it.
 *
 * A forecast that says "December: −₹4,800" is a prediction the user can do nothing with. A
 * forecast that says "the annual insurance premium falls before the salary arrives" is a
 * problem with two obvious fixes: move the premium, or set money aside for it. The number
 * is the alarm; this is the part that makes the alarm actionable.
 *
 * Nothing here is inferred beyond what the ledger already contains. The culprit is the
 * largest outflow the user themselves recorded, named as they named it. There is no model
 * of spending psychology, no advice, and no score.
 */
object ShortfallExplainer {

    /** Null when the horizon holds no shortfall, which is the common case. */
    fun explain(months: List<MonthForecast>): ShortfallExplanation? {
        val month = months.firstOrNull { it.isShortfall } ?: return null

        val outflows = month.items.filterNot { it.isInflow }
        val culprit = outflows.maxByOrNull { it.amount.paise }

        // The income that would have covered it, had it arrived first. Naming the date is
        // what turns "you are short" into "you are short for eleven days".
        val incomeAfterCulprit = culprit?.let { worst ->
            month.items
                .filter { it.isInflow && it.date.isAfter(worst.date) }
                .minByOrNull { it.date }
        }

        return ShortfallExplanation(
            month = month.month,
            projectedBalance = month.closingBalance,
            culpritTitle = culprit?.title,
            culpritAmount = culprit?.amount ?: Money.ZERO,
            culpritDate = culprit?.date,
            nextIncomeDate = incomeAfterCulprit?.date,
            // A shortfall the user could have seen coming from a recorded commitment is a
            // different problem from one that emerges out of estimated everyday spending.
            // Saying which it is stops the app blaming a bill for a habit.
            causedByRecordedCommitment = culprit != null &&
                culprit.amount.paise >= month.estimatedEverydaySpend.paise
        )
    }
}

/**
 * The first month projected to end below zero, and what pushes it there.
 *
 * Fields are nullable where the records cannot say. A month can run short because of the
 * accumulated weight of ordinary spending rather than any single payment, and inventing a
 * culprit in that case would point the user at the wrong thing.
 */
data class ShortfallExplanation(
    val month: YearMonth,
    val projectedBalance: Money,
    val culpritTitle: String?,
    val culpritAmount: Money,
    val culpritDate: LocalDate?,
    val nextIncomeDate: LocalDate?,
    val causedByRecordedCommitment: Boolean
) {
    /** True when there is a single payment worth naming. */
    val hasCulprit: Boolean get() = culpritTitle != null && culpritAmount.isPositive

    /**
     * How long the gap lasts, when income follows the payment inside the same month.
     * Null when nothing more is expected that month, which is a worse situation and one
     * the screen should not dress up as a short wait.
     */
    val daysUntilRelief: Long?
        get() = if (culpritDate != null && nextIncomeDate != null) {
            java.time.temporal.ChronoUnit.DAYS.between(culpritDate, nextIncomeDate)
        } else {
            null
        }
}
