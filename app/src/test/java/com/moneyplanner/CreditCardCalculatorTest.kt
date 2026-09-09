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

    // ---- Statement cycles ----------------------------------------------------------

    private fun cycleCard(statementDay: Int, dueDay: Int) =
        creditCard(id = 1, statementDay = statementDay, dueDay = dueDay)

    @Test
    fun `a due day after the statement day falls in the same month`() {
        // Statements on the 1st, due on the 20th: the bill cut on 1 Sep is due 20 Sep.
        val cycle = CreditCardCalculator.currentCycle(
            cycleCard(statementDay = 1, dueDay = 20),
            date("2026-08-15")
        )
        assertEquals(date("2026-09-01"), cycle.statementOn)
        assertEquals(date("2026-09-20"), cycle.dueOn)
    }

    @Test
    fun `a due day before the statement day falls in the next month`() {
        // Statements on the 25th, due on the 5th: the bill cut on 25 Aug is due 5 Sep.
        val cycle = CreditCardCalculator.currentCycle(
            cycleCard(statementDay = 25, dueDay = 5),
            date("2026-08-15")
        )
        assertEquals(date("2026-08-25"), cycle.statementOn)
        assertEquals(date("2026-09-05"), cycle.dueOn)
    }

    @Test
    fun `a due day equal to the statement day gives a month to pay, not no time`() {
        val cycle = CreditCardCalculator.currentCycle(
            cycleCard(statementDay = 10, dueDay = 10),
            date("2026-08-01")
        )
        assertEquals(date("2026-08-10"), cycle.statementOn)
        assertEquals(date("2026-09-10"), cycle.dueOn)
    }

    @Test
    fun `once the statement day has passed the open cycle is the next one`() {
        val cycle = CreditCardCalculator.currentCycle(
            cycleCard(statementDay = 5, dueDay = 25),
            date("2026-08-06")
        )
        assertEquals(date("2026-09-05"), cycle.statementOn)
        assertEquals(date("2026-08-06"), cycle.opensOn)
    }

    @Test
    fun `the cycle opens the day after the previous statement`() {
        val cycle = CreditCardCalculator.currentCycle(
            cycleCard(statementDay = 5, dueDay = 25),
            date("2026-08-20")
        )
        assertEquals(date("2026-08-06"), cycle.opensOn)
        assertEquals(date("2026-09-05"), cycle.statementOn)
    }

    @Test
    fun `a statement day of 31 lands on the last day of a short month`() {
        val cycle = CreditCardCalculator.currentCycle(
            cycleCard(statementDay = 31, dueDay = 20),
            date("2026-02-10")
        )
        assertEquals(date("2026-02-28"), cycle.statementOn)
    }

    @Test
    fun `only purchases inside the open cycle count towards the next bill`() {
        val card = cycleCard(statementDay = 5, dueDay = 25)
        val expenses = listOf(
            // Before the cycle opened, so already billed.
            purchase(id = 1, amount = 1_000, on = "2026-08-03"),
            // Inside the open cycle.
            purchase(id = 2, amount = 2_000, on = "2026-08-10"),
            purchase(id = 3, amount = 500, on = "2026-09-05"),
            // After the statement is cut, so on the bill after this one.
            purchase(id = 4, amount = 9_000, on = "2026-09-06")
        )
        val spend = CreditCardCalculator.currentCycleSpend(card, expenses, date("2026-08-20"))

        assertEquals(rupees(2_500), spend)
    }
}
