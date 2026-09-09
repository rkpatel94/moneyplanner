package com.moneyplanner.domain.nlp

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.Expense
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
 */
object SmsDuplicateDetector {

    /** How many days either side of the message an existing expense may sit. */
    const val DATE_TOLERANCE_DAYS = 1L

    /**
     * Compares one candidate against what is already recorded, and against the messages
     * ahead of it in the same paste.
     *
     * @param earlierInBatch candidates already accepted from this paste, so the same
     *        message pasted twice is caught before it becomes two expenses.
     */
    fun check(
        amount: Money,
        date: LocalDate,
        merchant: String?,
        existing: List<Expense>,
        earlierInBatch: List<Pair<Money, LocalDate>> = emptyList()
    ): DuplicateVerdict {
        if (!amount.isPositive) return DuplicateVerdict.None

        // The same message twice in one paste is not a judgement call.
        if (earlierInBatch.any { it.first == amount && it.second == date }) {
            return DuplicateVerdict.SameBatch
        }

        val sameAmount = existing.filter { it.amount == amount }
        if (sameAmount.isEmpty()) return DuplicateVerdict.None

        val exact = sameAmount.filter { it.date == date }
        if (exact.isNotEmpty()) {
            // An amount and a date both matching is as close to certain as this gets, and
            // closer still when the description names the same place.
            val named = merchant?.takeIf { it.isNotBlank() }
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
        val named = merchant?.takeIf { it.isNotBlank() }
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
enum class DuplicateVerdict(val label: String?) {
    None(null),
    /** Same amount within a day, nothing else to go on. */
    Possible("Possibly already recorded"),
    /** Same amount and date, or same amount and place a day apart. */
    Likely("Looks already recorded"),
    /** Same amount, date and place. */
    Certain("Already recorded"),
    /** The same message appears twice in what was pasted. */
    SameBatch("Repeated in what you pasted");

    val isDuplicate: Boolean get() = this != None
}
