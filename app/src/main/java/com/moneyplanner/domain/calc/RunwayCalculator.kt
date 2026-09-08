package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.FinancialSnapshot
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Walks the balance forward one day at a time to find the day money runs short.
 *
 * The monthly forecast answers "what will I have at the end of September". This answers
 * the more useful question: "when do I actually run out?" A month can close comfortably
 * and still have a week in the middle where rent, an EMI and a card bill land together
 * before the salary arrives — and a month-end figure hides that completely.
 *
 * Everything comes from the same dated items the forecast already produces, with everyday
 * spending spread evenly across the days rather than dropped on any particular one, since
 * nothing in the data says which day it will happen.
 */
object RunwayCalculator {

    /** Default cushion below which a balance is treated as uncomfortable rather than fine. */
    val DEFAULT_COMFORT_FLOOR: Money = Money.ofRupees(2_000)

    fun project(
        snapshot: FinancialSnapshot,
        monthsAhead: Int = 3,
        comfortFloor: Money = DEFAULT_COMFORT_FLOOR
    ): RunwayProjection {
        val forecast = ForecastCalculator.forecast(snapshot, monthsAhead)
        if (forecast.isEmpty()) return RunwayProjection(emptyList(), null, null, comfortFloor)

        val today = snapshot.today
        val lastDay = forecast.last().month.atEndOfMonth()

        // Everyday spending has no date of its own, so spread each month's estimate
        // evenly over that month's remaining days rather than inventing spikes.
        val dailySpendByMonth = forecast.associate { month ->
            val monthEnd = month.month.atEndOfMonth()
            val from = maxOf(today, month.month.atDay(1))
            val days = (ChronoUnit.DAYS.between(from, monthEnd).toInt() + 1).coerceAtLeast(1)
            month.month to month.estimatedEverydaySpend.divideRounded(days)
        }

        val itemsByDate = forecast
            .flatMap { it.items }
            .filter { !it.date.isBefore(today) }
            .groupBy { it.date }

        var balance = BalanceCalculator.currentBalance(snapshot)
        val days = mutableListOf<RunwayDay>()
        var shortfallDate: LocalDate? = null
        var belowFloorDate: LocalDate? = null

        var cursor = today
        while (!cursor.isAfter(lastDay)) {
            val dayItems = itemsByDate[cursor].orEmpty()
            val inflow = dayItems.filter { it.isInflow }
                .fold(Money.ZERO) { acc, item -> acc + item.amount }
            val outflow = dayItems.filterNot { it.isInflow }
                .fold(Money.ZERO) { acc, item -> acc + item.amount }
            val everyday = dailySpendByMonth[java.time.YearMonth.from(cursor)] ?: Money.ZERO

            balance = balance + inflow - outflow - everyday

            if (belowFloorDate == null && balance < comfortFloor) belowFloorDate = cursor
            if (shortfallDate == null && balance.isNegative) shortfallDate = cursor

            days += RunwayDay(
                date = cursor,
                closingBalance = balance,
                inflow = inflow,
                outflow = outflow,
                everydaySpend = everyday,
                items = dayItems
            )
            cursor = cursor.plusDays(1)
        }

        return RunwayProjection(
            days = days,
            firstShortfallDate = shortfallDate,
            firstBelowFloorDate = belowFloorDate,
            comfortFloor = comfortFloor
        )
    }
}

data class RunwayDay(
    val date: LocalDate,
    val closingBalance: Money,
    val inflow: Money,
    val outflow: Money,
    val everydaySpend: Money,
    val items: List<ForecastItem>
)

data class RunwayProjection(
    val days: List<RunwayDay>,
    /** The first day the balance is projected to go negative, if it ever does. */
    val firstShortfallDate: LocalDate?,
    /** The first day it dips below the comfort floor, which usually comes earlier. */
    val firstBelowFloorDate: LocalDate?,
    val comfortFloor: Money
) {
    val lowestPoint: RunwayDay? get() = days.minByOrNull { it.closingBalance.paise }

    val hasShortfall: Boolean get() = firstShortfallDate != null
    val dipsBelowFloor: Boolean get() = firstBelowFloorDate != null

    /** Days from today until money runs out, or null when it does not. */
    fun daysUntilShortfall(today: LocalDate): Long? =
        firstShortfallDate?.let { ChronoUnit.DAYS.between(today, it) }

    /**
     * The one sentence worth putting on the dashboard. Deliberately reports the comfort
     * dip as well as the shortfall, because "you get uncomfortably low on the 23rd" is
     * more actionable than waiting until the balance is actually negative.
     */
    fun headline(today: LocalDate): String? {
        val shortfall = firstShortfallDate
        val floor = firstBelowFloorDate
        return when {
            shortfall != null -> {
                val days = ChronoUnit.DAYS.between(today, shortfall)
                when {
                    days <= 0L -> "Your balance is already below zero"
                    days == 1L -> "Money runs out tomorrow"
                    else -> "Money runs out in $days days, on " +
                        com.moneyplanner.core.time.DateUtil.formatDayMonth(shortfall)
                }
            }
            floor != null -> "Balance dips low around " +
                com.moneyplanner.core.time.DateUtil.formatDayMonth(floor)
            else -> null
        }
    }
}
