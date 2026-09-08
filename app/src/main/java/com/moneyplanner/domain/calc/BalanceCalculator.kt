package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.SettlementDirection
import java.time.LocalDate

/**
 * Derives how much money the user actually has right now.
 *
 * The balance is never a stored number that the app edits. It is rebuilt from the
 * opening balances and every movement recorded on or before the date being asked about,
 * which means the figure on the dashboard can always be traced back to the records that
 * produced it, and a correction to an old entry fixes the present automatically.
 *
 * Two rules matter here and are applied nowhere else:
 *
 * - Purchases made on a credit card do not reduce cash on the day they happen. They
 *   raise the card outstanding, and cash leaves when the card bill is paid. Counting
 *   both would subtract the same rupee twice.
 * - Money put into a savings goal is set aside, not spent. It stays in the balance and
 *   is reported separately as earmarked, so the user can see both the real balance and
 *   the part of it that is already promised to something.
 */
object BalanceCalculator {

    /** The balance as of today. */
    fun currentBalance(snapshot: FinancialSnapshot): Money =
        balanceAsOf(snapshot, snapshot.today)

    /**
     * The balance as of [asOf].
     *
     * Records dated after [asOf] are excluded, so an expense the user entered in advance
     * shows up in the forecast for its own month without quietly reducing today's money.
     */
    fun balanceAsOf(snapshot: FinancialSnapshot, asOf: LocalDate): Money =
        breakdownAsOf(snapshot, asOf).available

    fun breakdown(snapshot: FinancialSnapshot): BalanceBreakdown =
        breakdownAsOf(snapshot, snapshot.today)

    fun breakdownAsOf(snapshot: FinancialSnapshot, asOf: LocalDate): BalanceBreakdown {
        fun onOrBefore(date: LocalDate) = !date.isAfter(asOf)

        val opening = snapshot.accounts
            .filter { onOrBefore(it.openingDate) }
            .sumOfMoney { it.openingBalance }

        val adjustments = snapshot.adjustments
            .filter { onOrBefore(it.date) }
            .sumOfMoney { it.delta }

        val incomeReceived = snapshot.incomeTransactions
            .filter { onOrBefore(it.date) }
            .sumOfMoney { it.amount }

        val cashSpent = snapshot.expenses
            .filter { onOrBefore(it.date) && it.movesCash }
            .sumOfMoney { it.amount }

        val cardBillsPaid = snapshot.creditCardPayments
            .filter { onOrBefore(it.paidDate) }
            .sumOfMoney { it.amount }

        val settlementsIn = snapshot.settlements
            .filter { onOrBefore(it.date) && it.direction == SettlementDirection.RECEIVED_FROM_THEM }
            .sumOfMoney { it.amount }

        val settlementsOut = snapshot.settlements
            .filter { onOrBefore(it.date) && it.direction == SettlementDirection.PAID_TO_THEM }
            .sumOfMoney { it.amount }

        val earmarked = snapshot.contributions
            .filter { onOrBefore(it.date) }
            .sumOfMoney { it.amount }
            .coerceAtLeastZero()

        val available = opening + adjustments + incomeReceived + settlementsIn -
            cashSpent - cardBillsPaid - settlementsOut

        return BalanceBreakdown(
            asOf = asOf,
            openingBalance = opening,
            adjustments = adjustments,
            incomeReceived = incomeReceived,
            cashSpent = cashSpent,
            creditCardBillsPaid = cardBillsPaid,
            settlementsReceived = settlementsIn,
            settlementsPaid = settlementsOut,
            earmarkedForGoals = earmarked,
            available = available
        )
    }

    /**
     * The balance sitting in each account, as of [asOf].
     *
     * Same derivation as the overall balance, narrowed to the records filed against one
     * account. Two rules make the per-account figures add up to the total:
     *
     *  - **Records with no account are reported separately.** A great deal of history was
     *    written before accounts were tracked, and quietly attributing it to whichever
     *    account happens to be first would put money in a place the user never put it.
     *    It is shown as unassigned so the difference is visible rather than invented.
     *  - **A transfer moves money without creating or destroying any.** It leaves one
     *    account and arrives in the other, so it changes both figures and changes the
     *    total by nothing at all.
     */
    fun accountBalancesAsOf(snapshot: FinancialSnapshot, asOf: LocalDate): AccountBalances {
        fun onOrBefore(date: LocalDate) = !date.isAfter(asOf)

        val rows = snapshot.accounts
            .filter { onOrBefore(it.openingDate) }
            .map { account ->
                val id = account.id

                val income = snapshot.incomeTransactions
                    .filter { it.accountId == id && onOrBefore(it.date) }
                    .sumOfMoney { it.amount }

                val spent = snapshot.expenses
                    .filter { it.accountId == id && onOrBefore(it.date) && it.movesCash }
                    .sumOfMoney { it.amount }

                val adjustments = snapshot.adjustments
                    .filter { it.accountId == id && onOrBefore(it.date) }
                    .sumOfMoney { it.delta }

                val cardBills = snapshot.creditCardPayments
                    .filter { it.accountId == id && onOrBefore(it.paidDate) }
                    .sumOfMoney { it.amount }

                val settledIn = snapshot.settlements
                    .filter {
                        it.accountId == id && onOrBefore(it.date) &&
                            it.direction == SettlementDirection.RECEIVED_FROM_THEM
                    }
                    .sumOfMoney { it.amount }

                val settledOut = snapshot.settlements
                    .filter {
                        it.accountId == id && onOrBefore(it.date) &&
                            it.direction == SettlementDirection.PAID_TO_THEM
                    }
                    .sumOfMoney { it.amount }

                val transferredIn = snapshot.transfers
                    .filter { it.toAccountId == id && onOrBefore(it.date) }
                    .sumOfMoney { it.amount }

                val transferredOut = snapshot.transfers
                    .filter { it.fromAccountId == id && onOrBefore(it.date) }
                    .sumOfMoney { it.amount }

                AccountBalance(
                    account = account,
                    openingBalance = account.openingBalance,
                    moneyIn = income + settledIn + transferredIn,
                    moneyOut = spent + cardBills + settledOut + transferredOut,
                    adjustments = adjustments,
                    balance = account.openingBalance + income + settledIn + transferredIn +
                        adjustments - spent - cardBills - settledOut - transferredOut
                )
            }
            .sortedBy { it.account.sortOrder }

        val total = breakdownAsOf(snapshot, asOf).available
        val assigned = rows.sumOfMoney { it.balance }

        return AccountBalances(
            asOf = asOf,
            rows = rows,
            unassigned = total - assigned,
            total = total
        )
    }

    fun accountBalances(snapshot: FinancialSnapshot): AccountBalances =
        accountBalancesAsOf(snapshot, snapshot.today)

    /** Total outstanding across every active credit card. */
    fun totalCreditCardOutstanding(snapshot: FinancialSnapshot): Money =
        snapshot.creditCards.filter { it.isActive }.sumOfMoney { it.currentOutstanding }

    /**
     * Everything the user owns minus everything they owe, including loan balances.
     * A negative figure is shown as-is; the app does not hide an uncomfortable number.
     */
    fun netPosition(snapshot: FinancialSnapshot): NetPosition {
        val available = currentBalance(snapshot)
        val receivable = SettlementCalculator.totalReceivable(
            snapshot.ledgerEntries, snapshot.settlements
        )
        val payable = SettlementCalculator.totalPayable(
            snapshot.ledgerEntries, snapshot.settlements
        )
        val loanOutstanding = snapshot.emis
            .filter { it.isActive }
            .sumOfMoney { EmiCalculator.outstandingAmount(it, snapshot.emiPayments) }
        val cardOutstanding = totalCreditCardOutstanding(snapshot)

        return NetPosition(
            available = available,
            receivable = receivable,
            payable = payable,
            loanOutstanding = loanOutstanding,
            creditCardOutstanding = cardOutstanding,
            net = available + receivable - payable - loanOutstanding - cardOutstanding
        )
    }
}

data class BalanceBreakdown(
    val asOf: LocalDate,
    val openingBalance: Money,
    val adjustments: Money,
    val incomeReceived: Money,
    val cashSpent: Money,
    val creditCardBillsPaid: Money,
    val settlementsReceived: Money,
    val settlementsPaid: Money,
    /** Part of [available] that the user has already promised to a savings goal. */
    val earmarkedForGoals: Money,
    val available: Money
) {
    /** What is left once money promised to goals is set aside. */
    val unallocated: Money get() = available - earmarkedForGoals
}

/** What one account holds, and the movements that explain it. */
data class AccountBalance(
    val account: com.moneyplanner.domain.model.Account,
    val openingBalance: Money,
    val moneyIn: Money,
    val moneyOut: Money,
    val adjustments: Money,
    val balance: Money
)

data class AccountBalances(
    val asOf: LocalDate,
    val rows: List<AccountBalance>,
    /**
     * Money the overall balance knows about but no account claims, because the record was
     * written without one. Shown rather than hidden: it is the difference between what the
     * user has and what they have told the app where to find.
     */
    val unassigned: Money,
    val total: Money
) {
    val hasUnassigned: Boolean get() = !unassigned.isZero
}

data class NetPosition(
    val available: Money,
    val receivable: Money,
    val payable: Money,
    val loanOutstanding: Money,
    val creditCardOutstanding: Money,
    val net: Money
)
