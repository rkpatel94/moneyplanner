package com.moneyplanner

import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.calc.CalendarCalculator
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.ForecastItemKind
import com.moneyplanner.domain.calc.RunwayCalculator
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.SettlementDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * Money recorded for a later date is in neither today's balance nor, until this fix, the
 * forecast. A ₹50,000 purchase entered for the 25th left November projected ₹50,000 better
 * than it would be until the morning it landed.
 */
class ForecastRecordedAheadTest {

    // One completed month of everyday spending, so the estimate comes from history and a
    // future-dated expense cannot leak into it.
    private val history = listOf(expense(id = 1, amount = 9_000, date = "2026-10-10"))

    private fun base(today: String = "2026-11-16") = FinancialSnapshot(
        today = date(today),
        accounts = listOf(account(opening = 100_000, openingDate = "2026-10-01")),
        categories = listOf(category(1)),
        expenses = history,
        people = listOf(person(1, "Amit"))
    )

    private fun november(snapshot: FinancialSnapshot) = ForecastCalculator.forecast(snapshot, 2)[0]

    @Test
    fun `a cash expense dated later this month comes off the month's projection`() {
        val without = base()
        val with = without.copy(expenses = history + expense(id = 2, amount = 50_000, date = "2026-11-25"))

        assertEquals(
            "today's balance does not include it yet",
            BalanceCalculator.currentBalance(without),
            BalanceCalculator.currentBalance(with)
        )
        assertEquals(rupees(50_000), november(with).plannedOutflow)
        assertEquals(november(without).closingBalance - rupees(50_000), november(with).closingBalance)
        assertTrue(november(with).items.any { it.kind == ForecastItemKind.PLANNED && it.date == date("2026-11-25") })
    }

    @Test
    fun `the month closes the same whichever side of the date it is viewed from`() {
        val planned = base().copy(expenses = history + expense(id = 2, amount = 50_000, date = "2026-11-25"))
        val before = november(planned).closingBalance
        val after = november(planned.copy(today = date("2026-11-26"))).closingBalance

        // Only the everyday estimate for the days in between differs; the ₹50,000 is in both.
        val everydayDifference = november(planned).estimatedEverydaySpend -
            november(planned.copy(today = date("2026-11-26"))).estimatedEverydaySpend
        assertEquals(before + everydayDifference, after)
    }

    @Test
    fun `a card purchase dated ahead is not cash and is not projected as one`() {
        val snap = base().copy(
            expenses = history + expense(id = 2, amount = 5_000, date = "2026-11-25", method = PaymentMethod.CREDIT_CARD)
        )
        assertEquals(rupees(0), november(snap).plannedOutflow)
    }

    @Test
    fun `a repayment recorded for later counts as money coming in`() {
        val snap = base().copy(
            settlements = listOf(
                settlement(id = 1, amount = 2_000, direction = SettlementDirection.RECEIVED_FROM_THEM, date = "2026-11-28")
            )
        )
        assertEquals(rupees(2_000), november(snap).plannedInflow)
        assertTrue(november(snap).items.any { it.kind == ForecastItemKind.PLANNED_IN })
    }

    @Test
    fun `the calendar shows a future-dated expense once, as the record it is`() {
        val snap = base().copy(expenses = history + expense(id = 2, amount = 400, date = "2026-11-25"))
        val onTheDay = CalendarCalculator.entriesFor(snap, YearMonth.of(2026, 11))
            .filter { it.date == date("2026-11-25") }
        assertEquals(1, onTheDay.size)
    }

    @Test
    fun `the runway counts an overdue bill today instead of dropping it`() {
        val snap = base().copy(bills = listOf(bill(amount = 13_000, dueDay = 5)))
        val runway = RunwayCalculator.project(snap, monthsAhead = 1)
        val month = november(snap)

        assertTrue("the bill is in the month and overdue", month.items.any { it.isOverdue })
        // Everyday spending is spread a day at a time, so the two can differ by rounding.
        val gap = kotlin.math.abs((month.closingBalance - runway.days.last().closingBalance).paise)
        assertTrue("walking day by day ends where the month-end figure does, off by $gap paise", gap <= 100)
    }
}
