package com.moneyplanner

import com.moneyplanner.domain.calc.AnnualPeriod
import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.calc.BillPeriod
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.BillPayment
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosedAccountAndWeeklyBillTest {

    // ---- Closed accounts ---------------------------------------------------------------

    private val hdfc = account(id = 1, opening = 50_000, openingDate = "2026-10-01").copy(name = "HDFC")
    private val sbi = account(id = 2, opening = 0, openingDate = "2026-10-01").copy(name = "SBI", sortOrder = 1)
    private val moved = AccountTransfer(
        id = 1, fromAccountId = 1, toAccountId = 2, amount = rupees(50_000), date = date("2026-11-01")
    )

    @Test
    fun `closing an emptied account leaves the total where it was`() {
        // The snapshot now carries closed accounts too, because their opening balances are
        // part of the history their transfers were written against.
        val closed = FinancialSnapshot(
            today = date("2026-11-16"),
            accounts = listOf(hdfc.copy(isArchived = true), sbi),
            transfers = listOf(moved)
        )
        val balances = BalanceCalculator.accountBalances(closed)

        assertEquals(rupees(50_000), balances.total)
        assertEquals(rupees(0), balances.unassigned)
        assertEquals("a closed account with nothing in it is not listed", listOf("SBI"), balances.rows.map { it.account.name })
    }

    @Test
    fun `a closed account that still holds money stays listed`() {
        val closed = FinancialSnapshot(
            today = date("2026-11-16"),
            accounts = listOf(hdfc.copy(isArchived = true), sbi)
        )
        val balances = BalanceCalculator.accountBalances(closed)
        assertEquals(listOf("HDFC", "SBI"), balances.rows.map { it.account.name })
        assertEquals(rupees(0), balances.unassigned)
    }

    // ---- Weekly bills ---------------------------------------------------------------------

    @Test
    fun `a weekly bill is paid week by week, and a monthly one month by month`() {
        assertEquals("2026-11-09", BillPeriod.keyFor(Frequency.WEEKLY, date("2026-11-09")))
        assertEquals("2026-11", BillPeriod.keyFor(Frequency.MONTHLY, date("2026-11-05")))
    }

    @Test
    fun `paying one week of a weekly bill leaves the other weeks in the forecast`() {
        val weekly = bill(amount = 500, startDate = "2026-11-02", frequency = Frequency.WEEKLY)
        val unpaid = FinancialSnapshot(
            today = date("2026-11-03"),
            accounts = listOf(account(opening = 100_000)),
            categories = listOf(category(1)),
            bills = listOf(weekly)
        )
        val paidOnce = unpaid.copy(
            billPayments = listOf(
                BillPayment(
                    id = 1, billId = 1, periodKey = BillPeriod.keyFor(weekly, date("2026-11-02")),
                    amount = rupees(500), dueDate = date("2026-11-02"), paidDate = date("2026-11-03"), notes = ""
                )
            ),
            expenses = listOf(
                expense(id = 1, amount = 500, date = "2026-11-03", linkType = ExpenseLinkType.RECURRING_BILL, linkId = 1)
            )
        )

        assertEquals(rupees(2_500), ForecastCalculator.forecast(unpaid, 1)[0].billsOutflow)
        assertEquals("four weeks still to pay", rupees(2_000), ForecastCalculator.forecast(paidOnce, 1)[0].billsOutflow)
    }

    // ---- Yearly expenses ------------------------------------------------------------------

    private val premium = annualExpense(dueMonth = 1, dueDay = 5)

    @Test
    fun `paying a January premium in late December pays next year's`() {
        assertEquals(2027, AnnualPeriod.focusFor(premium, date("2026-12-28")).year)
    }

    @Test
    fun `a premium a few weeks late is still this year's, and overdue`() {
        val focus = AnnualPeriod.focusFor(premium, date("2026-02-10"))
        assertEquals(2026, focus.year)
        assertTrue(focus.dueDate.isBefore(date("2026-02-10")))
    }

    @Test
    fun `a December premium paid in January is still last year's`() {
        val december = annualExpense(dueMonth = 12, dueDay = 20)
        assertEquals(2026, AnnualPeriod.focusFor(december, date("2027-01-10")).year)
    }
}
