package com.moneyplanner

import com.moneyplanner.domain.calc.CreditCardCalculator
import com.moneyplanner.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Card spending recorded after the statement figure was entered.
 *
 * The property that matters is that nothing is counted twice. A statement already contains
 * every purchase made before it was issued, so only what was charged afterwards may be
 * added to it. Getting that wrong would overstate a debt, which is the one direction of
 * error that makes someone pay money they do not owe.
 */
class CreditCardCalculatorTest {

    private fun card(
        outstanding: Long = 20_000,
        limit: Long = 100_000,
        lastUpdated: String? = "2026-08-31"
    ) = creditCard(id = 1, limit = limit, outstanding = outstanding)
        .copy(lastUpdated = lastUpdated?.let { date(it) })

    private fun purchase(id: Long, amount: Long, on: String, cardId: Long? = 1) =
        expense(id = id, amount = amount, date = on, method = PaymentMethod.CREDIT_CARD)
            .copy(creditCardId = cardId)

    @Test
    fun `a purchase made before the statement is already inside it`() {
        val c = card()
        val spend = CreditCardCalculator.unbilledSpendOn(
            c,
            listOf(purchase(id = 1, amount = 3_000, on = "2026-08-15"))
        )
        assertEquals(rupees(0), spend)
        assertEquals(rupees(20_000), CreditCardCalculator.projectedOutstanding(c, listOf()))
    }

    @Test
    fun `a purchase on the statement date itself counts as billed`() {
        val c = card(lastUpdated = "2026-08-31")
        val spend = CreditCardCalculator.unbilledSpendOn(
            c,
            listOf(purchase(id = 1, amount = 3_000, on = "2026-08-31"))
        )
        assertEquals(rupees(0), spend)
    }

    @Test
    fun `spending after the statement is added on top of it`() {
        val c = card(outstanding = 20_000)
        val expenses = listOf(
            purchase(id = 1, amount = 3_000, on = "2026-09-02"),
            purchase(id = 2, amount = 1_500, on = "2026-09-05")
        )
        assertEquals(rupees(4_500), CreditCardCalculator.unbilledSpendOn(c, expenses))
        assertEquals(rupees(24_500), CreditCardCalculator.projectedOutstanding(c, expenses))
    }

    @Test
    fun `spending on another card is not counted against this one`() {
        val c = card()
        val expenses = listOf(purchase(id = 1, amount = 3_000, on = "2026-09-02", cardId = 2))
        assertEquals(rupees(0), CreditCardCalculator.unbilledSpendOn(c, expenses))
    }

    @Test
    fun `an expense with no card named is not charged to any card`() {
        val c = card()
        val expenses = listOf(purchase(id = 1, amount = 3_000, on = "2026-09-02", cardId = null))
        assertEquals(rupees(0), CreditCardCalculator.unbilledSpendOn(c, expenses))
    }

    @Test
    fun `a cash expense pointing at a card is ignored`() {
        // Changing the payment method away from the card should clear the card, but a
        // record written before that rule existed must not still charge the card.
        val c = card()
        val expenses = listOf(
            expense(id = 1, amount = 3_000, date = "2026-09-02", method = PaymentMethod.CASH)
                .copy(creditCardId = 1)
        )
        assertEquals(rupees(0), CreditCardCalculator.unbilledSpendOn(c, expenses))
    }

    @Test
    fun `a card with no statement recorded counts every purchase as unbilled`() {
        val c = card(outstanding = 0, lastUpdated = null)
        val expenses = listOf(
            purchase(id = 1, amount = 3_000, on = "2026-08-15"),
            purchase(id = 2, amount = 2_000, on = "2026-09-05")
        )
        assertEquals(rupees(5_000), CreditCardCalculator.unbilledSpendOn(c, expenses))
    }

    @Test
    fun `utilisation reflects what is really owed, not just the statement`() {
        val c = card(outstanding = 60_000, limit = 100_000)
        val expenses = listOf(purchase(id = 1, amount = 25_000, on = "2026-09-02"))

        assertEquals(0.60f, c.utilisation, 0.001f)
        assertEquals(0.85f, CreditCardCalculator.projectedUtilisation(c, expenses), 0.001f)
    }

    @Test
    fun `spending since the statement can push a card past its limit`() {
        val snap = com.moneyplanner.domain.model.FinancialSnapshot(
            today = date("2026-09-08"),
            creditCards = listOf(card(outstanding = 95_000, limit = 100_000)),
            expenses = listOf(purchase(id = 1, amount = 8_000, on = "2026-09-02"))
        )
        val status = CreditCardCalculator.allStatuses(snap).single()

        assertTrue(status.hasUnbilled)
        assertTrue(status.isOverLimit)
        assertEquals(rupees(103_000), status.projectedOutstanding)
        assertEquals(rupees(0), status.availableLimit)
        assertEquals(1, status.purchaseCount)
    }

    @Test
    fun `a card with nothing charged since its statement reports no unbilled spending`() {
        val snap = com.moneyplanner.domain.model.FinancialSnapshot(
            today = date("2026-09-08"),
            creditCards = listOf(card()),
            expenses = emptyList()
        )
        val status = CreditCardCalculator.allStatuses(snap).single()

        assertFalse(status.hasUnbilled)
        assertEquals(rupees(20_000), status.projectedOutstanding)
    }
}
