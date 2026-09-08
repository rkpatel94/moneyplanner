package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.EmiPayment
import kotlin.math.pow

/**
 * What paying extra towards a loan actually buys.
 *
 * For a household carrying EMIs this is the highest-leverage decision available, and it is
 * pure arithmetic on figures already recorded. The answer is usually far larger than
 * people expect: money paid early reduces the balance that interest is charged on for
 * every remaining month, so a modest monthly top-up can remove years from a loan.
 *
 * A loan with no interest rate recorded cannot be modelled, and the calculator says so
 * rather than inventing a rate to make the numbers work.
 */
object PrepaymentCalculator {

    /**
     * @param extraPerMonth added to every remaining installment.
     * @param lumpSum paid once, immediately.
     */
    fun evaluate(
        emi: Emi,
        payments: List<EmiPayment>,
        extraPerMonth: Money = Money.ZERO,
        lumpSum: Money = Money.ZERO
    ): PrepaymentResult {
        val rate = emi.interestRatePercent
        val remaining = EmiCalculator.remainingInstallments(emi, payments)

        if (rate == null || rate <= 0.0 || remaining <= 0 || emi.emiAmount.paise <= 0L) {
            return PrepaymentResult(
                isModelled = false,
                reason = when {
                    remaining <= 0 -> "This loan is already fully repaid."
                    rate == null || rate <= 0.0 ->
                        "Add the interest rate to this loan and the saving can be worked out."
                    else -> "This loan cannot be modelled from the figures recorded."
                }
            )
        }

        val monthlyRate = rate / 12.0 / 100.0
        val outstanding = outstandingPrincipal(emi.emiAmount, monthlyRate, remaining)

        // The baseline needs no simulation. Carrying on unchanged means paying exactly the
        // installments that are left, so the months are the remaining count by definition
        // and the interest is simply what is paid beyond the principal. Deriving it rather
        // than replaying it also avoids a rounding residue in the last month, which would
        // otherwise add a phantom month and understate every saving by one.
        val baselineMonths = remaining
        val baselineInterest = (emi.emiAmount * remaining - outstanding).coerceAtLeastZero()

        val improved = amortise(outstanding, monthlyRate, emi.emiAmount, extraPerMonth, lumpSum)

        if (!improved.completes) {
            return PrepaymentResult(
                isModelled = false,
                reason = "The installment does not cover the interest on this loan, so it " +
                    "would never finish. Check the amount and rate."
            )
        }

        return PrepaymentResult(
            isModelled = true,
            outstandingPrincipal = outstanding,
            baselineMonths = baselineMonths,
            baselineInterest = baselineInterest,
            newMonths = improved.months,
            newInterest = improved.interest,
            monthsSaved = (baselineMonths - improved.months).coerceAtLeast(0),
            interestSaved = (baselineInterest - improved.interest).coerceAtLeastZero(),
            extraPerMonth = extraPerMonth,
            lumpSum = lumpSum
        )
    }

    /**
     * The principal still owed, worked out backwards from the installment and the number
     * of payments left. This is the present value of the remaining installments, which is
     * what a lender would quote as the outstanding balance.
     */
    private fun outstandingPrincipal(
        installment: Money,
        monthlyRate: Double,
        remaining: Int
    ): Money {
        val growth = (1.0 + monthlyRate).pow(remaining)
        val value = installment.paise * (growth - 1.0) / (monthlyRate * growth)
        return Money(Math.round(value))
    }

    private data class Schedule(val months: Int, val interest: Money, val completes: Boolean)

    /**
     * Runs the loan month by month.
     *
     * A cap guards against an installment too small to cover the interest, which would
     * otherwise loop forever on a balance that never falls.
     */
    private fun amortise(
        principal: Money,
        monthlyRate: Double,
        installment: Money,
        extraPerMonth: Money,
        lumpSum: Money
    ): Schedule {
        var balance = (principal - lumpSum).coerceAtLeastZero().paise
        var totalInterest = 0L
        var months = 0
        val payment = installment.paise + extraPerMonth.paise
        val maxMonths = 1_200

        while (balance > 0 && months < maxMonths) {
            val interest = Math.round(balance * monthlyRate)
            val principalPaid = payment - interest

            // An installment that does not even cover the interest never repays the loan.
            if (principalPaid <= 0) return Schedule(months, Money(totalInterest), false)

            totalInterest += interest

            // The final installment clears whatever is left, exactly as a real loan does.
            // Without this, rounding the derived balance up by a few paise would demand an
            // extra month and quietly understate every saving figure by one.
            if (balance + interest <= payment) {
                months++
                balance = 0
                break
            }

            balance -= principalPaid
            months++
        }

        return Schedule(months, Money(totalInterest), balance <= 0)
    }
}

data class PrepaymentResult(
    val isModelled: Boolean,
    val reason: String? = null,
    val outstandingPrincipal: Money = Money.ZERO,
    val baselineMonths: Int = 0,
    val baselineInterest: Money = Money.ZERO,
    val newMonths: Int = 0,
    val newInterest: Money = Money.ZERO,
    val monthsSaved: Int = 0,
    val interestSaved: Money = Money.ZERO,
    val extraPerMonth: Money = Money.ZERO,
    val lumpSum: Money = Money.ZERO
) {
    val hasSaving: Boolean get() = isModelled && (monthsSaved > 0 || interestSaved.isPositive)

    /** A plain sentence describing the benefit. */
    fun summary(): String {
        if (!isModelled) return reason.orEmpty()
        if (!hasSaving) return "Add an extra amount to see what it would save."

        val years = monthsSaved / 12
        val months = monthsSaved % 12
        val timeText = when {
            years > 0 && months > 0 -> "$years years $months months"
            years > 0 -> if (years == 1) "1 year" else "$years years"
            months == 1 -> "1 month"
            else -> "$months months"
        }
        return "Finishes $timeText earlier and saves " +
            com.moneyplanner.core.money.IndianFormat.format(interestSaved) + " in interest"
    }
}
