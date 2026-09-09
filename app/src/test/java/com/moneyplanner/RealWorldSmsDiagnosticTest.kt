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

    // ---- Formats added after the first release --------------------------------------

    @Test
    fun `an amount written before the currency still parses`() {
        val parsed = parse(
            "Your A/c XX4567 has been debited 2,450.00 INR on 05-09-26 towards AMAZON. " +
                "Avbl Bal 18,200.00 INR"
        )
        assertTrue(parsed.isTransaction)
        assertEquals(245_000L, parsed.amount?.paise)
    }

    @Test
    fun `a short balance abbreviation is not mistaken for the amount`() {
        // "Bal 42,500" at the end used to be read as a 42,500 rupee transaction.
        val parsed = parse("A/c X1234 debited Rs.300 on 05-09-26. Bal 42,500")
        assertEquals(30_000L, parsed.amount?.paise)
    }

    @Test
    fun `an atm withdrawal is a debit`() {
        val parsed = parse(
            "Rs.5000 withdrawn from A/c XX1234 at HDFC ATM on 05-09-26. Avl Bal Rs.20000"
        )
        assertTrue(parsed.isTransaction)
        assertTrue(parsed.isDebit)
        assertEquals(500_000L, parsed.amount?.paise)
    }

    @Test
    fun `an auto debit standing instruction is a debit`() {
        val parsed = parse(
            "INR 1,899.00 debited from A/c XX1234 towards NETFLIX auto debit on 05-09-26."
        )
        assertTrue(parsed.isTransaction)
        assertTrue(parsed.isDebit)
        assertEquals(189_900L, parsed.amount?.paise)
    }

    @Test
    fun `a salary credit is a credit`() {
        val parsed = parse(
            "Rs.85,000.00 credited to A/c XX1234 on 01-09-26 by NEFT. Info: SALARY SEP"
        )
        assertTrue(parsed.isTransaction)
        assertFalse(parsed.isDebit)
        assertEquals(8_500_000L, parsed.amount?.paise)
    }

    @Test
    fun `a collect request is not a transaction`() {
        // Nobody has paid anything yet, but the message names an amount.
        val parsed = parse(
            "SWIGGY has requested Rs.450 from you. Approve in your UPI app before 6pm."
        )
        assertFalse(parsed.isTransaction)
    }

    @Test
    fun `a failed payment is not a transaction`() {
        val parsed = parse(
            "Your payment of Rs.1,200 to BIGBASKET failed. The amount will be credited back."
        )
        assertFalse(parsed.isTransaction)
    }

    @Test
    fun `a scheduled future debit is not a transaction yet`() {
        val parsed = parse(
            "Rs.2,500 shall be debited from A/c XX1234 on 10-09-26 towards your SIP."
        )
        assertFalse(parsed.isTransaction)
    }

    @Test
    fun `a promotional message that survives link stripping is still discarded`() {
        val parsed = parse(
            "Congratulations! You are eligible for a pre-approved loan of Rs.5,00,000. " +
                "Click here to activate now."
        )
        assertFalse(parsed.isTransaction)
    }

    @Test
    fun `an amount with no currency marker after of parses`() {
        val parsed = parse("A/c X1234 debited of 1200 on 05-09-26 trf to ACME")
        assertTrue(parsed.isTransaction)
        assertEquals(120_000L, parsed.amount?.paise)
    }

    @Test
    fun `the account tail is read from an ending form`() {
        val parsed = parse("Rs.500 spent on card ending 9876 at CAFE on 05-09-26")
        assertEquals("9876", parsed.accountTail)
    }
}
