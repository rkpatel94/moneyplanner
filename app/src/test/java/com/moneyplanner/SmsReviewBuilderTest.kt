package com.moneyplanner

import com.moneyplanner.core.money.Money
import com.moneyplanner.data.sms.SmsCandidate
import com.moneyplanner.data.sms.SmsSource
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.nlp.BankSmsParser
import com.moneyplanner.domain.nlp.DuplicateVerdict
import com.moneyplanner.ui.screens.sms.SmsImportPlan
import com.moneyplanner.ui.screens.sms.SmsImportRow
import com.moneyplanner.ui.screens.sms.SmsReviewBuilder
import com.moneyplanner.ui.screens.sms.SmsReviewContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * Filling the review list from scans and pastes.
 *
 * The list is one batch however it was filled, and it is what will be imported, so it is
 * where duplicates have to stop. These tests hold it to offering each message once across
 * repeated scans and pastes, to saying so when a message was imported before, and to
 * comparing every new message with everything already waiting — the last of which it used
 * to forget at the start of every paste.
 */
class SmsReviewBuilderTest {

    private val bank = Account(
        id = 1, name = "HDFC Savings", type = AccountType.BANK,
        openingBalance = Money.ZERO, openingDate = date("2026-01-01")
    )
    private val salary = incomeSource(id = 3, amount = 85_000, dayOfMonth = 1)
    private val food = category(id = 1, name = "Food")
    private val context = SmsReviewContext(
        categories = listOf(food, category(id = 2, name = "Other")),
        accounts = listOf(bank),
        incomeSources = listOf(salary)
    )

    private val swiggy =
        "Sent Rs.500.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY LIMITED\nOn 16/11/26\n" +
            "Ref 631234567890\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"

    private fun inbox(id: Long, body: String, on: String = "2026-11-16") = SmsCandidate(
        smsId = id,
        sender = "VM-HDFCBK",
        receivedOn = date(on),
        parsed = BankSmsParser.parse(body, date(on)),
        source = SmsSource.INBOX
    )

    private fun pasted(id: Long, body: String, on: String = "2026-11-16") = SmsCandidate(
        smsId = -id,
        sender = "Pasted",
        receivedOn = date(on),
        parsed = BankSmsParser.parse(body, date(on)),
        source = SmsSource.PASTED
    )

    private fun add(
        current: List<SmsImportRow>,
        vararg incoming: SmsCandidate,
        with: SmsReviewContext = context
    ) = SmsReviewBuilder.add(current, incoming.toList(), with)

    @Test
    fun `a clean message arrives ticked with a category suggested`() {
        val result = add(emptyList(), inbox(101, swiggy))
        val row = result.rows.single()

        assertEquals(1, result.added)
        assertTrue(row.isSelected)
        assertEquals(DuplicateVerdict.None, row.duplicate)
        assertEquals(food.id, row.suggestedCategoryId)
    }

    @Test
    fun `scanning the same message twice keeps one row`() {
        val first = add(emptyList(), inbox(101, swiggy))
        val second = add(first.rows, inbox(101, swiggy))

        assertEquals(1, second.rows.size)
        assertEquals(0, second.added)
        assertEquals(1, second.repeats)
    }

    @Test
    fun `a pasted message that a scan then finds is one row`() {
        val first = add(emptyList(), pasted(1, swiggy))
        val second = add(first.rows, inbox(101, swiggy))

        assertEquals(1, second.rows.size)
        assertEquals(1, second.repeats)
    }

    @Test
    fun `the same text pasted twice is one row`() {
        val first = add(emptyList(), pasted(1, swiggy))
        val second = add(first.rows, pasted(2, swiggy))
        assertEquals(1, second.rows.size)
    }

    @Test
    fun `an alert the bank delivered twice is one row`() {
        val result = add(emptyList(), inbox(101, swiggy), inbox(102, swiggy))
        assertEquals(1, result.rows.size)
    }

    @Test
    fun `two different alerts that look alike both stay, the second held back`() {
        // Two separate payments of the same amount to the same place on the same day:
        // probably a duplicate, possibly not, so the user decides.
        val again = swiggy.replace("631234567890", "631234567999")
        val result = add(emptyList(), inbox(101, swiggy), inbox(102, again))

        assertEquals(2, result.rows.size)
        assertTrue(result.rows[0].isSelected)
        assertEquals(DuplicateVerdict.SameBatch, result.rows[1].duplicate)
        assertFalse(result.rows[1].isSelected)
    }

    @Test
    fun `rows from an earlier paste are part of the batch`() {
        // The comparison used to start empty on every paste, so this went through ticked.
        val first = add(emptyList(), pasted(1, swiggy))
        val zomato = "Rs.500.00 debited from A/c XX1234 on 16-11-26 to ZOMATO. Avl Bal Rs.9,000.00"
        val second = add(first.rows, pasted(2, zomato))

        val row = second.rows.last()
        assertEquals(DuplicateVerdict.SameBatch, row.duplicate)
        assertFalse(row.isSelected)
    }

    @Test
    fun `money in is not mistaken for a repeat of money out`() {
        val first = add(emptyList(), inbox(101, swiggy))
        val refund = "Rs.500.00 credited to your A/c XX1234 on 16-11-26 by SWIGGY refund."
        val second = add(first.rows, inbox(102, refund))

        assertEquals(DuplicateVerdict.None, second.rows.last().duplicate)
    }

    @Test
    fun `a message imported by an earlier scan says so and is not ticked`() {
        val result = add(emptyList(), inbox(101, swiggy), with = context.copy(importedSmsIds = setOf(101L)))
        val row = result.rows.single()

        assertEquals(DuplicateVerdict.AlreadyImported, row.duplicate)
        assertFalse(row.isSelected)
        assertEquals(1, result.alreadyImported)
    }

    @Test
    fun `a pasted message is never taken for an imported one by its id`() {
        // Pasted ids are negative and never stored, so they can match nothing imported.
        val result = add(emptyList(), pasted(101, swiggy), with = context.copy(importedSmsIds = setOf(101L)))
        assertEquals(DuplicateVerdict.None, result.rows.single().duplicate)
    }

    @Test
    fun `a debit matching a recorded expense is held back`() {
        val recorded = context.copy(
            expenses = listOf(expense(amount = 500, date = "2026-11-16", description = "Swiggy"))
        )
        val row = add(emptyList(), inbox(101, swiggy), with = recorded).rows.single()
        assertEquals(DuplicateVerdict.Certain, row.duplicate)
        assertFalse(row.isSelected)
    }

    @Test
    fun `a credit matching recorded income is held back`() {
        val credit = "Received Rs.1,250.00 in your Kotak Bank AC X5566 from priya@okicici on 15-11-26." +
            "UPI Ref:631234567890."
        val recorded = context.copy(
            incomeTransactions = listOf(incomeTransaction(amount = 1_250, date = "2026-11-15"))
        )
        val row = add(emptyList(), inbox(101, credit, on = "2026-11-15"), with = recorded).rows.single()

        assertEquals(DuplicateVerdict.Likely, row.duplicate)
        assertFalse(row.isSelected)
        // Income is not filed under an expense category.
        assertNull(row.suggestedCategoryId)
    }

    @Test
    fun `a second salary row for the same month is not stamped on it too`() {
        val salaryOn = { day: String, ref: String ->
            "Dear Customer, Your A/C XXXXX4321 has been credited with Rs.85,000.00 on $day by NEFT " +
                "from ACME TECHNOLOGIES PVT LTD. Ref $ref -SBI"
        }
        val first = add(emptyList(), inbox(201, salaryOn("01/11/26", "A1"), on = "2026-11-01"))
        val second = add(first.rows, inbox(202, salaryOn("02/11/26", "A2"), on = "2026-11-02"))

        assertEquals(SmsImportPlan.ScheduledIncome(salary, YearMonth.of(2026, 11)), first.rows.single().plan)
        val later = second.rows.last()
        assertTrue(later.plan is SmsImportPlan.OtherIncome)
        assertFalse(later.isSelected)
    }

    @Test
    fun `messages that are not transactions are left out`() {
        val otp = inbox(301, "123456 is your OTP for a transaction of Rs.5,000. Do not share.")
        val result = add(emptyList(), otp)
        assertTrue(result.rows.isEmpty())
        assertEquals(0, result.added)
    }
}
