package com.moneyplanner

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.nlp.BankSmsParser
import com.moneyplanner.ui.screens.sms.SmsImportPlan
import com.moneyplanner.ui.screens.sms.SmsImportPlanner
import com.moneyplanner.ui.screens.sms.SmsReviewContext
import com.moneyplanner.ui.screens.sms.summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * What each kind of bank alert becomes when it is imported.
 *
 * Every rule here protects a balance. A card purchase filed against the bank understates
 * cash until the bill is paid; a cash withdrawal filed as spending says money is gone that
 * is sitting in a wallet; a salary filed as "other income" leaves the forecast expecting the
 * same salary again. So each test names the record a message must turn into, and the ones
 * at the edges name the record it must not.
 */
class SmsImportPlannerTest {

    private val bank = Account(
        id = 1, name = "HDFC Savings", type = AccountType.BANK,
        openingBalance = Money.ZERO, openingDate = date("2026-01-01"), sortOrder = 0
    )
    private val cash = Account(
        id = 2, name = "Cash", type = AccountType.CASH,
        openingBalance = Money.ZERO, openingDate = date("2026-01-01"), sortOrder = 1
    )
    private val hdfcCard = creditCard(id = 7).copy(name = "HDFC Regalia", bank = "HDFC", lastFourDigits = "1234")
    private val iciciCard = creditCard(id = 8).copy(name = "Amazon Pay ICICI", bank = "ICICI", lastFourDigits = "9012")
    private val salary = incomeSource(id = 3, amount = 85_000, dayOfMonth = 1, startDate = "2026-01-01")

    private val everything = SmsReviewContext(
        accounts = listOf(bank, cash),
        creditCards = listOf(hdfcCard, iciciCard),
        incomeSources = listOf(salary)
    )

    private fun planFor(
        body: String,
        on: String = "2026-11-16",
        context: SmsReviewContext = everything
    ): SmsImportPlan {
        val parsed = BankSmsParser.parse(body, date(on))
        assertTrue("should parse: ${parsed.ignoredReason}", parsed.isTransaction)
        return SmsImportPlanner.plan(parsed, parsed.date ?: date(on), context)
    }

    // ---- Card purchases --------------------------------------------------------------------

    @Test
    fun `a credit card spend goes on the card it names`() {
        val plan = planFor(
            "Spent Rs.1,299.00 On HDFC Bank Card 1234 At AMAZON PAY INDIA On 2026-11-16:20:15:42 " +
                "Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 1234 to 7308080808"
        )
        assertEquals(SmsImportPlan.CardSpend(hdfcCard, "1234"), plan)
        assertTrue(plan.writesExpense)
        assertEquals("Card ••1234", plan.summary(date("2026-11-16")))
    }

    @Test
    fun `a card named only by its last four is the matching credit card`() {
        // ICICI's alert says "Bank Card", not "credit card"; the digits settle it.
        val plan = planFor(
            "INR 2,499.00 spent using ICICI Bank Card XX9012 on 15-Nov-26 on IND*FLIPKART. " +
                "Avl Limit: INR 1,47,501.00."
        )
        assertEquals(SmsImportPlan.CardSpend(iciciCard, "9012"), plan)
    }

    @Test
    fun `a card whose digits match no credit card is a debit card from the bank`() {
        val plan = planFor(
            "INR 2,499.00 spent using ICICI Bank Card XX5555 on 15-Nov-26 on IND*FLIPKART. " +
                "Avl Limit: INR 1,47,501.00."
        )
        assertEquals(SmsImportPlan.BankDebit(PaymentMethod.DEBIT_CARD, bank), plan)
    }

    @Test
    fun `a debit card sharing a credit card's last four stays a debit card`() {
        val plan = planFor(
            "Rs.850.00 spent on HDFC Bank Debit Card ending 1234 at RELIANCE SMART on 16-11-26. " +
                "Avl bal Rs.41,650.00."
        )
        assertEquals(SmsImportPlan.BankDebit(PaymentMethod.DEBIT_CARD, bank), plan)
    }

    @Test
    fun `a credit card the user has not added still never touches cash`() {
        val plan = planFor(
            "Rs.1,850.00 spent on your SBI Credit Card ending 4455 at SWIGGY on 16/11/26."
        )
        assertEquals(SmsImportPlan.CardSpend(card = null, tail = "4455"), plan)
        assertEquals(
            "Credit card ••4455, not one you have added",
            plan.summary(date("2026-11-16"))
        )
    }

    @Test
    fun `a credit card with no digits goes to the only card there is`() {
        val onlyCard = everything.copy(creditCards = listOf(hdfcCard))
        val plan = planFor("Rs.640.00 spent on your credit card at UBER on 16-11-26.", context = onlyCard)
        assertEquals(SmsImportPlan.CardSpend(hdfcCard, null), plan)
    }

    @Test
    fun `a credit card with no digits is matched by the bank it names`() {
        val plan = planFor("Rs.640.00 spent on your ICICI Bank Credit Card at UBER on 16-11-26.")
        assertEquals(SmsImportPlan.CardSpend(iciciCard, null), plan)
    }

    @Test
    fun `a credit card naming another bank is not put on the only card`() {
        val onlyCard = everything.copy(creditCards = listOf(hdfcCard.copy(lastFourDigits = null)))
        val plan = planFor("Rs.640.00 spent on your SBI Credit Card at UBER on 16-11-26.", context = onlyCard)
        assertEquals(SmsImportPlan.CardSpend(null, null), plan)
    }

    // ---- Money out of the bank -------------------------------------------------------------

    private val hdfcUpi =
        "Sent Rs.500.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY LIMITED\nOn 16/11/26\n" +
            "Ref 631234567890\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"

    @Test
    fun `a upi payment leaves the first bank account by upi`() {
        val plan = planFor(hdfcUpi)
        assertEquals(SmsImportPlan.BankDebit(PaymentMethod.UPI, bank), plan)
        assertEquals("UPI from HDFC Savings", plan.summary(date("2026-11-16")))
    }

    @Test
    fun `an archived bank account is passed over`() {
        val closed = bank.copy(id = 9, name = "Old account", isArchived = true)
        val context = everything.copy(accounts = listOf(closed, bank, cash))
        assertEquals(SmsImportPlan.BankDebit(PaymentMethod.UPI, bank), planFor(hdfcUpi, context = context))
    }

    @Test
    fun `a neft payment is a bank transfer and an auto debit says so`() {
        assertEquals(
            SmsImportPlan.BankDebit(PaymentMethod.BANK_TRANSFER, bank),
            planFor("Rs.12,000.00 debited from A/c XX1234 on 16-11-26 towards NEFT to RAMESH. Avl Bal Rs.30,000.00")
        )
        assertEquals(
            SmsImportPlan.BankDebit(PaymentMethod.AUTO_DEBIT, bank),
            planFor("INR 1,899.00 debited from A/c XX1234 towards NETFLIX auto debit on 05-11-26.")
        )
    }

    @Test
    fun `with no bank account the expense is still recorded, against no account`() {
        val plan = planFor(hdfcUpi, context = everything.copy(accounts = listOf(cash)))
        assertEquals(SmsImportPlan.BankDebit(PaymentMethod.UPI, null), plan)
        assertEquals("UPI, no account set", plan.summary(date("2026-11-16")))
    }

    // ---- Cash withdrawals --------------------------------------------------------------------

    @Test
    fun `an atm withdrawal moves money from the bank to cash`() {
        val plan = planFor("Rs.5000 withdrawn from A/c XX1234 at HDFC ATM on 15-11-26. Avl Bal Rs.20000")
        assertEquals(SmsImportPlan.CashWithdrawal(from = bank, to = cash), plan)
        assertEquals("Cash withdrawal → Cash", plan.summary(date("2026-11-15")))
        // A transfer is not spending, so there is no category to pick.
        assertEquals(false, plan.writesExpense)
    }

    @Test
    fun `without a cash account a withdrawal is recorded as spending from the bank`() {
        val plan = planFor(
            "Rs.5000 withdrawn from A/c XX1234 at HDFC ATM on 15-11-26. Avl Bal Rs.20000",
            context = everything.copy(accounts = listOf(bank))
        )
        assertEquals(
            SmsImportPlan.BankDebit(PaymentMethod.DEBIT_CARD, bank, isCashWithdrawal = true),
            plan
        )
    }

    // ---- Credit card bills -------------------------------------------------------------------

    @Test
    fun `a card bill paid from the bank is a payment on that card`() {
        val plan = planFor(
            "Rs.8,500.00 debited from A/c XX1234 on 10-11-26 towards ICICI Bank Credit Card " +
                "XX9012 payment. Avl Bal Rs.33,845.00"
        )
        assertEquals(SmsImportPlan.CardBillPayment(iciciCard, bank), plan)
    }

    @Test
    fun `a card bill for a card the app does not know is held back`() {
        val plan = planFor(
            "Rs.8,500.00 debited from A/c XX1234 on 10-11-26 towards ICICI Bank Credit Card " +
                "XX7777 payment. Avl Bal Rs.33,845.00"
        )
        assertTrue(plan is SmsImportPlan.BankDebit)
        // The digits are the credit card's, so they do not make this a debit card payment.
        assertEquals(PaymentMethod.BANK_TRANSFER, (plan as SmsImportPlan.BankDebit).method)
        assertNotNull("it should start unticked", plan.caution)
    }

    // ---- Money in ----------------------------------------------------------------------------

    private val sbiSalary =
        "Dear Customer, Your A/C XXXXX4321 has been credited with Rs.%s on %s by NEFT from " +
            "ACME TECHNOLOGIES PVT LTD. Avl Bal Rs.90,500.00 -SBI"

    @Test
    fun `a salary paid the day before it is due settles the coming month`() {
        val plan = planFor(sbiSalary.format("85,000.00", "31/10/26"), on = "2026-10-31")
        assertEquals(SmsImportPlan.ScheduledIncome(salary, YearMonth.of(2026, 11)), plan)
        assertEquals("Salary for November", plan.summary(date("2026-10-31")))
    }

    @Test
    fun `a salary a little short of the usual amount still matches`() {
        val plan = planFor(sbiSalary.format("81,250.00", "02/11/26"), on = "2026-11-02")
        assertEquals(SmsImportPlan.ScheduledIncome(salary, YearMonth.of(2026, 11)), plan)
    }

    @Test
    fun `a credit more than a tenth away from the salary is other income`() {
        val plan = planFor(sbiSalary.format("76,000.00", "01/11/26"), on = "2026-11-01")
        assertEquals(SmsImportPlan.OtherIncome(bank), plan)
    }

    @Test
    fun `a credit more than five days from the due date is other income`() {
        val plan = planFor(sbiSalary.format("85,000.00", "07/11/26"), on = "2026-11-07")
        assertEquals(SmsImportPlan.OtherIncome(bank), plan)
    }

    @Test
    fun `an inactive income is never matched`() {
        val context = everything.copy(incomeSources = listOf(salary.copy(isActive = false)))
        val plan = planFor(sbiSalary.format("85,000.00", "01/11/26"), on = "2026-11-01", context = context)
        assertEquals(SmsImportPlan.OtherIncome(bank), plan)
    }

    @Test
    fun `a salary already marked received is not stamped on the month again`() {
        val received = incomeTransaction(sourceId = salary.id, amount = 85_000, date = "2026-11-01", periodKey = "2026-11")
        val context = everything.copy(incomeTransactions = listOf(received))
        val plan = planFor(sbiSalary.format("85,000.00", "01/11/26"), on = "2026-11-01", context = context)

        assertTrue(plan is SmsImportPlan.OtherIncome)
        assertEquals("Salary for November is already marked received", plan.caution)
    }

    @Test
    fun `the closest income wins when two could match`() {
        val bonus = incomeSource(id = 4, amount = 80_000, dayOfMonth = 1).copy(name = "Rent from flat")
        val context = everything.copy(incomeSources = listOf(bonus, salary))
        val plan = planFor(sbiSalary.format("84,500.00", "01/11/26"), on = "2026-11-01", context = context)
        assertEquals(SmsImportPlan.ScheduledIncome(salary, YearMonth.of(2026, 11)), plan)
    }

    @Test
    fun `other income goes to the first bank account`() {
        val plan = planFor(
            "Received Rs.1,250.00 in your Kotak Bank AC X5566 from priya@okicici on 15-11-26.UPI Ref:631234567890."
        )
        assertEquals(SmsImportPlan.OtherIncome(bank), plan)
        assertEquals("Income to HDFC Savings", plan.summary(date("2026-11-15")))
        assertNull(plan.caution)
    }
}
