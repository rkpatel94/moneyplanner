package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.SettlementDirection
import java.time.LocalDate
import java.time.YearMonth

/**
 * The last few things that actually happened, whatever kind of record they were.
 *
 * Every other list in the app is filtered to one kind: expenses in History, receipts on the
 * income screen, settlements under a person. That is the right way to answer "what did I
 * spend on food", and the wrong way to answer "what did I just do", which is the question
 * someone asks after entering a figure wrong and wanting it back.
 *
 * Money movements only. A bill marked paid shows up as the expense it created rather than
 * as a second entry of its own, because one event should appear once — the same rule the
 * forecast is built on. Obligations, goals and budgets are not movements and are absent.
 */
object RecentActivityCalculator {

    const val DEFAULT_LIMIT = 10

    /**
     * The [limit] most recent movements, newest first.
     *
     * Future-dated records are included. A receipt entered in advance is something the
     * user did today and may well want to correct today, and hiding it until its own date
     * arrives would make a mistyped entry impossible to find.
     */
    fun recent(snapshot: FinancialSnapshot, limit: Int = DEFAULT_LIMIT): List<ActivityItem> =
        allMovements(snapshot)
            // Ties are broken by kind and then by id so the order never shuffles between
            // reads. Several records entered in one sitting share a date, and a list that
            // reorders itself under the user is a list they stop trusting.
            .sortedWith(
                compareByDescending<ActivityItem> { it.date }
                    .thenBy { it.kind.ordinal }
                    .thenByDescending { it.recordId }
            )
            .take(limit.coerceAtLeast(0))

    /** Every recorded movement, unsorted. */
    fun allMovements(snapshot: FinancialSnapshot): List<ActivityItem> = buildList {
        addAll(expenses(snapshot))
        addAll(income(snapshot))
        addAll(settlements(snapshot))
        addAll(transfers(snapshot))
        addAll(savings(snapshot))
    }

    /** Everything that actually happened in one month, oldest first. */
    fun movementsIn(snapshot: FinancialSnapshot, month: YearMonth): List<ActivityItem> =
        allMovements(snapshot)
            .filter { YearMonth.from(it.date) == month }
            .sortedWith(
                compareBy<ActivityItem> { it.date }
                    .thenBy { it.kind.ordinal }
                    .thenBy { it.recordId }
            )

    private fun expenses(snapshot: FinancialSnapshot): List<ActivityItem> {
        val categories = snapshot.categoriesById
        return snapshot.expenses.map { expense ->
            val categoryName = categories[expense.categoryId]?.name
            ActivityItem(
                kind = ActivityKind.EXPENSE,
                recordId = expense.id,
                title = expense.description.ifBlank { categoryName ?: "Expense" },
                subtitle = listOfNotNull(
                    categoryName,
                    expense.paymentMethod.label
                ).joinToString(" · "),
                amount = expense.amount,
                date = expense.date,
                direction = ActivityDirection.OUT,
                // A card purchase is recorded on the day it happens but takes no cash
                // until the bill is paid, so the row says so rather than implying the
                // balance moved.
                movesCashNow = expense.movesCash,
                linkType = expense.linkType,
                linkId = expense.linkId,
                linkPeriodKey = expense.linkPeriodKey
            )
        }
    }

    private fun income(snapshot: FinancialSnapshot): List<ActivityItem> =
        snapshot.incomeTransactions.map { receipt ->
            ActivityItem(
                kind = ActivityKind.INCOME,
                recordId = receipt.id,
                title = receipt.name.ifBlank { receipt.type.label },
                subtitle = receipt.type.label,
                amount = receipt.amount,
                date = receipt.date,
                direction = ActivityDirection.IN
            )
        }

    private fun settlements(snapshot: FinancialSnapshot): List<ActivityItem> {
        val people = snapshot.peopleById
        return snapshot.settlements.map { settlement ->
            val name = people[settlement.personId]?.name ?: "Someone"
            val received = settlement.direction == SettlementDirection.RECEIVED_FROM_THEM
            ActivityItem(
                kind = ActivityKind.SETTLEMENT,
                recordId = settlement.id,
                title = if (received) "Received from $name" else "Paid $name",
                subtitle = settlement.paymentMethod.label,
                amount = settlement.amount,
                date = settlement.date,
                direction = if (received) ActivityDirection.IN else ActivityDirection.OUT,
                parentId = settlement.personId
            )
        }
    }

    /**
     * A transfer changes no total, so it is marked neutral rather than being shown as
     * money in or out. Presenting a cash withdrawal as spending is exactly the confusion
     * separate cash and bank accounts exist to remove.
     */
    private fun transfers(snapshot: FinancialSnapshot): List<ActivityItem> {
        val names = snapshot.accounts.associate { it.id to it.name }
        return snapshot.transfers.map { transfer ->
            val from = names[transfer.fromAccountId] ?: "an account"
            val to = names[transfer.toAccountId] ?: "another account"
            ActivityItem(
                kind = ActivityKind.TRANSFER,
                recordId = transfer.id,
                title = "$from → $to",
                // Short enough to survive the row's single line next to a date. The title
                // already names both accounts, so this says the thing a reader might
                // otherwise get wrong rather than repeating it.
                subtitle = "Not income or spending",
                amount = transfer.amount,
                date = transfer.date,
                direction = ActivityDirection.NEUTRAL
            )
        }
    }

    /**
     * Money put into a goal is earmarked, not spent, so it is neutral too. A withdrawal
     * back out is stored as a negative contribution and reads the other way round.
     */
    private fun savings(snapshot: FinancialSnapshot): List<ActivityItem> {
        val goals = snapshot.goals.associate { it.id to it.name }
        return snapshot.contributions.map { contribution ->
            val goalName = goals[contribution.goalId] ?: "a goal"
            val isWithdrawal = contribution.amount.isNegative
            ActivityItem(
                kind = ActivityKind.SAVING,
                recordId = contribution.id,
                title = if (isWithdrawal) "Taken from $goalName" else "Put towards $goalName",
                subtitle = "Set aside, not spent",
                amount = contribution.amount.abs(),
                date = contribution.date,
                direction = ActivityDirection.NEUTRAL,
                parentId = contribution.goalId
            )
        }
    }
}

enum class ActivityDirection { IN, OUT, NEUTRAL }

enum class ActivityKind(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    SETTLEMENT("Person"),
    TRANSFER("Transfer"),
    SAVING("Savings")
}

/**
 * One movement, flattened into what a list row needs.
 *
 * [linkType] and its companions are carried so the screen can delete the record correctly.
 * An expense created by marking a bill paid cannot simply be deleted: the payment row would
 * survive it, leaving the forecast believing the bill is settled while the money is back in
 * the balance. It has to be undone through the obligation that owns it.
 */
data class ActivityItem(
    val kind: ActivityKind,
    val recordId: Long,
    val title: String,
    val subtitle: String,
    val amount: Money,
    val date: LocalDate,
    val direction: ActivityDirection,
    /**
     * The record this one hangs off, where reaching its editor needs more than its own id:
     * the person for a settlement, the goal for a contribution. Null for records that
     * stand alone.
     */
    val parentId: Long? = null,
    /** False for a card purchase, which raises the card outstanding instead of taking cash. */
    val movesCashNow: Boolean = true,
    val linkType: ExpenseLinkType = ExpenseLinkType.NONE,
    val linkId: Long? = null,
    val linkPeriodKey: String? = null
) {
    /** A stable key for the list, since ids repeat across the different record types. */
    val key: String get() = "${kind.name}-$recordId"

    /**
     * Whether tapping the row opens an editor.
     *
     * Every kind has one. Settlements and goal entries are only reachable when their
     * parent is known, since their editors live on that person's or goal's screen.
     */
    val isEditable: Boolean
        get() = when (kind) {
            ActivityKind.EXPENSE, ActivityKind.INCOME, ActivityKind.TRANSFER -> true
            // Their editors live on the parent's screen, so they need to know it.
            ActivityKind.SETTLEMENT, ActivityKind.SAVING -> parentId != null
        }

    /** True when this expense settles a bill, an EMI or a yearly commitment. */
    val isObligationPayment: Boolean
        get() = linkType == ExpenseLinkType.RECURRING_BILL ||
            linkType == ExpenseLinkType.EMI ||
            linkType == ExpenseLinkType.ANNUAL_EXPENSE

    fun relativeDate(today: LocalDate): String = DateUtil.relativeDayLabel(date, today)
}
