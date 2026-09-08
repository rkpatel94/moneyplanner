package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.EmiPayment
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.pow

/**
 * Everything about a loan repaid in installments.
 *
 * The number of installments already paid is derived, never stored: it is the count of
 * recorded payments plus the opening count the user gave when they added a loan that was
 * already part-way through. Deleting a payment therefore corrects the remaining tenure
 * automatically instead of leaving a stale counter behind.
 */
object EmiCalculator {

    /** The due date of a given installment, counting from one. */
    fun dueDateFor(emi: Emi, installmentNumber: Int): LocalDate {
        val periodsElapsed = (installmentNumber - 1).coerceAtLeast(0)
        if (emi.frequency.isWeekly) {
            return emi.firstDueDate.plusWeeks(periodsElapsed.toLong())
        }
        val monthsToAdd = periodsElapsed.toLong() * emi.frequency.monthsPerPeriod
        return DateUtil.addMonthsKeepingDay(
            start = emi.firstDueDate,
            monthsToAdd = monthsToAdd,
            intendedDay = emi.firstDueDate.dayOfMonth
        )
    }

    fun paidInstallments(emi: Emi, payments: List<EmiPayment>): Int {
        val recorded = payments.count { it.emiId == emi.id }
        return (emi.openingPaidInstallments + recorded).coerceAtMost(emi.totalInstallments)
    }

    fun remainingInstallments(emi: Emi, payments: List<EmiPayment>): Int =
        (emi.totalInstallments - paidInstallments(emi, payments)).coerceAtLeast(0)

    /** True once every installment is accounted for; such a loan leaves the forecast. */
    fun isCompleted(emi: Emi, payments: List<EmiPayment>): Boolean =
        remainingInstallments(emi, payments) == 0

    /** What is still owed on the loan, at the contracted installment amount. */
    fun outstandingAmount(emi: Emi, payments: List<EmiPayment>): Money =
        emi.emiAmount * remainingInstallments(emi, payments)

    fun totalPayable(emi: Emi): Money = emi.emiAmount * emi.totalInstallments

    /** The interest cost implied by the entered figures, when a principal was given. */
    fun totalInterest(emi: Emi): Money =
        (totalPayable(emi) - emi.principal).coerceAtLeastZero()

    /**
     * The installment numbers that have not been paid.
     *
     * The opening count occupies the lowest numbers, and recorded payments claim their
     * own numbers, so a user who pays installment 14 early does not have it counted twice.
     */
    fun unpaidInstallmentNumbers(emi: Emi, payments: List<EmiPayment>): List<Int> {
        val claimed = mutableSetOf<Int>()
        for (n in 1..emi.openingPaidInstallments.coerceAtMost(emi.totalInstallments)) {
            claimed += n
        }
        payments.filter { it.emiId == emi.id }.forEach { claimed += it.installmentNumber }
        return (1..emi.totalInstallments).filterNot { it in claimed }
    }

    /** The next installment number that should be paid, or null when the loan is done. */
    fun nextInstallmentNumber(emi: Emi, payments: List<EmiPayment>): Int? =
        unpaidInstallmentNumbers(emi, payments).minOrNull()

    fun nextDueDate(emi: Emi, payments: List<EmiPayment>): LocalDate? =
        nextInstallmentNumber(emi, payments)?.let { dueDateFor(emi, it) }

    /** The unpaid installments falling inside [month], used directly by the forecast. */
    fun unpaidInstallmentsIn(
        emi: Emi,
        payments: List<EmiPayment>,
        month: YearMonth
    ): List<EmiInstallment> {
        if (!emi.isActive) return emptyList()
        return unpaidInstallmentNumbers(emi, payments)
            .map { number -> EmiInstallment(number, dueDateFor(emi, number), emi.emiAmount) }
            .filter { YearMonth.from(it.dueDate) == month }
    }

    /** The full schedule, for the EMI detail screen. */
    fun schedule(emi: Emi, payments: List<EmiPayment>): List<EmiScheduleRow> {
        val paymentsByNumber = payments.filter { it.emiId == emi.id }
            .associateBy { it.installmentNumber }
        return (1..emi.totalInstallments).map { number ->
            val payment = paymentsByNumber[number]
            val coveredByOpening = number <= emi.openingPaidInstallments
            EmiScheduleRow(
                installmentNumber = number,
                dueDate = dueDateFor(emi, number),
                amount = payment?.amount ?: emi.emiAmount,
                isPaid = payment != null || coveredByOpening,
                paidDate = payment?.paidDate,
                wasPaidBeforeTracking = coveredByOpening && payment == null
            )
        }
    }

    /**
     * The standard reducing-balance installment for a loan.
     *
     * Offered as a helper when the user knows the principal, rate and tenure but not the
     * installment. It never overwrites what the user typed; the amount they entered is
     * always the amount the forecast uses.
     */
    fun calculateInstallment(
        principal: Money,
        annualRatePercent: Double,
        tenureMonths: Int
    ): Money {
        if (tenureMonths <= 0) return Money.ZERO
        if (annualRatePercent <= 0.0) return principal.divideRounded(tenureMonths)

        val monthlyRate = annualRatePercent / 12.0 / 100.0
        val growth = (1.0 + monthlyRate).pow(tenureMonths)
        val installment = principal.paise * monthlyRate * growth / (growth - 1.0)
        return Money(Math.round(installment))
    }
}

data class EmiInstallment(
    val installmentNumber: Int,
    val dueDate: LocalDate,
    val amount: Money
)

data class EmiScheduleRow(
    val installmentNumber: Int,
    val dueDate: LocalDate,
    val amount: Money,
    val isPaid: Boolean,
    val paidDate: LocalDate?,
    val wasPaidBeforeTracking: Boolean
)
