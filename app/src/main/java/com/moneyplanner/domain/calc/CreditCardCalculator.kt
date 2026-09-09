package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.PaymentMethod
import java.time.LocalDate
import java.time.YearMonth

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

    /**
     * The billing cycle a card is currently in.
     *
     * A statement is cut on the card's statement day, and the bill for it falls due on the
     * due day after that. Which month the due date lands in depends on the two days: a card
     * that statements on the 25th and is due on the 5th is due the following month, while
     * one that statements on the 1st and is due on the 20th is due in the same one. Working
     * that out from the days rather than assuming is the whole job here.
     */
    fun currentCycle(card: CreditCard, today: LocalDate): StatementCycle {
        val thisMonthStatement = DateUtil.dayInMonth(YearMonth.from(today), card.statementDayOfMonth)

        // The cycle that is still open is the one whose statement has not been cut yet.
        val statementOn = if (today.isAfter(thisMonthStatement)) {
            DateUtil.dayInMonth(YearMonth.from(today).plusMonths(1), card.statementDayOfMonth)
        } else {
            thisMonthStatement
        }
        val previousStatement = DateUtil.dayInMonth(
            YearMonth.from(statementOn).minusMonths(1),
            card.statementDayOfMonth
        )

        // The first due day strictly after the statement is cut. Landing on the same day
        // would give no time to pay it.
        var dueOn = DateUtil.dayInMonth(YearMonth.from(statementOn), card.dueDayOfMonth)
        if (!dueOn.isAfter(statementOn)) {
            dueOn = DateUtil.dayInMonth(YearMonth.from(statementOn).plusMonths(1), card.dueDayOfMonth)
        }

        return StatementCycle(
            opensOn = previousStatement.plusDays(1),
            statementOn = statementOn,
            dueOn = dueOn
        )
    }

    /** Purchases charged inside the cycle that has not been billed yet. */
    fun currentCycleSpend(
        card: CreditCard,
        expenses: List<Expense>,
        today: LocalDate
    ): Money {
        val cycle = currentCycle(card, today)
        return expenses
            .filter { expense ->
                expense.creditCardId == card.id &&
                    expense.paymentMethod == PaymentMethod.CREDIT_CARD &&
                    !expense.date.isBefore(cycle.opensOn) &&
                    !expense.date.isAfter(cycle.statementOn)
            }
            .sumOfMoney { it.amount }
    }

    fun statusFor(card: CreditCard, snapshot: FinancialSnapshot): CreditCardStatus {
        val unbilled = unbilledSpendOn(card, snapshot.expenses)
        return CreditCardStatus(
            card = card,
            unbilledSpend = unbilled,
            projectedOutstanding = card.currentOutstanding + unbilled,
            purchaseCount = purchasesOn(card, snapshot.expenses).size,
            cycle = currentCycle(card, snapshot.today),
            currentCycleSpend = currentCycleSpend(card, snapshot.expenses, snapshot.today)
        )
    }

    fun allStatuses(snapshot: FinancialSnapshot): List<CreditCardStatus> =
        snapshot.creditCards.map { statusFor(it, snapshot) }
}

/**
 * Where a card is in its billing cycle.
 *
 * [opensOn] and [statementOn] bound the purchases that will appear on the next statement;
 * [dueOn] is when the bill for that statement has to be paid.
 */
data class StatementCycle(
    val opensOn: LocalDate,
    val statementOn: LocalDate,
    val dueOn: LocalDate
) {
    fun daysUntilStatement(today: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(today, statementOn)

    fun daysUntilDue(today: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(today, dueOn)
}

data class CreditCardStatus(
    val card: CreditCard,
    /** Charged to the card since the statement figure was entered. */
    val unbilledSpend: Money,
    val projectedOutstanding: Money,
    val purchaseCount: Int,
    val cycle: StatementCycle,
    /** Charged inside the cycle that has not been billed yet. */
    val currentCycleSpend: Money = Money.ZERO
) {
    val hasUnbilled: Boolean get() = unbilledSpend.isPositive

    /** True when spending since the statement has pushed the card past its limit. */
    val isOverLimit: Boolean
        get() = card.creditLimit.isPositive && projectedOutstanding > card.creditLimit

    val availableLimit: Money
        get() = (card.creditLimit - projectedOutstanding).coerceAtLeastZero()
}
