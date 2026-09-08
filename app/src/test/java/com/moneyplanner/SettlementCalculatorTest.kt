package com.moneyplanner

import com.moneyplanner.domain.calc.SettlementCalculator
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.SettlementDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settlement engine must never duplicate or quietly drop a balance. These tests hold
 * it to the worked examples in the product brief and to the awkward cases around them.
 */
class SettlementCalculatorTest {

    @Test
    fun `a part payment leaves the remainder outstanding`() {
        // Amit owes 3,000 and pays 1,000, so 2,000 is still due.
        val entries = listOf(ledgerEntry(amount = 3_000))
        val settlements = listOf(settlement(amount = 1_000))

        assertEquals(rupees(2_000), SettlementCalculator.balanceFor(entries, settlements))
    }

    @Test
    fun `paying in full settles the balance exactly`() {
        val entries = listOf(ledgerEntry(amount = 3_000))
        val settlements = listOf(
            settlement(id = 1, amount = 1_000, date = "2026-08-10"),
            settlement(id = 2, amount = 2_000, date = "2026-08-20")
        )
        assertEquals(rupees(0), SettlementCalculator.balanceFor(entries, settlements))
    }

    @Test
    fun `money the user owes is held as a negative balance`() {
        val entries = listOf(
            ledgerEntry(amount = 2_500, direction = LedgerDirection.I_OWE_THEM)
        )
        val balance = SettlementCalculator.balanceFor(entries, emptyList())
        assertTrue(balance.isNegative)
        assertEquals(rupees(2_500), balance.abs())
    }

    @Test
    fun `repaying what the user owes moves the balance back towards zero`() {
        val entries = listOf(
            ledgerEntry(amount = 2_500, direction = LedgerDirection.I_OWE_THEM)
        )
        val settlements = listOf(
            settlement(amount = 1_000, direction = SettlementDirection.PAID_TO_THEM)
        )
        assertEquals(rupees(-1_500), SettlementCalculator.balanceFor(entries, settlements))
    }

    @Test
    fun `obligations in both directions net off against each other`() {
        val entries = listOf(
            ledgerEntry(id = 1, amount = 5_000, direction = LedgerDirection.THEY_OWE_ME),
            ledgerEntry(id = 2, amount = 2_000, direction = LedgerDirection.I_OWE_THEM)
        )
        assertEquals(rupees(3_000), SettlementCalculator.balanceFor(entries, emptyList()))
    }

    @Test
    fun `an overpayment carries the balance past zero rather than being absorbed`() {
        val entries = listOf(ledgerEntry(amount = 3_000))
        val settlements = listOf(settlement(amount = 4_000))

        val balance = SettlementCalculator.balanceFor(entries, settlements)
        assertEquals(
            "receiving more than was owed means the user now owes the difference",
            rupees(-1_000),
            balance
        )

        val allocation = SettlementCalculator.allocate(entries, settlements)
        assertTrue(allocation.hasOverpayment)
        assertEquals(rupees(1_000), allocation.unallocatedReceived)
    }

    @Test
    fun `settlements clear the oldest obligation first`() {
        val entries = listOf(
            ledgerEntry(id = 1, amount = 1_000, date = "2026-06-01", description = "June"),
            ledgerEntry(id = 2, amount = 2_000, date = "2026-07-01", description = "July")
        )
        val settlements = listOf(settlement(amount = 1_500, date = "2026-08-01"))

        val allocation = SettlementCalculator.allocate(entries, settlements)

        val june = allocation.rows.first { it.entry.id == 1L }
        val july = allocation.rows.first { it.entry.id == 2L }

        assertTrue(june.isFullySettled)
        assertEquals(rupees(0), june.outstandingAmount)
        assertEquals(rupees(500), july.settledAmount)
        assertEquals(rupees(1_500), july.outstandingAmount)
        assertFalse(july.isFullySettled)
    }

    @Test
    fun `allocation does not let a repayment cancel money owed the other way`() {
        val entries = listOf(
            ledgerEntry(id = 1, amount = 1_000, direction = LedgerDirection.THEY_OWE_ME),
            ledgerEntry(id = 2, amount = 1_000, direction = LedgerDirection.I_OWE_THEM)
        )
        // The user pays back what they owe. It must not touch what the friend owes them.
        val settlements = listOf(
            settlement(amount = 1_000, direction = SettlementDirection.PAID_TO_THEM)
        )
        val allocation = SettlementCalculator.allocate(entries, settlements)

        assertEquals(rupees(1_000), allocation.rows.first { it.entry.id == 1L }.outstandingAmount)
        assertEquals(rupees(0), allocation.rows.first { it.entry.id == 2L }.outstandingAmount)
    }

    @Test
    fun `allocation is stable when two entries share a date`() {
        val entries = listOf(
            ledgerEntry(id = 2, amount = 500, date = "2026-06-01"),
            ledgerEntry(id = 1, amount = 500, date = "2026-06-01")
        )
        val settlements = listOf(settlement(amount = 500))

        repeat(5) {
            val allocation = SettlementCalculator.allocate(entries, settlements)
            assertTrue(
                "the lower id must always be settled first so results never vary",
                allocation.rows.first { it.entry.id == 1L }.isFullySettled
            )
        }
    }

    @Test
    fun `deleting an obligation removes it from the balance`() {
        val entries = listOf(
            ledgerEntry(id = 1, amount = 3_000),
            ledgerEntry(id = 2, amount = 2_000)
        )
        assertEquals(rupees(5_000), SettlementCalculator.balanceFor(entries, emptyList()))

        val afterDelete = entries.filterNot { it.id == 2L }
        assertEquals(rupees(3_000), SettlementCalculator.balanceFor(afterDelete, emptyList()))
    }

    @Test
    fun `totals separate what is receivable from what is payable`() {
        val entries = listOf(
            ledgerEntry(id = 1, personId = 1, amount = 5_000),
            ledgerEntry(id = 2, personId = 2, amount = 2_500, direction = LedgerDirection.I_OWE_THEM)
        )
        assertEquals(
            rupees(5_000),
            SettlementCalculator.totalReceivable(entries, emptyList())
        )
        assertEquals(
            rupees(2_500),
            SettlementCalculator.totalPayable(entries, emptyList())
        )
    }

    @Test
    fun `a summary reports the running totals behind the balance`() {
        val people = listOf(person(1, "Amit"))
        val entries = listOf(
            ledgerEntry(id = 1, amount = 3_000, date = "2026-06-01"),
            ledgerEntry(id = 2, amount = 1_000, direction = LedgerDirection.I_OWE_THEM, date = "2026-06-05")
        )
        val settlements = listOf(settlement(amount = 500, date = "2026-07-01"))

        val summary = SettlementCalculator.summaries(people, entries, settlements).single()

        assertEquals(rupees(1_500), summary.balance)
        assertEquals(rupees(3_000), summary.totalTheyOwedMe)
        assertEquals(rupees(1_000), summary.totalIOwedThem)
        assertEquals(rupees(500), summary.totalReceived)
        assertTrue(summary.theyOweMe)
        assertEquals(date("2026-07-01"), summary.lastActivity)
    }

    @Test
    fun `a person with no records has a zero balance and is treated as settled`() {
        val summary = SettlementCalculator
            .summaries(listOf(person(1)), emptyList(), emptyList())
            .single()
        assertEquals(rupees(0), summary.balance)
        assertTrue(summary.isSettled)
    }

    /**
     * Editing a settlement has to be shown against the balance *without* it.
     *
     * The live balance already has the payment taken off, so offering that as "still to
     * pay" while the user edits the very payment that produced it describes a debt reduced
     * twice, and every "what would be left" line under it comes out wrong.
     */
    @Test
    fun `the balance excluding one settlement is the balance before it was applied`() {
        // I owe Amit 10,000 and have paid 1,000 of it.
        val entries = listOf(
            ledgerEntry(amount = 10_000, direction = LedgerDirection.I_OWE_THEM)
        )
        val paid = settlement(
            id = 7,
            amount = 1_000,
            direction = SettlementDirection.PAID_TO_THEM
        )

        val withIt = SettlementCalculator.balanceFor(entries, listOf(paid))
        val withoutIt = SettlementCalculator.balanceFor(
            entries,
            listOf(paid).filterNot { it.id == 7L }
        )

        assertEquals(rupees(-9_000), withIt)
        assertEquals(rupees(-10_000), withoutIt)
    }

    @Test
    fun `excluding one of several part payments leaves the others applied`() {
        val entries = listOf(
            ledgerEntry(amount = 10_000, direction = LedgerDirection.I_OWE_THEM)
        )
        val settlements = listOf(
            settlement(id = 1, amount = 1_000, direction = SettlementDirection.PAID_TO_THEM),
            settlement(id = 2, amount = 1_500, direction = SettlementDirection.PAID_TO_THEM),
            settlement(id = 3, amount = 500, direction = SettlementDirection.PAID_TO_THEM)
        )

        assertEquals(rupees(-7_000), SettlementCalculator.balanceFor(entries, settlements))

        // Editing the 1,500: the other two still count, so 8,500 is the figure to show.
        val others = settlements.filterNot { it.id == 2L }
        assertEquals(rupees(-8_500), SettlementCalculator.balanceFor(entries, others))
    }
}
