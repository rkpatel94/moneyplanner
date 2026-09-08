package com.moneyplanner

import com.moneyplanner.domain.nlp.BankSmsParser
import com.moneyplanner.domain.nlp.SmsDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Real alert formats from the banks people in India actually use.
 *
 * The parser was rejecting genuine debits because "http" was treated as a mark of
 * advertising, and most banks close a debit alert with a fraud-reporting link. SBI's
 * standard UPI alert is one, which meant the largest bank in the country produced nothing
 * at all. These are kept as literal message bodies so the next change to the rules has to
 * face the same formats.
 */
class RealWorldSmsDiagnosticTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 20)

    private fun parse(body: String) = BankSmsParser.parse(body, today)

    // ---- Real debits that must survive ---------------------------------------------

    @Test
    fun `an sbi upi debit ending in a fraud reporting link is a transaction`() {
        val parsed = parse(
            "Dear Customer, Rs.500.00 debited from A/c X1234 on 20Aug25 to VPA " +
                "merchant@okhdfcbank Ref 522334455. If not done by you, report at " +
                "https://sbi.co.in/SBIePay"
        )
        assertTrue("a link is not an advertisement", parsed.isTransaction)
        assertEquals(50_000L, parsed.amount?.paise)
        assertEquals(SmsDirection.DEBIT, parsed.direction)
    }

    @Test
    fun `a kotak debit ending in a bare fraud link is a transaction`() {
        val parsed = parse(
            "Sent Rs.750.00 from Kotak Bank AC X1234 to bigbasket@icici on 20-08-25. " +
                "UPI Ref 522334455. Not you, https://kotak.com/fraud"
        )
        assertTrue(parsed.isTransaction)
        assertEquals(75_000L, parsed.amount?.paise)
        assertEquals("Bigbasket", parsed.merchant)
    }

    @Test
    fun `a debit with no currency marker on the amount still parses`() {
        val parsed = parse(
            "Dear UPI user A/C X1234 debited by 250.0 on date 20Aug25 trf to SWIGGY " +
                "Refno 123456789012. If not u? call 1800111109. -SBI"
        )
        assertTrue(parsed.isTransaction)
        assertEquals(25_000L, parsed.amount?.paise)
        assertEquals("Swiggy", parsed.merchant)
    }

    @Test
    fun `a card spend alert parses`() {
        val parsed = parse(
            "Spent Card no. XX1234 INR 899.00 20-08-25 12:30:45 AMAZON Avl Lmt " +
                "INR 45000.00. Not you? SMS BLOCK 1234 to 919951860002"
        )
        assertTrue(parsed.isTransaction)
        assertEquals(89_900L, parsed.amount?.paise)
        assertEquals(SmsDirection.DEBIT, parsed.direction)
    }

    @Test
    fun `a wallet payment does not run the merchant into the account name`() {
        val parsed = parse(
            "Rs.150 paid to SWIGGY from Paytm Payments Bank A/c XX1234 on 20-08-25. " +
                "UPI Ref 522334455"
        )
        assertTrue(parsed.isTransaction)
        assertEquals("Swiggy", parsed.merchant)
    }

    // ---- Real credits ---------------------------------------------------------------

    @Test
    fun `a salary credit is a credit and its balance is not the amount`() {
        val parsed = parse(
            "Rs.55,000.00 credited to A/c XX1234 on 20-08-25 by SALARY. Avl Bal Rs.97,500.00"
        )
        assertTrue(parsed.isTransaction)
        assertEquals(SmsDirection.CREDIT, parsed.direction)
        assertEquals(5_500_000L, parsed.amount?.paise)
        assertEquals(9_750_000L, parsed.balanceAfter?.paise)
    }

    // ---- Things that must still be thrown away --------------------------------------

    @Test
    fun `an otp is not a transaction`() {
        assertFalse(
            parse("123456 is your OTP for a transaction of Rs.500 at AMAZON. Valid for 10 min.")
                .isTransaction
        )
    }

    @Test
    fun `a loan advertisement is still rejected once its link is stripped`() {
        assertFalse(
            parse("Get a pre-approved loan offer of Rs.5,00,000! Click https://bank.com/apply now")
                .isTransaction
        )
    }

    @Test
    fun `a balance enquiry reply is not a transaction`() {
        assertFalse(
            parse("Available balance is Rs.42,500.00 in A/c XX1234 as on 20-08-25").isTransaction
        )
    }

    @Test
    fun `an upcoming emi reminder is not a payment`() {
        assertFalse(
            parse("Your EMI of Rs.5,500 is due on 05-09-25 for loan XX1234.").isTransaction
        )
    }

    @Test
    fun `a promotional link cannot smuggle an offer past the filter`() {
        // The word only appears inside the URL, which is removed before classification —
        // so this one is judged on the rest of the sentence, and the rest is a real debit.
        val parsed = parse("Rs.500.00 debited from A/c X1234 on 20-08-25. https://b.com/offer")
        assertTrue(parsed.isTransaction)
    }
}
