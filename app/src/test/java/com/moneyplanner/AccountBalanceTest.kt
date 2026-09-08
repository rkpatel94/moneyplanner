package com.moneyplanner

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.calc.SettlementCalculator
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.SettlementDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the money sits, as opposed to how much of it there is.
 *
 * The property that matters throughout: the per-account figures plus whatever could not be
 * placed must always come to the same total the dashboard shows. If those two ever
 * disagree, one of the screens is lying.
 */
class AccountBalanceTest {

    private fun bank(id: Long, name: String, opening: Long, order: Int = 0) = Account(
        id = id,
        name = name,
        type = AccountType.BANK,
        openingBalance = rupees(opening),
        openingDate = date("2026-01-01"),
        sortOrder = order
    )

    private fun cash(id: Long, opening: Long, order: Int = 1) = Account(
        id = id,
        name = "Cash",
        type = AccountType.CASH,
        openingBalance = rupees(opening),
        openingDate = date("2026-01-01"),
        sortOrder = order
    )

    private fun snapshot(vararg accounts: Account) = FinancialSnapshot(
        today = date("2026-08-18"),
        accounts = accounts.toList()
    )

    @Test
    fun `each account starts from its own opening balance`() {
        val snap = snapshot(bank(1, "Savings", 40_000), bank(2, "Current", 10_000))
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(40_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(10_000), balances.rows.first { it.account.id == 2L }.balance)
        assertEquals(rupees(50_000), balances.total)
        assertFalse(balances.hasUnassigned)
    }

    @Test
    fun `spending reduces only the account it was paid from`() {
        val snap = snapshot(bank(1, "Savings", 40_000), bank(2, "Current", 10_000)).copy(
            expenses = listOf(
                expense(id = 1, amount = 5_000, date = "2026-08-05").copy(accountId = 2)
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(40_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(5_000), balances.rows.first { it.account.id == 2L }.balance)
        assertEquals(rupees(45_000), balances.total)
    }

    @Test
    fun `a card purchase does not touch any account until the bill is paid`() {
        val snap = snapshot(bank(1, "Savings", 40_000)).copy(
            expenses = listOf(
                expense(
                    id = 1,
                    amount = 5_000,
                    date = "2026-08-05",
                    method = PaymentMethod.CREDIT_CARD
                ).copy(accountId = 1)
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)
        assertEquals(rupees(40_000), balances.rows.single().balance)
    }

    @Test
    fun `a transfer moves money without changing the total`() {
        val snap = snapshot(bank(1, "Savings", 40_000), bank(2, "Current", 10_000)).copy(
            transfers = listOf(
                AccountTransfer(
                    id = 1,
                    fromAccountId = 1,
                    toAccountId = 2,
                    amount = rupees(15_000),
                    date = date("2026-08-10")
                )
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(25_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(25_000), balances.rows.first { it.account.id == 2L }.balance)
        assertEquals(
            "moving money between your own accounts creates none and destroys none",
            rupees(50_000),
            balances.total
        )
        assertFalse(balances.hasUnassigned)
    }

    @Test
    fun `a transfer dated in the future has not happened yet`() {
        val snap = snapshot(bank(1, "Savings", 40_000), bank(2, "Current", 10_000)).copy(
            transfers = listOf(
                AccountTransfer(
                    id = 1,
                    fromAccountId = 1,
                    toAccountId = 2,
                    amount = rupees(15_000),
                    date = date("2026-09-10")
                )
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)
        assertEquals(rupees(40_000), balances.rows.first { it.account.id == 1L }.balance)
    }

    @Test
    fun `spending with no account named is reported as unassigned rather than guessed`() {
        val snap = snapshot(bank(1, "Savings", 40_000)).copy(
            expenses = listOf(expense(id = 1, amount = 5_000, date = "2026-08-05"))
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(
            "the account itself is untouched by an entry that never named it",
            rupees(40_000),
            balances.rows.single().balance
        )
        assertEquals(rupees(35_000), balances.total)
        assertTrue(balances.hasUnassigned)
        assertEquals(Money(-500_000), balances.unassigned)
    }

    @Test
    fun `the parts always reconcile with the total`() {
        val snap = snapshot(bank(1, "Savings", 40_000), bank(2, "Current", 10_000)).copy(
            incomeTransactions = listOf(
                incomeTransaction(id = 1, amount = 55_000, date = "2026-08-01").copy(accountId = 1)
            ),
            expenses = listOf(
                expense(id = 1, amount = 5_000, date = "2026-08-05").copy(accountId = 2),
                // Deliberately unassigned, so the reconciliation has something to explain.
                expense(id = 2, amount = 1_000, date = "2026-08-06")
            ),
            transfers = listOf(
                AccountTransfer(
                    id = 1,
                    fromAccountId = 1,
                    toAccountId = 2,
                    amount = rupees(20_000),
                    date = date("2026-08-07")
                )
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)
        val rebuilt = balances.rows.fold(Money.ZERO) { acc, row -> acc + row.balance } +
            balances.unassigned

        assertEquals(
            "what each account holds, plus what could not be placed, is what you have",
            balances.total,
            rebuilt
        )
        assertEquals(BalanceCalculator.currentBalance(snap), balances.total)
    }

    @Test
    fun `income credited to an account raises that account and no other`() {
        val snap = snapshot(bank(1, "Savings", 40_000), bank(2, "Current", 10_000)).copy(
            incomeTransactions = listOf(
                incomeTransaction(id = 1, amount = 60_000, date = "2026-08-01")
                    .copy(accountId = 2)
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(40_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(70_000), balances.rows.first { it.account.id == 2L }.balance)
        assertEquals(rupees(110_000), balances.total)
        assertFalse(balances.hasUnassigned)
    }

    @Test
    fun `income filed against no account is reported as unassigned rather than guessed`() {
        val snap = snapshot(bank(1, "Savings", 40_000)).copy(
            incomeTransactions = listOf(
                incomeTransaction(id = 1, amount = 60_000, date = "2026-08-01")
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(40_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(100_000), balances.total)
        assertTrue(balances.hasUnassigned)
        assertEquals(rupees(60_000), balances.unassigned)
    }

    @Test
    fun `withdrawing cash moves money out of the bank without spending it`() {
        val snap = snapshot(bank(1, "Bank account", 50_000), cash(2, 0)).copy(
            transfers = listOf(
                AccountTransfer(
                    id = 1,
                    fromAccountId = 1,
                    toAccountId = 2,
                    amount = rupees(8_000),
                    date = date("2026-08-10")
                )
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(42_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(8_000), balances.rows.first { it.account.id == 2L }.balance)
        // The money moved, it was not spent, so the total is untouched.
        assertEquals(rupees(50_000), balances.total)
        assertFalse(balances.hasUnassigned)
    }

    @Test
    fun `depositing cash back moves it the other way and still changes no total`() {
        val snap = snapshot(bank(1, "Bank account", 42_000), cash(2, 8_000)).copy(
            transfers = listOf(
                AccountTransfer(
                    id = 1,
                    fromAccountId = 2,
                    toAccountId = 1,
                    amount = rupees(3_000),
                    date = date("2026-08-12")
                )
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(45_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(5_000), balances.rows.first { it.account.id == 2L }.balance)
        assertEquals(rupees(50_000), balances.total)
    }

    @Test
    fun `cash spending draws down the cash account, not the bank`() {
        val snap = snapshot(bank(1, "Bank account", 42_000), cash(2, 8_000)).copy(
            expenses = listOf(
                expense(id = 1, amount = 1_200, date = "2026-08-14")
                    .copy(accountId = 2, paymentMethod = PaymentMethod.CASH)
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(42_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(6_800), balances.rows.first { it.account.id == 2L }.balance)
        assertEquals(rupees(48_800), balances.total)
        assertFalse(balances.hasUnassigned)
    }

    @Test
    fun `paying a person draws down the account the money actually came from`() {
        val snap = snapshot(bank(1, "Bank account", 42_000), cash(2, 8_000)).copy(
            settlements = listOf(
                settlement(
                    id = 1,
                    amount = 1_000,
                    direction = SettlementDirection.PAID_TO_THEM,
                    date = "2026-08-14"
                ).copy(accountId = 2, paymentMethod = PaymentMethod.CASH)
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(42_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(7_000), balances.rows.first { it.account.id == 2L }.balance)
        assertEquals(rupees(49_000), balances.total)
        assertFalse(balances.hasUnassigned)
    }

    @Test
    fun `money received from a person lands in the account it was received into`() {
        val snap = snapshot(bank(1, "Bank account", 42_000), cash(2, 8_000)).copy(
            settlements = listOf(
                settlement(
                    id = 1,
                    amount = 2_500,
                    direction = SettlementDirection.RECEIVED_FROM_THEM,
                    date = "2026-08-14"
                ).copy(accountId = 1)
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(44_500), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(8_000), balances.rows.first { it.account.id == 2L }.balance)
        assertEquals(rupees(52_500), balances.total)
        assertFalse(balances.hasUnassigned)
    }

    @Test
    fun `a settlement filed against no account is reported as unassigned rather than guessed`() {
        val snap = snapshot(bank(1, "Bank account", 42_000)).copy(
            settlements = listOf(
                settlement(
                    id = 1,
                    amount = 1_000,
                    direction = SettlementDirection.PAID_TO_THEM,
                    date = "2026-08-14"
                )
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        // The total has fallen because the money genuinely left, but no account claims it.
        assertEquals(rupees(42_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(41_000), balances.total)
        assertTrue(balances.hasUnassigned)
        assertEquals(rupees(-1_000), balances.unassigned)
    }

    @Test
    fun `part payments come off the account one at a time and still reconcile`() {
        val snap = snapshot(bank(1, "Bank account", 50_000), cash(2, 5_000)).copy(
            ledgerEntries = listOf(
                ledgerEntry(
                    id = 1,
                    amount = 10_000,
                    direction = LedgerDirection.I_OWE_THEM,
                    date = "2026-08-01"
                )
            ),
            settlements = listOf(
                settlement(
                    id = 1,
                    amount = 1_000,
                    direction = SettlementDirection.PAID_TO_THEM,
                    date = "2026-08-10"
                ).copy(accountId = 1),
                settlement(
                    id = 2,
                    amount = 1_500,
                    direction = SettlementDirection.PAID_TO_THEM,
                    date = "2026-08-14"
                ).copy(accountId = 2, paymentMethod = PaymentMethod.CASH)
            )
        )
        val balances = BalanceCalculator.accountBalances(snap)

        assertEquals(rupees(49_000), balances.rows.first { it.account.id == 1L }.balance)
        assertEquals(rupees(3_500), balances.rows.first { it.account.id == 2L }.balance)
        assertEquals(rupees(52_500), balances.total)
        assertFalse(balances.hasUnassigned)

        // The debt itself is untouched by which pocket paid it: 10,000 less 2,500.
        assertEquals(
            rupees(-7_500),
            SettlementCalculator.balanceFor(snap.ledgerEntries, snap.settlements)
        )
    }
}
