package com.moneyplanner.domain.calc

import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.Frequency
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Works out when a repeating obligation or income actually falls due.
 *
 * Every schedule in the app funnels through here, so the awkward calendar rules are
 * solved once: a payment set for the 31st resolves to the 28th or 29th in February and
 * returns to the 31st afterwards, leap years come from the platform calendar, and a
 * quarterly schedule counts its periods from its own start month rather than from
 * January.
 */
object RecurrenceCalculator {

    /**
     * The dates on which a schedule falls due inside [month].
     *
     * Returns an empty list when the month is outside the active window, which is how a
     * closed loan or an ended subscription silently drops out of the forecast.
     */
    fun occurrencesIn(
        month: YearMonth,
        start: LocalDate,
        end: LocalDate?,
        dayOfMonth: Int,
        frequency: Frequency
    ): List<LocalDate> {
        if (frequency.isWeekly) return weeklyOccurrencesIn(month, start, end)

        val startMonth = YearMonth.from(start)
        if (month.isBefore(startMonth)) return emptyList()

        val monthsSinceStart = ChronoUnit.MONTHS.between(startMonth, month)
        if (monthsSinceStart % frequency.monthsPerPeriod != 0L) return emptyList()

        val date = DateUtil.dayInMonth(month, dayOfMonth)

        // In the very first month the schedule only counts if the due day has not
        // already passed before the schedule began.
        if (date.isBefore(start)) return emptyList()
        if (end != null && date.isAfter(end)) return emptyList()

        return listOf(date)
    }

    private fun weeklyOccurrencesIn(
        month: YearMonth,
        start: LocalDate,
        end: LocalDate?
    ): List<LocalDate> {
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        if (start.isAfter(monthEnd)) return emptyList()
        if (end != null && end.isBefore(monthStart)) return emptyList()

        val daysFromStart = ChronoUnit.DAYS.between(start, monthStart)
        val firstOffset = if (daysFromStart <= 0) 0L else ((daysFromStart + 6) / 7) * 7

        val result = mutableListOf<LocalDate>()
        var candidate = start.plusDays(firstOffset)
        while (!candidate.isAfter(monthEnd)) {
            if (!candidate.isBefore(monthStart) && (end == null || !candidate.isAfter(end))) {
                result += candidate
            }
            candidate = candidate.plusDays(7)
        }
        return result
    }

    /** All occurrences between two dates, inclusive. Used by the calendar and reminders. */
    fun occurrencesBetween(
        from: LocalDate,
        to: LocalDate,
        start: LocalDate,
        end: LocalDate?,
        dayOfMonth: Int,
        frequency: Frequency
    ): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        return DateUtil.monthRange(YearMonth.from(from), YearMonth.from(to))
            .flatMap { occurrencesIn(it, start, end, dayOfMonth, frequency) }
            .filter { !it.isBefore(from) && !it.isAfter(to) }
    }

    /**
     * The scheduled occurrence that a payment made on [around] most likely satisfies.
     *
     * Money rarely arrives on the exact day it is due. A salary due on the 1st is often
     * credited on the last working day of the month before, and treating that as the
     * previous month's salary would leave the app still expecting one that is never
     * coming. Nearest wins, so a receipt is attributed to the due date it is closest to
     * rather than to whichever month the calendar happens to put it in.
     *
     * Returns null when the schedule has no occurrence anywhere near [around].
     */
    fun nearestOccurrence(
        around: LocalDate,
        start: LocalDate,
        end: LocalDate?,
        dayOfMonth: Int,
        frequency: Frequency
    ): LocalDate? {
        val month = YearMonth.from(around)
        return (-1..1)
            .flatMap { offset ->
                occurrencesIn(month.plusMonths(offset.toLong()), start, end, dayOfMonth, frequency)
            }
            .minByOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(it, around)) }
    }

    /**
     * The next date on or after [from] that this schedule falls due, or null when the
     * schedule has finished. The search is bounded so an ended schedule cannot loop.
     */
    fun nextOccurrenceOnOrAfter(
        from: LocalDate,
        start: LocalDate,
        end: LocalDate?,
        dayOfMonth: Int,
        frequency: Frequency,
        searchMonths: Int = 24
    ): LocalDate? {
        val firstMonth = YearMonth.from(maxOf(from, start))
        for (offset in 0..searchMonths) {
            val month = firstMonth.plusMonths(offset.toLong())
            val hit = occurrencesIn(month, start, end, dayOfMonth, frequency)
                .firstOrNull { !it.isBefore(from) }
            if (hit != null) return hit
            if (end != null && month.atDay(1).isAfter(end)) return null
        }
        return null
    }
}
