package com.moneyplanner

import com.moneyplanner.domain.calc.PaymentSuggestion
import com.moneyplanner.domain.calc.SuggestionTarget
import com.moneyplanner.domain.calc.UnlinkedCalculator
import com.moneyplanner.domain.calc.UnlinkedKind
import com.moneyplanner.domain.model.BillPayment
import com.moneyplanner.domain.model.CreditCardPayment
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlinkedCalculatorTest {

    private fun base(vararg accounts: com.moneyplanner.domain.model.Account = arrayOf(account(opening = 10_000))) =
        FinancialSnapshot(
            today = date("2026-11-16"),
            accounts = accounts.toList(),
            categories = listOf(category(1), category(2, "Rent")),
            people = listOf(person(1, "Amit"))
        )

    // ---- No account --------------------------------------------------------------------

    @Test
    fun `money that moved through no account is listed, whatever kind of record it is`() {
        val snap = base().copy(
            expenses = listOf(expense(id = 1, amount = 500, date = "2026-11-10")),
            incomeTransactions = listOf(incomeTransaction(id = 2, amount = 1_000, date = "2026-11-11")),
            settlements = listOf(settlement(id = 3, amount = 200, date = "2026-11-12")),
            creditCardPayments = listOf(
                CreditCardPayment(id = 4, cardId = 1, amount = rupees(900), paidDate = date("2026-11-13"), accountId = null, notes = "")
            )
        )
        val kinds = UnlinkedCalculator.find(snap).noAccount.map { it.kind }.toSet()
        assertEquals(UnlinkedKind.entries.toSet(), kinds)
    }

    @Test
    fun `nothing is flagged before the user has an account`() {
        val snap = base(*emptyArray()).copy(expenses = listOf(expense(id = 1, amount = 500, date = "2026-11-10")))
        assertTrue(UnlinkedCalculator.find(snap).isEmpty)
    }

    @Test
    fun `a card purchase with no card asks for the card`() {
        val snap = base().copy(
            expenses = listOf(expense(id = 1, amount = 500, date = "2026-11-10", method = PaymentMethod.CREDIT_CARD))
        )
        assertTrue(UnlinkedCalculator.find(snap).noAccount.single().needsCard)
    }

    @Test
    fun `an expense filed against an account is not listed`() {
        val snap = base().copy(expenses = listOf(expense(id = 1, amount = 500, date = "2026-11-10").assignedTo(1)))
        assertTrue(UnlinkedCalculator.find(snap).isEmpty)
    }

    // ---- Not matched -------------------------------------------------------------------

    private val rent = bill(id = 7, name = "Rent", amount = 13_000, dueDay = 5)

    @Test
    fun `an everyday expense of exactly the rent near its due date is offered as the rent`() {
        val snap = base().copy(
            bills = listOf(rent),
            expenses = listOf(expense(id = 1, amount = 13_000, date = "2026-11-06", description = "Transfer to landlord").assignedTo(1))
        )
        val suggestion = UnlinkedCalculator.find(snap).suggestions.single()
        val target = suggestion.target as SuggestionTarget.BillOccurrence
        assertEquals(7L, target.bill.id)
        assertEquals("2026-11", target.periodKey)
    }

    @Test
    fun `nothing is offered for a period already paid`() {
        val snap = base().copy(
            bills = listOf(rent),
            billPayments = listOf(
                BillPayment(1, 7, "2026-11", rupees(13_000), date("2026-11-05"), date("2026-11-05"), "")
            ),
            expenses = listOf(expense(id = 1, amount = 13_000, date = "2026-11-06").assignedTo(1))
        )
        assertTrue(UnlinkedCalculator.find(snap).suggestions.isEmpty())
    }

    @Test
    fun `an amount that only roughly agrees needs the name too`() {
        val near = expense(id = 1, amount = 12_500, date = "2026-11-06", description = "Groceries").assignedTo(1)
        val named = expense(id = 2, amount = 12_500, date = "2026-11-06", description = "Rent for November").assignedTo(1)
        assertTrue(UnlinkedCalculator.find(base().copy(bills = listOf(rent), expenses = listOf(near))).suggestions.isEmpty())
        assertEquals(1, UnlinkedCalculator.find(base().copy(bills = listOf(rent), expenses = listOf(named))).suggestions.size)
    }

    @Test
    fun `a dismissed suggestion is not offered again`() {
        val snap = base().copy(
            bills = listOf(rent),
            expenses = listOf(expense(id = 1, amount = 13_000, date = "2026-11-06").assignedTo(1))
        )
        val dismissed = setOf(PaymentSuggestion.keyFor(1))
        assertTrue(UnlinkedCalculator.find(snap, dismissed).suggestions.isEmpty())
    }

    @Test
    fun `an EMI auto-debit of the installment amount is offered as the installment`() {
        val loan = emi(id = 3, name = "Car loan", emiAmount = 4_500, firstDueDate = "2026-01-05", totalInstallments = 36)
        val snap = base().copy(
            emis = listOf(loan),
            expenses = listOf(expense(id = 1, amount = 4_500, date = "2026-11-05", description = "NACH debit").assignedTo(1))
        )
        val target = UnlinkedCalculator.find(snap).suggestions.single().target as SuggestionTarget.EmiInstallment
        assertEquals(3L, target.emi.id)
    }

    @Test
    fun `a card bill paid from the bank and typed as an expense is offered as the card bill`() {
        val card = creditCard(outstanding = 8_000, statementDay = 1, dueDay = 15)
            .copy(statementDate = date("2026-11-01"))
        val snap = base().copy(
            creditCards = listOf(card),
            expenses = listOf(expense(id = 1, amount = 8_000, date = "2026-11-12", description = "HDFC credit card bill").assignedTo(1))
        )
        assertTrue(UnlinkedCalculator.find(snap).suggestions.single().target is SuggestionTarget.CardBill)
    }

    @Test
    fun `one rent is offered for one expense, not for two`() {
        val snap = base().copy(
            bills = listOf(rent),
            expenses = listOf(
                expense(id = 1, amount = 13_000, date = "2026-11-05").assignedTo(1),
                expense(id = 2, amount = 13_000, date = "2026-11-09").assignedTo(1)
            )
        )
        val suggestions = UnlinkedCalculator.find(snap).suggestions
        assertEquals(1, suggestions.size)
        assertEquals("the closer one wins", 1L, suggestions.single().expense.id)
    }

    @Test
    fun `an expense offered as a payment is not listed twice`() {
        val snap = base().copy(
            bills = listOf(rent),
            expenses = listOf(expense(id = 1, amount = 13_000, date = "2026-11-06"))
        )
        val records = UnlinkedCalculator.find(snap)
        assertEquals(1, records.suggestions.size)
        assertTrue(records.noAccount.isEmpty())
    }
}
