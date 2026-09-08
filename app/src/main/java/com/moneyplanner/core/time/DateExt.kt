package com.moneyplanner.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Date helpers shared by the calculators and the UI.
 *
 * Everything here is built on java.time so that month lengths, leap years and
 * end-of-month rules are handled by the platform rather than by hand-rolled arithmetic.
 */
object DateUtil {

    private val dayMonthYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val dayMonth: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val monthYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val shortMonthYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)
    private val weekdayDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

    fun formatDate(date: LocalDate): String = date.format(dayMonthYear)
    fun formatDayMonth(date: LocalDate): String = date.format(dayMonth)
    fun formatWeekdayDate(date: LocalDate): String = date.format(weekdayDate)
    fun formatMonth(month: YearMonth): String = month.atDay(1).format(monthYear)
    fun formatMonthShort(month: YearMonth): String = month.atDay(1).format(shortMonthYear)

    /**
     * Resolves a "day of the month" rule against a specific month.
     *
     * A rent that is due on the 31st still has to resolve to a real date in February,
     * so the day is clamped to the last day the month actually has. This is the single
     * place that rule is implemented; no calculator hard-codes month lengths.
     */
    fun dayInMonth(month: YearMonth, dayOfMonth: Int): LocalDate {
        val safeDay = dayOfMonth.coerceIn(1, month.lengthOfMonth())
        return month.atDay(safeDay)
    }

    /** The same rule applied against the month containing [reference]. */
    fun dayInMonthOf(reference: LocalDate, dayOfMonth: Int): LocalDate =
        dayInMonth(YearMonth.from(reference), dayOfMonth)

    /**
     * Adds months while keeping the intended day-of-month rule.
     *
     * LocalDate.plusMonths clamps permanently: 31 Jan plus one month is 28 Feb, and
     * adding another month from there gives 28 Mar instead of 31 Mar. Passing the
     * original day through explicitly keeps a 31st-of-the-month schedule on the 31st
     * for every month that has one.
     */
    fun addMonthsKeepingDay(start: LocalDate, monthsToAdd: Long, intendedDay: Int): LocalDate {
        val targetMonth = YearMonth.from(start).plusMonths(monthsToAdd)
        return dayInMonth(targetMonth, intendedDay)
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Long =
        ChronoUnit.DAYS.between(from, to)

    fun monthsBetween(from: YearMonth, to: YearMonth): Long =
        ChronoUnit.MONTHS.between(from, to)

    /** Inclusive list of months from [start] to [end]. */
    fun monthRange(start: YearMonth, end: YearMonth): List<YearMonth> {
        if (end.isBefore(start)) return emptyList()
        val count = ChronoUnit.MONTHS.between(start, end).toInt()
        return (0..count).map { start.plusMonths(it.toLong()) }
    }

    /** Start of the week (Monday) containing [date]. */
    fun startOfWeek(date: LocalDate): LocalDate =
        date.minusDays(((date.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong())

    /**
     * A friendly relative description used in reminder text and due-date chips.
     */
    fun relativeDayLabel(target: LocalDate, today: LocalDate): String =
        when (val days = daysBetween(today, target)) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            in 2L..6L -> "In $days days"
            in -6L..-2L -> "${-days} days ago"
            else -> formatDate(target)
        }
}

fun LocalDate.toYearMonth(): YearMonth = YearMonth.from(this)

/** Stable key used for period bookkeeping, for example "2026-09". */
fun YearMonth.periodKey(): String = "%04d-%02d".format(year, monthValue)

fun parsePeriodKey(key: String): YearMonth? {
    val parts = key.split("-")
    if (parts.size != 2) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return YearMonth.of(year, month)
}
