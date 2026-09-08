package com.moneyplanner

import com.moneyplanner.domain.calc.EmiCalculator
import com.moneyplanner.domain.model.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class EmiCalculatorTest {

    @Test
    fun `installment due dates step forward one month at a time`() {
        val loan = emi(firstDueDate = "2026-01-05", totalInstallments = 36)
        assertEquals(date("2026-01-05"), EmiCalculator.dueDateFor(loan, 1))
        assertEquals(date("2026-02-05"), EmiCalculator.dueDateFor(loan, 2))
        assertEquals(date("2026-09-05"), EmiCalculator.dueDateFor(loan, 9))
        assertEquals(date("2027-01-05"), EmiCalculator.dueDateFor(loan, 13))
    }

    @Test
    fun `a loan due on the 31st keeps returning to the 31st`() {
        val loan = emi(firstDueDate = "2026-01-31", totalInstallments = 12)
        assertEquals(date("2026-01-31"), EmiCalculator.dueDateFor(loan, 1))
        assertEquals(date("2026-02-28"), EmiCalculator.dueDateFor(loan, 2))
        assertEquals(
            "clamping in February must not permanently shift the schedule",
            date("2026-03-31"),
            EmiCalculator.dueDateFor(loan, 3)
        )
        assertEquals(date("2026-04-30"), EmiCalculator.dueDateFor(loan, 4))
    }

    @Test
    fun `paid count combines recorded payments with installments paid before tracking`() {
        val loan = emi(totalInstallments = 36, openingPaid = 3)
        val payments = listOf(
            emiPayment(id = 1, installmentNumber = 4, dueDate = "2026-04-05", paidDate = "2026-04-05"),
            emiPayment(id = 2, installmentNumber = 5, dueDate = "2026-05-05", paidDate = "2026-05-04")
        )
        assertEquals(5, EmiCalculator.paidInstallments(loan, payments))
        assertEquals(31, EmiCalculator.remainingInstallments(loan, payments))
    }

    @Test
    fun `outstanding falls as installments are paid`() {
        val loan = emi(emiAmount = 4_500, totalInstallments = 36, openingPaid = 3)
        assertEquals(rupees(148_500), EmiCalculator.outstandingAmount(loan, emptyList()))

        val payments = listOf(
            emiPayment(installmentNumber = 4, dueDate = "2026-04-05", paidDate = "2026-04-05")
        )
        assertEquals(rupees(144_000), EmiCalculator.outstandingAmount(loan, payments))
    }

    @Test
    fun `a fully paid loan is complete and owes nothing`() {
        val loan = emi(emiAmount = 4_500, totalInstallments = 3)
        val payments = (1..3).map {
            emiPayment(
                id = it.toLong(),
                installmentNumber = it,
                dueDate = "2026-0$it-05",
                paidDate = "2026-0$it-05"
            )
        }
        assertTrue(EmiCalculator.isCompleted(loan, payments))
        assertEquals(0, EmiCalculator.remainingInstallments(loan, payments))
        assertEquals(rupees(0), EmiCalculator.outstandingAmount(loan, payments))
        assertNull(EmiCalculator.nextInstallmentNumber(loan, payments))
        assertNull(EmiCalculator.nextDueDate(loan, payments))
    }

    @Test
    fun `deleting a payment restores the remaining tenure`() {
        val loan = emi(totalInstallments = 12)
        val all = listOf(
            emiPayment(id = 1, installmentNumber = 1, dueDate = "2026-01-05", paidDate = "2026-01-05"),
            emiPayment(id = 2, installmentNumber = 2, dueDate = "2026-02-05", paidDate = "2026-02-05")
        )
        assertEquals(10, EmiCalculator.remainingInstallments(loan, all))

        val afterDelete = all.dropLast(1)
        assertEquals(
            "removing a payment must correct the tenure with no repair step",
            11,
            EmiCalculator.remainingInstallments(loan, afterDelete)
        )
    }

    @Test
    fun `paying an installment early does not double count it`() {
        val loan = emi(totalInstallments = 12, openingPaid = 2)
        val payments = listOf(
            emiPayment(id = 1, installmentNumber = 5, dueDate = "2026-05-05", paidDate = "2026-03-20")
        )
        assertEquals(3, EmiCalculator.paidInstallments(loan, payments))
        assertEquals(
            "the next installment due is the earliest one still unpaid",
            3,
            EmiCalculator.nextInstallmentNumber(loan, payments)
        )
        assertFalse(5 in EmiCalculator.unpaidInstallmentNumbers(loan, payments))
    }

    @Test
    fun `a missed installment stays due and appears in its own month`() {
        val loan = emi(firstDueDate = "2026-07-05", totalInstallments = 12)
        val julyDue = EmiCalculator.unpaidInstallmentsIn(loan, emptyList(), YearMonth.of(2026, 7))
        assertEquals(1, julyDue.size)
        assertEquals(date("2026-07-05"), julyDue.first().dueDate)

        // Still unpaid two months later: it remains attached to July, not moved forward.
        val augustDue = EmiCalculator.unpaidInstallmentsIn(loan, emptyList(), YearMonth.of(2026, 8))
        assertEquals(1, augustDue.size)
        assertEquals(2, augustDue.first().installmentNumber)
    }

    @Test
    fun `an inactive loan contributes nothing to a month`() {
        val loan = emi(firstDueDate = "2026-07-05", totalInstallments = 12, active = false)
        assertTrue(
            EmiCalculator.unpaidInstallmentsIn(loan, emptyList(), YearMonth.of(2026, 7)).isEmpty()
        )
    }

    @Test
    fun `the schedule marks pre tracking installments separately from recorded ones`() {
        val loan = emi(totalInstallments = 6, openingPaid = 2)
        val payments = listOf(
            emiPayment(installmentNumber = 3, dueDate = "2026-03-05", paidDate = "2026-03-05")
        )
        val schedule = EmiCalculator.schedule(loan, payments)

        assertEquals(6, schedule.size)
        assertTrue(schedule[0].isPaid && schedule[0].wasPaidBeforeTracking)
        assertTrue(schedule[2].isPaid && !schedule[2].wasPaidBeforeTracking)
        assertEquals(date("2026-03-05"), schedule[2].paidDate)
        assertFalse(schedule[3].isPaid)
    }

    @Test
    fun `quarterly installments are three months apart`() {
        val loan = emi(firstDueDate = "2026-01-10", frequency = Frequency.QUARTERLY, totalInstallments = 8)
        assertEquals(date("2026-04-10"), EmiCalculator.dueDateFor(loan, 2))
        assertEquals(date("2026-07-10"), EmiCalculator.dueDateFor(loan, 3))
    }

    @Test
    fun `installment formula matches the standard reducing balance result`() {
        // A well known worked example: 1,00,000 at 12 percent for 12 months.
        val installment = EmiCalculator.calculateInstallment(
            principal = rupees(100_000),
            annualRatePercent = 12.0,
            tenureMonths = 12
        )
        assertEquals(888_488L, installment.paise)
    }

    @Test
    fun `a zero interest loan divides the principal evenly`() {
        val installment = EmiCalculator.calculateInstallment(rupees(120_000), 0.0, 12)
        assertEquals(rupees(10_000), installment)
    }

    @Test
    fun `total interest is the difference between what is repaid and what was borrowed`() {
        val loan = emi(emiAmount = 4_500, principal = 150_000, totalInstallments = 36)
        assertEquals(rupees(162_000), EmiCalculator.totalPayable(loan))
        assertEquals(rupees(12_000), EmiCalculator.totalInterest(loan))
    }
}
