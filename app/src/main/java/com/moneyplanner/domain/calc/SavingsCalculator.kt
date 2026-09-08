package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.SavingsContribution
import com.moneyplanner.domain.model.SavingsGoal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Progress towards savings goals, and what it takes each month to reach them.
 *
 * A goal balance is the sum of its contributions rather than a stored total, so
 * withdrawing from a goal or deleting a mistaken contribution is reflected immediately
 * and the progress bar can never disagree with the contribution history beneath it.
 */
object SavingsCalculator {

    fun balanceOf(goalId: Long, contributions: List<SavingsContribution>): Money =
        contributions.filter { it.goalId == goalId }.sumOfMoney { it.amount }

    fun progressOf(
        goal: SavingsGoal,
        contributions: List<SavingsContribution>,
        today: LocalDate
    ): GoalProgress {
        val saved = balanceOf(goal.id, contributions)
        val remaining = (goal.targetAmount - saved).coerceAtLeastZero()

        val monthsLeft = goal.targetDate?.let { target ->
            val months = ChronoUnit.MONTHS.between(
                YearMonth.from(today),
                YearMonth.from(target)
            ).toInt()
            // A target in the current month or already past still needs one more push
            // rather than a division by zero.
            months.coerceAtLeast(if (target.isBefore(today)) 0 else 1)
        }

        val requiredMonthly = when {
            remaining.isZero -> Money.ZERO
            monthsLeft == null -> Money.ZERO
            monthsLeft <= 0 -> remaining
            else -> remaining.divideRounded(monthsLeft)
        }

        val isOverdue = monthsLeft != null && monthsLeft <= 0 && remaining.isPositive

        val fraction = if (goal.targetAmount.paise <= 0L) 0f
        else (saved.paise.toDouble() / goal.targetAmount.paise).toFloat().coerceIn(0f, 1f)

        return GoalProgress(
            goal = goal,
            saved = saved,
            remaining = remaining,
            progressFraction = fraction,
            monthsRemaining = monthsLeft,
            requiredMonthlySaving = requiredMonthly,
            isAchieved = remaining.isZero,
            isBehindSchedule = isOverdue
        )
    }

    fun allProgress(snapshot: FinancialSnapshot): List<GoalProgress> =
        snapshot.goals.map { progressOf(it, snapshot.contributions, snapshot.today) }

    /**
     * The total that must be saved this month to keep every unmet goal on schedule.
     * The forecast treats this as a plan, not as a bill: it is shown separately so the
     * user can see what is left both with and without their savings plan.
     *
     * Goals whose date has already passed are left out. Their [GoalProgress.requiredMonthlySaving]
     * is the entire remaining balance, which is the honest answer to "what would finishing
     * this now cost" but a nonsense monthly plan: carried into the forecast it would
     * repeat that whole figure in every projected month, for a deadline that cannot be met
     * anyway. They are surfaced as behind schedule instead, which is the actionable fact.
     */
    fun totalRequiredMonthlySaving(snapshot: FinancialSnapshot): Money =
        allProgress(snapshot)
            .filterNot { it.isAchieved || it.isBehindSchedule }
            .sumOfMoney { it.requiredMonthlySaving }

    fun totalSaved(snapshot: FinancialSnapshot): Money =
        snapshot.contributions.sumOfMoney { it.amount }

    /** Money actually put aside during [month], used by the monthly view. */
    fun savedIn(snapshot: FinancialSnapshot, month: YearMonth): Money =
        snapshot.contributions
            .filter { YearMonth.from(it.date) == month }
            .sumOfMoney { it.amount }
}

data class GoalProgress(
    val goal: SavingsGoal,
    val saved: Money,
    val remaining: Money,
    val progressFraction: Float,
    val monthsRemaining: Int?,
    val requiredMonthlySaving: Money,
    val isAchieved: Boolean,
    /** The target date has passed with money still to find. */
    val isBehindSchedule: Boolean
) {
    val progressPercent: Int get() = (progressFraction * 100).toInt()
}
