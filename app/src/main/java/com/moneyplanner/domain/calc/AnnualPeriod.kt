package com.moneyplanner.domain.calc

import com.moneyplanner.domain.model.AnnualExpense
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Which year's occurrence of a yearly expense is the live one.
 *
 * One that fell due in the last few months is still the live one, whether paid or overdue;
 * otherwise it is the next one to come. Keying this on the calendar year marked a premium
 * due on 5 January, paid on 28 December, against the year about to end, and the real one
 * stayed unpaid and was reminded about.
 */
object AnnualPeriod {

    /** How long after its due date an occurrence is still the one being talked about. */
    const val RECENT_DAYS = 120L

    fun dueIn(expense: AnnualExpense, year: Int): LocalDate =
        LocalDate.of(
            year,
            expense.dueMonth,
            minOf(expense.dueDayOfMonth, YearMonth.of(year, expense.dueMonth).lengthOfMonth())
        )

    fun focusFor(expense: AnnualExpense, today: LocalDate): AnnualFocus {
        val recent = listOf(today.year, today.year - 1)
            .map { it to dueIn(expense, it) }
            .filter { (_, due) -> due.isBefore(today) && ChronoUnit.DAYS.between(due, today) <= RECENT_DAYS }
            .maxByOrNull { (_, due) -> due }
        val (year, due) = recent
            ?: listOf(today.year, today.year + 1).map { it to dueIn(expense, it) }.first { (_, d) -> !d.isBefore(today) }
        return AnnualFocus(year = year, dueDate = due)
    }
}

data class AnnualFocus(val year: Int, val dueDate: LocalDate)
