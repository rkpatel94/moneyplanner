package com.moneyplanner

import com.moneyplanner.domain.nlp.DuplicateVerdict
import com.moneyplanner.domain.nlp.SmsDuplicateDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deciding whether a pasted alert is something already recorded.
 *
 * The asymmetry decides everything here. A missed import is visibly absent and gets fixed;
 * a duplicated one is invisible and makes every balance downstream quietly wrong. So the
 * detector leans towards suspicion and hands the judgement to the user, and the tests hold
 * it to catching the near misses without collapsing genuinely separate spending.
 */
class SmsDuplicateDetectorTest {

    private fun existing(amount: Long, on: String, description: String = "Lunch") = listOf(
        expense(id = 1, amount = amount, date = on, description = description)
    )

    private fun check(
        amount: Long,
        on: String,
        merchant: String? = null,
        recorded: List<com.moneyplanner.domain.model.Expense> = emptyList(),
        batch: List<Pair<com.moneyplanner.core.money.Money, java.time.LocalDate>> = emptyList()
    ) = SmsDuplicateDetector.check(
        amount = rupees(amount),
        date = date(on),
        merchant = merchant,
        existing = recorded,
        earlierInBatch = batch
    )

    @Test
    fun `nothing recorded means nothing to duplicate`() {
        assertEquals(DuplicateVerdict.None, check(amount = 500, on = "2026-09-05"))
    }

    @Test
    fun `a different amount on the same day is not a duplicate`() {
        val verdict = check(
            amount = 500,
            on = "2026-09-05",
            recorded = existing(amount = 900, on = "2026-09-05")
        )
        assertEquals(DuplicateVerdict.None, verdict)
    }

    @Test
    fun `the same amount on the same day is likely already recorded`() {
        val verdict = check(
            amount = 500,
            on = "2026-09-05",
            recorded = existing(amount = 500, on = "2026-09-05")
        )
        assertEquals(DuplicateVerdict.Likely, verdict)
        assertTrue(verdict.isDuplicate)
    }

    @Test
    fun `the same amount, day and place is as certain as this gets`() {
        val verdict = check(
            amount = 500,
            on = "2026-09-05",
            merchant = "Swiggy",
            recorded = existing(amount = 500, on = "2026-09-05", description = "Swiggy dinner")
        )
        assertEquals(DuplicateVerdict.Certain, verdict)
    }

    @Test
    fun `an alert that arrived after midnight still matches the day before`() {
        // The commonest false negative: the purchase was Friday, the alert says Saturday.
        val verdict = check(
            amount = 500,
            on = "2026-09-06",
            recorded = existing(amount = 500, on = "2026-09-05")
        )
        assertEquals(DuplicateVerdict.Possible, verdict)
    }

    @Test
    fun `a day out with the same place is more than a possibility`() {
        val verdict = check(
            amount = 500,
            on = "2026-09-06",
            merchant = "Swiggy",
            recorded = existing(amount = 500, on = "2026-09-05", description = "Swiggy dinner")
        )
        assertEquals(DuplicateVerdict.Likely, verdict)
    }

    @Test
    fun `two days apart is far enough to be separate spending`() {
        // Otherwise a daily coffee at the same price would collapse into one entry.
        val verdict = check(
            amount = 500,
            on = "2026-09-07",
            recorded = existing(amount = 500, on = "2026-09-05")
        )
        assertEquals(DuplicateVerdict.None, verdict)
    }

    @Test
    fun `the same message pasted twice is caught within the batch`() {
        val verdict = check(
            amount = 500,
            on = "2026-09-05",
            batch = listOf(rupees(500) to date("2026-09-05"))
        )
        assertEquals(DuplicateVerdict.SameBatch, verdict)
    }

    @Test
    fun `a batch match outranks having nothing recorded`() {
        // Nothing in the ledger yet, but the paste itself repeats. Both would import.
        val verdict = check(
            amount = 500,
            on = "2026-09-05",
            recorded = emptyList(),
            batch = listOf(rupees(500) to date("2026-09-05"))
        )
        assertTrue(verdict.isDuplicate)
    }

    @Test
    fun `a merchant naming works whichever side is longer`() {
        // "SWIGGY" from the bank against "Swiggy dinner" typed by hand, and the reverse.
        assertEquals(
            DuplicateVerdict.Certain,
            check(
                amount = 500, on = "2026-09-05", merchant = "Swiggy dinner",
                recorded = existing(500, "2026-09-05", description = "Swiggy")
            )
        )
    }

    @Test
    fun `an unrelated place on the same day stays a likely rather than a certain`() {
        val verdict = check(
            amount = 500,
            on = "2026-09-05",
            merchant = "Uber",
            recorded = existing(amount = 500, on = "2026-09-05", description = "Swiggy dinner")
        )
        assertEquals(DuplicateVerdict.Likely, verdict)
    }

    @Test
    fun `a zero amount is never treated as a duplicate`() {
        val verdict = check(
            amount = 0,
            on = "2026-09-05",
            recorded = existing(amount = 0, on = "2026-09-05")
        )
        assertFalse(verdict.isDuplicate)
    }

    // ---- Money in, transfers and card payments -----------------------------------------

    @Test
    fun `a credit matching income already recorded is flagged`() {
        // A salary marked received by hand, then imported again from its credit alert.
        val recorded = listOf(incomeTransaction(amount = 85_000, date = "2026-11-01"))
        val verdict = SmsDuplicateDetector.checkIncome(
            amount = rupees(85_000),
            date = date("2026-11-01"),
            payer = null,
            existing = recorded
        )
        assertEquals(DuplicateVerdict.Likely, verdict)
    }

    @Test
    fun `a credit a day away from recorded income is still a question`() {
        val recorded = listOf(incomeTransaction(amount = 85_000, date = "2026-11-01"))
        val verdict = SmsDuplicateDetector.checkIncome(
            amount = rupees(85_000),
            date = date("2026-10-31"),
            payer = null,
            existing = recorded
        )
        assertEquals(DuplicateVerdict.Possible, verdict)
    }

    @Test
    fun `a credit naming the recorded payer on the same day is certain`() {
        val recorded = listOf(incomeTransaction(amount = 85_000, date = "2026-11-01"))
        val verdict = SmsDuplicateDetector.checkIncome(
            amount = rupees(85_000),
            date = date("2026-11-01"),
            payer = "Salary",
            existing = recorded
        )
        assertEquals(DuplicateVerdict.Certain, verdict)
    }

    @Test
    fun `a different credit amount is not income already recorded`() {
        val recorded = listOf(incomeTransaction(amount = 85_000, date = "2026-11-01"))
        val verdict = SmsDuplicateDetector.checkIncome(
            amount = rupees(84_999),
            date = date("2026-11-01"),
            payer = null,
            existing = recorded
        )
        assertEquals(DuplicateVerdict.None, verdict)
    }

    @Test
    fun `a withdrawal matching a recorded transfer is flagged`() {
        val transfers = listOf(
            com.moneyplanner.domain.model.AccountTransfer(
                id = 1, fromAccountId = 1, toAccountId = 2,
                amount = rupees(5_000), date = date("2026-11-15")
            )
        )
        assertEquals(
            DuplicateVerdict.Likely,
            SmsDuplicateDetector.checkTransfer(rupees(5_000), date("2026-11-15"), transfers)
        )
        assertEquals(
            DuplicateVerdict.None,
            SmsDuplicateDetector.checkTransfer(rupees(5_000), date("2026-11-18"), transfers)
        )
    }

    @Test
    fun `a card bill payment matching one already recorded is flagged`() {
        val payments = listOf(
            com.moneyplanner.domain.model.CreditCardPayment(
                id = 1, cardId = 1, amount = rupees(8_500),
                paidDate = date("2026-11-10"), accountId = 1, notes = ""
            )
        )
        assertEquals(
            DuplicateVerdict.Possible,
            SmsDuplicateDetector.checkCardPayment(rupees(8_500), date("2026-11-11"), payments)
        )
    }

    @Test
    fun `the stronger of two verdicts wins`() {
        assertEquals(
            DuplicateVerdict.Likely,
            SmsDuplicateDetector.strongest(DuplicateVerdict.Possible, DuplicateVerdict.Likely)
        )
        assertEquals(
            DuplicateVerdict.SameBatch,
            SmsDuplicateDetector.strongest(DuplicateVerdict.SameBatch, DuplicateVerdict.Certain)
        )
        assertEquals(
            DuplicateVerdict.None,
            SmsDuplicateDetector.strongest(DuplicateVerdict.None, DuplicateVerdict.None)
        )
    }

    @Test
    fun `an already imported message says so and is not ready to import`() {
        assertTrue(DuplicateVerdict.AlreadyImported.isDuplicate)
        assertEquals("Already imported", DuplicateVerdict.AlreadyImported.label)
    }
}
