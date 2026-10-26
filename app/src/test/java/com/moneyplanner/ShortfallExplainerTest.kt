package com.moneyplanner

import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.ShortfallExplainer
import com.moneyplanner.domain.model.FinancialSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning "December: minus four thousand" into something a person can act on.
 *
 * A projected shortfall with no cause attached is just bad news. The value is in naming the
 * payment, because that is what tells somebody whether to move a due date, put money aside,
 * or ignore it because the figure was an estimate anyway.
 */
class ShortfallExplainerTest {

    @Test
    fun `a healthy forecast explains nothing`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 100_000)),
            incomeSources = listOf(incomeSource(id = 1, amount = 80_000, dayOfMonth = 1))
        )

        val months = ForecastCalculator.forecast(snapshot, 6)

        assertNull(ShortfallExplainer.explain(months))
    }

    /**
     * The case the design brief is built around: a large annual payment landing before the
     * salary that would have covered it.
     */
    @Test
    fun `the largest payment in the short month is named`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 5_000)),
            incomeSources = listOf(incomeSource(id = 1, amount = 20_000, dayOfMonth = 28)),
            annualExpenses = listOf(
                annualExpense(id = 1, amount = 60_000, dueMonth = 10, dueDay = 5, name = "Car insurance")
            )
        )

        val months = ForecastCalculator.forecast(snapshot, 6)
        val explanation = ShortfallExplainer.explain(months)

        assertNotNull(explanation)
        requireNotNull(explanation)
        assertTrue(explanation.hasCulprit)
        assertEquals("Car insurance", explanation.culpritTitle)
        assertEquals(rupees(60_000), explanation.culpritAmount)
        assertTrue(explanation.projectedBalance.isNegative)
    }

    /** The gap between the payment and the money that closes it. */
    @Test
    fun `the wait for the next money is measured in days`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 5_000)),
            incomeSources = listOf(incomeSource(id = 1, amount = 20_000, dayOfMonth = 28)),
            annualExpenses = listOf(
                annualExpense(id = 1, amount = 60_000, dueMonth = 10, dueDay = 5, name = "Car insurance")
            )
        )

        val explanation = ShortfallExplainer.explain(ForecastCalculator.forecast(snapshot, 6))
        requireNotNull(explanation)

        // The 5th to the 28th of the same month.
        assertEquals(23L, explanation.daysUntilRelief)
    }

    /**
     * Only the first shortfall is explained.
     *
     * Once a month ends below zero the ones after it usually do too, for the same reason.
     * Listing all of them would be four alarms for one problem.
     */
    @Test
    fun `only the earliest short month is reported`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 1_000)),
            incomeSources = listOf(incomeSource(id = 1, amount = 5_000, dayOfMonth = 28)),
            annualExpenses = listOf(
                annualExpense(id = 1, amount = 40_000, dueMonth = 10, dueDay = 5, name = "Insurance"),
                annualExpense(id = 2, amount = 40_000, dueMonth = 11, dueDay = 5, name = "School fees")
            )
        )

        val explanation = ShortfallExplainer.explain(ForecastCalculator.forecast(snapshot, 6))
        requireNotNull(explanation)

        assertEquals(10, explanation.month.monthValue)
        assertEquals("Insurance", explanation.culpritTitle)
    }

    /**
     * A month with no dated commitments at all cannot blame one.
     *
     * Pointing at an arbitrary small payment would send the user to fix the wrong thing.
     */
    @Test
    fun `a month with no payments names no culprit`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = -20_000))
        )

        val explanation = ShortfallExplainer.explain(ForecastCalculator.forecast(snapshot, 3))
        requireNotNull(explanation)

        assertFalse(explanation.hasCulprit)
        assertNull(explanation.culpritTitle)
        assertNull(explanation.daysUntilRelief)
    }
}
