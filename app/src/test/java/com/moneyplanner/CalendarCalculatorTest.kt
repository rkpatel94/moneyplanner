package com.moneyplanner

import com.moneyplanner.domain.calc.ActivityDirection
import com.moneyplanner.domain.calc.CalendarCalculator
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.FinancialSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * A month of dated money, both what happened and what is still expected.
 *
 * The calendar used to be built from the forecast alone, which projects forward from the
 * current month, so any earlier month came back empty and the screen said "nothing is
 * scheduled" about a month that plainly had spending in it. Claiming nothing happened is
 * worse than showing nothing: the user has no reason to doubt it.
 */
class CalendarCalculatorTest {

    private val september = YearMonth.of(2026, 9)
    private val august = YearMonth.of(2026, 8)

    private fun snapshot(
        expenses: List<com.moneyplanner.domain.model.Expense> = emptyList(),
        transfers: List<AccountTransfer> = emptyList(),
        bills: List<com.moneyplanner.domain.model.RecurringBill> = emptyList()
    ) = FinancialSnapshot(
        today = date("2026-09-09"),
        accounts = listOf(account(id = 1).copy(name = "Bank")),
        categories = listOf(category(id = 1, name = "Food")),
        expenses = expenses,
        transfers = transfers,
        bills = bills
    )

    @Test
    fun `a past month shows what actually happened`() {
        val snap = snapshot(
            expenses = listOf(expense(id = 1, amount = 1_500, date = "2026-08-20"))
        )
        val entries = CalendarCalculator.entriesFor(snap, august)

        assertEquals(1, entries.size)
        assertEquals(date("2026-08-20"), entries.first().date)
        // It happened, so it is not something still expected.
        assertFalse(entries.first().isScheduled)
    }

    @Test
    fun `a finished month expects nothing, however its schedules read`() {
        // A monthly bill would otherwise be projected into a month that has already been.
        val snap = snapshot(
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 5))
        )
        val entries = CalendarCalculator.entriesFor(snap, august)

        assertTrue(entries.none { it.isScheduled })
    }

    @Test
    fun `the current month holds both what happened and what is coming`() {
        val snap = snapshot(
            expenses = listOf(expense(id = 1, amount = 400, date = "2026-09-02")),
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 25))
        )
        val entries = CalendarCalculator.entriesFor(snap, september)

        assertTrue(entries.any { !it.isScheduled && it.date == date("2026-09-02") })
        assertTrue(entries.any { it.isScheduled && it.date == date("2026-09-25") })
    }

    @Test
    fun `an unpaid bill whose day has passed is marked overdue`() {
        // Rent due on the 5th, today is the 9th, nothing recorded against it.
        val snap = snapshot(
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 5))
        )
        val rent = CalendarCalculator.entriesFor(snap, september)
            .single { it.title == "Rent" }

        assertTrue(rent.isScheduled)
        assertTrue(rent.isOverdue)
    }

    @Test
    fun `a bill still ahead of its day is not overdue`() {
        val snap = snapshot(
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 25))
        )
        val rent = CalendarCalculator.entriesFor(snap, september)
            .single { it.title == "Rent" }

        assertTrue(rent.isScheduled)
        assertFalse(rent.isOverdue)
    }

    @Test
    fun `a transfer is neither money in nor money out`() {
        // It changes no total, so marking it as an outflow would misdescribe the month.
        val snap = snapshot(
            transfers = listOf(
                AccountTransfer(
                    id = 1,
                    fromAccountId = 1,
                    toAccountId = 1,
                    amount = rupees(5_000),
                    date = date("2026-09-03"),
                    notes = ""
                )
            )
        )
        val entry = CalendarCalculator.entriesFor(snap, september).single()

        assertEquals(ActivityDirection.NEUTRAL, entry.direction)
        assertFalse(entry.isInflow)
        assertFalse(entry.isOutflow)
    }

    @Test
    fun `entries run in date order with what happened before what is merely expected`() {
        val snap = snapshot(
            expenses = listOf(expense(id = 1, amount = 400, date = "2026-09-25")),
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 25))
        )
        val onTheDay = CalendarCalculator.entriesFor(snap, september)
            .filter { it.date == date("2026-09-25") }

        assertEquals(2, onTheDay.size)
        assertFalse(onTheDay.first().isScheduled)
        assertTrue(onTheDay.last().isScheduled)
    }

    @Test
    fun `a month with nothing in it returns nothing rather than failing`() {
        assertTrue(CalendarCalculator.entriesFor(snapshot(), august).isEmpty())
    }

    @Test
    fun `grouping by date keeps every entry`() {
        val snap = snapshot(
            expenses = listOf(
                expense(id = 1, amount = 400, date = "2026-09-02"),
                expense(id = 2, amount = 900, date = "2026-09-02"),
                expense(id = 3, amount = 100, date = "2026-09-04")
            )
        )
        val grouped = CalendarCalculator.byDate(snap, september)

        assertEquals(2, grouped.getValue(date("2026-09-02")).size)
        assertEquals(1, grouped.getValue(date("2026-09-04")).size)
    }

    @Test
    fun `a paid bill appears once, as the expense it created`() {
        // The forecast drops a settled period, so the calendar must not show both the
        // payment and a bill still expecting it.
        val snap = snapshot(
            expenses = listOf(
                expense(
                    id = 1,
                    amount = 13_000,
                    date = "2026-09-05",
                    linkType = com.moneyplanner.domain.model.ExpenseLinkType.RECURRING_BILL,
                    linkId = 1,
                    description = "Rent"
                ).copy(linkPeriodKey = "2026-09")
            ),
            bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 5))
        ).copy(
            billPayments = listOf(
                com.moneyplanner.domain.model.BillPayment(
                    id = 1,
                    billId = 1,
                    periodKey = "2026-09",
                    amount = rupees(13_000),
                    dueDate = date("2026-09-05"),
                    paidDate = date("2026-09-05"),
                    notes = ""
                )
            )
        )
        val rentEntries = CalendarCalculator.entriesFor(snap, september)
            .filter { it.title == "Rent" }

        assertEquals(1, rentEntries.size)
        assertFalse(rentEntries.single().isScheduled)
    }
}
