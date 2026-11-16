package com.moneyplanner.domain.nlp

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.CreditCardPayment
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.IncomeTransaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Whether a parsed message is something already recorded.
 *
 * The asymmetry here decides the design. A missed import is visibly absent: the user looks
 * for the expense, does not find it, and adds it. A duplicated one is invisible and makes
 * every balance and projection downstream quietly wrong. So this leans towards suspecting
 * a duplicate, and hands the judgement to the user rather than acting on it.
 *
 * Matching on an exact amount and an exact date is too narrow to be useful. A bank alert
 * can arrive after midnight for a purchase made before it, and the same transaction typed
 * by hand is usually dated the day it happened. A day either side catches that without
 * reaching so far that two genuine coffees on consecutive days collapse into one.
 *
 * Money in is held to the same rule as money out. A salary the user already marked as
 * received, then imported again from its credit alert, would count the month's pay twice.
 */
object SmsDuplicateDetector {

    /** How many days either side of the message an existing record may sit. */
    const val DATE_TOLERANCE_DAYS = 1L

    /**
     * Compares one debit against the expenses already recorded, and against the messages
     * ahead of it in the review list.
     *
     * @param earlierInBatch amounts and dates already waiting in the review list, from
     *        this paste or scan and every one before it, so the same message read twice
     *        is caught before it becomes two expenses.
     */
    fun check(
        amount: Money,
        date: LocalDate,
        merchant: String?,
        existing: List<Expense>,
        earlierInBatch: List<Pair<Money, LocalDate>> = emptyList()
    ): DuplicateVerdict = checkAgainst(
        amount = amount,
        date = date,
        name = merchant,
        recorded = existing.map { Recorded(it.amount, it.date, it.description) },
        earlierInBatch = earlierInBatch
    )

    /** The same judgement for a credit, against the income already recorded. */
    fun checkIncome(
        amount: Money,
        date: LocalDate,
        payer: String?,
        existing: List<IncomeTransaction>,
        earlierInBatch: List<Pair<Money, LocalDate>> = emptyList()
    ): DuplicateVerdict = checkAgainst(
        amount = amount,
        date = date,
        name = payer,
        recorded = existing.map { Recorded(it.amount, it.date, it.name) },
        earlierInBatch = earlierInBatch
    )

    /**
     * A cash withdrawal against the transfers already recorded. A transfer has no
     * merchant, so a match on amount and day is as close as it gets.
     */
    fun checkTransfer(
        amount: Money,
        date: LocalDate,
        existing: List<AccountTransfer>
    ): DuplicateVerdict = checkAgainst(
        amount = amount,
        date = date,
        name = null,
        recorded = existing.map { Recorded(it.amount, it.date, it.notes) }
    )

    /** A card bill payment against the payments already recorded on the cards. */
    fun checkCardPayment(
        amount: Money,
        date: LocalDate,
        existing: List<CreditCardPayment>
    ): DuplicateVerdict = checkAgainst(
        amount = amount,
        date = date,
        name = null,
        recorded = existing.map { Recorded(it.amount, it.paidDate, it.notes) }
    )

    /** The more suspicious of two verdicts, for a message checked against two kinds of record. */
    fun strongest(first: DuplicateVerdict, second: DuplicateVerdict): DuplicateVerdict =
        if (second.strength > first.strength) second else first

    private data class Recorded(val amount: Money, val date: LocalDate, val description: String)

    private fun checkAgainst(
        amount: Money,
        date: LocalDate,
        name: String?,
        recorded: List<Recorded>,
        earlierInBatch: List<Pair<Money, LocalDate>> = emptyList()
    ): DuplicateVerdict {
        if (!amount.isPositive) return DuplicateVerdict.None

        // The same message twice in one review list is not a judgement call.
        if (earlierInBatch.any { it.first == amount && it.second == date }) {
            return DuplicateVerdict.SameBatch
        }

        val sameAmount = recorded.filter { it.amount == amount }
        if (sameAmount.isEmpty()) return DuplicateVerdict.None

        val named = name?.takeIf { it.isNotBlank() }

        val exact = sameAmount.filter { it.date == date }
        if (exact.isNotEmpty()) {
            // An amount and a date both matching is as close to certain as this gets, and
            // closer still when the description names the same place.
            val alsoNamed = named != null && exact.any { it.description.containsWord(named) }
            return if (alsoNamed) DuplicateVerdict.Certain else DuplicateVerdict.Likely
        }

        val nearby = sameAmount.filter {
            ChronoUnit.DAYS.between(it.date, date).let { gap ->
                gap <= DATE_TOLERANCE_DAYS && gap >= -DATE_TOLERANCE_DAYS
            }
        }
        if (nearby.isEmpty()) return DuplicateVerdict.None

        // Same amount, a day out. Common enough after midnight to be worth flagging, but
        // not certain enough to act on, so it is offered as a question.
        return if (named != null && nearby.any { it.description.containsWord(named) }) {
            DuplicateVerdict.Likely
        } else {
            DuplicateVerdict.Possible
        }
    }

    /**
     * Loose word matching, because a merchant reaches the two sides differently: "SWIGGY"
     * from the bank against "Swiggy dinner" typed by hand.
     */
    private fun String.containsWord(other: String): Boolean {
        val a = lowercase().trim()
        val b = other.lowercase().trim()
        if (a.isBlank() || b.isBlank()) return false
        return a.contains(b) || b.contains(a)
    }
}

/**
 * How sure the app is, and therefore how it should behave.
 *
 * Only [None] is imported without the user looking. Everything else starts unticked, and
 * says why, so a real duplicate is never added silently and a false alarm costs one tap.
 */
enum class DuplicateVerdict(val label: String?, internal val strength: Int) {
    None(null, 0),
    /** Same amount within a day, nothing else to go on. */
    Possible("Possibly already recorded", 1),
    /** Same amount and date, or same amount and place a day apart. */
    Likely("Looks already recorded", 2),
    /** Same amount, date and place. */
    Certain("Already recorded", 3),
    /** The same amount on the same day is already waiting in the review list. */
    SameBatch("Repeated in this list", 4),
    /** This very message was imported by an earlier scan. */
    AlreadyImported("Already imported", 5);

    val isDuplicate: Boolean get() = this != None
}
