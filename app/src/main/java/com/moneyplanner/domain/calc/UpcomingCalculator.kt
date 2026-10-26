package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import java.time.LocalDate

/**
 * What falls due in the next few days, and how much of it has to be paid.
 *
 * The dashboard lists only the first few of these, because a card that lists everything
 * stops being something a person can take in at a glance. The total is the reason this
 * lives in a calculator rather than in the screen: it has to be summed over every item in
 * the window, not over the rows that happened to fit. Summing the visible rows makes a busy
 * week read as a quiet one, which is exactly the week the figure exists for.
 *
 * Overdue items are left out. The dashboard already shows them in a card of their own with
 * their own total, and counting them here as well would show the same rupee twice.
 */
object UpcomingCalculator {

    /** A week, which is what the dashboard plans around day to day. */
    const val DEFAULT_DAYS = 7

    /**
     * Every scheduled item from [today] through the last of [days] calendar days, in date
     * order. Today is the first of those days, so a seven day window ends six days later.
     *
     * @param items forecast items in any order, usually every month of a projection
     *   flattened together. Overdue ones may be included; they are filtered out here.
     */
    fun window(
        items: List<ForecastItem>,
        today: LocalDate,
        days: Int = DEFAULT_DAYS
    ): UpcomingWindow {
        val span = days.coerceAtLeast(1)
        val lastDay = today.plusDays(span - 1L)
        val inWindow = items
            .filter {
                !it.isOverdue &&
                    !it.date.isBefore(today) &&
                    !it.date.isAfter(lastDay)
            }
            // The same order the forecast itself uses, so a salary and a bill on one day
            // appear the same way round here as everywhere else.
            .sortedWith(compareBy({ it.date }, { it.kind.ordinal }))
        return UpcomingWindow(days = span, lastDay = lastDay, items = inWindow)
    }
}

/** The items in one window and the figure the dashboard states about them. */
data class UpcomingWindow(
    /** How many calendar days the window covers, today included. */
    val days: Int,
    /** The final day included, so a label can name the date the total runs to. */
    val lastDay: LocalDate,
    /** Every item in the window, not only the ones a screen has room to show. */
    val items: List<ForecastItem>
) {
    /**
     * Money scheduled to leave within the window.
     *
     * Inflows are not netted off. A salary landing on the last day does not make a bill
     * due on the first any easier to pay, and a figure that let it would understate what
     * has to be found before then.
     */
    val committedOutflow: Money
        get() = items.filterNot { it.isInflow }.sumOfMoney { it.amount }

    val isEmpty: Boolean get() = items.isEmpty()
}
