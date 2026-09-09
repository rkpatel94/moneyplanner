package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.FinancialSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * A month of dated money, both what happened and what is still expected.
 *
 * The calendar used to be built from the forecast alone, which projects forward from the
 * current month. Any earlier month therefore came back empty, and the screen said "nothing
 * is scheduled" about a month that plainly had spending in it. Saying nothing happened is
 * worse than saying nothing at all: the user has no reason to doubt it.
 *
 * So a day carries two different kinds of thing and keeps them apart:
 *
 *  - what **happened**, taken from the records, which is all a past day can hold;
 *  - what is **expected**, taken from the forecast, which is all a future day can hold.
 *
 * The current month holds both, and that is the point of it. There is no double counting:
 * a bill that has been paid leaves the forecast and appears as the expense it created,
 * which is the same rule the projection is built on.
 */
object CalendarCalculator {

    fun entriesFor(snapshot: FinancialSnapshot, month: YearMonth): List<CalendarEntry> {
        val recorded = RecentActivityCalculator.movementsIn(snapshot, month).map { movement ->
            CalendarEntry(
                date = movement.date,
                title = movement.title,
                subtitle = movement.subtitle,
                amount = movement.amount,
                direction = movement.direction,
                isScheduled = false,
                isOverdue = false
            )
        }

        val scheduled = scheduledIn(snapshot, month).map { item ->
            CalendarEntry(
                date = item.date,
                title = item.title,
                subtitle = item.subtitle,
                amount = item.amount,
                direction = if (item.isInflow) ActivityDirection.IN else ActivityDirection.OUT,
                isScheduled = true,
                // Still expected, but its day has been and gone. Marked rather than shown
                // as though it were ordinary, because a rent due on the 5th displayed the
                // same way on the 9th tells the user nothing is wrong.
                isOverdue = item.date.isBefore(snapshot.today)
            )
        }

        return (recorded + scheduled).sortedWith(
            compareBy<CalendarEntry> { it.date }
                // What happened comes before what is merely expected on the same day.
                .thenBy { it.isScheduled }
                .thenByDescending { it.amount.paise }
        )
    }

    /**
     * The forecast items falling in [month].
     *
     * A month before the current one has no forecast at all, which is correct rather than
     * a gap: nothing can still be expected in a month that has finished.
     */
    private fun scheduledIn(snapshot: FinancialSnapshot, month: YearMonth): List<ForecastItem> {
        if (month.isBefore(snapshot.currentMonth)) return emptyList()

        val monthsAhead = ChronoUnit.MONTHS.between(snapshot.currentMonth, month).toInt()
        return ForecastCalculator
            .forecast(snapshot, (monthsAhead + 1).coerceAtLeast(1))
            .firstOrNull { it.month == month }
            ?.items
            .orEmpty()
    }

    fun byDate(snapshot: FinancialSnapshot, month: YearMonth): Map<LocalDate, List<CalendarEntry>> =
        entriesFor(snapshot, month).groupBy { it.date }
}

/**
 * One dated line on the calendar.
 *
 * [isScheduled] is the distinction that matters: a payment that has left the account and
 * one that is merely due look identical on a grid, and treating them the same is how
 * somebody concludes a bill is paid when it is not.
 */
data class CalendarEntry(
    val date: LocalDate,
    val title: String,
    val subtitle: String,
    val amount: Money,
    val direction: ActivityDirection,
    val isScheduled: Boolean,
    val isOverdue: Boolean
) {
    val isInflow: Boolean get() = direction == ActivityDirection.IN
    val isOutflow: Boolean get() = direction == ActivityDirection.OUT

    /** Neither in nor out: a transfer or money set aside changes no total. */
    val isNeutral: Boolean get() = direction == ActivityDirection.NEUTRAL
}
