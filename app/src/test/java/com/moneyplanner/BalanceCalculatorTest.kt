package com.moneyplanner

import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.model.BalanceAdjustment
import com.moneyplanner.domain.model.CreditCardPayment
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.SettlementDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceCalculatorTest {

    private fun snapshot(
        today: String = "2026-08-18",
        block: FinancialSnapshot.() -> FinancialSnapshot = { this }
    ) = FinancialSnapshot(today = date(today), accounts = listOf(account(opening = 50_000))).block()

    @Test
    fun `balance starts from the opening balances of the accounts`() {
        val snap = snapshot()
        assertEquals(rupees(50_000), BalanceCalculator.currentBalance(snap))
    }

    @Test
    fun `income raises the balance and cash spending lowers it`() {
        val snap = snapshot().copy(
            incomeTransactions = listOf(incomeTransaction(amount = 55_000, date = "2026-08-01")),
            expenses = listOf(expense(amount = 12_000, date = "2026-08-05"))
        )
        assertEquals(rupees(93_000), BalanceCalculator.currentBalance(snap))
    }

    @Test
    fun `a credit card purchase does not reduce cash on the day it happens`() {
        val snap = snapshot().copy(
            expenses = listOf(
                expense(id = 1, amount = 5_000, date = "2026-08-05", method = PaymentMethod.CREDIT_CARD)
            )
        )
        assertEquals(
            "spending on a card raises the card outstanding, it does not move cash",
            rupees(50_000),
            BalanceCalculator.currentBalance(snap)
        )
    }

    @Test
    fun `cash leaves the account when the card bill is paid`() {
        val snap = snapshot().copy(
            expenses = listOf(
                expense(id = 1, amount = 5_000, date = "2026-08-05", method = PaymentMethod.CREDIT_CARD)
            ),
            creditCardPayments = listOf(
                CreditCardPayment(
                    id = 1, cardId = 1, amount = rupees(5_000),
                    paidDate = date("2026-08-15"), accountId = null, notes = ""
                )
            )
        )
        assertEquals(
            "the same 5,000 must be subtracted exactly once, when the bill is paid",
            rupees(45_000),
            BalanceCalculator.currentBalance(snap)
        )
    }

    @Test
    fun `an expense dated in the future does not touch today's balance`() {
        val snap = snapshot().copy(
            expenses = listOf(expense(amount = 9_000, date = "2026-09-05"))
        )
        assertEquals(rupees(50_000), BalanceCalculator.currentBalance(snap))
        assertEquals(
            rupees(41_000),
            BalanceCalculator.balanceAsOf(snap, date("2026-09-30"))
        )
    }

    @Test
    fun `settlements move the balance in the direction the money went`() {
        val snap = snapshot().copy(
            settlements = listOf(
                settlement(id = 1, amount = 3_000, date = "2026-08-10"),
                settlement(
                    id = 2, amount = 1_000,
                    direction = SettlementDirection.PAID_TO_THEM, date = "2026-08-12"
                )
            )
        )
        assertEquals(rupees(52_000), BalanceCalculator.currentBalance(snap))
    }

    @Test
    fun `money put into a savings goal stays in the balance and is reported as earmarked`() {
        val snap = snapshot().copy(
            goals = listOf(goal()),
            contributions = listOf(contribution(amount = 10_000, date = "2026-08-02"))
        )
        val breakdown = BalanceCalculator.breakdown(snap)

        assertEquals(
            "setting money aside is not spending it",
            rupees(50_000),
            breakdown.available
        )
        assertEquals(rupees(10_000), breakdown.earmarkedForGoals)
        assertEquals(rupees(40_000), breakdown.unallocated)
    }

    @Test
    fun `a reconciliation adjustment corrects the balance without rewriting history`() {
        val snap = snapshot().copy(
            adjustments = listOf(
                BalanceAdjustment(
                    id = 1, accountId = 1, delta = rupees(-7_500),
                    date = date("2026-08-15"), reason = "Reconciled with passbook"
                )
            )
        )
        assertEquals(rupees(42_500), BalanceCalculator.currentBalance(snap))
    }

    @Test
    fun `a negative balance is reported honestly rather than clamped`() {
        val snap = snapshot().copy(
            expenses = listOf(expense(amount = 60_000, date = "2026-08-05"))
        )
        val balance = BalanceCalculator.currentBalance(snap)
        assertTrue(balance.isNegative)
        assertEquals(rupees(-10_000), balance)
    }

    @Test
    fun `an account opened later does not count before its opening date`() {
        val snap = snapshot().copy(
            accounts = listOf(
                account(id = 1, opening = 50_000, openingDate = "2026-01-01"),
                account(id = 2, opening = 20_000, openingDate = "2026-09-01")
            )
        )
        assertEquals(rupees(50_000), BalanceCalculator.currentBalance(snap))
        assertEquals(
            rupees(70_000),
            BalanceCalculator.balanceAsOf(snap, date("2026-09-02"))
        )
    }

    @Test
    fun `the breakdown explains every component of the balance`() {
        val snap = snapshot().copy(
            incomeTransactions = listOf(incomeTransaction(amount = 55_000, date = "2026-08-01")),
            expenses = listOf(expense(amount = 12_000, date = "2026-08-05")),
            settlements = listOf(settlement(amount = 2_000, date = "2026-08-08"))
        )
        val breakdown = BalanceCalculator.breakdown(snap)

        val rebuilt = breakdown.openingBalance + breakdown.adjustments +
            breakdown.incomeReceived + breakdown.settlementsReceived -
            breakdown.cashSpent - breakdown.creditCardBillsPaid - breakdown.settlementsPaid

        assertEquals(
            "the parts shown to the user must add up to the number they are shown",
            breakdown.available,
            rebuilt
        )
    }

    @Test
    fun `net position subtracts loans and card debt from what is owned`() {
        val snap = snapshot().copy(
            emis = listOf(emi(emiAmount = 4_500, totalInstallments = 10)),
            creditCards = listOf(creditCard(outstanding = 8_000)),
            ledgerEntries = listOf(ledgerEntry(amount = 5_000)),
            people = listOf(person())
        )
        val net = BalanceCalculator.netPosition(snap)

        assertEquals(rupees(50_000), net.available)
        assertEquals(rupees(5_000), net.receivable)
        assertEquals(rupees(45_000), net.loanOutstanding)
        assertEquals(rupees(8_000), net.creditCardOutstanding)
        assertEquals(rupees(2_000), net.net)
    }
}
