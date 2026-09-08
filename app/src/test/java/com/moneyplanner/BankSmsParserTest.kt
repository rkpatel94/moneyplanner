package com.moneyplanner

import com.moneyplanner.domain.nlp.BankSmsParser
import com.moneyplanner.domain.nlp.SmsDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-world bank alert formats from the major Indian banks.
 *
 * Two properties matter more than breadth of coverage: an amount is never wrong, and a
 * message that is not a transaction is never treated as one. A missed SMS costs the user
 * one manual entry; a mis-parsed one silently corrupts their ledger.
 */
class BankSmsParserTest {

    private val received = date("2026-08-18")

    private fun parse(body: String) = BankSmsParser.parse(body, received)

    @Test
    fun `reads a standard debit alert`() {
        val result = parse(
            "Rs.1,500.00 debited from A/c XX1234 on 18-Aug-26 to SWIGGY. Avl Bal Rs.42,500.00"
        )
        assertTrue(result.isTransaction)
        assertEquals(rupees(1_500), result.amount)
        assertEquals(SmsDirection.DEBIT, result.direction)
        assertEquals(date("2026-08-18"), result.date)
        assertEquals("Swiggy", result.merchant)
        assertEquals("1234", result.accountTail)
    }

    @Test
    fun `never mistakes the available balance for the amount`() {
        // The balance is far larger than the transaction; taking it would be a bad error.
        val result = parse(
            "Rs.250.00 debited from A/c XX1234 on 18-08-26. Avl Bal Rs.42,500.00"
        )
        assertEquals(rupees(250), result.amount)
        assertEquals(rupees(42_500), result.balanceAfter)
    }

    @Test
    fun `reads a card spend alert`() {
        val result = parse("INR 2,500 spent on HDFC Bank Card x1234 at AMAZON on 18-08-26")
        assertTrue(result.isTransaction)
        assertEquals(rupees(2_500), result.amount)
        assertEquals(SmsDirection.DEBIT, result.direction)
        assertEquals("Amazon", result.merchant)
    }

    @Test
    fun `reads a credit alert`() {
        val result = parse("Your A/c XXXXX1234 is credited by Rs.55,000.00 on 01-08-26")
        assertTrue(result.isTransaction)
        assertEquals(rupees(55_000), result.amount)
        assertEquals(SmsDirection.CREDIT, result.direction)
        assertFalse(result.isDebit)
        assertEquals(date("2026-08-01"), result.date)
    }

    @Test
    fun `reads a upi alert with no currency prefix on the amount`() {
        val result = parse("A/C X1234 debited by 500.0 on date 18Aug26 trf to JOHN Refno 12345")
        assertTrue(result.isTransaction)
        assertEquals(rupees(500), result.amount)
        assertEquals("John", result.merchant)
    }

    @Test
    fun `keeps paise when the amount has them`() {
        val result = parse("Rs.1,234.56 debited from A/c XX1234 at STORE on 18-08-26")
        assertEquals(com.moneyplanner.core.money.Money(123_456), result.amount)
    }

    @Test
    fun `falls back to the received date when the text has none`() {
        val result = parse("Rs.300 debited from A/c XX1234 to AUTO")
        assertTrue(result.isTransaction)
        assertEquals(received, result.date)
    }

    @Test
    fun `ignores a one time password message`() {
        val result = parse("123456 is your OTP for a transaction of Rs.5,000. Do not share.")
        assertFalse(result.isTransaction)
        assertNotNull(result.ignoredReason)
    }

    @Test
    fun `ignores a payment reminder rather than recording it as spent`() {
        // This is a future obligation, not money that has moved.
        val result = parse("Your credit card bill of Rs.8,500 is due on 15-09-26. Pay now.")
        assertFalse(result.isTransaction)
    }

    @Test
    fun `ignores a promotional message`() {
        val result = parse("You are eligible for a pre-approved loan offer of Rs.5,00,000. Apply now.")
        assertFalse(result.isTransaction)
    }

    @Test
    fun `ignores an autodebit warning about a future date`() {
        val result = parse("Rs.4,500 will be debited from A/c XX1234 on 05-09-26 towards EMI.")
        assertFalse(result.isTransaction)
    }

    @Test
    fun `ignores a balance enquiry reply`() {
        val result = parse("Available balance is Rs.42,500.00 in A/c XX1234 as on 18-08-26")
        assertFalse(result.isTransaction)
    }

    @Test
    fun `ignores a message with no amount at all`() {
        val result = parse("Your account has been debited. Please check your statement.")
        assertFalse(result.isTransaction)
    }

    @Test
    fun `ignores an empty message`() {
        assertFalse(parse("").isTransaction)
        assertFalse(parse("   ").isTransaction)
    }

    @Test
    fun `treats a message as a debit when both words appear`() {
        // Some banks say "debited ... credited to beneficiary" in one message.
        val result = parse(
            "Rs.2,000 debited from A/c XX1234 and credited to beneficiary on 18-08-26"
        )
        assertEquals(SmsDirection.DEBIT, result.direction)
    }

    @Test
    fun `trims trailing noise out of the merchant name`() {
        val result = parse("Rs.500 debited from A/c XX1234 to SWIGGY on 18-08-26 Ref 998877")
        assertEquals("Swiggy", result.merchant)
    }

    @Test
    fun `suggests an honest description when no merchant could be read`() {
        val result = parse("Rs.300 debited from A/c XX1234 on 18-08-26")
        assertTrue(result.isTransaction)
        assertEquals("Card or UPI payment", result.suggestedDescription())
    }

    @Test
    fun `handles several date formats`() {
        assertEquals(
            date("2026-08-18"),
            parse("Rs.100 debited from A/c XX1234 on 18/08/2026 at SHOP").date
        )
        assertEquals(
            date("2026-08-18"),
            parse("Rs.100 debited from A/c XX1234 on 18-Aug-2026 at SHOP").date
        )
    }

    @Test
    fun `is deterministic across repeated parses`() {
        val body = "Rs.1,500.00 debited from A/c XX1234 on 18-Aug-26 to SWIGGY. Avl Bal Rs.42,500.00"
        val first = parse(body)
        repeat(5) {
            val again = parse(body)
            assertEquals(first.amount, again.amount)
            assertEquals(first.direction, again.direction)
            assertEquals(first.merchant, again.merchant)
            assertEquals(first.date, again.date)
        }
    }

    @Test
    fun `keeps the original text so the user can check what was read`() {
        val body = "Rs.500 debited from A/c XX1234 to SWIGGY on 18-08-26"
        assertEquals(body, parse(body).originalText)
    }
}
