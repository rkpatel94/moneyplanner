package com.moneyplanner

import com.moneyplanner.domain.calc.ReportsCalculator
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.FinancialSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * The three comparisons a single month cannot make about itself.
 *
 * The care here is in not drawing a comparison that is not there. A category with no
 * history has no usual level to be above, and a month still running is not comparable with
 * the finished ones beside it. Both are reported as such rather than measured against a
 * zero, which would read as a collapse.
 */
class ReportsCalculatorTest {

    private val september = YearMonth.of(2026, 9)

    private fun snapshot(
        expenses: List<com.moneyplanner.domain.model.Expense> = emptyList(),
        incomes: List<com.moneyplanner.domain.model.IncomeTransaction> = emptyList(),
        transfers: List<AccountTransfer> = emptyList()
    ) = FinancialSnapshot(
        today = date("2026-09-15"),
        accounts = listOf(
            account(id = 1).copy(name = "Bank"),
            account(id = 2).copy(name = "Cash", type = AccountType.CASH)
        ),
        categories = listOf(category(id = 1, name = "Food"), category(id = 2, name = "Travel")),
        expenses = expenses,
        incomeTransactions = incomes,
        transfers = transfers
    )

    // ---- Trend ---------------------------------------------------------------------

    @Test
    fun `the trend runs oldest first and ends on the current month`() {
        val rows = ReportsCalculator.monthlyTrend(snapshot(), months = 6)

        assertEquals(6, rows.size)
        assertEquals(YearMonth.of(2026, 4), rows.first().month)
        assertEquals(september, rows.last().month)
    }

    @Test
    fun `each month reports what came in against what went out`() {
        val snap = snapshot(
            expenses = listOf(expense(id = 1, amount = 4_000, date = "2026-08-10")),
            incomes = listOf(incomeTransaction(id = 1, amount = 55_000, date = "2026-08-01"))
        )
        val august = ReportsCalculator.monthlyTrend(snap).first { it.month == YearMonth.of(2026, 8) }

        assertEquals(rupees(55_000), august.income)
        assertEquals(rupees(4_000), august.spent)
        assertEquals(rupees(51_000), august.net)
        assertFalse(august.isNegative)
    }

    @Test
    fun `a month that spent more than it earned reports a negative`() {
        val snap = snapshot(
            expenses = listOf(expense(id = 1, amount = 60_000, date = "2026-08-10")),
            incomes = listOf(incomeTransaction(id = 1, amount = 55_000, date = "2026-08-01"))
        )
        val august = ReportsCalculator.monthlyTrend(snap).first { it.month == YearMonth.of(2026, 8) }

        assertEquals(rupees(-5_000), august.net)
        assertTrue(august.isNegative)
    }

    @Test
    fun `only the current month is marked as still running`() {
        val rows = ReportsCalculator.monthlyTrend(snapshot())

        assertTrue(rows.last().isPartial)
        assertTrue(rows.dropLast(1).none { it.isPartial })
    }

    // ---- Category comparison -------------------------------------------------------

    @Test
    fun `a category is compared with its own average of completed months`() {
        val snap = snapshot(
            expenses = listOf(
                expense(id = 1, amount = 4_000, date = "2026-06-10", categoryId = 1),
                expense(id = 2, amount = 6_000, date = "2026-07-10", categoryId = 1),
                expense(id = 3, amount = 8_000, date = "2026-08-10", categoryId = 1),
                expense(id = 4, amount = 9_000, date = "2026-09-10", categoryId = 1)
            )
        )
        val row = ReportsCalculator.categoryComparison(snap, september)
            .single { it.categoryId == 1L }

        // June, July and August average 6,000. September is 9,000, half again as much.
        assertEquals(rupees(9_000), row.thisMonth)
        assertEquals(rupees(6_000), row.baseline)
        assertEquals(50, row.changePercent)
        assertTrue(row.isUp)
    }

    @Test
    fun `the current month is kept out of its own baseline`() {
        val snap = snapshot(
            expenses = listOf(
                expense(id = 1, amount = 4_000, date = "2026-08-10", categoryId = 1),
                expense(id = 2, amount = 100_000, date = "2026-09-10", categoryId = 1)
            )
        )
        val row = ReportsCalculator.categoryComparison(snap, september)
            .single { it.categoryId == 1L }

        // Were September inside the average, the spike would largely hide itself.
        assertEquals(rupees(4_000), row.baseline)
    }

    @Test
    fun `a category with no history reports no comparison rather than a false one`() {
        val snap = snapshot(
            expenses = listOf(expense(id = 1, amount = 9_000, date = "2026-09-10", categoryId = 1))
        )
        val row = ReportsCalculator.categoryComparison(snap, september)
            .single { it.categoryId == 1L }

        assertFalse(row.hasBaseline)
        assertNull(row.changePercent)
    }

    @Test
    fun `a category that stopped this month is still listed`() {
        // Spending that has gone to zero is as worth seeing as spending that started.
        val snap = snapshot(
            expenses = listOf(expense(id = 1, amount = 5_000, date = "2026-08-10", categoryId = 2))
        )
        val row = ReportsCalculator.categoryComparison(snap, september)
            .single { it.categoryId == 2L }

        assertEquals(rupees(0), row.thisMonth)
        assertEquals(rupees(5_000), row.baseline)
        assertEquals(-100, row.changePercent)
        assertTrue(row.isDown)
    }

    // ---- Account cash flow ---------------------------------------------------------

    @Test
    fun `each account reports what flowed through it`() {
        val snap = snapshot(
            expenses = listOf(
                expense(id = 1, amount = 4_000, date = "2026-09-10").copy(accountId = 1)
            ),
            incomes = listOf(
                incomeTransaction(id = 1, amount = 55_000, date = "2026-09-01").copy(accountId = 1)
            )
        )
        val bank = ReportsCalculator.accountCashFlow(snap, september, september)
            .single { it.account.id == 1L }

        assertEquals(rupees(55_000), bank.moneyIn)
        assertEquals(rupees(4_000), bank.moneyOut)
        assertEquals(rupees(51_000), bank.net)
    }

    @Test
    fun `a transfer leaves one account and arrives in the other`() {
        val snap = snapshot(
            transfers = listOf(
                AccountTransfer(
                    id = 1,
                    fromAccountId = 1,
                    toAccountId = 2,
                    amount = rupees(5_000),
                    date = date("2026-09-05"),
                    notes = ""
                )
            )
        )
        val flows = ReportsCalculator.accountCashFlow(snap, september, september)

        assertEquals(rupees(5_000), flows.single { it.account.id == 1L }.moneyOut)
        assertEquals(rupees(5_000), flows.single { it.account.id == 2L }.moneyIn)
        // It cancels across the accounts, not within one, so the household is unchanged.
        assertEquals(rupees(0), flows.sumOf { it.net.paise }.let { rupees(it / 100) })
    }

    @Test
    fun `activity outside the range is not counted`() {
        val snap = snapshot(
            expenses = listOf(
                expense(id = 1, amount = 4_000, date = "2026-07-10").copy(accountId = 1)
            )
        )
        val bank = ReportsCalculator.accountCashFlow(snap, september, september)
            .single { it.account.id == 1L }

        assertEquals(rupees(0), bank.moneyOut)
        assertFalse(bank.hasActivity)
    }
}
