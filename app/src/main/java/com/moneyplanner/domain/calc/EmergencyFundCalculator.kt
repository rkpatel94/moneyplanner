package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.FinancialSnapshot
import java.time.YearMonth

/**
 * Works out what one month of essential living actually costs, and how far the emergency
 * fund has to go to cover it.
 *
 * The monthly figure is assembled from three separate places, each counted once:
 *
 *  - bills the user has marked essential, taken from their own schedules;
 *  - loan installments, because a lender does not pause during a bad month;
 *  - everyday spending in essential categories, taken from actual history.
 *
 * Annual commitments such as insurance are included at their monthly reserve, since that
 * is the amount that genuinely has to be found every month even though the bill arrives
 * once a year. Nothing is counted from two sources: bills come from their definitions and
 * groceries come from spending history, and the two never overlap because bill payments
 * are recorded as linked expenses which the spending average ignores.
 */
object EmergencyFundCalculator {

    fun calculate(snapshot: FinancialSnapshot): EmergencyFundStatus {
        val month = snapshot.currentMonth

        val essentialBills = snapshot.bills
            .filter { it.isActive && it.isEssential }
            .sumOfMoney { bill ->
                val occurrences = RecurrenceCalculator.occurrencesIn(
                    month = month,
                    start = bill.startDate,
                    end = bill.endDate,
                    dayOfMonth = bill.dueDayOfMonth,
                    frequency = bill.frequency
                ).size
                bill.amount * occurrences
            }

        val emiBurden = snapshot.emis
            .filter { it.isActive && !EmiCalculator.isCompleted(it, snapshot.emiPayments) }
            .sumOfMoney { emi ->
                val perMonth = if (emi.frequency.isWeekly) {
                    emi.emiAmount * 4
                } else {
                    emi.emiAmount.divideRounded(emi.frequency.monthsPerPeriod)
                }
                perMonth
            }

        val essentialEverydaySpend = SpendingAnalyzer.averageMonthlyEssentialSpend(snapshot)

        val annualReserve = snapshot.annualExpenses
            .filter { it.isActive }
            .sumOfMoney { it.monthlyReserve }

        val monthlyEssential = essentialBills + emiBurden + essentialEverydaySpend + annualReserve

        val months = snapshot.profile.emergencyFundMonths.coerceIn(1, 24)
        val target = monthlyEssential * months

        val fundGoal = snapshot.goals.firstOrNull { it.isEmergencyFund }
        val current = fundGoal
            ?.let { SavingsCalculator.balanceOf(it.id, snapshot.contributions) }
            ?: Money.ZERO

        val remaining = (target - current).coerceAtLeastZero()
        val fraction = if (target.paise <= 0L) 0f
        else (current.paise.toDouble() / target.paise).toFloat().coerceIn(0f, 1f)

        return EmergencyFundStatus(
            monthlyEssentialExpenses = monthlyEssential,
            essentialBills = essentialBills,
            emiBurden = emiBurden,
            essentialEverydaySpend = essentialEverydaySpend,
            annualReserve = annualReserve,
            monthsOfCover = months,
            targetAmount = target,
            currentAmount = current,
            remainingAmount = remaining,
            progressFraction = fraction,
            hasGoal = fundGoal != null,
            goalId = fundGoal?.id,
            /**
             * How many months the fund would actually last today, which is often the
             * more useful number than a percentage.
             */
            monthsCovered = if (monthlyEssential.paise <= 0L) 0.0
            else current.paise.toDouble() / monthlyEssential.paise
        )
    }

    /** The essential cost of a specific month, used by the affordability check. */
    fun essentialCostForMonth(snapshot: FinancialSnapshot, month: YearMonth): Money {
        val bills = snapshot.bills
            .filter { it.isActive && it.isEssential }
            .sumOfMoney { bill ->
                val occurrences = RecurrenceCalculator.occurrencesIn(
                    month, bill.startDate, bill.endDate, bill.dueDayOfMonth, bill.frequency
                ).size
                bill.amount * occurrences
            }
        val emis = snapshot.emis
            .filter { it.isActive }
            .sumOfMoney { emi ->
                EmiCalculator.unpaidInstallmentsIn(emi, snapshot.emiPayments, month)
                    .sumOfMoney { it.amount }
            }
        return bills + emis + SpendingAnalyzer.averageMonthlyEssentialSpend(snapshot)
    }
}

data class EmergencyFundStatus(
    val monthlyEssentialExpenses: Money,
    val essentialBills: Money,
    val emiBurden: Money,
    val essentialEverydaySpend: Money,
    val annualReserve: Money,
    val monthsOfCover: Int,
    val targetAmount: Money,
    val currentAmount: Money,
    val remainingAmount: Money,
    val progressFraction: Float,
    val hasGoal: Boolean,
    val goalId: Long?,
    val monthsCovered: Double
) {
    val progressPercent: Int get() = (progressFraction * 100).toInt()
    val isFunded: Boolean get() = remainingAmount.isZero && targetAmount.isPositive
}
