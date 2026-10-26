package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.FinancialSnapshot

/**
 * The things that are wrong right now, gathered in one place.
 *
 * The dashboard's job is to answer "how much have I got" without making anybody hunt. But a
 * figure computed from records that are late or unattributed is quietly less true than it
 * looks, and burying that at the bottom of a scroll is how a number stops being trusted.
 *
 * So this collects exactly two things, both of which the user can act on today: money that
 * was due and has not moved, and money that moved through an account the app cannot name.
 * Anything the user cannot do something about does not belong here.
 */
object AttentionCalculator {

    /**
     * @param overdue the forecast items already past their date, in any order. They come
     *   back oldest due first, by [ForecastItem.dueDate]: a late person balance is shown
     *   on today's date to keep it in view, and sorting by that put the longest-standing
     *   debt last, where the card's three rows cut it off.
     */
    fun analyse(
        snapshot: FinancialSnapshot,
        overdue: List<ForecastItem>
    ): AttentionSummary {
        // Only meaningful once there is an account to have assigned. Before the user sets
        // one up, an unassigned expense is not a mistake, it is the app not having asked
        // yet, and flagging it would be scolding somebody for following the setup.
        val unassigned = if (snapshot.accounts.isEmpty()) {
            emptyList()
        } else {
            snapshot.expenses.filter(::isUnassigned)
        }

        return AttentionSummary(
            overdue = overdue.sortedBy { it.dueDate },
            unassignedCount = unassigned.size,
            unassignedTotal = unassigned.sumOfMoney { it.amount }
        )
    }

    /**
     * Money that moved through no account the app can name.
     *
     * One definition, used both for the count on the dashboard and for the list that row
     * opens, so the list always holds exactly the expenses the count promised.
     */
    fun isUnassigned(expense: Expense): Boolean =
        expense.accountId == null && expense.creditCardId == null
}

/**
 * What needs attention, and how much of it there is.
 *
 * [actionCount] counts unassigned expenses as one item rather than as many, because
 * assigning them is one trip to one screen. A count of 43 would read as a catastrophe when
 * the work is a single sitting.
 */
data class AttentionSummary(
    val overdue: List<ForecastItem> = emptyList(),
    val unassignedCount: Int = 0,
    val unassignedTotal: Money = Money.ZERO
) {
    val hasUnassigned: Boolean get() = unassignedCount > 0

    val isEmpty: Boolean get() = overdue.isEmpty() && !hasUnassigned

    val actionCount: Int get() = overdue.size + if (hasUnassigned) 1 else 0

    /** Total that is late. Unassigned money is not added in: it is not missing, just unlabelled. */
    val overdueTotal: Money get() = overdue.sumOfMoney { it.amount }

    /**
     * Late money still to go out, and late money still to come in, kept apart.
     *
     * A friend's overdue repayment does not pay an overdue bill, so netting the two would
     * describe neither; the card states each, so no late rupee is left out of a figure
     * when rows are cut for space.
     */
    val overdueToPay: Money get() = overdue.filterNot { it.isInflow }.sumOfMoney { it.amount }
    val overdueToReceive: Money get() = overdue.filter { it.isInflow }.sumOfMoney { it.amount }
}
