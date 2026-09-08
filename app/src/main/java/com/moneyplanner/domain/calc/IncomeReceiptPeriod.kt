package com.moneyplanner.domain.calc

import com.moneyplanner.core.time.periodKey
import java.time.LocalDate
import java.time.YearMonth

/**
 * Which scheduled month a recorded receipt satisfies.
 *
 * The stamp is what lets the forecast tell a salary that has landed from one still
 * expected. Getting it wrong in either direction is visible to the user: a receipt stamped
 * with no month leaves the forecast expecting money that has already arrived, and one
 * stamped with the wrong month cancels the wrong occurrence.
 *
 * The rule that is easy to miss is the one about editing. A salary due on the 1st and paid
 * on the 31st of the month before belongs to the month it was *for*, which is why
 * [RecurrenceCalculator.nearestOccurrence] exists and why "mark received" stamps a month
 * the date alone would not produce. If opening that receipt to fix a typo in the amount
 * recomputed the stamp from its date, the receipt would silently move to the previous month
 * and the forecast would start expecting a salary that is already in the bank.
 *
 * So an existing receipt keeps the stamp it has until the user actually moves it.
 */
object IncomeReceiptPeriod {

    /**
     * @param isNewReceipt true when recording a receipt rather than editing one.
     * @param sourceId the regular income this receipt belongs to, or null for a one-off.
     * @param date the date on the form now.
     * @param originalSourceId what the record said when it was loaded.
     * @param originalDate the date the record was loaded with.
     * @param originalPeriodKey the stamp the record was loaded with.
     */
    fun resolve(
        isNewReceipt: Boolean,
        sourceId: Long?,
        date: LocalDate,
        originalSourceId: Long? = null,
        originalDate: LocalDate? = null,
        originalPeriodKey: String? = null
    ): String? {
        // A one-off receipt satisfies no schedule, so it carries no stamp at all.
        if (sourceId == null) return null

        if (!isNewReceipt) {
            val unmoved = sourceId == originalSourceId && date == originalDate
            if (unmoved) return originalPeriodKey
        }

        // Either a new receipt, or one the user has genuinely re-dated or re-pointed at a
        // different income. Its own date is then the best statement of which month it is.
        return YearMonth.from(date).periodKey()
    }
}
