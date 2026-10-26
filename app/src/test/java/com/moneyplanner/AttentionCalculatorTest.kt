package com.moneyplanner

import com.moneyplanner.domain.calc.AttentionCalculator
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.LedgerDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the dashboard interrupts somebody for.
 *
 * The rules here are about restraint as much as detection. Flagging something the user
 * cannot act on, or cannot yet have got right, teaches them to scroll past the whole
 * section, and then the one thing that mattered goes unread too.
 */
class AttentionCalculatorTest {

    @Test
    fun `a clean set of records raises nothing`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 50_000)),
            expenses = listOf(expense(id = 1, amount = 500, date = "2026-09-05").assignedTo(1))
        )

        val summary = AttentionCalculator.analyse(snapshot, overdue = emptyList())

        assertTrue(summary.isEmpty)
        assertEquals(0, summary.actionCount)
    }

    /**
     * The rule that stops the app scolding a new user.
     *
     * Before any account exists, every expense is unassigned. That is the setup not having
     * happened yet, not a mistake, and an accusatory card on day one is how somebody
     * decides the app is nagging them.
     */
    @Test
    fun `unassigned expenses are not raised before any account exists`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = emptyList(),
            expenses = listOf(
                expense(id = 1, amount = 500, date = "2026-09-05"),
                expense(id = 2, amount = 900, date = "2026-09-06")
            )
        )

        val summary = AttentionCalculator.analyse(snapshot, overdue = emptyList())

        assertFalse(summary.hasUnassigned)
        assertTrue(summary.isEmpty)
    }

    @Test
    fun `once an account exists unassigned expenses are counted and totalled`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 50_000)),
            expenses = listOf(
                expense(id = 1, amount = 500, date = "2026-09-05"),
                expense(id = 2, amount = 900, date = "2026-09-06"),
                expense(id = 3, amount = 700, date = "2026-09-07").assignedTo(1)
            )
        )

        val summary = AttentionCalculator.analyse(snapshot, overdue = emptyList())

        assertTrue(summary.hasUnassigned)
        assertEquals(2, summary.unassignedCount)
        assertEquals(rupees(1_400), summary.unassignedTotal)
    }

    /** A card purchase has a card, not a bank account. It is attributed, so it is fine. */
    @Test
    fun `a card expense is not unassigned`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 50_000)),
            expenses = listOf(expense(id = 1, amount = 500, date = "2026-09-05").onCard(7))
        )

        val summary = AttentionCalculator.analyse(snapshot, overdue = emptyList())

        assertFalse(summary.hasUnassigned)
    }

    /**
     * Many unassigned expenses are one job, so they count once.
     *
     * "43 things need attention" reads as a disaster when the work is one screen and one
     * sitting. The count is a measure of effort, not of records.
     */
    @Test
    fun `every unassigned expense together counts as a single action`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 50_000)),
            expenses = (1L..12L).map { expense(id = it, amount = 100, date = "2026-09-05") }
        )

        val summary = AttentionCalculator.analyse(snapshot, overdue = emptyList())

        assertEquals(12, summary.unassignedCount)
        assertEquals(1, summary.actionCount)
    }

    @Test
    fun `overdue money is listed oldest first and totalled on its own`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 50_000)),
            people = listOf(person(id = 1)),
            ledgerEntries = listOf(
                ledgerEntry(
                    id = 1,
                    personId = 1,
                    amount = 3_500,
                    direction = LedgerDirection.THEY_OWE_ME,
                    date = "2026-06-01",
                    expectedDate = "2026-07-01"
                )
            ),
            expenses = listOf(expense(id = 1, amount = 400, date = "2026-09-01"))
        )

        val overdue = ForecastCalculator.forecast(snapshot, 3)
            .flatMap { it.items }
            .filter { it.isOverdue }
        val summary = AttentionCalculator.analyse(snapshot, overdue)

        assertEquals(1, summary.overdue.size)
        assertEquals(rupees(3_500), summary.overdueTotal)

        // The unassigned expense is real money that was spent; it is simply unlabelled.
        // Adding it to an "overdue" figure would invent a debt that does not exist.
        assertEquals(rupees(3_500), summary.overdueTotal)
        assertEquals(2, summary.actionCount)
    }

    /**
     * Oldest due first, even when the oldest is shown on today's date.
     *
     * A late person balance is carried forward to today so it stays in view. Sorted by that
     * date it came after every bill that went overdue this month, and the card shows only
     * three rows, so the longest-standing debt was the one cut off.
     */
    @Test
    fun `a balance carried forward to today still sorts by when it was due`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 50_000)),
            people = listOf(person(id = 1)),
            ledgerEntries = listOf(
                ledgerEntry(
                    id = 1,
                    personId = 1,
                    amount = 5_000,
                    direction = LedgerDirection.I_OWE_THEM,
                    date = "2026-07-01",
                    expectedDate = "2026-08-01"
                )
            ),
            bills = listOf(
                bill(id = 1, name = "Rent", amount = 13_000, dueDay = 1),
                bill(id = 2, name = "Electricity", amount = 2_200, dueDay = 3),
                bill(id = 3, name = "Phone", amount = 800, dueDay = 5)
            )
        )

        val overdue = ForecastCalculator.forecast(snapshot, 1)
            .flatMap { it.items }
            .filter { it.isOverdue }
        val summary = AttentionCalculator.analyse(snapshot, overdue)

        assertEquals(
            listOf("Amit", "Rent", "Electricity", "Phone"),
            summary.overdue.map { it.title }
        )
        val carried = summary.overdue.first()
        assertEquals("shown on today's date to stay in view", date("2026-09-10"), carried.date)
        assertEquals("but due a month and more ago", date("2026-08-01"), carried.dueDate)
    }

    /** Money to pay and money to receive are each totalled, never netted. */
    @Test
    fun `late money owed and late money due are totalled apart`() {
        val snapshot = FinancialSnapshot(
            today = date("2026-09-10"),
            accounts = listOf(account(opening = 50_000)),
            people = listOf(person(id = 1)),
            ledgerEntries = listOf(
                ledgerEntry(
                    id = 1,
                    personId = 1,
                    amount = 3_000,
                    direction = LedgerDirection.THEY_OWE_ME,
                    date = "2026-07-01",
                    expectedDate = "2026-08-01"
                )
            ),
            bills = listOf(
                bill(id = 1, name = "Rent", amount = 13_000, dueDay = 1),
                bill(id = 2, name = "Electricity", amount = 2_200, dueDay = 3)
            )
        )

        val overdue = ForecastCalculator.forecast(snapshot, 1)
            .flatMap { it.items }
            .filter { it.isOverdue }
        val summary = AttentionCalculator.analyse(snapshot, overdue)

        assertEquals(rupees(15_200), summary.overdueToPay)
        assertEquals(rupees(3_000), summary.overdueToReceive)
    }
}
