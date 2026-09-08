package com.moneyplanner

import com.moneyplanner.domain.calc.BudgetCalculator
import com.moneyplanner.domain.calc.BudgetState
import com.moneyplanner.domain.calc.RunwayCalculator
import com.moneyplanner.domain.model.Budget
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

fun budget(id: Long = 1, categoryId: Long? = null, amount: Long) = Budget(
    id = id,
    categoryId = categoryId,
    amount = rupees(amount)
)

/**
 * Budgets measure spending, the forecast measures cash. These tests pin that difference,
 * because getting it wrong would make a budget silently ignore card purchases.
 */
class BudgetCalculatorTest {

    private val food = category(1, "Food")
    private val rent = category(2, "Rent", essential = true)

    // Mid-month, so the pace projection has something to work with.
    private fun snapshot(vararg expenses: com.moneyplanner.domain.model.Expense) =
        FinancialSnapshot(
            today = date("2026-08-10"),
            categories = listOf(food, rent),
            expenses = expenses.toList()
        )

    @Test
    fun `counts spending in the budgeted category only`() {
        val snap = snapshot(
            expense(id = 1, amount = 2_000, date = "2026-08-02", categoryId = 1),
            expense(id = 2, amount = 13_000, date = "2026-08-05", categoryId = 2)
        )
        val status = BudgetCalculator.statusFor(budget(categoryId = 1, amount = 8_000), snap)

        assertEquals(rupees(2_000), status.spent)
        assertEquals(rupees(6_000), status.remaining)
        assertEquals("Food", status.categoryName)
    }

    @Test
    fun `an overall budget counts everything`() {
        val snap = snapshot(
            expense(id = 1, amount = 2_000, date = "2026-08-02", categoryId = 1),
            expense(id = 2, amount = 13_000, date = "2026-08-05", categoryId = 2)
        )
        val status = BudgetCalculator.statusFor(budget(categoryId = null, amount = 40_000), snap)

        assertEquals(rupees(15_000), status.spent)
        assertEquals("Everything", status.categoryName)
    }

    @Test
    fun `a card purchase counts against the budget on the day it was made`() {
        // The forecast deliberately ignores card spending as cash; a budget must not.
        val snap = snapshot(
            expense(
                id = 1, amount = 3_000, date = "2026-08-03",
                categoryId = 1, method = PaymentMethod.CREDIT_CARD
            )
        )
        val status = BudgetCalculator.statusFor(budget(categoryId = 1, amount = 8_000), snap)

        assertEquals(
            "a budget limits spending, however it was paid for",
            rupees(3_000),
            status.spent
        )
    }

    @Test
    fun `an expense from a scheduled bill still counts against its category budget`() {
        val snap = snapshot(
            expense(
                id = 1, amount = 13_000, date = "2026-08-05", categoryId = 2,
                linkType = com.moneyplanner.domain.model.ExpenseLinkType.RECURRING_BILL,
                linkId = 1
            )
        )
        val status = BudgetCalculator.statusFor(budget(categoryId = 2, amount = 13_000), snap)
        assertEquals(rupees(13_000), status.spent)
    }

    @Test
    fun `reports being over and by how much`() {
        val snap = snapshot(expense(id = 1, amount = 9_500, date = "2026-08-02", categoryId = 1))
        val status = BudgetCalculator.statusFor(budget(categoryId = 1, amount = 8_000), snap)

        assertEquals(BudgetState.OVER, status.state)
        assertEquals(rupees(1_500), status.overspendAmount)
        assertEquals(rupees(0), status.remaining)
        assertTrue(status.isOver)
        assertEquals("Over by ₹1,500", status.summary())
    }

    @Test
    fun `flags being near the limit before it is breached`() {
        val snap = snapshot(expense(id = 1, amount = 6_800, date = "2026-08-02", categoryId = 1))
        val status = BudgetCalculator.statusFor(budget(categoryId = 1, amount = 8_000), snap)

        assertEquals(BudgetState.NEAR_LIMIT, status.state)
        assertEquals(85, status.usedPercent)
    }

    @Test
    fun `warns when the pace will breach the limit even though it has not yet`() {
        // 4,000 spent over the first 10 days projects to ~12,400 across August.
        val snap = snapshot(expense(id = 1, amount = 4_000, date = "2026-08-02", categoryId = 1))
        val status = BudgetCalculator.statusFor(budget(categoryId = 1, amount = 8_000), snap)

        assertEquals(BudgetState.PROJECTED_OVER, status.state)
        assertEquals(rupees(12_400), status.projectedSpend)
        assertTrue(status.projectedSpend > status.budget.amount)
    }

    @Test
    fun `is on track when neither the total nor the pace is a problem`() {
        val snap = snapshot(expense(id = 1, amount = 1_000, date = "2026-08-02", categoryId = 1))
        val status = BudgetCalculator.statusFor(budget(categoryId = 1, amount = 8_000), snap)
        assertEquals(BudgetState.ON_TRACK, status.state)
    }

    @Test
    fun `spends nothing when there are no expenses`() {
        val status = BudgetCalculator.statusFor(budget(categoryId = 1, amount = 8_000), snapshot())
        assertEquals(rupees(0), status.spent)
        assertEquals(rupees(8_000), status.remaining)
        assertEquals(BudgetState.ON_TRACK, status.state)
    }

    @Test
    fun `daily allowance spreads what is left over the days remaining`() {
        val snap = snapshot(expense(id = 1, amount = 4_000, date = "2026-08-02", categoryId = 1))
        val status = BudgetCalculator.statusFor(budget(categoryId = 1, amount = 8_000), snap)

        // 4,000 left across 10 August to 31 August inclusive is 22 days.
        assertEquals(rupees(4_000).divideRounded(22), status.dailyAllowanceLeft)
    }

    @Test
    fun `ignores expenses from other months`() {
        val snap = snapshot(
            expense(id = 1, amount = 5_000, date = "2026-07-15", categoryId = 1),
            expense(id = 2, amount = 1_000, date = "2026-08-02", categoryId = 1)
        )
        val status = BudgetCalculator.statusFor(budget(categoryId = 1, amount = 8_000), snap)
        assertEquals(rupees(1_000), status.spent)
    }

    @Test
    fun `all statuses put the overall budget first`() {
        val snap = snapshot(
            expense(id = 1, amount = 7_000, date = "2026-08-02", categoryId = 1)
        ).copy(
            budgets = listOf(
                budget(id = 1, categoryId = 1, amount = 8_000),
                budget(id = 2, categoryId = null, amount = 40_000)
            )
        )
        val statuses = BudgetCalculator.allStatuses(snap)
        assertEquals(2, statuses.size)
        assertTrue(statuses.first().budget.isOverall)
    }

    @Test
    fun `inactive budgets are left out`() {
        val snap = snapshot().copy(
            budgets = listOf(budget(categoryId = 1, amount = 8_000).copy(isActive = false))
        )
        assertTrue(BudgetCalculator.allStatuses(snap).isEmpty())
    }
}

/**
 * The runway projection exists to catch the mid-month squeeze that a month-end figure
 * hides, so the tests focus on exactly that case.
 */
class RunwayCalculatorTest {

    @Test
    fun `finds the day the balance goes negative`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-01"),
            accounts = listOf(account(opening = 10_000)),
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 5))
        )
        val projection = RunwayCalculator.project(snap, monthsAhead = 1)

        assertTrue(projection.hasShortfall)
        assertEquals(date("2026-08-05"), projection.firstShortfallDate)
        assertEquals(4L, projection.daysUntilShortfall(snap.today))
    }

    @Test
    fun `reports no shortfall when the money holds`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-01"),
            accounts = listOf(account(opening = 100_000)),
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 5))
        )
        val projection = RunwayCalculator.project(snap, monthsAhead = 1)

        assertFalse(projection.hasShortfall)
        assertNull(projection.firstShortfallDate)
        assertNull(projection.headline(snap.today))
    }

    @Test
    fun `catches a mid month squeeze that the month end figure hides`() {
        // Rent and an EMI land before the salary arrives on the 28th, so the month
        // closes fine while the middle of it does not.
        val snap = FinancialSnapshot(
            today = date("2026-08-01"),
            accounts = listOf(account(opening = 15_000)),
            incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 28)),
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 5)),
            emis = listOf(
                emi(id = 1, emiAmount = 4_500, firstDueDate = "2026-08-07", totalInstallments = 12)
            )
        )
        val projection = RunwayCalculator.project(snap, monthsAhead = 1)
        val monthEnd = projection.days.last().closingBalance

        assertTrue("the month itself ends comfortably", monthEnd.isPositive)
        assertTrue("but there is a squeeze inside it", projection.hasShortfall)
        assertEquals(date("2026-08-07"), projection.firstShortfallDate)
    }

    @Test
    fun `flags dipping below the comfort floor before going negative`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-01"),
            accounts = listOf(account(opening = 15_000)),
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 5))
        )
        val projection = RunwayCalculator.project(
            snap, monthsAhead = 1, comfortFloor = rupees(5_000)
        )

        assertFalse(projection.hasShortfall)
        assertTrue(projection.dipsBelowFloor)
        assertEquals(date("2026-08-05"), projection.firstBelowFloorDate)
        assertNotNull(projection.headline(snap.today))
    }

    @Test
    fun `identifies the lowest point of the projection`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-01"),
            accounts = listOf(account(opening = 50_000)),
            incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 28)),
            bills = listOf(bill(id = 1, name = "Rent", amount = 40_000, dueDay = 10))
        )
        val projection = RunwayCalculator.project(snap, monthsAhead = 1)
        val lowest = projection.lowestPoint

        assertNotNull(lowest)
        assertTrue(
            "the low point must fall between the rent and the salary",
            !lowest!!.date.isBefore(date("2026-08-10")) &&
                lowest.date.isBefore(date("2026-08-28"))
        )
    }

    @Test
    fun `covers every day of the requested horizon`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-15"),
            accounts = listOf(account(opening = 50_000))
        )
        val projection = RunwayCalculator.project(snap, monthsAhead = 2)

        assertEquals(date("2026-08-15"), projection.days.first().date)
        assertEquals(date("2026-09-30"), projection.days.last().date)
    }

    @Test
    fun `is deterministic`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-01"),
            accounts = listOf(account(opening = 15_000)),
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 5))
        )
        val first = RunwayCalculator.project(snap, monthsAhead = 2)
        repeat(3) {
            val again = RunwayCalculator.project(snap, monthsAhead = 2)
            assertEquals(first.firstShortfallDate, again.firstShortfallDate)
            assertEquals(first.days.size, again.days.size)
        }
    }
}
