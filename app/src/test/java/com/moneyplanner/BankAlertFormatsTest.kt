package com.moneyplanner

import com.moneyplanner.domain.nlp.BankSmsParser
import com.moneyplanner.domain.nlp.ParsedSms
import com.moneyplanner.domain.nlp.SmsDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Current alert formats from HDFC, ICICI, SBI, Axis, Kotak and Paytm, as the inbox scan
 * now reads them.
 *
 * Each is a literal message body in the shape the bank sends today, multi-line ones
 * included, with reference numbers and digits changed. They check what the importer
 * depends on as well as the amount: the direction, the date, who was paid, and how the
 * money moved, because a card purchase filed against the bank or a cash withdrawal filed as
 * spending makes the balance wrong just as surely as a bad amount does.
 *
 * The second half pins the gaps the scan exposed, each of which used to produce a wrong
 * record rather than a missing one.
 */
class BankAlertFormatsTest {

    private val received: LocalDate = LocalDate.of(2026, 11, 16)

    private fun parse(body: String, on: LocalDate = received): ParsedSms = BankSmsParser.parse(body, on)

    private fun assertDebit(parsed: ParsedSms, rupees: Long) {
        assertTrue("should be a transaction: ${parsed.ignoredReason}", parsed.isTransaction)
        assertEquals(SmsDirection.DEBIT, parsed.direction)
        assertEquals(rupees(rupees), parsed.amount)
    }

    private fun assertCredit(parsed: ParsedSms, rupees: Long) {
        assertTrue("should be a transaction: ${parsed.ignoredReason}", parsed.isTransaction)
        assertEquals(SmsDirection.CREDIT, parsed.direction)
        assertEquals(rupees(rupees), parsed.amount)
    }

    // ---- HDFC Bank ---------------------------------------------------------------------

    @Test
    fun `hdfc multi-line upi send`() {
        val parsed = parse(
            "Sent Rs.500.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY LIMITED\nOn 16/11/26\n" +
                "Ref 631234567890\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"
        )
        assertDebit(parsed, 500)
        assertEquals("Swiggy Limited", parsed.merchant)
        assertEquals(date("2026-11-16"), parsed.date)
        assertEquals("1234", parsed.accountTail)
        assertTrue(parsed.isUpi)
        assertFalse(parsed.isCreditCard)
    }

    @Test
    fun `hdfc upi money received`() {
        val parsed = parse(
            "Money Received - INR 2,000.00 in HDFC Bank A/c xx1234 on 15-11-26 by A/c linked " +
                "to VPA rahul.k@okaxis (UPI Ref No 631234567890). Avl bal: INR 52,345.50"
        )
        assertCredit(parsed, 2_000)
        assertEquals(date("2026-11-15"), parsed.date)
        assertEquals(rupees(52_345) + com.moneyplanner.core.money.Money(50), parsed.balanceAfter)
        assertTrue(parsed.isUpi)
    }

    @Test
    fun `hdfc credit card spend is a card purchase with its last four`() {
        val parsed = parse(
            "Spent Rs.1,299.00 On HDFC Bank Card 1234 At AMAZON PAY INDIA On " +
                "2026-11-16:20:15:42 Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 1234 " +
                "to 7308080808"
        )
        assertDebit(parsed, 1_299)
        // "BLOCK CC" is how HDFC marks a credit card; its debit cards say "BLOCK DC".
        assertTrue(parsed.isCreditCard)
        assertFalse(parsed.isCardBillPayment)
        assertEquals("1234", parsed.cardTail)
        assertEquals("Amazon Pay India", parsed.merchant)
        assertEquals(date("2026-11-16"), parsed.date)
    }

    @Test
    fun `hdfc debit card purchase is not a credit card`() {
        val parsed = parse(
            "Rs.850.00 spent on HDFC Bank Debit Card ending 5678 at RELIANCE SMART on 16-11-26. " +
                "Avl bal Rs.41,650.00. Not you? Call 18002586161"
        )
        assertDebit(parsed, 850)
        assertFalse(parsed.isCreditCard)
        assertTrue(parsed.isDebitCard)
        assertEquals("5678", parsed.cardTail)
        assertEquals("Reliance Smart", parsed.merchant)
    }

    @Test
    fun `hdfc atm withdrawal`() {
        val parsed = parse(
            "Withdrawn Rs.5,000.00 From HDFC Bank Card x5678 At +HDFC BANK LTD ATM On " +
                "2026-11-15:11:02:33 Bal Rs.36,650.00 Not You? Call 18002586161/SMS BLOCK DC 5678 " +
                "to 7308080808"
        )
        assertDebit(parsed, 5_000)
        assertTrue(parsed.isAtmWithdrawal)
        assertTrue(parsed.isDebitCard)
        assertFalse(parsed.isCreditCard)
        assertEquals(rupees(36_650), parsed.balanceAfter)
        assertEquals("Cash withdrawal", parsed.suggestedDescription())
    }

    @Test
    fun `hdfc neft salary deposit reads the remitter and an upper case month`() {
        val parsed = parse(
            "Update! INR 85,000.00 deposited in HDFC Bank A/c XX1234 on 01-NOV-26 for NEFT " +
                "Cr-ICIC0000104-ACME TECHNOLOGIES PVT LTD-RAJ KUMAR-ICICN52026110112345.Avl bal " +
                "INR 1,21,345.50. Cheque deposits in A/c are subject to clearing",
            on = date("2026-11-02")
        )
        assertCredit(parsed, 85_000)
        assertEquals(date("2026-11-01"), parsed.date)
        assertEquals("Acme Technologies Pvt Ltd", parsed.merchant)
    }

    // ---- ICICI Bank ----------------------------------------------------------------------

    @Test
    fun `icici upi debit names the payee after the semicolon`() {
        val parsed = parse(
            "ICICI Bank Acct XX123 debited for Rs 450.00 on 16-Nov-26; ZOMATO credited. " +
                "UPI:631234567890. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766."
        )
        assertDebit(parsed, 450)
        assertEquals("Zomato", parsed.merchant)
        assertEquals("123", parsed.accountTail)
        assertTrue(parsed.isUpi)
    }

    @Test
    fun `icici card spend keeps the amount and not the limit`() {
        val parsed = parse(
            "INR 2,499.00 spent using ICICI Bank Card XX9012 on 15-Nov-26 on IND*FLIPKART. " +
                "Avl Limit: INR 1,47,501.00. If not you, call 1800 2662/SMS BLOCK 9012 to 9215676766"
        )
        assertDebit(parsed, 2_499)
        assertEquals("9012", parsed.cardTail)
        assertEquals("Flipkart", parsed.merchant)
        assertEquals(date("2026-11-15"), parsed.date)
    }

    @Test
    fun `icici credit card used for a transaction is money out`() {
        // "Credit card" holds the word "credit"; this used to come out as money received.
        val parsed = parse(
            "Your ICICI Bank Credit Card XX9012 has been used for a transaction of INR 3,150.00 " +
                "on Nov 14, 2026 at 19:42:10. Info: DMART AVENUE."
        )
        assertDebit(parsed, 3_150)
        assertTrue(parsed.isCreditCard)
        assertEquals("Dmart Avenue", parsed.merchant)
        assertEquals(date("2026-11-14"), parsed.date)
    }

    @Test
    fun `icici neft credit ending in its available balance is kept`() {
        // "Available Balance is" used to mark this as a balance enquiry and drop the salary.
        val parsed = parse(
            "ICICI Bank Account XX123 credited:Rs. 85,000.00 on 01-Nov-26. Info NEFT-ACME " +
                "TECHNOLOGIES. Available Balance is Rs. 1,02,345.67."
        )
        assertCredit(parsed, 85_000)
        assertEquals("Acme Technologies", parsed.merchant)
        assertEquals(date("2026-11-01"), parsed.date)
    }

    @Test
    fun `icici atm withdrawal`() {
        val parsed = parse(
            "Dear Customer, Rs.10,000 withdrawn from ICICI Bank Acct XX123 at ATM S1ACN123 on " +
                "13-Nov-26. Avl Bal Rs.26,650.00."
        )
        assertDebit(parsed, 10_000)
        assertTrue(parsed.isAtmWithdrawal)
    }

    // ---- State Bank of India -------------------------------------------------------------

    @Test
    fun `sbi upi debit with no currency marker`() {
        val parsed = parse(
            "Dear UPI user A/C X4321 debited by 120.0 on date 16Nov26 trf to UBER INDIA Refno " +
                "631234567890. If not u? call 1800111109. -SBI"
        )
        assertDebit(parsed, 120)
        assertEquals("Uber India", parsed.merchant)
        assertEquals(date("2026-11-16"), parsed.date)
        assertTrue(parsed.isUpi)
    }

    @Test
    fun `sbi upi credit does not take the amount for the payer`() {
        val parsed = parse(
            "Dear SBI UPI User, ur A/cX4321 credited by Rs1500 on 15Nov26 by (Ref no 631234567890)"
        )
        assertCredit(parsed, 1_500)
        assertEquals(date("2026-11-15"), parsed.date)
        assertNull(parsed.merchant)
        assertEquals("Money received", parsed.suggestedDescription())
    }

    @Test
    fun `sbi atm withdrawal`() {
        val parsed = parse(
            "Dear Customer, Rs.2,000 withdrawn at SBI ATM S1BW000123 from A/cX4321 on 14Nov26 " +
                "Transaction Number 4567. Available Balance Rs.18,000. If not withdrawn by you, " +
                "forward this SMS to 9223008333/ call 1800111109 to block your card. -SBI"
        )
        assertDebit(parsed, 2_000)
        assertTrue(parsed.isAtmWithdrawal)
        assertEquals(rupees(18_000), parsed.balanceAfter)
    }

    @Test
    fun `sbi card spend`() {
        val parsed = parse(
            "Rs.1,850.00 spent on your SBI Credit Card ending 4455 at SWIGGY on 16/11/26. Trxn. " +
                "not done by you? Report at https://sbicard.com/Dispute"
        )
        assertDebit(parsed, 1_850)
        assertTrue(parsed.isCreditCard)
        assertEquals("4455", parsed.cardTail)
        assertEquals("Swiggy", parsed.merchant)
    }

    @Test
    fun `sbi neft salary credited the day before it was due`() {
        val parsed = parse(
            "Dear Customer, Your A/C XXXXX4321 has been credited with Rs.72,500.00 on 31/10/26 " +
                "by NEFT from ACME TECHNOLOGIES PVT LTD. Avl Bal Rs.90,500.00 -SBI",
            on = date("2026-10-31")
        )
        assertCredit(parsed, 72_500)
        assertEquals(date("2026-10-31"), parsed.date)
        assertEquals("Acme Technologies Pvt Ltd", parsed.merchant)
    }

    // ---- Axis Bank -----------------------------------------------------------------------

    @Test
    fun `axis multi-line upi debit reads the payee from the upi path`() {
        val parsed = parse(
            "INR 349.00 debited\nA/c no. XX7788\n16-11-26, 13:05:11\nUPI/P2M/631234567890/BLINKIT\n" +
                "Not you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank"
        )
        assertDebit(parsed, 349)
        assertEquals("Blinkit", parsed.merchant)
        assertEquals("7788", parsed.accountTail)
        assertEquals(date("2026-11-16"), parsed.date)
        assertTrue(parsed.isUpi)
    }

    @Test
    fun `axis card spend names the merchant after the time`() {
        val parsed = parse(
            "Spent INR 1,199.00 Axis Bank Card no. XX3344 15-11-26 21:10:05 IST NETFLIX Avl " +
                "Limit: INR 88,801.00 Not you? SMS BLOCK 3344 to 919951860002"
        )
        assertDebit(parsed, 1_199)
        assertEquals("3344", parsed.cardTail)
        assertEquals("Netflix", parsed.merchant)
    }

    @Test
    fun `axis neft salary reads the remitter from the neft path`() {
        val parsed = parse(
            "INR 85,000.00 credited to A/c no. XX7788 on 01-11-26 at 09:15:20 IST. Info- " +
                "NEFT/ICIC0000104/ACME TECHNOLOGIES. Avl Bal INR 1,02,000.00"
        )
        assertCredit(parsed, 85_000)
        assertEquals("Acme Technologies", parsed.merchant)
    }

    @Test
    fun `axis atm withdrawal`() {
        val parsed = parse(
            "INR 3,000.00 withdrawn from A/c no. XX7788 on 14-11-26 at 18:40:00 IST at ATM " +
                "S1CN1234. Avl Bal INR 24,500.00"
        )
        assertDebit(parsed, 3_000)
        assertTrue(parsed.isAtmWithdrawal)
        assertEquals(rupees(24_500), parsed.balanceAfter)
    }

    // ---- Kotak Mahindra Bank -------------------------------------------------------------

    @Test
    fun `kotak upi send`() {
        val parsed = parse(
            "Sent Rs.750.00 from Kotak Bank AC X5566 to dmart@ybl on 16-11-26.UPI Ref " +
                "631234567890. Not you, https://kotak.com/KBANKT/Fraud"
        )
        assertDebit(parsed, 750)
        assertEquals("Dmart", parsed.merchant)
        assertEquals(date("2026-11-16"), parsed.date)
        assertTrue(parsed.isUpi)
    }

    @Test
    fun `kotak upi received`() {
        val parsed = parse(
            "Received Rs.1,250.00 in your Kotak Bank AC X5566 from priya@okicici on 15-11-26." +
                "UPI Ref:631234567890."
        )
        assertCredit(parsed, 1_250)
        assertEquals("Priya", parsed.merchant)
    }

    @Test
    fun `kotak credit card spend`() {
        val parsed = parse(
            "INR 4,250.00 spent on Kotak Credit Card x9988 on 14-Nov-2026 at CROMA. Avl limit " +
                "INR 95,750.00. Not you? Call 18602662666"
        )
        assertDebit(parsed, 4_250)
        assertTrue(parsed.isCreditCard)
        assertEquals("9988", parsed.cardTail)
        assertEquals("Croma", parsed.merchant)
        assertEquals(date("2026-11-14"), parsed.date)
    }

    @Test
    fun `kotak imps credit names the sender`() {
        val parsed = parse(
            "Your a/c XX5566 is credited by Rs.15,000.00 on 12-11-26 by IMPS from RAHUL SHARMA. " +
                "Ref 631234567890."
        )
        assertCredit(parsed, 15_000)
        assertEquals("Rahul Sharma", parsed.merchant)
    }

    // ---- Paytm Payments Bank, as a UPI app -----------------------------------------------

    @Test
    fun `paytm upi payment`() {
        val parsed = parse(
            "Paid Rs.180.00 to RAPIDO from Paytm Payments Bank a/c XX6677. UPI Ref: 631234567890. " +
                "Not you? Call 01204456456"
        )
        assertDebit(parsed, 180)
        assertEquals("Rapido", parsed.merchant)
        assertTrue(parsed.isUpi)
    }

    @Test
    fun `paytm upi money received`() {
        val parsed = parse(
            "Rs.2,000.00 received from AMIT VERMA in your Paytm Payments Bank a/c XX6677. " +
                "UPI Ref: 631234567890"
        )
        assertCredit(parsed, 2_000)
        assertEquals("Amit Verma", parsed.merchant)
    }

    // ---- Gaps the scan exposed: each used to produce a wrong record ----------------------

    @Test
    fun `a spend in another currency is not given the card limit as its amount`() {
        // The only rupee figure here is the available limit. It used to become the amount.
        val parsed = parse(
            "USD 12.99 spent using ICICI Bank Card XX9012 on 15-Nov-26 on NETFLIX.COM. Avl " +
                "Limit: INR 98,701.00."
        )
        assertFalse(parsed.isTransaction)
        assertNotNull(parsed.ignoredReason)
    }

    @Test
    fun `a spend written with a dollar sign is not given the limit either`() {
        val parsed = parse(
            "Spent US\$ 12.99 on HDFC Bank Credit Card ending 1234 at APPLE.COM/BILL on 16-11-26. " +
                "Avl Lmt Rs.98,701.00"
        )
        assertFalse(parsed.isTransaction)
    }

    @Test
    fun `a rupee amount quoted with the foreign one still imports in rupees`() {
        val parsed = parse(
            "INR 1,089.50 spent on HDFC Bank Credit Card ending 1234 at NETFLIX (USD 12.99) on " +
                "16-11-26."
        )
        assertTrue(parsed.isTransaction)
        assertEquals(com.moneyplanner.core.money.Money(108_950), parsed.amount)
    }

    @Test
    fun `thank you for using your credit card is money out`() {
        val parsed = parse(
            "Thank you for using your HDFC Bank Credit Card ending 1234 for Rs 1,299.00 at AMAZON " +
                "on 2026-11-16:20:15:42."
        )
        assertDebit(parsed, 1_299)
        assertTrue(parsed.isCreditCard)
        assertEquals("Amazon", parsed.merchant)
    }

    @Test
    fun `a refund onto a credit card is not money into an account`() {
        val parsed = parse(
            "Refund of Rs.499.00 from AMAZON has been credited to your HDFC Bank Credit Card " +
                "ending 1234 on 16-11-26."
        )
        assertFalse(parsed.isTransaction)
    }

    @Test
    fun `the card issuer acknowledging a bill payment is not new spending`() {
        val parsed = parse(
            "Payment of Rs.8,500.00 has been received on your ICICI Bank Credit Card XX9012 on " +
                "10-Nov-26. Thank you."
        )
        assertFalse(parsed.isTransaction)
    }

    @Test
    fun `a card bill paid from the bank is a bill payment and not a card purchase`() {
        val parsed = parse(
            "Rs.8,500.00 debited from A/c XX1234 on 10-11-26 towards ICICI Bank Credit Card " +
                "XX9012 payment. Avl Bal Rs.33,845.00"
        )
        assertDebit(parsed, 8_500)
        assertTrue(parsed.isCardBillPayment)
        assertEquals("9012", parsed.cardTail)
        assertEquals("1234", parsed.accountTail)
        assertEquals("Credit card bill", parsed.suggestedDescription())
    }

    @Test
    fun `a reversed debit is money back`() {
        val parsed = parse(
            "Rs.500.00 debited from your A/c XX1234 on 12-11-26 for UPI txn has been reversed. " +
                "Avl Bal Rs.40,500.00"
        )
        assertCredit(parsed, 500)
    }

    @Test
    fun `money returned after a failed payment is kept`() {
        // The debit alert arrived first; without this the ledger keeps the money as spent.
        val parsed = parse(
            "Your UPI txn of Rs.500 failed. Rs.500.00 has been credited back to your A/c XX1234 " +
                "on 12-11-26."
        )
        assertCredit(parsed, 500)
    }

    @Test
    fun `an autopay mandate being set up is not a debit`() {
        val parsed = parse(
            "Your UPI AutoPay mandate for Rs.649.00 towards NETFLIX has been created. Amount will " +
                "be auto-debited on 05-12-26."
        )
        assertFalse(parsed.isTransaction)
    }

    @Test
    fun `a date after the message arrived is not taken as the transaction date`() {
        val parsed = parse("Rs.500 debited from A/c XX1234 on 25-11-26 to SWIGGY")
        assertTrue(parsed.isTransaction)
        assertEquals(received, parsed.date)
    }

    @Test
    fun `hours written before the amount are not read as rupees`() {
        val parsed = parse("At 14:32 hrs 16-11-26, INR 250.00 was debited from A/c XX1234 to OLA")
        assertDebit(parsed, 250)
    }

    @Test
    fun `a balance enquiry is still not a transaction`() {
        assertFalse(
            parse("Avl Bal is Rs.12,500.00 for A/c XX1234 as on 16-11-26. Last credited Rs.500")
                .isTransaction
        )
    }
}
