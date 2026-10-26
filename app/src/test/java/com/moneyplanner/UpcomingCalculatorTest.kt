package com.moneyplanner

import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.ForecastItem
import com.moneyplanner.domain.calc.ForecastItemKind
import com.moneyplanner.domain.calc.UpcomingCalculator
import com.moneyplanner.domain.model.FinancialSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard's "next 7 days" total.
 *
 * It used to be summed from the rows the card had room for, so a week with more than six
 * payments in it reported less than was actually due. These tests pin the figure to the
 * window itself: every outflow in it, nothing outside it, and nothing the attention card
 * is already counting.
 */
class UpcomingCalculatorTest {

    private val today = date("2026-09-10")

    private fun item(
        on: String,
        amount: Long,
        kind: ForecastItemKind = ForecastItemKind.BILL,
        overdue: Boolean = false,
        title: String = kind.label
    ) = ForecastItem(
        date = date(on),
        title = title,
        subtitle = "Due",
        amount = rupees(amount),
        kind = kind,
        sourceId = null,
        isEstimate = false,
        isOverdue = overdue
    )

    @Test
    fun `every payment in the window is counted even when the card cannot list them all`() {
        // Nine payments, more than the six rows the dashboard shows.
        val items = listOf(
            item("2026-09-10", 1_000),
            item("2026-09-11", 1_000),
            item("2026-09-11", 1_000),
            item("2026-09-12", 1_000),
            item("2026-09-13", 1_000),
            item("2026-09-14", 1_000),
            item("2026-09-15", 1_000),
            item("2026-09-16", 1_000),
            item("2026-09-16", 1_000)
        )

        val window = UpcomingCalculator.window(items, today, days = 7)

        assertEquals(9, window.items.size)
        assertEquals(rupees(9_000), window.committedOutflow)
    }

    /**
     * The same busy week, built the way the dashboard builds it: from a real projection
     * spanning several months, flattened together.
     */
    @Test
    fun `a busy week of real bills is totalled in full`() {
        val dueDays = listOf(10, 11, 12, 13, 14, 15, 16, 16)
        val snapshot = FinancialSnapshot(
            today = today,
            accounts = listOf(account(opening = 50_000)),
            bills = dueDays.mapIndexed { index, day ->
                bill(id = index + 1L, name = "Bill ${index + 1}", amount = 1_500, dueDay = day)
            }
        )

        val items = ForecastCalculator.forecast(snapshot, 3).flatMap { it.items }
        val window = UpcomingCalculator.window(items, snapshot.today)

        assertEquals(8, window.items.size)
        assertEquals(rupees(12_000), window.committedOutflow)
    }

    /**
     * Money arriving is still listed, because seeing the salary land is part of planning
     * the week, but it is not netted off. A salary on the last day does not pay a bill due
     * on the first.
     */
    @Test
    fun `money coming in is listed but not taken off the total`() {
        val items = listOf(
            item("2026-09-11", 13_000, title = "Rent"),
            item("2026-09-12", 55_000, kind = ForecastItemKind.INCOME, title = "Salary"),
            item("2026-09-13", 2_000, kind = ForecastItemKind.PERSON_INCOMING, title = "Amit"),
            item("2026-09-14", 1_500, kind = ForecastItemKind.PERSON_OUTGOING, title = "Neha")
        )

        val window = UpcomingCalculator.window(items, today, days = 7)

        assertEquals(4, window.items.size)
        assertEquals(rupees(14_500), window.committedOutflow)
    }

    /**
     * Overdue money already has its own card and its own total on the dashboard.
     *
     * The second overdue item is the case the date check alone would miss: an overdue
     * person entry is carried forward to today so it stays visible, which puts it inside
     * the window by date.
     */
    @Test
    fun `overdue money is left to the attention card`() {
        val items = listOf(
            item("2026-09-05", 4_500, kind = ForecastItemKind.EMI, overdue = true),
            item("2026-09-10", 3_000, kind = ForecastItemKind.PERSON_OUTGOING, overdue = true),
            item("2026-09-12", 2_200, title = "Electricity")
        )

        val window = UpcomingCalculator.window(items, today, days = 7)

        assertEquals(listOf("Electricity"), window.items.map { it.title })
        assertEquals(rupees(2_200), window.committedOutflow)
    }

    @Test
    fun `the window runs from today through its last day and no further`() {
        val items = listOf(
            // Given out of order, to show the result is in date order regardless.
            item("2026-09-17", 400, title = "Day after"),
            item("2026-09-16", 300, title = "Last day"),
            item("2026-09-10", 200, title = "Today"),
            // Before today but not flagged overdue, such as a salary that is late but
            // still expected. It is in the past, so it is not coming up.
            item("2026-09-09", 100, title = "Yesterday")
        )

        val window = UpcomingCalculator.window(items, today, days = 7)

        assertEquals(7, window.days)
        assertEquals(date("2026-09-16"), window.lastDay)
        assertEquals(listOf("Today", "Last day"), window.items.map { it.title })
        assertEquals(rupees(500), window.committedOutflow)
    }

    @Test
    fun `a window that crosses into next month takes items from both`() {
        val endOfMonth = date("2026-09-28")
        val items = listOf(
            item("2026-09-30", 1_000, title = "September"),
            item("2026-10-04", 2_000, title = "October"),
            item("2026-10-05", 4_000, title = "Too late")
        )

        val window = UpcomingCalculator.window(items, endOfMonth, days = 7)

        assertEquals(date("2026-10-04"), window.lastDay)
        assertEquals(listOf("September", "October"), window.items.map { it.title })
        assertEquals(rupees(3_000), window.committedOutflow)
    }

    @Test
    fun `nothing scheduled gives an empty window and a zero total`() {
        val window = UpcomingCalculator.window(emptyList(), today)

        assertTrue(window.isEmpty)
        assertTrue(window.committedOutflow.isZero)
    }
}
