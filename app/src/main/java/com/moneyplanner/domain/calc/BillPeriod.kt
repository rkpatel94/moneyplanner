package com.moneyplanner.domain.calc

import com.moneyplanner.core.time.periodKey
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.RecurringBill
import java.time.LocalDate
import java.time.YearMonth

/**
 * Which occurrence of a bill a payment settles.
 *
 * A monthly, quarterly or yearly bill falls due at most once in a month, so the month names
 * the occurrence: "2026-11". A weekly bill falls due four or five times in the same month,
 * and keying its payments by month meant one tick on the Bills screen marked every week of
 * that month paid: five ₹500 weeks, one ₹500 payment, and ₹2,000 quietly gone from the
 * forecast. A weekly payment is therefore keyed by the date it was due instead.
 *
 * Kept in one place because the forecast, the Bills screen, the calendar and the payment
 * records all have to agree on it, or a paid week reads as unpaid in one and paid in another.
 */
object BillPeriod {

    fun keyFor(frequency: Frequency, occurrence: LocalDate): String =
        if (frequency.isWeekly) occurrence.toString() else YearMonth.from(occurrence).periodKey()

    fun keyFor(bill: RecurringBill, occurrence: LocalDate): String =
        keyFor(bill.frequency, occurrence)

    /** True when the occurrence of [bill] due on [occurrence] has a payment recorded. */
    fun isPaid(bill: RecurringBill, occurrence: LocalDate, paidKeys: Set<Pair<Long, String>>): Boolean =
        (bill.id to keyFor(bill, occurrence)) in paidKeys
}
