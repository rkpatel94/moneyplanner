package com.moneyplanner

import com.moneyplanner.domain.calc.InsightKind
import com.moneyplanner.domain.calc.InsightsCalculator
import com.moneyplanner.domain.calc.PrepaymentCalculator
import com.moneyplanner.domain.model.FinancialSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prepayment maths has to be right: it is the number a person would act on by handing a
 * bank a large sum. The tests hold it to properties that must be true of any correct
 * amortisation, rather than to values copied from the implementation.
 */
class PrepaymentCalculatorTest {

    // 1,00,000 at 12 percent over 12 months is an installment of about 8,885.
    private fun loan(rate: Double? = 12.0) = emi(
        emiAmount = 8_885,
        principal = 100_000,
        totalInstallments = 12,
        firstDueDate = "2026-01-05"
    ).copy(interestRatePercent = rate)

    @Test
    fun `paying extra every month shortens the loan and saves interest`() {
        val result = PrepaymentCalculator.evaluate(
            emi = loan(),
            payments = emptyList(),
            extraPerMonth = rupees(2_000)
        )

        assertTrue(result.isModelled)
        assertTrue("the loan must finish sooner", result.monthsSaved > 0)
        assertTrue("and cost less interest", result.interestSaved.isPositive)
        assertTrue(result.newMonths < result.baselineMonths)
        assertTrue(result.newInterest < result.baselineInterest)
    }

    @Test
    fun `a lump sum also shortens the loan`() {
        val result = PrepaymentCalculator.evaluate(
            emi = loan(),
            payments = emptyList(),
            lumpSum = rupees(25_000)
        )
        assertTrue(result.isModelled)
        assertTrue(result.monthsSaved > 0)
        assertTrue(result.interestSaved.isPositive)
    }

    @Test
    fun `paying nothing extra changes nothing`() {
        val result = PrepaymentCalculator.evaluate(loan(), emptyList())
        assertTrue(result.isModelled)
        assertEquals(0, result.monthsSaved)
        assertEquals(rupees(0), result.interestSaved)
        assertFalse(result.hasSaving)
    }

    @Test
    fun `paying more saves more`() {
        val small = PrepaymentCalculator.evaluate(loan(), emptyList(), extraPerMonth = rupees(1_000))
        val large = PrepaymentCalculator.evaluate(loan(), emptyList(), extraPerMonth = rupees(5_000))

        assertTrue(large.interestSaved > small.interestSaved)
        assertTrue(large.monthsSaved >= small.monthsSaved)
    }

    @Test
    fun `says plainly when there is no interest rate to model with`() {
        val result = PrepaymentCalculator.evaluate(loan(rate = null), emptyList())
        assertFalse(result.isModelled)
        assertTrue(result.reason!!.contains("interest rate"))
    }

    @Test
    fun `says plainly when the loan is already repaid`() {
        val paidOff = loan().copy(openingPaidInstallments = 12)
        val result = PrepaymentCalculator.evaluate(paidOff, emptyList())
        assertFalse(result.isModelled)
        assertTrue(result.reason!!.contains("fully repaid"))
    }

    @Test
    fun `the baseline schedule matches the installments actually left`() {
        // The outstanding balance is derived from the installment and the remaining
        // tenure, so replaying it must land back on exactly that many months. This is
        // what makes every saving figure a like-for-like comparison, and it also means
        // an installment too small to cover the interest cannot arise from real input.
        val withSomePaid = loan().copy(openingPaidInstallments = 4)
        val result = PrepaymentCalculator.evaluate(withSomePaid, emptyList())

        assertTrue(result.isModelled)
        assertEquals(8, result.baselineMonths)
    }

    @Test
    fun `outstanding principal is less than the sum of remaining installments`() {
        // The difference between the two is exactly the interest yet to be charged.
        val result = PrepaymentCalculator.evaluate(loan(), emptyList())
        val totalRemaining = rupees(8_885) * 12
        assertTrue(result.outstandingPrincipal < totalRemaining)
        assertTrue(result.outstandingPrincipal.isPositive)
    }

    @Test
    fun `summary describes the benefit in plain words`() {
        val result = PrepaymentCalculator.evaluate(
            loan(), emptyList(), extraPerMonth = rupees(3_000)
        )
        assertTrue(result.summary().contains("earlier"))
        assertTrue(result.summary().contains("saves"))
    }
}

/**
 * Insights must stay quiet until there is enough history to justify them. A confident
 * claim from two weeks of data is noise, not intelligence.
 */
class InsightsCalculatorTest {

    private fun base(today: String = "2026-08-26") = FinancialSnapshot(
        today = date(today),
        accounts = listOf(account(opening = 50_000)),
        categories = listOf(category(1, "Food"), category(2, "Fuel", essential = true))
    )

    @Test
    fun `says nothing at all when there is no history`() {
        assertTrue(InsightsCalculator.generate(base()).isEmpty())
    }

    @Test
    fun `notices a category well above its own recent average`() {
        val snap = base().copy(
            expenses = listOf(
                expense(id = 1, amount = 5_000, date = "2026-06-10", categoryId = 1),
                expense(id = 2, amount = 5_000, date = "2026-07-10", categoryId = 1),
                expense(id = 3, amount = 12_000, date = "2026-08-10", categoryId = 1)
            )
        )
        val spike = InsightsCalculator.generate(snap)
            .firstOrNull { it.kind == InsightKind.SPENDING_SPIKE }

        assertTrue("a spike over the threshold should be reported", spike != null)
        assertTrue(spike!!.title.contains("Food"))
    }

    @Test
    fun `stays quiet about a category that is only slightly above average`() {
        val snap = base().copy(
            expenses = listOf(
                expense(id = 1, amount = 5_000, date = "2026-06-10", categoryId = 1),
                expense(id = 2, amount = 5_000, date = "2026-07-10", categoryId = 1),
                expense(id = 3, amount = 5_500, date = "2026-08-10", categoryId = 1)
            )
        )
        assertTrue(
            InsightsCalculator.generate(snap).none { it.kind == InsightKind.SPENDING_SPIKE }
        )
    }

    @Test
    fun `notices a loan about to finish`() {
        val snap = base().copy(
            emis = listOf(
                emi(emiAmount = 4_500, totalInstallments = 12, openingPaid = 10)
            )
        )
        val insight = InsightsCalculator.generate(snap)
            .firstOrNull { it.kind == InsightKind.LOAN_ENDING }

        assertTrue(insight != null)
        assertTrue(insight!!.detail.contains("frees up"))
    }

    @Test
    fun `stays quiet about a loan with plenty left to run`() {
        val snap = base().copy(
            emis = listOf(emi(emiAmount = 4_500, totalInstallments = 36, openingPaid = 2))
        )
        assertTrue(InsightsCalculator.generate(snap).none { it.kind == InsightKind.LOAN_ENDING })
    }

    @Test
    fun `notices a card close to its limit`() {
        val snap = base().copy(
            creditCards = listOf(creditCard(outstanding = 85_000, limit = 100_000))
        )
        val insight = InsightsCalculator.generate(snap)
            .firstOrNull { it.kind == InsightKind.CARD_UTILISATION }
        assertTrue(insight != null)
        assertTrue(insight!!.title.contains("85%"))
    }

    @Test
    fun `warns when more has been spent than received`() {
        val snap = base().copy(
            incomeTransactions = listOf(incomeTransaction(amount = 20_000, date = "2026-08-01")),
            expenses = listOf(expense(id = 1, amount = 30_000, date = "2026-08-05", categoryId = 1))
        )
        val insight = InsightsCalculator.generate(snap)
            .firstOrNull { it.kind == InsightKind.SAVINGS_RATE }

        assertTrue(insight != null)
        assertTrue(insight!!.title.contains("spent more"))
        assertFalse(insight.isPositive)
    }

    @Test
    fun `holds the savings rate back until the month is far enough along`() {
        val early = base(today = "2026-08-05").copy(
            incomeTransactions = listOf(incomeTransaction(amount = 50_000, date = "2026-08-01")),
            expenses = listOf(expense(id = 1, amount = 10_000, date = "2026-08-02", categoryId = 1))
        )
        assertTrue(
            "a rate from five days of data would be meaningless",
            InsightsCalculator.generate(early).none { it.kind == InsightKind.SAVINGS_RATE }
        )
    }

    @Test
    fun `reports good news too`() {
        val snap = base(today = "2026-08-28").copy(
            expenses = listOf(
                expense(id = 1, amount = 20_000, date = "2026-07-10", categoryId = 1),
                expense(id = 2, amount = 10_000, date = "2026-08-10", categoryId = 1)
            )
        )
        val good = InsightsCalculator.generate(snap)
            .firstOrNull { it.kind == InsightKind.GOOD_NEWS }

        assertTrue("an app that only reports problems stops being read", good != null)
        assertTrue(good!!.isPositive)
    }

    @Test
    fun `orders the most important insight first`() {
        val snap = base().copy(
            incomeTransactions = listOf(incomeTransaction(amount = 20_000, date = "2026-08-01")),
            expenses = listOf(expense(id = 1, amount = 30_000, date = "2026-08-05", categoryId = 1)),
            creditCards = listOf(creditCard(outstanding = 85_000, limit = 100_000))
        )
        val insights = InsightsCalculator.generate(snap)
        assertTrue(insights.size >= 2)
        assertTrue(insights[0].priority >= insights[1].priority)
    }

    @Test
    fun `is deterministic`() {
        val snap = base().copy(
            expenses = listOf(
                expense(id = 1, amount = 5_000, date = "2026-06-10", categoryId = 1),
                expense(id = 2, amount = 12_000, date = "2026-08-10", categoryId = 1)
            )
        )
        val first = InsightsCalculator.generate(snap)
        repeat(3) {
            val again = InsightsCalculator.generate(snap)
            assertEquals(first.map { it.title }, again.map { it.title })
        }
    }
}
