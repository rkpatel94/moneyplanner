package com.moneyplanner

import com.moneyplanner.domain.calc.elapsedComparison
import com.moneyplanner.domain.model.FinancialSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * Comparing a month still being lived through against the one before it.
 *
 * The whole point is refusing the flattering comparison. Nine days of September against the
 * whole of August always looks like a triumph, and an app that reports it that way is
 * lying by arithmetic.
 */
class ElapsedComparisonTest {

    private fun snapshotWith(
        today: String,
        vararg expenses: Pair<String, Long>
    ) = FinancialSnapshot(
        today = date(today),
        accounts = listOf(account(opening = 100_000)),
        expenses = expenses.mapIndexed { index, (on, amount) ->
            expense(id = index + 1L, amount = amount, date = on)
        }
    )

    @Test
    fun `only the elapsed part of the current month counts`() {
        val snapshot = snapshotWith(
            "2026-09-09",
            "2026-09-03" to 4_000,
            "2026-09-08" to 10_500,
            // Dated later this month. Real, but not yet spent as of the 9th, so counting
            // it would compare eleven days of September with nine of August.
            "2026-09-20" to 9_000
        )

        val result = elapsedComparison(snapshot, YearMonth.of(2026, 9))

        assertEquals(9, result.daysElapsed)
        assertEquals(30, result.daysInMonth)
        assertEquals(30, result.elapsedPercent)
        assertEquals(rupees(14_500), result.spentSoFar)
        assertTrue(result.isPartial)
    }

    @Test
    fun `the previous month is measured to the same day`() {
        val snapshot = snapshotWith(
            "2026-09-09",
            "2026-09-08" to 14_500,
            "2026-08-05" to 13_800,
            // After the 9th of August, so outside the comparable window.
            "2026-08-25" to 20_000
        )

        val result = elapsedComparison(snapshot, YearMonth.of(2026, 9))

        assertEquals(9, result.comparableDay)
        assertEquals(rupees(13_800), result.spentBySamePoint)
        assertEquals(rupees(700), result.difference)
        assertEquals(5, result.changePercent)
    }

    /**
     * The 31st of March has no counterpart in February.
     *
     * Rather than compare 31 days against 28 and report a fictional surge, the window
     * shrinks to what both months have and says that it did.
     */
    @Test
    fun `a shorter previous month shrinks the window and says so`() {
        val snapshot = snapshotWith(
            "2026-03-31",
            "2026-03-15" to 5_000,
            "2026-02-27" to 4_000
        )

        val result = elapsedComparison(snapshot, YearMonth.of(2026, 3))

        assertEquals(31, result.daysElapsed)
        assertEquals(28, result.comparableDay)
        assertTrue(result.windowShortened)
        assertEquals(rupees(4_000), result.spentBySamePoint)
    }

    /**
     * Shrinking only February's window still compares 31 days with 28.
     *
     * Something bought on the 30th of March has really gone, so it belongs in the month to
     * date, but February has no 30th to set it against. Counting it in the comparison would
     * report a 100% rise that is entirely the calendar's doing.
     */
    @Test
    fun `spending after the comparable day counts in the total but not in the comparison`() {
        val snapshot = snapshotWith(
            "2026-03-31",
            "2026-03-15" to 5_000,
            "2026-03-30" to 3_000,
            "2026-02-27" to 4_000
        )

        val result = elapsedComparison(snapshot, YearMonth.of(2026, 3))

        assertEquals(rupees(8_000), result.spentSoFar)
        assertEquals(rupees(5_000), result.spentToComparableDay)
        assertEquals(rupees(4_000), result.spentBySamePoint)
        assertEquals(rupees(1_000), result.difference)
        assertEquals(25, result.changePercent)
    }

    /** Where nothing had to shrink, the compared figure simply is the month to date. */
    @Test
    fun `the percentage is unchanged when the windows already match`() {
        val snapshot = snapshotWith(
            "2026-09-09",
            "2026-09-03" to 4_000,
            "2026-09-08" to 8_000,
            "2026-08-05" to 10_000
        )

        val result = elapsedComparison(snapshot, YearMonth.of(2026, 9))

        assertFalse(result.windowShortened)
        assertEquals(result.spentSoFar, result.spentToComparableDay)
        assertEquals(rupees(2_000), result.difference)
        assertEquals(20, result.changePercent)
    }

    /**
     * A longer previous month is measured to the same day, not to its end.
     *
     * On the 30th of April the 31st of March lies outside the window. Counting it would set
     * thirty days against thirty-one and make April look thriftier by a whole day.
     */
    @Test
    fun `a longer previous month is cut at the same day`() {
        val snapshot = snapshotWith(
            "2026-04-30",
            "2026-04-10" to 6_000,
            "2026-03-10" to 5_000,
            "2026-03-31" to 7_000
        )

        val result = elapsedComparison(snapshot, YearMonth.of(2026, 4))

        assertEquals(30, result.comparableDay)
        assertFalse(result.windowShortened)
        assertEquals(rupees(6_000), result.spentToComparableDay)
        assertEquals(rupees(5_000), result.spentBySamePoint)
        assertEquals(20, result.changePercent)
    }

    /**
     * A finished month gets the same treatment as one in progress.
     *
     * Looking back at March in April, all of March has elapsed, but February still only had
     * 28 days, so the last three days of March stay out of the comparison.
     */
    @Test
    fun `a finished month after a shorter one compares only the days both have`() {
        val snapshot = snapshotWith(
            "2026-04-10",
            "2026-03-20" to 6_000,
            "2026-03-31" to 9_000,
            "2026-02-20" to 5_000
        )

        val result = elapsedComparison(snapshot, YearMonth.of(2026, 3))

        assertEquals(31, result.daysElapsed)
        assertEquals(28, result.comparableDay)
        assertTrue(result.windowShortened)
        assertEquals(rupees(15_000), result.spentSoFar)
        assertEquals(rupees(6_000), result.spentToComparableDay)
        assertEquals(20, result.changePercent)
    }

    /** In a leap year February has a 29th, and the window runs to it in both months. */
    @Test
    fun `a leap year february keeps its 29th in the window`() {
        val snapshot = snapshotWith(
            "2028-03-31",
            "2028-03-29" to 3_000,
            "2028-03-30" to 2_000,
            "2028-02-29" to 4_000
        )

        val result = elapsedComparison(snapshot, YearMonth.of(2028, 3))

        assertEquals(29, result.comparableDay)
        assertEquals(rupees(5_000), result.spentSoFar)
        assertEquals(rupees(3_000), result.spentToComparableDay)
        assertEquals(rupees(4_000), result.spentBySamePoint)
        assertEquals(-25, result.changePercent)
    }

    /** A finished month has elapsed entirely, so it compares in full. */
    @Test
    fun `a past month is not treated as partial`() {
        val snapshot = snapshotWith(
            "2026-09-09",
            "2026-07-20" to 3_000,
            "2026-06-20" to 2_000
        )

        val result = elapsedComparison(snapshot, YearMonth.of(2026, 7))

        assertEquals(31, result.daysElapsed)
        assertFalse(result.isPartial)
        assertEquals(rupees(3_000), result.spentSoFar)
    }

    /** Nothing recorded last month means no honest percentage exists. */
    @Test
    fun `no history means no percentage rather than an infinite one`() {
        val snapshot = snapshotWith("2026-09-09", "2026-09-02" to 6_000)

        val result = elapsedComparison(snapshot, YearMonth.of(2026, 9))

        assertFalse(result.hasPrevious)
        assertNull(result.changePercent)
        assertEquals(rupees(6_000), result.spentSoFar)
    }

    @Test
    fun `a month that has not started yet has elapsed nothing`() {
        val snapshot = snapshotWith("2026-09-09", "2026-09-02" to 6_000)

        val result = elapsedComparison(snapshot, YearMonth.of(2026, 11))

        assertEquals(0, result.daysElapsed)
        assertEquals(rupees(0), result.spentSoFar)
        assertFalse(result.isPartial)
    }
}
