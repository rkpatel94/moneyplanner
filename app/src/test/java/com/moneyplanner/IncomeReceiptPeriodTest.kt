package com.moneyplanner

import com.moneyplanner.domain.calc.IncomeReceiptPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which month a receipt is stamped with, and when editing one is allowed to change it.
 *
 * The case that matters is a salary paid early: due on the 1st of September, credited on
 * the 31st of August, stamped "2026-09" because that is the month it was for. Opening that
 * receipt to fix a typo must not quietly hand it back to August, which would leave the
 * forecast expecting a salary that is already in the bank.
 */
class IncomeReceiptPeriodTest {

    @Test
    fun `a one-off receipt carries no month at all`() {
        assertNull(
            IncomeReceiptPeriod.resolve(
                isNewReceipt = true,
                sourceId = null,
                date = date("2026-08-14")
            )
        )
    }

    @Test
    fun `a new receipt for a regular income takes the month of its date`() {
        assertEquals(
            "2026-08",
            IncomeReceiptPeriod.resolve(
                isNewReceipt = true,
                sourceId = 1,
                date = date("2026-08-14")
            )
        )
    }

    @Test
    fun `editing a receipt without moving it keeps the month it already had`() {
        // Paid 31 August, but it was September's salary.
        assertEquals(
            "2026-09",
            IncomeReceiptPeriod.resolve(
                isNewReceipt = false,
                sourceId = 1,
                date = date("2026-08-31"),
                originalSourceId = 1,
                originalDate = date("2026-08-31"),
                originalPeriodKey = "2026-09"
            )
        )
    }

    @Test
    fun `re-dating a receipt into another month moves which month it settles`() {
        assertEquals(
            "2026-07",
            IncomeReceiptPeriod.resolve(
                isNewReceipt = false,
                sourceId = 1,
                date = date("2026-07-31"),
                originalSourceId = 1,
                originalDate = date("2026-08-31"),
                originalPeriodKey = "2026-09"
            )
        )
    }

    @Test
    fun `pointing a receipt at a different income restamps it from its date`() {
        assertEquals(
            "2026-08",
            IncomeReceiptPeriod.resolve(
                isNewReceipt = false,
                sourceId = 2,
                date = date("2026-08-31"),
                originalSourceId = 1,
                originalDate = date("2026-08-31"),
                originalPeriodKey = "2026-09"
            )
        )
    }

    @Test
    fun `detaching a receipt from a regular income clears its month`() {
        assertNull(
            IncomeReceiptPeriod.resolve(
                isNewReceipt = false,
                sourceId = null,
                date = date("2026-08-31"),
                originalSourceId = 1,
                originalDate = date("2026-08-31"),
                originalPeriodKey = "2026-09"
            )
        )
    }

    @Test
    fun `an older receipt that never carried a month is left without one`() {
        // Records written before the stamp existed have a null key. An edit that does not
        // move them must not invent a month they were never attributed to.
        assertNull(
            IncomeReceiptPeriod.resolve(
                isNewReceipt = false,
                sourceId = 1,
                date = date("2026-08-14"),
                originalSourceId = 1,
                originalDate = date("2026-08-14"),
                originalPeriodKey = null
            )
        )
    }
}
