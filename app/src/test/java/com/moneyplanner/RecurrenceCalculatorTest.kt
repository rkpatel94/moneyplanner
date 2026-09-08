package com.moneyplanner

import com.moneyplanner.domain.calc.RecurrenceCalculator
import com.moneyplanner.domain.model.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * The calendar rules that quietly break naive budgeting apps: short months, leap years
 * and schedules that do not line up with the January quarter.
 */
class RecurrenceCalculatorTest {

    @Test
    fun `a monthly schedule produces one date per month`() {
        val dates = RecurrenceCalculator.occurrencesIn(
            month = YearMonth.of(2026, 9),
            start = date("2026-01-01"),
            end = null,
            dayOfMonth = 5,
            frequency = Frequency.MONTHLY
        )
        assertEquals(listOf(date("2026-09-05")), dates)
    }

    @Test
    fun `the 31st falls back to the last day of a shorter month`() {
        val april = RecurrenceCalculator.occurrencesIn(
            YearMonth.of(2026, 4), date("2026-01-31"), null, 31, Frequency.MONTHLY
        )
        assertEquals(listOf(date("2026-04-30")), april)

        val may = RecurrenceCalculator.occurrencesIn(
            YearMonth.of(2026, 5), date("2026-01-31"), null, 31, Frequency.MONTHLY
        )
        assertEquals(
            "a schedule must return to the 31st once the month is long enough",
            listOf(date("2026-05-31")),
            may
        )
    }

    @Test
    fun `february is handled correctly in a common year and a leap year`() {
        val common = RecurrenceCalculator.occurrencesIn(
            YearMonth.of(2026, 2), date("2025-01-30"), null, 30, Frequency.MONTHLY
        )
        assertEquals(listOf(date("2026-02-28")), common)

        val leap = RecurrenceCalculator.occurrencesIn(
            YearMonth.of(2028, 2), date("2025-01-30"), null, 30, Frequency.MONTHLY
        )
        assertEquals(listOf(date("2028-02-29")), leap)
    }

    @Test
    fun `a schedule does not start before its own start date`() {
        val sameMonthButEarlier = RecurrenceCalculator.occurrencesIn(
            YearMonth.of(2026, 3), date("2026-03-20"), null, 5, Frequency.MONTHLY
        )
        assertTrue(sameMonthButEarlier.isEmpty())

        val sameMonthButLater = RecurrenceCalculator.occurrencesIn(
            YearMonth.of(2026, 3), date("2026-03-01"), null, 5, Frequency.MONTHLY
        )
        assertEquals(listOf(date("2026-03-05")), sameMonthButLater)
    }

    @Test
    fun `a schedule stops after its end date`() {
        val within = RecurrenceCalculator.occurrencesIn(
            YearMonth.of(2026, 6), date("2026-01-01"), date("2026-06-30"), 10, Frequency.MONTHLY
        )
        assertEquals(listOf(date("2026-06-10")), within)

        val after = RecurrenceCalculator.occurrencesIn(
            YearMonth.of(2026, 7), date("2026-01-01"), date("2026-06-30"), 10, Frequency.MONTHLY
        )
        assertTrue(after.isEmpty())
    }

    @Test
    fun `quarterly counts from its own start month rather than from january`() {
        val start = date("2026-02-10")
        fun hits(month: Int) = RecurrenceCalculator
            .occurrencesIn(YearMonth.of(2026, month), start, null, 10, Frequency.QUARTERLY)
            .isNotEmpty()

        assertTrue(hits(2))
        assertTrue(!hits(3))
        assertTrue(!hits(4))
        assertTrue(hits(5))
        assertTrue(hits(8))
        assertTrue(hits(11))
    }

    @Test
    fun `yearly repeats only in its anniversary month`() {
        val start = date("2026-03-15")
        assertTrue(
            RecurrenceCalculator
                .occurrencesIn(YearMonth.of(2027, 3), start, null, 15, Frequency.YEARLY)
                .isNotEmpty()
        )
        assertTrue(
            RecurrenceCalculator
                .occurrencesIn(YearMonth.of(2027, 4), start, null, 15, Frequency.YEARLY)
                .isEmpty()
        )
    }

    @Test
    fun `weekly produces every seventh day inside the month`() {
        val dates = RecurrenceCalculator.occurrencesIn(
            YearMonth.of(2026, 9), date("2026-09-01"), null, 1, Frequency.WEEKLY
        )
        assertEquals(
            listOf(
                date("2026-09-01"), date("2026-09-08"),
                date("2026-09-15"), date("2026-09-22"), date("2026-09-29")
            ),
            dates
        )
    }

    @Test
    fun `weekly stays aligned when the month starts mid cycle`() {
        val dates = RecurrenceCalculator.occurrencesIn(
            YearMonth.of(2026, 10), date("2026-09-01"), null, 1, Frequency.WEEKLY
        )
        assertEquals(date("2026-10-06"), dates.first())
        dates.zipWithNext().forEach { (a, b) ->
            assertEquals(7L, java.time.temporal.ChronoUnit.DAYS.between(a, b))
        }
    }

    @Test
    fun `next occurrence finds the following due date`() {
        val next = RecurrenceCalculator.nextOccurrenceOnOrAfter(
            from = date("2026-09-20"),
            start = date("2026-01-05"),
            end = null,
            dayOfMonth = 5,
            frequency = Frequency.MONTHLY
        )
        assertEquals(date("2026-10-05"), next)
    }

    @Test
    fun `next occurrence includes today when it is the due date`() {
        val next = RecurrenceCalculator.nextOccurrenceOnOrAfter(
            from = date("2026-09-05"),
            start = date("2026-01-05"),
            end = null,
            dayOfMonth = 5,
            frequency = Frequency.MONTHLY
        )
        assertEquals(date("2026-09-05"), next)
    }

    @Test
    fun `an ended schedule has no next occurrence`() {
        val next = RecurrenceCalculator.nextOccurrenceOnOrAfter(
            from = date("2026-09-01"),
            start = date("2025-01-05"),
            end = date("2026-06-05"),
            dayOfMonth = 5,
            frequency = Frequency.MONTHLY
        )
        assertNull(next)
    }

    @Test
    fun `occurrences between two dates respect both bounds`() {
        val dates = RecurrenceCalculator.occurrencesBetween(
            from = date("2026-09-10"),
            to = date("2026-12-01"),
            start = date("2026-01-05"),
            end = null,
            dayOfMonth = 5,
            frequency = Frequency.MONTHLY
        )
        assertEquals(listOf(date("2026-10-05"), date("2026-11-05")), dates)
    }
}
