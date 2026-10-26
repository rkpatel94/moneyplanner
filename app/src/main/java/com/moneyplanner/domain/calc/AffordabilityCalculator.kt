package com.moneyplanner.domain.calc

import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.model.AffordabilityVerdict
import com.moneyplanner.domain.model.FinancialSnapshot

/**
 * Answers "can I afford this?" using only what the user has already entered.
 *
 * The check compares the money available today against the commitments that fall due
 * over the chosen horizon. It deliberately does not offer investment advice, predict
 * markets, or promise anything: it reports the arithmetic and leaves the decision with
 * the person. Every figure shown in the explanation is one the user can go and verify on
 * another screen of the app.
 *
 * Money set aside for goals counts as available, because it really is in the account, so
 * it can keep a purchase from breaking a commitment. A purchase that would need it is
 * never called safe, though, just as the home screen never offers it as money to spend.
 */
object AffordabilityCalculator {

    const val DEFAULT_HORIZON_MONTHS = 3

    fun check(
        snapshot: FinancialSnapshot,
        purchaseAmount: Money,
        horizonMonths: Int = DEFAULT_HORIZON_MONTHS
    ): AffordabilityResult {
        val months = horizonMonths.coerceIn(1, 12)
        val forecast = ForecastCalculator.forecast(snapshot, months)

        val available = BalanceCalculator.currentBalance(snapshot)
        val committed = forecast.sumOfMoney { it.committedOutflow }
        val everyday = forecast.sumOfMoney { it.estimatedEverydaySpend }
        val incoming = forecast.sumOfMoney { it.totalInflow }

        val emergency = EmergencyFundCalculator.calculate(snapshot)
        val monthlyEssential = emergency.monthlyEssentialExpenses
        val setAside = BalanceCalculator.earmarkedForGoalsAsOf(snapshot, snapshot.today)

        val remainingAfterPurchase = available - purchaseAmount
        val projectedClose = available + incoming - committed - everyday - purchaseAmount

        // The emergency fund gets a warning of its own. Money for any other goal is still
        // flagged, in general terms, so a holiday fund is not quietly spent either.
        val dipsIntoEmergencyFund = emergency.hasGoal && remainingAfterPurchase < emergency.currentAmount
        val dipsIntoGoals = remainingAfterPurchase < setAside

        // The lowest point the balance is expected to reach at any month end, which is
        // what actually determines whether a purchase causes trouble. A comfortable
        // final month can easily hide an empty one in between.
        var running = available - purchaseAmount
        var lowestPoint = running
        var lowestMonthLabel: String? = null
        forecast.forEach { month ->
            running = running + month.totalInflow - month.totalOutflow
            if (running < lowestPoint) {
                lowestPoint = running
                lowestMonthLabel = com.moneyplanner.core.time.DateUtil.formatMonth(month.month)
            }
        }

        // A cushion of half a month of essentials is treated as the line between
        // comfortable and tight. It is a rule of thumb, and the app says so.
        val comfortCushion = monthlyEssential.divideRounded(2)

        val verdict = when {
            purchaseAmount > available -> AffordabilityVerdict.NOT_RECOMMENDED
            lowestPoint.isNegative -> AffordabilityVerdict.NOT_RECOMMENDED
            lowestPoint < comfortCushion -> AffordabilityVerdict.BE_CAREFUL
            dipsIntoEmergencyFund || dipsIntoGoals -> AffordabilityVerdict.BE_CAREFUL
            else -> AffordabilityVerdict.SAFE
        }

        val headline = when (verdict) {
            AffordabilityVerdict.SAFE ->
                "This fits comfortably alongside your upcoming payments."
            AffordabilityVerdict.BE_CAREFUL ->
                if (lowestPoint < comfortCushion) {
                    "You can do this, but it leaves little room for anything unexpected."
                } else {
                    // Room to spare at every month end, so the only trouble is the savings.
                    "You can do this, but only by using money you have set aside."
                }
            AffordabilityVerdict.NOT_RECOMMENDED ->
                "This purchase would put your upcoming payments under pressure."
        }

        val reasons = buildList {
            if (purchaseAmount > available) {
                add("The amount is more than the money you have available right now.")
            }
            if (lowestPoint.isNegative) {
                val where = lowestMonthLabel?.let { " around $it" }.orEmpty()
                add("Your balance is projected to fall below zero$where after this purchase.")
            } else if (lowestPoint < comfortCushion && monthlyEssential.isPositive) {
                add("At its lowest point you would be left with less than half a month of essential expenses.")
            }
            if (dipsIntoEmergencyFund) {
                add("Covering this would mean dipping into money you have set aside as your emergency fund.")
            } else if (dipsIntoGoals) {
                add(
                    "Covering this would mean using money you have set aside for your goals: " +
                        "${IndianFormat.format(setAside)} of your balance is set aside, and " +
                        "you would have ${IndianFormat.format(remainingAfterPurchase.coerceAtLeastZero())} left."
                )
            }
            if (isEmpty()) {
                add("Your projected balance stays above your commitments for the whole period.")
            }
        }

        return AffordabilityResult(
            purchaseAmount = purchaseAmount,
            horizonMonths = months,
            availableNow = available,
            expectedIncoming = incoming,
            upcomingCommitments = committed,
            estimatedEverydaySpend = everyday,
            remainingAfterPurchase = remainingAfterPurchase,
            projectedBalanceAtEnd = projectedClose,
            lowestProjectedBalance = lowestPoint,
            lowestPointMonth = lowestMonthLabel,
            monthlyEssentialExpenses = monthlyEssential,
            verdict = verdict,
            headline = headline,
            reasons = reasons,
            /**
             * If it is tight, say how long waiting would help. Based on the projected
             * monthly surplus, so it is only offered when there is a surplus to build on.
             */
            monthsToWaitForComfort = monthsToWait(
                shortfall = (comfortCushion - lowestPoint).coerceAtLeastZero(),
                monthlySurplus = averageSurplus(forecast)
            )
        )
    }

    private fun averageSurplus(forecast: List<MonthForecast>): Money {
        if (forecast.isEmpty()) return Money.ZERO
        val total = forecast.sumOfMoney { it.netFlow }
        return total.divideRounded(forecast.size)
    }

    private fun monthsToWait(shortfall: Money, monthlySurplus: Money): Int? {
        if (!shortfall.isPositive) return null
        if (!monthlySurplus.isPositive) return null
        var months = 0
        var accumulated = Money.ZERO
        while (accumulated < shortfall && months < 24) {
            accumulated += monthlySurplus
            months++
        }
        return if (accumulated >= shortfall) months else null
    }
}

data class AffordabilityResult(
    val purchaseAmount: Money,
    val horizonMonths: Int,
    val availableNow: Money,
    val expectedIncoming: Money,
    val upcomingCommitments: Money,
    val estimatedEverydaySpend: Money,
    val remainingAfterPurchase: Money,
    val projectedBalanceAtEnd: Money,
    val lowestProjectedBalance: Money,
    val lowestPointMonth: String?,
    val monthlyEssentialExpenses: Money,
    val verdict: AffordabilityVerdict,
    val headline: String,
    val reasons: List<String>,
    val monthsToWaitForComfort: Int?
)
