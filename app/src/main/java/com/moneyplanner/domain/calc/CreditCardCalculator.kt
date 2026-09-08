package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.PaymentMethod

/**
 * What has gone on a card since the figure on it was last set.
 *
 * The outstanding on a card is a number the user copies from their statement, and that is
 * deliberate: only the bank knows what interest and fees it has added. It is therefore the
 * one balance in this app that is *not* derived, and the one place where purchases recorded
 * afterwards cannot simply be added to it.
 *
 * Adding them would double count. A statement of ₹20,000 already contains every purchase
 * made before it was issued, so summing all card expenses on top would charge those
 * purchases twice. What is safe, and useful, is the spending recorded *after* the statement
 * figure was entered: that is the part the statement cannot know about yet.
 *
 * So this reports the two separately rather than merging them. The user sees the figure
 * their bank gave them, and beside it what they have spent since, and the two together are
 * an honest picture without either being invented.
 */
object CreditCardCalculator {

    /**
     * Expenses charged to [card] that the recorded outstanding does not yet include.
     *
     * A card with no statement date recorded has nothing to be "since", so every purchase
     * against it counts as unbilled.
     */
    fun unbilledSpendOn(card: CreditCard, expenses: List<Expense>): Money =
        purchasesOn(card, expenses).sumOfMoney { it.amount }

    fun purchasesOn(card: CreditCard, expenses: List<Expense>): List<Expense> =
        expenses.filter { expense ->
            expense.creditCardId == card.id &&
                expense.paymentMethod == PaymentMethod.CREDIT_CARD &&
                // Strictly after, so a purchase recorded on the same day the statement
                // figure was entered is treated as already inside it.
                (card.lastUpdated == null || expense.date.isAfter(card.lastUpdated))
        }

    /**
     * The outstanding plus what has been spent since, which is what the user actually
     * owes today even though the bank has not billed it yet.
     */
    fun projectedOutstanding(card: CreditCard, expenses: List<Expense>): Money =
        card.currentOutstanding + unbilledSpendOn(card, expenses)

    /** Utilisation against the limit once unbilled spending is taken into account. */
    fun projectedUtilisation(card: CreditCard, expenses: List<Expense>): Float {
        if (card.creditLimit.paise <= 0L) return 0f
        val projected = projectedOutstanding(card, expenses)
        return (projected.paise.toDouble() / card.creditLimit.paise)
            .toFloat()
            .coerceIn(0f, 1f)
    }

    fun statusFor(card: CreditCard, snapshot: FinancialSnapshot): CreditCardStatus {
        val unbilled = unbilledSpendOn(card, snapshot.expenses)
        return CreditCardStatus(
            card = card,
            unbilledSpend = unbilled,
            projectedOutstanding = card.currentOutstanding + unbilled,
            purchaseCount = purchasesOn(card, snapshot.expenses).size
        )
    }

    fun allStatuses(snapshot: FinancialSnapshot): List<CreditCardStatus> =
        snapshot.creditCards.map { statusFor(it, snapshot) }
}

data class CreditCardStatus(
    val card: CreditCard,
    /** Charged to the card since the statement figure was entered. */
    val unbilledSpend: Money,
    val projectedOutstanding: Money,
    val purchaseCount: Int
) {
    val hasUnbilled: Boolean get() = unbilledSpend.isPositive

    /** True when spending since the statement has pushed the card past its limit. */
    val isOverLimit: Boolean
        get() = card.creditLimit.isPositive && projectedOutstanding > card.creditLimit

    val availableLimit: Money
        get() = (card.creditLimit - projectedOutstanding).coerceAtLeastZero()
}
