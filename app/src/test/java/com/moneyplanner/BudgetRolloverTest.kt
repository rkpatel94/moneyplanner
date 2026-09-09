package com.moneyplanner

import com.moneyplanner.domain.calc.BudgetCalculator
import com.moneyplanner.domain.calc.BudgetState
import com.moneyplanner.domain.model.Budget
import com.moneyplanner.domain.model.FinancialSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * Carrying unspent budget forward, and the alert threshold the user chose.
 *
 * The rule that shapes everything here is that only unspent money carries. An overspend
 * does not follow you into the next month as a debt, because that turns one bad month into
 * a limit the next month cannot meet either, and a budget nobody can hit stops being read.
 */
class BudgetRolloverTest {

    private fun snapshot(vararg spends: Pair<String, Long>) = FinancialSnapshot(
        today = date("2026-09-15"),
        categories = listOf(category(id = 1, name = "Food")),
        expenses = spends.mapIndexed { index, (on, amount) ->
            expense(id = index + 1L, amount = amount, date = on, categoryId = 1)
        }
    )

    private fun budget(
        amount: Long = 10_000,
        rollover: Boolean = true,
        threshold: Int = Budget.DEFAULT_ALERT_THRESHOLD,
        createdOn: String? = "2026-01-01"
    ) = Budget(
        id = 1,
        categoryId = 1,
        amount = rupees(amount),
        rolloverEnabled = rollover,
        alertThresholdPercent = threshold,
        createdAt = createdOn?.let { date(it) }
    )

    private val september = YearMonth.of(2026, 9)

    @Test
    fun `without rollover every month starts at the same limit`() {
        val snap = snapshot("2026-08-05" to 2_000)
        val status = BudgetCalculator.statusFor(budget(rollover = false), snap, september)

        assertEquals(rupees(0), status.carriedOver)
        assertEquals(rupees(10_000), status.effectiveLimit)
        assertFalse(status.hasCarryOver)
    }

    @Test
    fun `an unspent month raises the next month's limit`() {
        // 10,000 limit, 4,000 spent in August, so 6,000 carries into September.
        val snap = snapshot("2026-08-05" to 4_000)
        val status = BudgetCalculator.statusFor(budget(createdOn = "2026-08-01"), snap, september)

        assertEquals(rupees(6_000), status.carriedOver)
        assertEquals(rupees(16_000), status.effectiveLimit)
    }

    @Test
    fun `two quiet months are worth more than one`() {
        // July untouched carries 10,000; August then has a 20,000 limit and spends 4,000,
        // so 16,000 reaches September.
        val snap = snapshot("2026-08-05" to 4_000)
        val status = BudgetCalculator.statusFor(budget(createdOn = "2026-07-01"), snap, september)

        assertEquals(rupees(16_000), status.carriedOver)
        assertEquals(rupees(26_000), status.effectiveLimit)
    }

    @Test
    fun `going over carries nothing rather than a debt`() {
        // August spent 15,000 against a 10,000 limit. September starts clean at 10,000.
        val snap = snapshot("2026-08-05" to 15_000)
        val status = BudgetCalculator.statusFor(budget(createdOn = "2026-08-01"), snap, september)

        assertEquals(rupees(0), status.carriedOver)
        assertEquals(rupees(10_000), status.effectiveLimit)
    }

    @Test
    fun `an overspend wipes an allowance built up before it`() {
        // July carries 10,000, so August has 20,000 and spends 25,000. Nothing survives.
        val snap = snapshot("2026-08-05" to 25_000)
        val status = BudgetCalculator.statusFor(budget(createdOn = "2026-07-01"), snap, september)

        assertEquals(rupees(0), status.carriedOver)
    }

    @Test
    fun `nothing carries from before the budget existed`() {
        // Created in September, so August's restraint is not the budget's to reward.
        val snap = snapshot("2026-08-05" to 1_000)
        val status = BudgetCalculator.statusFor(budget(createdOn = "2026-09-01"), snap, september)

        assertEquals(rupees(0), status.carriedOver)
        assertEquals(rupees(10_000), status.effectiveLimit)
    }

    @Test
    fun `rollover reaches back no further than a year`() {
        val snap = snapshot()
        val status = BudgetCalculator.statusFor(budget(createdOn = "2015-01-01"), snap, september)

        // Twelve untouched months at 10,000, and no more however old the budget is.
        assertEquals(rupees(120_000), status.carriedOver)
    }

    @Test
    fun `spending is measured against the carried limit, not the plain one`() {
        val snap = snapshot("2026-08-05" to 4_000, "2026-09-10" to 12_000)
        val status = BudgetCalculator.statusFor(budget(createdOn = "2026-08-01"), snap, september)

        // 12,000 spent against a 16,000 effective limit is under, not over.
        assertEquals(rupees(4_000), status.remaining)
        assertEquals(rupees(0), status.overspendAmount)
        assertTrue(status.state != BudgetState.OVER)
    }

    @Test
    fun `the alert threshold the user chose is the one that fires`() {
        val snap = snapshot("2026-09-10" to 6_000)

        // 60 percent of 10,000, on the 15th. Under an 80 percent threshold, over a 50.
        val lenient = BudgetCalculator.statusFor(
            budget(rollover = false, threshold = 80), snap, september
        )
        val strict = BudgetCalculator.statusFor(
            budget(rollover = false, threshold = 50), snap, september
        )

        // The strict one has crossed its threshold. The lenient one has not, and reports
        // the pace warning instead, which is what half a month at this rate implies.
        assertEquals(BudgetState.NEAR_LIMIT, strict.state)
        assertEquals(BudgetState.PROJECTED_OVER, lenient.state)
        assertTrue(strict.usedFraction >= strict.budget.alertFraction)
        assertFalse(lenient.usedFraction >= lenient.budget.alertFraction)
    }

    @Test
    fun `a budget with no creation date still rolls over within the year window`() {
        val snap = snapshot("2026-08-05" to 4_000)
        val status = BudgetCalculator.statusFor(budget(createdOn = null), snap, september)

        // Eleven untouched months plus August's 6,000 left over.
        assertEquals(rupees(116_000), status.carriedOver)
    }
}
