package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.AnnualExpense
import com.moneyplanner.domain.model.BillAmountType
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.Emi
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.RecurringBill
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Records the app cannot place, gathered for the home screen to settle or delete.
 *
 * Two kinds, both of which quietly make a figure wrong:
 *
 *  - **No account.** Money that moved through no account the app can name. The total is
 *    still right, but the per-account balances cannot add up to it, and the bank looks
 *    richer than it is. Bill, EMI and card payments used to be written this way every time.
 *  - **Not matched.** An everyday expense that looks like the payment of a bill, an EMI, a
 *    yearly expense or a card bill the app is still expecting. Left alone, that payment is
 *    counted twice: once as the expense, and again when the forecast takes the bill it
 *    still thinks is unpaid. Bank SMS imports produce exactly these.
 *
 * Matching is deliberately cautious. A suggestion is only made when the amount agrees, or
 * nearly agrees and the name or category agrees too, inside a short window around the due
 * date. A suggestion is never acted on without the user; it can be settled, deleted, or
 * dismissed as not a payment, and a dismissed one is not offered again.
 */
object UnlinkedCalculator {

    /** How far either side of a due date a payment may land and still be taken for it. */
    const val WINDOW_DAYS = 10L

    /** How far back an everyday expense is still worth offering as a payment. */
    const val LOOKBACK_DAYS = 75L

    fun find(snapshot: FinancialSnapshot, dismissed: Set<String> = emptySet()): UnlinkedRecords {
        val suggestions = suggestPayments(snapshot, dismissed)
        val suggested = suggestions.map { it.expense.id }.toSet()
        return UnlinkedRecords(
            noAccount = noAccount(snapshot).filterNot { it.kind == UnlinkedKind.EXPENSE && it.id in suggested },
            suggestions = suggestions
        )
    }

    // ---- No account ------------------------------------------------------------------

    fun noAccount(snapshot: FinancialSnapshot): List<NoAccountRecord> {
        // Before the user has an account there is nothing to have assigned, and flagging
        // every record would scold them for following the setup.
        if (snapshot.accounts.none { !it.isArchived }) return emptyList()
        val records = mutableListOf<NoAccountRecord>()

        snapshot.expenses
            .filter(AttentionCalculator::isUnassigned)
            .forEach { expense ->
                records += NoAccountRecord(
                    kind = UnlinkedKind.EXPENSE,
                    id = expense.id,
                    title = expense.description.ifBlank {
                        snapshot.categoriesById[expense.categoryId]?.name ?: "Expense"
                    },
                    amount = expense.amount,
                    date = expense.date,
                    isInflow = false,
                    // A card purchase with no card is placed on a card, not in an account.
                    needsCard = expense.paymentMethod == PaymentMethod.CREDIT_CARD
                )
            }

        snapshot.incomeTransactions
            .filter { it.accountId == null }
            .forEach { income ->
                records += NoAccountRecord(UnlinkedKind.INCOME, income.id, income.name, income.amount, income.date, isInflow = true)
            }

        snapshot.settlements
            .filter { it.accountId == null && it.paymentMethod.reducesCashImmediately }
            .forEach { settlement ->
                val name = snapshot.peopleById[settlement.personId]?.name ?: "Someone"
                val received = settlement.direction == com.moneyplanner.domain.model.SettlementDirection.RECEIVED_FROM_THEM
                records += NoAccountRecord(
                    kind = UnlinkedKind.SETTLEMENT,
                    id = settlement.id,
                    title = if (received) "Repayment from $name" else "Paid back to $name",
                    amount = settlement.amount,
                    date = settlement.date,
                    isInflow = received
                )
            }

        snapshot.creditCardPayments
            .filter { it.accountId == null }
            .forEach { payment ->
                val card = snapshot.creditCards.firstOrNull { it.id == payment.cardId }
                records += NoAccountRecord(
                    kind = UnlinkedKind.CARD_PAYMENT,
                    id = payment.id,
                    title = "${card?.name ?: "Card"} bill payment",
                    amount = payment.amount,
                    date = payment.paidDate,
                    isInflow = false
                )
            }

        return records.sortedWith(compareByDescending<NoAccountRecord> { it.date }.thenByDescending { it.id })
    }

    // ---- Not matched -----------------------------------------------------------------

    fun suggestPayments(snapshot: FinancialSnapshot, dismissed: Set<String> = emptySet()): List<PaymentSuggestion> {
        val today = snapshot.today
        val candidates = snapshot.expenses.filter { expense ->
            expense.linkType == ExpenseLinkType.NONE &&
                expense.movesCash &&
                !expense.date.isAfter(today) &&
                ChronoUnit.DAYS.between(expense.date, today) <= LOOKBACK_DAYS &&
                PaymentSuggestion.keyFor(expense.id) !in dismissed
        }
        if (candidates.isEmpty()) return emptyList()

        val scored = mutableListOf<PaymentSuggestion>()
        candidates.forEach { expense ->
            val best = (billTargets(snapshot, expense) + emiTargets(snapshot, expense) +
                annualTargets(snapshot, expense) + cardTargets(snapshot, expense))
                .maxByOrNull { it.score }
            if (best != null) scored += PaymentSuggestion(expense, best.target, best.score)
        }

        // Each occurrence is offered for at most one expense, the best fitting one.
        val used = mutableSetOf<String>()
        return scored
            .sortedByDescending { it.score }
            .filter { used.add(it.target.key) }
            .sortedByDescending { it.expense.date }
    }

    private data class Scored(val target: SuggestionTarget, val score: Int)

    private fun billTargets(snapshot: FinancialSnapshot, expense: Expense): List<Scored> {
        val paidKeys = snapshot.billPayments.map { it.billId to it.periodKey }.toSet()
        return snapshot.bills.filter { it.isActive }.flatMap { bill ->
            RecurrenceCalculator.occurrencesBetween(
                from = expense.date.minusDays(WINDOW_DAYS),
                to = expense.date.plusDays(WINDOW_DAYS),
                start = bill.startDate,
                end = bill.endDate,
                dayOfMonth = bill.dueDayOfMonth,
                frequency = bill.frequency
            ).filterNot { BillPeriod.isPaid(bill, it, paidKeys) }
                .mapNotNull { due ->
                    val score = score(
                        expense = expense,
                        expected = bill.amount,
                        tolerancePercent = if (bill.amountType == BillAmountType.ESTIMATED) 25 else 10,
                        names = listOf(bill.name),
                        categoryId = bill.categoryId,
                        due = due
                    ) ?: return@mapNotNull null
                    Scored(SuggestionTarget.BillOccurrence(bill, due, BillPeriod.keyFor(bill, due)), score)
                }
        }
    }

    private fun emiTargets(snapshot: FinancialSnapshot, expense: Expense): List<Scored> =
        snapshot.emis.filter { it.isActive }.flatMap { emi ->
            EmiCalculator.unpaidInstallmentNumbers(emi, snapshot.emiPayments)
                .map { number -> number to EmiCalculator.dueDateFor(emi, number) }
                .filter { (_, due) -> abs(ChronoUnit.DAYS.between(due, expense.date)) <= WINDOW_DAYS }
                .mapNotNull { (number, due) ->
                    val score = score(
                        expense = expense,
                        expected = emi.emiAmount,
                        tolerancePercent = 5,
                        names = listOf(emi.name, "emi", "loan", "nach", "ecs"),
                        categoryId = emi.categoryId,
                        due = due
                    ) ?: return@mapNotNull null
                    Scored(SuggestionTarget.EmiInstallment(emi, number, due), score)
                }
        }

    private fun annualTargets(snapshot: FinancialSnapshot, expense: Expense): List<Scored> {
        val paid = snapshot.annualExpensePayments.map { it.annualExpenseId to it.year }.toSet()
        return snapshot.annualExpenses.filter { it.isActive }.flatMap { annual ->
            listOf(expense.date.year - 1, expense.date.year, expense.date.year + 1)
                .filterNot { (annual.id to it) in paid }
                .map { year -> year to DateUtil.dayInMonth(java.time.YearMonth.of(year, annual.dueMonth), annual.dueDayOfMonth) }
                .filter { (_, due) -> abs(ChronoUnit.DAYS.between(due, expense.date)) <= WINDOW_DAYS * 2 }
                .mapNotNull { (year, due) ->
                    val score = score(
                        expense = expense,
                        expected = annual.amount,
                        tolerancePercent = 15,
                        names = listOf(annual.name),
                        categoryId = annual.categoryId,
                        due = due
                    ) ?: return@mapNotNull null
                    Scored(SuggestionTarget.AnnualOccurrence(annual, year, due), score)
                }
        }
    }

    /**
     * A card bill paid from the bank but recorded as an expense. It is not spending at all:
     * the purchases were already counted on the card, so as an expense it counts them twice.
     */
    private fun cardTargets(snapshot: FinancialSnapshot, expense: Expense): List<Scored> =
        snapshot.creditCards.filter { it.isActive }.mapNotNull { card ->
            val bill = CreditCardCalculator.statementBill(card, snapshot.creditCardPayments, snapshot.today)
            val owed = CreditCardCalculator.projectedOutstanding(card, snapshot.expenses)
            if (!owed.isPositive) return@mapNotNull null
            if (expense.amount > owed + Money.ofRupees(1)) return@mapNotNull null

            val text = expense.description.lowercase() + " " + expense.notes.lowercase()
            val namesCard = listOfNotNull(
                card.name.lowercase().takeIf { it.length >= 3 },
                card.lastFourDigits,
                "credit card", "card bill", "cc bill", "cc payment", "card payment"
            ).any { text.contains(it) }
            val exactBill = expense.amount == bill.remaining || expense.amount == bill.minimumRemaining
            val nearDue = abs(ChronoUnit.DAYS.between(bill.dueOn, expense.date)) <= WINDOW_DAYS * 2

            val score = when {
                namesCard && exactBill -> 100
                namesCard -> 80
                exactBill && nearDue && bill.remaining.isPositive -> 60
                else -> return@mapNotNull null
            }
            Scored(SuggestionTarget.CardBill(card, bill.dueOn), score)
        }

    /**
     * How well [expense] fits a payment expected on [due], or null when it does not fit.
     *
     * An exact amount is strong evidence on its own. A near amount needs the name or the
     * category to agree as well. Closer to the due date scores a little higher.
     */
    private fun score(
        expense: Expense,
        expected: Money,
        tolerancePercent: Int,
        names: List<String>,
        categoryId: Long?,
        due: LocalDate
    ): Int? {
        if (!expected.isPositive) return null
        val exact = expense.amount == expected
        val difference = abs(expense.amount.paise - expected.paise).toDouble() / expected.paise
        val near = difference * 100 <= tolerancePercent
        val text = (expense.description + " " + expense.notes).lowercase()
        val nameHit = names.any { name ->
            val n = name.trim().lowercase()
            n.length >= 3 && (text.contains(n) || (expense.description.length >= 3 && n.contains(expense.description.trim().lowercase())))
        }
        val categoryHit = categoryId != null && categoryId == expense.categoryId

        val base = when {
            exact && (nameHit || categoryHit) -> 100
            exact -> 70
            near && nameHit -> 65
            near && categoryHit -> 55
            else -> return null
        }
        val days = abs(ChronoUnit.DAYS.between(due, expense.date)).toInt()
        return base - days.coerceAtMost(20)
    }
}

enum class UnlinkedKind { EXPENSE, INCOME, SETTLEMENT, CARD_PAYMENT }

/** Something recorded with no account the money moved through. */
data class NoAccountRecord(
    val kind: UnlinkedKind,
    val id: Long,
    val title: String,
    val amount: Money,
    val date: LocalDate,
    val isInflow: Boolean,
    /** A card purchase with no card: it belongs on a card, not in an account. */
    val needsCard: Boolean = false
) {
    val key: String get() = "${kind.name}:$id"
}

/** What an unmatched expense looks like the payment of. */
sealed interface SuggestionTarget {
    val key: String
    val title: String
    val dueDate: LocalDate

    data class BillOccurrence(val bill: RecurringBill, override val dueDate: LocalDate, val periodKey: String) : SuggestionTarget {
        override val key get() = "bill:${bill.id}:$periodKey"
        override val title get() = bill.name
    }

    data class EmiInstallment(val emi: Emi, val installment: Int, override val dueDate: LocalDate) : SuggestionTarget {
        override val key get() = "emi:${emi.id}:$installment"
        override val title get() = "${emi.name} installment $installment"
    }

    data class AnnualOccurrence(val annual: AnnualExpense, val year: Int, override val dueDate: LocalDate) : SuggestionTarget {
        override val key get() = "annual:${annual.id}:$year"
        override val title get() = "${annual.name} $year"
    }

    data class CardBill(val card: CreditCard, override val dueDate: LocalDate) : SuggestionTarget {
        override val key get() = "card:${card.id}"
        override val title get() = "${card.name} bill"
    }
}

data class PaymentSuggestion(
    val expense: Expense,
    val target: SuggestionTarget,
    val score: Int = 0
) {
    val key: String get() = keyFor(expense.id)

    companion object {
        fun keyFor(expenseId: Long) = "suggest:$expenseId"
    }
}

data class UnlinkedRecords(
    val noAccount: List<NoAccountRecord> = emptyList(),
    val suggestions: List<PaymentSuggestion> = emptyList()
) {
    val isEmpty: Boolean get() = noAccount.isEmpty() && suggestions.isEmpty()
    val count: Int get() = noAccount.size + suggestions.size
}
