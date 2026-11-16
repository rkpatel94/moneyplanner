package com.moneyplanner

import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.calc.CreditCardCalculator
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.ForecastItemKind
import com.moneyplanner.domain.model.CreditCardPayment
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A card's statement bill falls due on its own due date.
 *
 * It used to be booked on the due date of the cycle still open, a month later. Between a
 * statement and its due date, exactly when the bill is outstanding, the forecast showed it
 * a month late and the reminder, never within seven days of it, did not fire.
 */
class CardBillTimingTest {

    /** Statement on the 15th, payable by the 4th of the next month. */
    private val card = creditCard(outstanding = 20_000, statementDay = 15, dueDay = 4)
        .copy(statementDate = date("2026-11-15"), lastUpdated = date("2026-11-16"))

    private fun snapshot(today: String, vararg extra: (FinancialSnapshot) -> FinancialSnapshot): FinancialSnapshot {
        var snap = FinancialSnapshot(
            today = date(today),
            accounts = listOf(account(opening = 100_000, openingDate = "2026-01-01")),
            categories = listOf(category(1)),
            creditCards = listOf(card)
        )
        extra.forEach { snap = it(snap) }
        return snap
    }

    private fun payment(amount: Long, on: String) = CreditCardPayment(
        id = 1, cardId = card.id, amount = rupees(amount), paidDate = date(on), accountId = 1, notes = ""
    )

    @Test
    fun `the statement on file is due on its own due date`() {
        val bill = CreditCardCalculator.statementBill(card, emptyList(), date("2026-12-02"))
        assertEquals(date("2026-11-15"), bill.statementOn)
        assertEquals(date("2026-12-04"), bill.dueOn)
        assertEquals(rupees(20_000), bill.remaining)
    }

    @Test
    fun `the forecast books the bill in the month it is due, not the one after`() {
        val months = ForecastCalculator.forecast(snapshot("2026-12-02"), 2)
        val december = months[0].items.filter { it.kind == ForecastItemKind.CREDIT_CARD }
        assertEquals(listOf(date("2026-12-04")), december.map { it.date })
        assertEquals(rupees(20_000), months[0].creditCardOutflow)
        assertEquals("nothing left over for January", rupees(0), months[1].creditCardOutflow)
    }

    @Test
    fun `a bill past its due date is carried to today and marked overdue`() {
        val item = ForecastCalculator.forecast(snapshot("2026-12-09"), 1)[0].items
            .single { it.kind == ForecastItemKind.CREDIT_CARD }
        assertTrue(item.isOverdue)
        assertEquals(date("2026-12-09"), item.date)
        assertEquals(date("2026-12-04"), item.originallyDue)
    }

    @Test
    fun `spending since the statement is due after the next statement, as an estimate`() {
        val snap = snapshot("2026-11-20", {
            it.copy(
                expenses = listOf(
                    expense(id = 1, amount = 3_000, date = "2026-11-18", method = PaymentMethod.CREDIT_CARD)
                        .onCard(card.id)
                )
            )
        })
        val items = ForecastCalculator.forecast(snap, 3).flatMap { it.items }
            .filter { it.kind == ForecastItemKind.CREDIT_CARD }

        val statement = items.single { !it.isEstimate }
        val next = items.single { it.isEstimate }
        assertEquals(date("2026-12-04"), statement.date)
        assertEquals(rupees(20_000), statement.amount)
        assertEquals("on the 15 Dec statement, due 4 Jan", date("2027-01-04"), next.date)
        assertEquals(rupees(3_000), next.amount)
    }

    @Test
    fun `paying the bill no longer hides what was spent between the statement and the payment`() {
        // Before the fix, paying moved the card's "last updated" date to the payment day,
        // and every purchase before that day dropped out of what the card owed.
        val spentAfterStatement = expense(id = 1, amount = 3_000, date = "2026-11-18", method = PaymentMethod.CREDIT_CARD)
            .onCard(card.id)
        val paid = card.copy(currentOutstanding = rupees(0))
        assertEquals(rupees(3_000), CreditCardCalculator.projectedOutstanding(paid, listOf(spentAfterStatement)))
    }

    @Test
    fun `only payments after the statement date come off the figure`() {
        assertTrue(CreditCardCalculator.paymentReducesFigure(card, date("2026-11-20")))
        assertFalse(CreditCardCalculator.paymentReducesFigure(card, date("2026-11-15")))
        // A card whose figure predates the statement date keeps its old behaviour.
        assertTrue(CreditCardCalculator.paymentReducesFigure(card.copy(statementDate = null), date("2026-11-01")))
    }

    @Test
    fun `the minimum still due falls as payments come in`() {
        val withMinimum = card.copy(minimumDue = rupees(1_000), currentOutstanding = rupees(19_500))
        val bill = CreditCardCalculator.statementBill(withMinimum, listOf(payment(500, "2026-11-20")), date("2026-11-21"))
        assertEquals(rupees(500), bill.minimumRemaining)
        assertEquals(rupees(500), bill.paidSinceStatement)
    }

    @Test
    fun `a card in credit takes the next bill down before anything is projected`() {
        val inCredit = card.copy(currentOutstanding = rupees(-2_000))
        val snap = snapshot("2026-11-20", {
            it.copy(
                creditCards = listOf(inCredit),
                expenses = listOf(
                    expense(id = 1, amount = 3_000, date = "2026-11-18", method = PaymentMethod.CREDIT_CARD).onCard(card.id)
                )
            )
        })
        val outflow = ForecastCalculator.forecast(snap, 3).sumOf { it.creditCardOutflow.paise }
        assertEquals(rupees(1_000).paise, outflow)
    }

    @Test
    fun `a figure with no date at all is read as the bill due next`() {
        val undated = creditCard(outstanding = 8_000, statementDay = 1, dueDay = 15)
        assertNull(undated.lastUpdated)
        val bill = CreditCardCalculator.statementBill(undated, emptyList(), date("2026-08-31"))
        assertEquals(date("2026-09-15"), bill.dueOn)
    }

    @Test
    fun `the dashboard total includes spending since the statement`() {
        val snap = snapshot("2026-11-20", {
            it.copy(
                creditCards = listOf(card.copy(currentOutstanding = rupees(0))),
                expenses = listOf(
                    expense(id = 1, amount = 3_000, date = "2026-11-18", method = PaymentMethod.CREDIT_CARD).onCard(card.id)
                )
            )
        })
        assertEquals(rupees(3_000), BalanceCalculator.totalCreditCardOutstanding(snap))
    }
}
