package com.moneyplanner

import com.moneyplanner.data.prefs.AppSettings
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.ForecastItem
import com.moneyplanner.domain.calc.ForecastItemKind
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.work.ReminderSelection
import com.moneyplanner.work.delayUntilNextCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When the daily reminder check runs, and what it reminds about.
 *
 * The check used to be periodic work, which keeps counting from its first run, so moving
 * the reminder hour did nothing once it had run. It is now a one-time request timed for
 * the next occurrence of the hour, so the timing is a calculation worth pinning down.
 *
 * It also never mentioned anything overdue. A missed EMI or bill keeps the date it was
 * due, which is before today, and the reminder window starts today, so the payment most
 * likely to cost a late fee was the one thing guaranteed never to be reminded about.
 */
class ReminderSchedulingTest {

    // ---- When it runs --------------------------------------------------------------

    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun at(dateTime: String, zone: ZoneId = kolkata): ZonedDateTime =
        LocalDateTime.parse(dateTime).atZone(zone)

    @Test
    fun `before the hour, the check is later the same day`() {
        assertEquals(
            Duration.ofHours(2).plusMinutes(30),
            delayUntilNextCheck(at("2026-11-16T06:30"), hourOfDay = 9)
        )
    }

    @Test
    fun `after the hour, the check is the next day`() {
        assertEquals(
            Duration.ofHours(23),
            delayUntilNextCheck(at("2026-11-16T10:00"), hourOfDay = 9)
        )
    }

    @Test
    fun `exactly on the hour, the check is the next day rather than now`() {
        assertEquals(Duration.ofDays(1), delayUntilNextCheck(at("2026-11-16T09:00"), hourOfDay = 9))
    }

    @Test
    fun `a check that has just run queues the next one a day later`() {
        val justAfter = at("2026-11-16T09:00:05")

        assertEquals(
            Duration.ofDays(1).minusSeconds(5),
            delayUntilNextCheck(justAfter, hourOfDay = 9, minimumGap = Duration.ofHours(1))
        )
    }

    @Test
    fun `a run the clock caught early does not queue a second one minutes later`() {
        val aMomentEarly = at("2026-11-16T08:59:30")

        assertEquals(
            Duration.ofDays(1).plusSeconds(30),
            delayUntilNextCheck(aMomentEarly, hourOfDay = 9, minimumGap = Duration.ofHours(1))
        )
    }

    @Test
    fun `a run held up overnight still leaves today's check in place`() {
        // Yesterday's check, held back by a low battery until 6am, should not cost the
        // user today's at 9.
        assertEquals(
            Duration.ofHours(3),
            delayUntilNextCheck(at("2026-11-16T06:00"), hourOfDay = 9, minimumGap = Duration.ofHours(1))
        )
    }

    @Test
    fun `an hour outside the day is held to the day`() {
        assertEquals(
            Duration.ofHours(1),
            delayUntilNextCheck(at("2026-11-16T22:00"), hourOfDay = 30)
        )
    }

    @Test
    fun `a day on which the clocks change still lands on the chosen hour`() {
        // The clocks in London go forward on the night of 28 March 2026, so 9am to 9am
        // across it is only 23 hours. Counting a fixed 24 would put the check at 10am.
        val london = ZoneId.of("Europe/London")

        assertEquals(
            Duration.ofHours(23),
            delayUntilNextCheck(at("2026-03-28T09:00", london), hourOfDay = 9)
        )
    }

    // ---- What it reminds about -----------------------------------------------------

    private val today = date("2026-09-10")

    private fun snapshot() = FinancialSnapshot(
        today = today,
        accounts = listOf(account(opening = 100_000)),
        people = listOf(person(id = 1, name = "Amit")),
        // Due on the 5th; nothing paid, so September's installment is five days late.
        emis = listOf(emi(firstDueDate = "2026-01-05")),
        bills = listOf(
            bill(id = 1, name = "Rent", dueDay = 5),
            bill(id = 2, name = "Electricity", amount = 1_800, dueDay = 11)
        ),
        // Expected back on the 1st and still outstanding.
        ledgerEntries = listOf(
            ledgerEntry(amount = 2_000, direction = LedgerDirection.THEY_OWE_ME, expectedDate = "2026-09-01")
        )
    )

    private val currentMonth get() = ForecastCalculator.forecast(snapshot(), 2).first()

    @Test
    fun `missed payments this month are found as overdue, the longest overdue first`() {
        val overdue = ReminderSelection.overdue(currentMonth, AppSettings())

        assertEquals(listOf("Amit", "Bike EMI", "Rent"), overdue.map { it.title })
        assertTrue(overdue.all { it.isOverdue })
    }

    @Test
    fun `overdue items are kept out of the upcoming reminders`() {
        val items = ForecastCalculator.forecast(snapshot(), 2).flatMap { it.items }
        val upcoming = ReminderSelection.upcoming(items, today, today.plusDays(2))

        // Amit's money is carried forward to today, so judged by date alone it looked
        // merely due and was announced as expected today.
        assertEquals(listOf("Electricity"), upcoming.map { it.title })
    }

    @Test
    fun `each kind can be switched off for overdue reminders too`() {
        val settings = AppSettings(remindAboutEmis = false, remindAboutPeople = false)

        assertEquals(listOf("Rent"), ReminderSelection.overdue(currentMonth, settings).map { it.title })
        assertTrue(
            ReminderSelection.overdue(
                currentMonth,
                AppSettings(
                    remindAboutEmis = false,
                    remindAboutBills = false,
                    remindAboutCards = false,
                    remindAboutPeople = false
                )
            ).isEmpty()
        )
    }

    @Test
    fun `income is never something to be reminded about`() {
        assertFalse(ReminderSelection.wantsReminders(ForecastItemKind.INCOME, AppSettings()))
        assertTrue(ReminderSelection.wantsReminders(ForecastItemKind.ANNUAL, AppSettings()))
        assertFalse(
            ReminderSelection.wantsReminders(
                ForecastItemKind.ANNUAL,
                AppSettings(remindAboutBills = false)
            )
        )
    }

    private fun item(
        title: String,
        amount: Long,
        due: String,
        kind: ForecastItemKind = ForecastItemKind.BILL,
        subtitle: String = "Due"
    ) = ForecastItem(
        date = date(due),
        title = title,
        subtitle = subtitle,
        amount = rupees(amount),
        kind = kind,
        sourceId = 1,
        isEstimate = false,
        isOverdue = true
    )

    @Test
    fun `one late payment is named, with when it was due`() {
        val message = ReminderSelection.overdueMessage(
            listOf(item("Bike EMI", 4_500, "2026-09-07", ForecastItemKind.EMI, "Installment 9 of 36")),
            today
        )

        assertEquals("Overdue: Bike EMI", message.title)
        assertEquals("₹4,500 was due 3 days ago · Installment 9 of 36", message.body)
    }

    @Test
    fun `money owed to the user reads as expected, not as a payment`() {
        val late = ForecastItem(
            date = today,
            title = "Amit",
            subtitle = "Loan",
            amount = rupees(2_000),
            kind = ForecastItemKind.PERSON_INCOMING,
            sourceId = 1,
            isEstimate = false,
            isOverdue = true,
            originallyDue = date("2026-09-09")
        )

        val message = ReminderSelection.overdueMessage(listOf(late), today)

        assertEquals("Overdue: ₹2,000 from Amit", message.title)
        assertEquals("Expected yesterday · Loan", message.body)
    }

    @Test
    fun `several late payments are summed in one message`() {
        val message = ReminderSelection.overdueMessage(
            listOf(
                item("Rent", 13_000, "2026-09-05"),
                item("Bike EMI", 4_500, "2026-09-05", ForecastItemKind.EMI),
                item("Insurance", 8_000, "2026-08-20", ForecastItemKind.ANNUAL),
                item("Water", 500, "2026-09-01")
            ),
            today
        )

        assertEquals("Overdue: 4 payments", message.title)
        assertEquals("₹26,000 unpaid · Rent, Bike EMI, Insurance and 1 more", message.body)
    }

    @Test
    fun `a date more than a week back is given as a date`() {
        val message = ReminderSelection.overdueMessage(
            listOf(item("Insurance", 8_000, "2026-08-20", ForecastItemKind.ANNUAL)),
            today
        )

        assertEquals("₹8,000 was due on 20 Aug 2026 · Due", message.body)
    }

    @Test
    fun `money both owed and owing is told apart in the total`() {
        val message = ReminderSelection.overdueMessage(
            listOf(
                item("Rent", 13_000, "2026-09-05"),
                item("Amit", 2_000, "2026-09-01", ForecastItemKind.PERSON_INCOMING)
            ),
            today
        )

        assertEquals("Overdue: 2 payments", message.title)
        assertEquals("₹13,000 unpaid · ₹2,000 owed to you · Rent, Amit", message.body)
    }
}
