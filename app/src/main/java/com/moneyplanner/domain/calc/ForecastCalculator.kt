package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.core.time.periodKey
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.IncomeSource
import com.moneyplanner.domain.model.LedgerDirection
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * Projects the months ahead from what the user has already told the app.
 *
 * This is the part of the application that separates it from a plain expense tracker.
 * Nothing here is estimated from outside data or from assumptions about how people
 * generally spend: every rupee in the projection traces back to a salary, a loan, a bill,
 * a commitment or the user's own spending history.
 *
 * Three rules keep the projection honest, and each is applied in exactly one place:
 *
 *  - **An obligation is counted once.** A bill that has been paid is already reflected in
 *    the balance, so only unpaid occurrences are projected. The expense created when it
 *    was paid is linked to the bill, and linked expenses are excluded from spending
 *    averages, so rent can never appear both as a scheduled bill and as everyday spending.
 *  - **A credit card purchase is not a cash outflow.** It raises the card outstanding;
 *    cash leaves when the card bill is paid. Only the card due is projected.
 *  - **The current month is partial.** Money already spent and received this month is
 *    inside the opening balance, so only what remains is projected on top of it.
 */
object ForecastCalculator {

    const val DEFAULT_MONTHS_AHEAD = 12

    /**
     * Builds a projection starting with the current month.
     *
     * Each month opens with the closing balance of the month before it, so a shortfall
     * in one month is carried forward instead of being quietly reset.
     */
    fun forecast(
        snapshot: FinancialSnapshot,
        monthsAhead: Int = DEFAULT_MONTHS_AHEAD
    ): List<MonthForecast> {
        val startMonth = snapshot.currentMonth
        val averageMonthlySpend = SpendingAnalyzer.averageMonthlyDiscretionaryCash(snapshot)
        val averageDailySpend = SpendingAnalyzer.averageDailyDiscretionaryCash(snapshot)
        val plannedSavings = SavingsCalculator.totalRequiredMonthlySaving(snapshot)

        var opening = BalanceCalculator.currentBalance(snapshot)
        val results = mutableListOf<MonthForecast>()

        for (offset in 0 until monthsAhead.coerceAtLeast(1)) {
            val month = startMonth.plusMonths(offset.toLong())
            val isCurrent = offset == 0

            val forecastMonth = buildMonth(
                snapshot = snapshot,
                month = month,
                openingBalance = opening,
                isCurrentMonth = isCurrent,
                averageMonthlySpend = averageMonthlySpend,
                averageDailySpend = averageDailySpend,
                plannedSavings = plannedSavings
            )
            results += forecastMonth
            opening = forecastMonth.closingBalance
        }
        return results
    }

    /** A single month, useful on its own for the monthly view. */
    fun forecastMonth(snapshot: FinancialSnapshot, month: YearMonth): MonthForecast {
        val offset = ChronoUnit.MONTHS.between(snapshot.currentMonth, month).toInt()
        if (offset <= 0) {
            return buildMonth(
                snapshot = snapshot,
                month = month,
                openingBalance = BalanceCalculator.currentBalance(snapshot),
                isCurrentMonth = month == snapshot.currentMonth,
                averageMonthlySpend = SpendingAnalyzer.averageMonthlyDiscretionaryCash(snapshot),
                averageDailySpend = SpendingAnalyzer.averageDailyDiscretionaryCash(snapshot),
                plannedSavings = SavingsCalculator.totalRequiredMonthlySaving(snapshot)
            )
        }
        return forecast(snapshot, offset + 1).last()
    }

    private fun buildMonth(
        snapshot: FinancialSnapshot,
        month: YearMonth,
        openingBalance: Money,
        isCurrentMonth: Boolean,
        averageMonthlySpend: Money,
        averageDailySpend: Money,
        plannedSavings: Money
    ): MonthForecast {
        val items = mutableListOf<ForecastItem>()

        items += expectedIncomeItems(snapshot, month)
        items += peopleItems(snapshot, month)
        items += emiItems(snapshot, month)
        items += billItems(snapshot, month)
        items += annualItems(snapshot, month)
        items += creditCardItems(snapshot, month)

        val ordered = items.sortedWith(compareBy({ it.date }, { it.kind.ordinal }))

        fun sumOf(vararg kinds: ForecastItemKind): Money =
            ordered.filter { it.kind in kinds }.sumOfMoney { it.amount }

        val expectedIncome = sumOf(ForecastItemKind.INCOME)
        val expectedIncoming = sumOf(ForecastItemKind.PERSON_INCOMING)
        val emiOutflow = sumOf(ForecastItemKind.EMI)
        val billsOutflow = sumOf(ForecastItemKind.BILL)
        val annualOutflow = sumOf(ForecastItemKind.ANNUAL)
        val cardOutflow = sumOf(ForecastItemKind.CREDIT_CARD)
        val owedOutflow = sumOf(ForecastItemKind.PERSON_OUTGOING)

        val estimatedEveryday = estimateEverydaySpend(
            snapshot, month, isCurrentMonth, averageMonthlySpend, averageDailySpend
        )

        val actualIncome = snapshot.incomeTransactions
            .filter { YearMonth.from(it.date) == month }
            .sumOfMoney { it.amount }
        val actualSpend = SpendingAnalyzer.totalSpendIn(snapshot, month)

        return MonthForecast(
            month = month,
            openingBalance = openingBalance,
            expectedIncome = expectedIncome,
            expectedIncomingFromPeople = expectedIncoming,
            emiOutflow = emiOutflow,
            billsOutflow = billsOutflow,
            annualOutflow = annualOutflow,
            creditCardOutflow = cardOutflow,
            owedToPeopleOutflow = owedOutflow,
            estimatedEverydaySpend = estimatedEveryday,
            plannedSavings = plannedSavings,
            actualIncomeSoFar = actualIncome,
            actualSpendSoFar = actualSpend,
            isCurrentMonth = isCurrentMonth,
            isProjectionBasedOnHistory = averageMonthlySpend.isPositive,
            items = ordered
        )
    }

    // ---- Income ------------------------------------------------------------------

    /**
     * Salary and other recurring income that has not arrived yet, plus any future-dated
     * receipts the user has already entered.
     *
     * An occurrence that has already been received is dropped, because that money is
     * inside the opening balance. Matching is done per month against the source, so a
     * salary credited late still cancels the right occurrence.
     */
    private fun expectedIncomeItems(
        snapshot: FinancialSnapshot,
        month: YearMonth
    ): List<ForecastItem> {
        val items = mutableListOf<ForecastItem>()

        snapshot.incomeSources.filter { it.isActive }.forEach { source ->
            val occurrences = RecurrenceCalculator.occurrencesIn(
                month = month,
                start = source.startDate,
                end = source.endDate,
                dayOfMonth = source.dayOfMonth,
                frequency = source.frequency
            )
            if (occurrences.isEmpty()) return@forEach

            // Which period a receipt satisfies is recorded on the receipt itself, because
            // the date it landed does not always agree: a salary due on the 1st and paid
            // on the last day of the previous month belongs to the month it was for.
            // Receipts written before that was stamped fall back to their own month.
            val monthKey = month.periodKey()
            val alreadyReceived = snapshot.incomeTransactions.count { transaction ->
                transaction.sourceId == source.id &&
                    (transaction.periodKey ?: YearMonth.from(transaction.date).periodKey()) ==
                    monthKey
            }

            occurrences.drop(alreadyReceived).forEach { date ->
                items += ForecastItem(
                    date = date,
                    title = source.name,
                    subtitle = source.type.label,
                    amount = amountWithIncrement(source, date),
                    kind = ForecastItemKind.INCOME,
                    sourceId = source.id,
                    isEstimate = false
                )
            }
        }

        // Receipts the user has already recorded with a future date. They are not yet in
        // the balance, so the month they fall in should expect them.
        snapshot.incomeTransactions
            .filter { YearMonth.from(it.date) == month && it.date.isAfter(snapshot.today) }
            .forEach { transaction ->
                items += ForecastItem(
                    date = transaction.date,
                    title = transaction.name,
                    subtitle = "Recorded in advance",
                    amount = transaction.amount,
                    kind = ForecastItemKind.INCOME,
                    sourceId = transaction.sourceId,
                    isEstimate = false
                )
            }

        return items
    }

    /**
     * Applies the expected annual increment.
     *
     * The raise takes effect on each anniversary of the start date, so a salary that
     * started in April rises in April rather than in January.
     */
    private fun amountWithIncrement(source: IncomeSource, on: LocalDate): Money {
        if (source.annualIncrementPercent <= 0.0) return source.amount
        val years = ChronoUnit.YEARS.between(source.startDate, on).toInt()
        if (years <= 0) return source.amount
        val factor = (1.0 + source.annualIncrementPercent / 100.0).pow(years)
        return Money(Math.round(source.amount.paise * factor))
    }

    // ---- People ------------------------------------------------------------------

    /**
     * Money expected from, or owed to, other people in this month.
     *
     * Only what is still outstanding after settlements counts, so a loan that has been
     * half repaid contributes only its remaining half. Entries with no expected date are
     * left out of the month projection on purpose: they are real balances, but guessing
     * when they will be paid would make the forecast less trustworthy, not more.
     *
     * An entry whose expected date has already passed is different: the user did name a
     * date, it was simply missed. Those are carried forward to today and marked overdue
     * rather than dropped, because a balance that silently leaves the projection makes
     * every month after it look better than it is.
     */
    private fun peopleItems(
        snapshot: FinancialSnapshot,
        month: YearMonth
    ): List<ForecastItem> {
        val entriesByPerson = snapshot.ledgerEntries.groupBy { it.personId }
        val settlementsByPerson = snapshot.settlements.groupBy { it.personId }
        val names = snapshot.peopleById

        return entriesByPerson.flatMap { (personId, entries) ->
            val allocation = SettlementCalculator.allocate(
                entries, settlementsByPerson[personId].orEmpty()
            )
            allocation.rows
                .filter { it.outstandingAmount.isPositive }
                .mapNotNull { row ->
                    val expected = row.entry.expectedDate ?: return@mapNotNull null

                    // An overdue balance has not gone away, it is simply late. Dropping it
                    // because its month has passed would remove it from the projection
                    // entirely and quietly flatter the months ahead, so it is carried into
                    // the first month still being projected and shown there.
                    val effective = maxOf(expected, snapshot.today)
                    if (YearMonth.from(effective) != month) return@mapNotNull null

                    val personName = names[personId]?.name ?: "Someone"
                    val isOverdue = expected.isBefore(snapshot.today)
                    ForecastItem(
                        date = effective,
                        title = personName,
                        subtitle = row.entry.description.ifBlank {
                            if (row.entry.direction == LedgerDirection.THEY_OWE_ME) {
                                "Money expected"
                            } else {
                                "Money to repay"
                            }
                        }.let { if (isOverdue) "$it · overdue" else it },
                        amount = row.outstandingAmount,
                        kind = if (row.entry.direction == LedgerDirection.THEY_OWE_ME) {
                            ForecastItemKind.PERSON_INCOMING
                        } else {
                            ForecastItemKind.PERSON_OUTGOING
                        },
                        sourceId = personId,
                        isEstimate = false
                    )
                }
        }
    }

    // ---- Obligations -------------------------------------------------------------

    private fun emiItems(snapshot: FinancialSnapshot, month: YearMonth): List<ForecastItem> =
        snapshot.emis
            .filter { it.isActive }
            .flatMap { emi ->
                EmiCalculator.unpaidInstallmentsIn(emi, snapshot.emiPayments, month)
                    .map { installment ->
                        ForecastItem(
                            date = installment.dueDate,
                            title = emi.name,
                            subtitle = "Installment ${installment.installmentNumber} of ${emi.totalInstallments}",
                            amount = installment.amount,
                            kind = ForecastItemKind.EMI,
                            sourceId = emi.id,
                            isEstimate = false
                        )
                    }
            }

    private fun billItems(snapshot: FinancialSnapshot, month: YearMonth): List<ForecastItem> {
        val paidKeys = snapshot.billPayments
            .map { it.billId to it.periodKey }
            .toSet()

        return snapshot.bills
            .filter { it.isActive }
            .flatMap { bill ->
                val alreadyPaid = (bill.id to month.periodKey()) in paidKeys
                if (alreadyPaid) return@flatMap emptyList()

                RecurrenceCalculator.occurrencesIn(
                    month, bill.startDate, bill.endDate, bill.dueDayOfMonth, bill.frequency
                ).map { date ->
                    ForecastItem(
                        date = date,
                        title = bill.name,
                        subtitle = if (bill.amountType.name == "ESTIMATED") "Estimated" else "Due",
                        amount = bill.amount,
                        kind = ForecastItemKind.BILL,
                        sourceId = bill.id,
                        isEstimate = bill.amountType.name == "ESTIMATED"
                    )
                }
            }
    }

    private fun annualItems(snapshot: FinancialSnapshot, month: YearMonth): List<ForecastItem> {
        val paid = snapshot.annualExpensePayments
            .map { it.annualExpenseId to it.year }
            .toSet()

        return snapshot.annualExpenses
            .filter { it.isActive && it.dueMonth == month.monthValue }
            .filterNot { (it.id to month.year) in paid }
            .map { annual ->
                ForecastItem(
                    date = DateUtil.dayInMonth(month, annual.dueDayOfMonth),
                    title = annual.name,
                    subtitle = "Yearly commitment",
                    amount = annual.amount,
                    kind = ForecastItemKind.ANNUAL,
                    sourceId = annual.id,
                    isEstimate = false
                )
            }
    }

    /**
     * The card bill, projected once against the outstanding the user has recorded.
     *
     * It is placed in the month of the next due date and not repeated afterwards. The app
     * has no way of knowing what will be spent on the card next month, and inventing a
     * figure would be a guess presented as a fact.
     */
    private fun creditCardItems(
        snapshot: FinancialSnapshot,
        month: YearMonth
    ): List<ForecastItem> =
        snapshot.creditCards
            .filter { it.isActive && it.currentOutstanding.isPositive }
            .mapNotNull { card ->
                val nextDue = RecurrenceCalculator.nextOccurrenceOnOrAfter(
                    from = snapshot.today,
                    start = snapshot.today.minusYears(5),
                    end = null,
                    dayOfMonth = card.dueDayOfMonth,
                    frequency = com.moneyplanner.domain.model.Frequency.MONTHLY
                ) ?: return@mapNotNull null

                if (YearMonth.from(nextDue) != month) return@mapNotNull null

                ForecastItem(
                    date = nextDue,
                    title = card.name,
                    subtitle = "Credit card due",
                    amount = card.currentOutstanding,
                    kind = ForecastItemKind.CREDIT_CARD,
                    sourceId = card.id,
                    isEstimate = false
                )
            }

    // ---- Everyday spending --------------------------------------------------------

    /**
     * What everyday living is likely to cost for the rest of the month.
     *
     * For the current month only the remaining days are projected, because what has
     * already been spent is inside the opening balance. Whichever way the estimate is
     * produced, it comes from the user's own past spending and nothing else.
     */
    private fun estimateEverydaySpend(
        snapshot: FinancialSnapshot,
        month: YearMonth,
        isCurrentMonth: Boolean,
        averageMonthlySpend: Money,
        averageDailySpend: Money
    ): Money {
        if (!isCurrentMonth) return averageMonthlySpend

        val today = snapshot.today
        val lastDay = month.atEndOfMonth()
        if (today.isAfter(lastDay)) return Money.ZERO

        val remainingDays = ChronoUnit.DAYS.between(today, lastDay).toInt()
        if (remainingDays <= 0) return Money.ZERO
        return averageDailySpend * remainingDays
    }
}

enum class ForecastItemKind(val label: String, val isInflow: Boolean) {
    INCOME("Income", true),
    PERSON_INCOMING("Money coming in", true),
    EMI("EMI", false),
    BILL("Bill", false),
    ANNUAL("Yearly expense", false),
    CREDIT_CARD("Credit card", false),
    PERSON_OUTGOING("Money to repay", false)
}

/** A single dated line in the projection. Also drives the calendar and the reminders. */
data class ForecastItem(
    val date: LocalDate,
    val title: String,
    val subtitle: String,
    val amount: Money,
    val kind: ForecastItemKind,
    val sourceId: Long?,
    /** True when the amount is the user's own estimate rather than a fixed figure. */
    val isEstimate: Boolean
) {
    val isInflow: Boolean get() = kind.isInflow
    val signedAmount: Money get() = if (isInflow) amount else -amount
}

data class MonthForecast(
    val month: YearMonth,
    val openingBalance: Money,
    val expectedIncome: Money,
    val expectedIncomingFromPeople: Money,
    val emiOutflow: Money,
    val billsOutflow: Money,
    val annualOutflow: Money,
    val creditCardOutflow: Money,
    val owedToPeopleOutflow: Money,
    val estimatedEverydaySpend: Money,
    /** Guidance, not a bill: what the savings goals need this month to stay on track. */
    val plannedSavings: Money,
    val actualIncomeSoFar: Money,
    val actualSpendSoFar: Money,
    val isCurrentMonth: Boolean,
    /** False when there is not enough history yet for the everyday spending estimate. */
    val isProjectionBasedOnHistory: Boolean,
    val items: List<ForecastItem>
) {
    val totalInflow: Money get() = expectedIncome + expectedIncomingFromPeople

    /** Payments that are already committed, before everyday spending. */
    val committedOutflow: Money
        get() = emiOutflow + billsOutflow + annualOutflow + creditCardOutflow + owedToPeopleOutflow

    val totalOutflow: Money get() = committedOutflow + estimatedEverydaySpend

    /** The projected balance at the end of the month. Savings stay inside this figure. */
    val closingBalance: Money get() = openingBalance + totalInflow - totalOutflow

    /** What is left after also putting the recommended savings aside. */
    val closingAfterPlannedSavings: Money get() = closingBalance - plannedSavings

    val netFlow: Money get() = totalInflow - totalOutflow

    /** True when the month is projected to end short of money. */
    val isShortfall: Boolean get() = closingBalance.isNegative

    /** Money that is genuinely free to spend once every commitment is met. */
    val safeToSpend: Money
        get() = (openingBalance + totalInflow - committedOutflow - estimatedEverydaySpend)
            .coerceAtLeastZero()

    fun itemsOn(date: LocalDate): List<ForecastItem> = items.filter { it.date == date }
}
