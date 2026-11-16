package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.CreditCardPayment
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

    fun purchasesOn(card: CreditCard, expenses: List<Expense>): List<Expense> {
        val billedUpTo = billedUpTo(card)
        return expenses.filter { expense ->
            expense.creditCardId == card.id &&
                expense.paymentMethod == PaymentMethod.CREDIT_CARD &&
                // Strictly after, so a purchase on the statement day itself is treated as
                // already inside that statement.
                (billedUpTo == null || expense.date.isAfter(billedUpTo))
        }
    }

    /**
     * The last day whose purchases the outstanding figure already contains.
     *
     * The statement's own date when the user gave one. Cards whose figure predates that
     * question fall back to the day the figure was last changed, which is how they always
     * behaved. Paying the bill no longer moves this date: it used to, and every purchase
     * made between the statement and the payment then dropped out of what the card was
     * said to owe, although the next statement was certain to contain them.
     */
    fun billedUpTo(card: CreditCard): LocalDate? = card.statementDate ?: card.lastUpdated

    /**
     * Whether a payment made on [paidOn] comes off the outstanding figure, rather than
     * already being inside the statement that figure was copied from.
     *
     * A card without a statement date keeps its old behaviour, where every payment came off.
     */
    fun paymentReducesFigure(card: CreditCard, paidOn: LocalDate): Boolean =
        card.statementDate == null || paidOn.isAfter(card.statementDate)

    // ---- Statement dates --------------------------------------------------------------

    /** The latest statement cut on or before [date]. */
    fun statementOnOrBefore(card: CreditCard, date: LocalDate): LocalDate {
        val thisMonth = DateUtil.dayInMonth(YearMonth.from(date), card.statementDayOfMonth)
        return if (!thisMonth.isAfter(date)) {
            thisMonth
        } else {
            DateUtil.dayInMonth(YearMonth.from(date).minusMonths(1), card.statementDayOfMonth)
        }
    }

    /** The first statement cut on or after [date], which is the one a purchase made that day lands on. */
    fun statementOnOrAfter(card: CreditCard, date: LocalDate): LocalDate {
        val thisMonth = DateUtil.dayInMonth(YearMonth.from(date), card.statementDayOfMonth)
        return if (!thisMonth.isBefore(date)) {
            thisMonth
        } else {
            DateUtil.dayInMonth(YearMonth.from(date).plusMonths(1), card.statementDayOfMonth)
        }
    }

    /**
     * When the bill for a statement cut on [statementOn] has to be paid: the first due day
     * strictly after it. Landing on the same day would leave no time to pay.
     */
    fun dueDateFor(card: CreditCard, statementOn: LocalDate): LocalDate {
        val sameMonth = DateUtil.dayInMonth(YearMonth.from(statementOn), card.dueDayOfMonth)
        return if (sameMonth.isAfter(statementOn)) {
            sameMonth
        } else {
            DateUtil.dayInMonth(YearMonth.from(statementOn).plusMonths(1), card.dueDayOfMonth)
        }
    }

    /**
     * The statement the outstanding figure on file was copied from.
     *
     * The statement's own date when the user gave one; otherwise the last statement before
     * the figure was changed. A figure with no date at all is taken as the bill due next,
     * which is how such a figure has always been read, rather than declared overdue.
     */
    fun statementOnFile(card: CreditCard, today: LocalDate): LocalDate {
        card.statementDate?.let { return it }
        card.lastUpdated?.let { return statementOnOrBefore(card, it) }
        val latest = statementOnOrBefore(card, today)
        if (!dueDateFor(card, latest).isBefore(today)) return latest
        return DateUtil.dayInMonth(YearMonth.from(latest).plusMonths(1), card.statementDayOfMonth)
    }

    /**
     * The bill the bank has already issued, and how much of it is still to pay.
     *
     * This is what falls due next. It used to be booked on the due date of the cycle still
     * open, which is a month later, so between a statement and its due date — exactly when
     * the bill is outstanding — the forecast showed it a month late and the reminder, never
     * closer than that gap, could not fire.
     */
    fun statementBill(
        card: CreditCard,
        payments: List<CreditCardPayment>,
        today: LocalDate
    ): StatementBill {
        val statementOn = statementOnFile(card, today)
        val paidSince = payments
            .filter { it.cardId == card.id && it.paidDate.isAfter(statementOn) }
            .sumOfMoney { it.amount }
        val remaining = card.currentOutstanding.coerceAtLeastZero()
        val minimumRemaining = if (!remaining.isPositive) {
            Money.ZERO
        } else {
            minOf((card.minimumDue - paidSince).coerceAtLeastZero(), remaining)
        }
        return StatementBill(
            statementOn = statementOn,
            dueOn = dueDateFor(card, statementOn),
            remaining = remaining,
            minimumRemaining = minimumRemaining,
            paidSinceStatement = paidSince
        )
    }

    /**
     * Spending not yet on any statement, grouped by the statement it will land on.
     *
     * Usually one group, the cycle now open. More than one appears when a statement has
     * been cut since the figure on file was entered: that statement's purchases are already
     * billed by the bank, due on its own date, and the next cycle's come after.
     */
    fun upcomingBills(card: CreditCard, expenses: List<Expense>): List<UpcomingCardBill> =
        purchasesOn(card, expenses)
            .groupBy { statementOnOrAfter(card, it.date) }
            .map { (statementOn, purchases) ->
                UpcomingCardBill(
                    statementOn = statementOn,
                    dueOn = dueDateFor(card, statementOn),
                    amount = purchases.sumOfMoney { it.amount },
                    purchaseCount = purchases.size
                )
            }
            .sortedBy { it.statementOn }

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
            currentCycleSpend = currentCycleSpend(card, snapshot.expenses, snapshot.today),
            bill = statementBill(card, snapshot.creditCardPayments, snapshot.today),
            upcoming = upcomingBills(card, snapshot.expenses)
        )
    }

    fun allStatuses(snapshot: FinancialSnapshot): List<CreditCardStatus> =
        snapshot.creditCards.map { statusFor(it, snapshot) }
}

/** The statement already issued, and what is left of it. */
data class StatementBill(
    val statementOn: LocalDate,
    val dueOn: LocalDate,
    /** Still to pay on this statement. Zero once paid, or when the card is in credit. */
    val remaining: Money,
    val minimumRemaining: Money,
    val paidSinceStatement: Money
) {
    val isPaid: Boolean get() = !remaining.isPositive
    fun daysUntilDue(today: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(today, dueOn)
}

/** Purchases that will appear on a statement not yet cut, or cut but not yet entered. */
data class UpcomingCardBill(
    val statementOn: LocalDate,
    val dueOn: LocalDate,
    val amount: Money,
    val purchaseCount: Int
)

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
    val currentCycleSpend: Money = Money.ZERO,
    /** The statement already issued and what is left to pay on it. */
    val bill: StatementBill? = null,
    /** Spending since, grouped by the statement it will appear on. */
    val upcoming: List<UpcomingCardBill> = emptyList()
) {
    val hasUnbilled: Boolean get() = unbilledSpend.isPositive

    /** True when spending since the statement has pushed the card past its limit. */
    val isOverLimit: Boolean
        get() = card.creditLimit.isPositive && projectedOutstanding > card.creditLimit

    val availableLimit: Money
        get() = (card.creditLimit - projectedOutstanding).coerceAtLeastZero()
}
