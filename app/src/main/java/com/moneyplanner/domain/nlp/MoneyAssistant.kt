package com.moneyplanner.domain.nlp

import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.domain.calc.AffordabilityCalculator
import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.calc.EmergencyFundCalculator
import com.moneyplanner.domain.calc.EmiCalculator
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.SavingsCalculator
import com.moneyplanner.domain.calc.SettlementCalculator
import com.moneyplanner.domain.calc.SpendingAnalyzer
import com.moneyplanner.domain.model.AffordabilityVerdict
import com.moneyplanner.domain.model.FinancialSnapshot
import java.time.YearMonth

/**
 * Answers questions about the user's own money, on the device.
 *
 * This is a rules engine, not a language model. It matches a question against a fixed set
 * of intents and then answers from the same calculators the rest of the app uses, so every
 * figure it quotes is one the user can go and verify on another screen. It is offline,
 * instant, free, and — the property that matters for money — incapable of inventing a
 * number that is not in the data.
 *
 * The honest limitation is that it only understands what it has been taught. When nothing
 * matches it says so and offers what it can answer, rather than guessing.
 */
object MoneyAssistant {

    fun answer(question: String, snapshot: FinancialSnapshot): AssistantAnswer {
        val text = question.lowercase().trim()
        if (text.isEmpty()) return unknown(snapshot)

        // Ordered most specific first: "how much can I spend on food" is a category
        // question, not a general "how much can I spend" question.
        return when {
            asksAffordability(text) -> answerAffordability(text, snapshot)
            asksCategorySpend(text) -> answerCategorySpend(text, snapshot)
            asksWhoOwesMe(text) -> answerWhoOwesMe(snapshot)
            asksWhomDoIOwe(text) -> answerWhomDoIOwe(snapshot)
            asksEmi(text) -> answerEmi(snapshot)
            asksBills(text) -> answerBills(snapshot)
            asksSafeToSpend(text) -> answerSafeToSpend(snapshot)
            asksNextMonth(text) -> answerNextMonth(snapshot)
            asksSavings(text) -> answerSavings(snapshot)
            asksEmergencyFund(text) -> answerEmergencyFund(snapshot)
            asksSpendThisMonth(text) -> answerSpentThisMonth(snapshot)
            asksIncome(text) -> answerIncome(snapshot)
            asksBalance(text) -> answerBalance(snapshot)
            else -> unknown(snapshot)
        }
    }

    // ---- Intent matching ----------------------------------------------------------

    private fun String.hasAny(vararg terms: String) = terms.any { contains(it) }

    private fun asksBalance(t: String) =
        t.hasAny("how much do i have", "how much money", "my balance", "current balance",
            "available", "balance")

    private fun asksSafeToSpend(t: String) =
        t.hasAny("safely spend", "safe to spend", "can i spend", "how much can i spend",
            "spare", "free to spend")

    private fun asksWhoOwesMe(t: String) =
        t.hasAny("owes me", "owe me", "who owes", "receivable", "get back", "lent")

    private fun asksWhomDoIOwe(t: String) =
        t.hasAny("do i owe", "i owe", "whom do i", "payable", "borrowed")

    private fun asksEmi(t: String) = t.hasAny("emi", "loan", "installment", "instalment")

    private fun asksBills(t: String) =
        t.hasAny("bill", "due", "upcoming", "coming up", "pay this month", "to pay")

    private fun asksNextMonth(t: String) =
        t.hasAny("next month", "forecast", "future", "month ahead", "will i have")

    private fun asksSavings(t: String) =
        t.hasAny("saving", "goal", "how much have i saved", "save")

    private fun asksEmergencyFund(t: String) = t.hasAny("emergency")

    private fun asksSpendThisMonth(t: String) =
        t.hasAny("spent this month", "how much did i spend", "my spending", "spent so far")

    private fun asksIncome(t: String) = t.hasAny("income", "salary", "earn", "receive")

    private fun asksAffordability(t: String) =
        t.hasAny("can i afford", "should i buy", "afford")

    private fun asksCategorySpend(t: String) =
        (t.contains("spend") || t.contains("spent")) && t.contains(" on ")

    // ---- Answers ------------------------------------------------------------------

    private fun answerBalance(snapshot: FinancialSnapshot): AssistantAnswer {
        val breakdown = BalanceCalculator.breakdown(snapshot)
        return AssistantAnswer(
            headline = IndianFormat.format(breakdown.available),
            detail = buildString {
                append("That is what you have available right now, worked out from your ")
                append("opening balances and everything you have recorded since.")
                if (breakdown.earmarkedForGoals.isPositive) {
                    append(" ${IndianFormat.format(breakdown.earmarkedForGoals)} of it is ")
                    append("already set aside for goals.")
                }
            },
            action = AssistantAction.OPEN_DASHBOARD
        )
    }

    private fun answerSafeToSpend(snapshot: FinancialSnapshot): AssistantAnswer {
        val month = ForecastCalculator.forecast(snapshot, 1).first()
        return AssistantAnswer(
            headline = IndianFormat.format(month.safeToSpend),
            detail = "That is what is left for the rest of ${DateUtil.formatMonth(month.month)} " +
                "once every commitment you have recorded is met, including " +
                "${IndianFormat.format(month.committedOutflow)} of payments still to come.",
            action = AssistantAction.OPEN_FORECAST
        )
    }

    private fun answerWhoOwesMe(snapshot: FinancialSnapshot): AssistantAnswer {
        val summaries = SettlementCalculator
            .summaries(snapshot.people, snapshot.ledgerEntries, snapshot.settlements)
            .filter { it.theyOweMe }
            .sortedByDescending { it.balance.paise }

        if (summaries.isEmpty()) {
            return AssistantAnswer(
                headline = "Nobody",
                detail = "No one currently owes you anything that you have recorded.",
                action = AssistantAction.OPEN_PEOPLE
            )
        }

        val total = summaries.sumOfMoney { it.balance }
        return AssistantAnswer(
            headline = IndianFormat.format(total),
            detail = summaries.joinToString("\n") { summary ->
                "${summary.person.name} owes you ${IndianFormat.format(summary.balance)}"
            },
            action = AssistantAction.OPEN_PEOPLE
        )
    }

    private fun answerWhomDoIOwe(snapshot: FinancialSnapshot): AssistantAnswer {
        val summaries = SettlementCalculator
            .summaries(snapshot.people, snapshot.ledgerEntries, snapshot.settlements)
            .filter { it.iOweThem }
            .sortedBy { it.balance.paise }

        if (summaries.isEmpty()) {
            return AssistantAnswer(
                headline = "Nobody",
                detail = "You have not recorded owing anything to anyone.",
                action = AssistantAction.OPEN_PEOPLE
            )
        }

        val total = summaries.sumOfMoney { it.balance.abs() }
        return AssistantAnswer(
            headline = IndianFormat.format(total),
            detail = summaries.joinToString("\n") { summary ->
                "You owe ${summary.person.name} ${IndianFormat.format(summary.balance.abs())}"
            },
            action = AssistantAction.OPEN_PEOPLE
        )
    }

    private fun answerEmi(snapshot: FinancialSnapshot): AssistantAnswer {
        val active = snapshot.emis
            .filter { it.isActive }
            .filterNot { EmiCalculator.isCompleted(it, snapshot.emiPayments) }

        if (active.isEmpty()) {
            return AssistantAnswer(
                headline = "No active loans",
                detail = "You have not recorded any EMIs that are still running.",
                action = AssistantAction.OPEN_PLANS
            )
        }

        val monthly = active.sumOfMoney { emi ->
            if (emi.frequency.isWeekly) emi.emiAmount * 4
            else emi.emiAmount.divideRounded(emi.frequency.monthsPerPeriod)
        }
        val outstanding = active.sumOfMoney {
            EmiCalculator.outstandingAmount(it, snapshot.emiPayments)
        }

        return AssistantAnswer(
            headline = "${IndianFormat.format(monthly)} a month",
            detail = buildString {
                append("Across ${active.size} ")
                append(if (active.size == 1) "loan" else "loans")
                append(", with ${IndianFormat.format(outstanding)} still to repay in total.\n")
                append(
                    active.joinToString("\n") { emi ->
                        val next = EmiCalculator.nextDueDate(emi, snapshot.emiPayments)
                        val remaining = EmiCalculator.remainingInstallments(emi, snapshot.emiPayments)
                        "${emi.name}: ${IndianFormat.format(emi.emiAmount)}" +
                            (next?.let { ", next on ${DateUtil.formatDayMonth(it)}" } ?: "") +
                            ", $remaining left"
                    }
                )
            },
            action = AssistantAction.OPEN_PLANS
        )
    }

    private fun answerBills(snapshot: FinancialSnapshot): AssistantAnswer {
        val month = ForecastCalculator.forecast(snapshot, 1).first()
        val upcoming = month.items
            .filter { !it.isInflow && !it.date.isBefore(snapshot.today) }
            .sortedBy { it.date }

        if (upcoming.isEmpty()) {
            return AssistantAnswer(
                headline = "Nothing left",
                detail = "You have no scheduled payments remaining in " +
                    "${DateUtil.formatMonth(month.month)}.",
                action = AssistantAction.OPEN_CALENDAR
            )
        }

        val total = upcoming.sumOfMoney { it.amount }
        return AssistantAnswer(
            headline = IndianFormat.format(total),
            detail = "Still to pay in ${DateUtil.formatMonth(month.month)}:\n" +
                upcoming.take(6).joinToString("\n") { item ->
                    "${DateUtil.formatDayMonth(item.date)} · ${item.title} · " +
                        IndianFormat.format(item.amount)
                },
            action = AssistantAction.OPEN_CALENDAR
        )
    }

    private fun answerNextMonth(snapshot: FinancialSnapshot): AssistantAnswer {
        val forecast = ForecastCalculator.forecast(snapshot, 2)
        val next = forecast.getOrNull(1)
            ?: return unknown(snapshot)

        return AssistantAnswer(
            headline = IndianFormat.format(next.closingBalance),
            detail = "That is what ${DateUtil.formatMonth(next.month)} is projected to end " +
                "with: ${IndianFormat.format(next.totalInflow)} expected in against " +
                "${IndianFormat.format(next.totalOutflow)} going out." +
                if (next.isShortfall) " That month is projected to fall short." else "",
            action = AssistantAction.OPEN_FORECAST
        )
    }

    private fun answerSavings(snapshot: FinancialSnapshot): AssistantAnswer {
        val progress = SavingsCalculator.allProgress(snapshot)
        if (progress.isEmpty()) {
            return AssistantAnswer(
                headline = "No goals yet",
                detail = "You have not set up any savings goals.",
                action = AssistantAction.OPEN_GOALS
            )
        }

        val saved = progress.sumOfMoney { it.saved }
        val required = progress.filterNot { it.isAchieved }.sumOfMoney { it.requiredMonthlySaving }

        return AssistantAnswer(
            headline = IndianFormat.format(saved),
            detail = buildString {
                append("Saved across ${progress.size} ")
                append(if (progress.size == 1) "goal" else "goals")
                append(".")
                if (required.isPositive) {
                    append(" Staying on track needs ${IndianFormat.format(required)} a month.")
                }
                append("\n")
                append(
                    progress.joinToString("\n") { row ->
                        "${row.goal.name}: ${IndianFormat.format(row.saved)} of " +
                            "${IndianFormat.format(row.goal.targetAmount)} " +
                            "(${row.progressPercent}%)"
                    }
                )
            },
            action = AssistantAction.OPEN_GOALS
        )
    }

    private fun answerEmergencyFund(snapshot: FinancialSnapshot): AssistantAnswer {
        val status = EmergencyFundCalculator.calculate(snapshot)
        if (status.targetAmount.isZero) {
            return AssistantAnswer(
                headline = "Not set up",
                detail = "Add your essential bills and some spending history, and the app " +
                    "will work out what your emergency fund target should be.",
                action = AssistantAction.OPEN_EMERGENCY_FUND
            )
        }
        return AssistantAnswer(
            headline = "${IndianFormat.format(status.currentAmount)} of " +
                IndianFormat.format(status.targetAmount),
            detail = "Your essentials cost ${IndianFormat.format(status.monthlyEssentialExpenses)} " +
                "a month, so ${status.monthsOfCover} months of cover means " +
                "${IndianFormat.format(status.targetAmount)}. " +
                if (status.isFunded) {
                    "You are fully funded."
                } else {
                    "${IndianFormat.format(status.remainingAmount)} still to go."
                },
            action = AssistantAction.OPEN_EMERGENCY_FUND
        )
    }

    private fun answerSpentThisMonth(snapshot: FinancialSnapshot): AssistantAnswer {
        val month = snapshot.currentMonth
        val spent = SpendingAnalyzer.totalSpendIn(snapshot, month)
        val breakdown = SpendingAnalyzer.categoryBreakdown(snapshot, month).take(4)

        return AssistantAnswer(
            headline = IndianFormat.format(spent),
            detail = if (breakdown.isEmpty()) {
                "Nothing recorded in ${DateUtil.formatMonth(month)} yet."
            } else {
                "Spent in ${DateUtil.formatMonth(month)}. Biggest categories:\n" +
                    breakdown.joinToString("\n") { row ->
                        "${row.categoryName}: ${IndianFormat.format(row.amount)}"
                    }
            },
            action = AssistantAction.OPEN_REPORTS
        )
    }

    private fun answerIncome(snapshot: FinancialSnapshot): AssistantAnswer {
        val month = snapshot.currentMonth
        val received = snapshot.incomeTransactions
            .filter { YearMonth.from(it.date) == month }
            .sumOfMoney { it.amount }
        val expected = ForecastCalculator.forecast(snapshot, 1).first().expectedIncome

        return AssistantAnswer(
            headline = IndianFormat.format(received),
            detail = "Received in ${DateUtil.formatMonth(month)}." +
                if (expected.isPositive) {
                    " A further ${IndianFormat.format(expected)} is still expected this month."
                } else {
                    ""
                },
            action = AssistantAction.OPEN_INCOME
        )
    }

    /**
     * "Can I afford a 25000 phone" — the amount is pulled straight out of the question and
     * handed to the same calculator the dedicated screen uses.
     */
    private fun answerAffordability(text: String, snapshot: FinancialSnapshot): AssistantAnswer {
        val amount = firstAmountIn(text)
            ?: return AssistantAnswer(
                headline = "How much?",
                detail = "Tell me the amount and I will check it against what is coming, " +
                    "for example \"can I afford 25000\".",
                action = AssistantAction.OPEN_AFFORDABILITY
            )

        val result = AffordabilityCalculator.check(snapshot, amount)
        return AssistantAnswer(
            headline = result.verdict.label,
            detail = buildString {
                append(result.headline)
                append("\n\n")
                append(result.reasons.joinToString("\n") { "• $it" })
                append("\n\nAvailable now ${IndianFormat.format(result.availableNow)}, ")
                append("commitments ${IndianFormat.format(result.upcomingCommitments)} ")
                append("over the next ${result.horizonMonths} months.")
            },
            action = AssistantAction.OPEN_AFFORDABILITY,
            tone = when (result.verdict) {
                AffordabilityVerdict.SAFE -> AnswerTone.POSITIVE
                AffordabilityVerdict.BE_CAREFUL -> AnswerTone.CAUTION
                AffordabilityVerdict.NOT_RECOMMENDED -> AnswerTone.NEGATIVE
            }
        )
    }

    private fun answerCategorySpend(text: String, snapshot: FinancialSnapshot): AssistantAnswer {
        val month = snapshot.currentMonth
        val breakdown = SpendingAnalyzer.categoryBreakdown(snapshot, month)

        val match = breakdown.firstOrNull { row ->
            text.contains(row.categoryName.lowercase())
        } ?: return answerSpentThisMonth(snapshot)

        return AssistantAnswer(
            headline = IndianFormat.format(match.amount),
            detail = "Spent on ${match.categoryName} in ${DateUtil.formatMonth(month)}, " +
                "across ${match.transactionCount} " +
                (if (match.transactionCount == 1) "entry" else "entries") +
                ". That is ${(match.shareOfTotal * 100).toInt()}% of the month's spending.",
            action = AssistantAction.OPEN_REPORTS
        )
    }

    private fun unknown(snapshot: FinancialSnapshot): AssistantAnswer = AssistantAnswer(
        headline = "I am not sure about that one",
        detail = "I can answer things like: how much do I have, how much can I safely " +
            "spend, who owes me money, what are my EMIs, what is due this month, what " +
            "will I have next month, or can I afford 25000.",
        action = AssistantAction.NONE,
        isUnderstood = false
    )

    /** Pulls the first plain number out of a question, so "afford 25000" works. */
    private fun firstAmountIn(text: String): Money? {
        val cleaned = text.replace(",", "").replace("₹", " ")
        val match = Regex("\\d+(\\.\\d+)?").find(cleaned) ?: return null
        val value = match.value.toDoubleOrNull() ?: return null

        // Respect a spoken multiplier right after the number.
        val after = cleaned.substring(match.range.last + 1).trim().split(" ").firstOrNull()
        val multiplier = when (after) {
            "thousand", "k" -> 1_000L
            "lakh", "lakhs", "lac" -> 100_000L
            "crore", "crores" -> 10_000_000L
            else -> 1L
        }
        val total = value * multiplier
        return Money(Math.round(total * 100)).takeIf { it.isPositive }
    }

    /** The questions offered as starting points when the assistant is opened. */
    val SUGGESTIONS = listOf(
        "How much do I have?",
        "How much can I safely spend?",
        "Who owes me money?",
        "What are my EMIs?",
        "What is due this month?",
        "What will I have next month?",
        "How much did I spend on food?",
        "Can I afford 25000?"
    )
}

enum class AnswerTone { NEUTRAL, POSITIVE, CAUTION, NEGATIVE }

/** Where the answer can take the user next, so it ends in something actionable. */
enum class AssistantAction {
    NONE,
    OPEN_DASHBOARD,
    OPEN_FORECAST,
    OPEN_PEOPLE,
    OPEN_PLANS,
    OPEN_CALENDAR,
    OPEN_GOALS,
    OPEN_EMERGENCY_FUND,
    OPEN_REPORTS,
    OPEN_INCOME,
    OPEN_AFFORDABILITY
}

data class AssistantAnswer(
    val headline: String,
    val detail: String,
    val action: AssistantAction = AssistantAction.NONE,
    val tone: AnswerTone = AnswerTone.NEUTRAL,
    /** False when nothing matched, which the UI shows plainly rather than dressing up. */
    val isUnderstood: Boolean = true
)
