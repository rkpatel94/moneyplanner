package com.moneyplanner

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.ForecastItemKind
import com.moneyplanner.domain.calc.InsightsCalculator
import com.moneyplanner.domain.calc.InsightKind
import com.moneyplanner.domain.calc.RecurrenceCalculator
import com.moneyplanner.domain.calc.SavingsCalculator
import com.moneyplanner.domain.calc.SpendingAnalyzer
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.Frequency
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.ui.screens.expenses.toEditableText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * Guards for behaviour that was wrong and has been corrected.
 *
 * Each of these failed before the fix it covers. They are grouped here rather than spread
 * across the existing suites because what they have in common is being the evidence that
 * a specific defect does not come back.
 */
class RegressionFixesTest {

    // ---- Income credited early ----------------------------------------------------

    @Test
    fun `a salary paid before its due date settles the month it was for`() {
        val salary = incomeSource(id = 1, amount = 55_000, dayOfMonth = 1)
        val paidEarly = RecurrenceCalculator.nearestOccurrence(
            around = date("2026-08-31"),
            start = date("2026-01-01"),
            end = null,
            dayOfMonth = 1,
            frequency = Frequency.MONTHLY
        )
        assertEquals(
            "a salary due on the 1st and paid on the 31st is next month's",
            date("2026-09-01"),
            paidEarly
        )
        assertNotNull(salary)
    }

    @Test
    fun `income already received for a month is not expected again`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-26"),
            accounts = listOf(account(opening = 10_000)),
            incomeSources = listOf(incomeSource(id = 1, amount = 55_000, dayOfMonth = 1)),
            // Credited on 31 Aug, but it is September's salary and says so.
            incomeTransactions = listOf(
                incomeTransaction(
                    id = 1,
                    sourceId = 1,
                    amount = 55_000,
                    date = "2026-08-31",
                    periodKey = "2026-09"
                )
            )
        )

        val september = ForecastCalculator.forecast(snap, 3)
            .first { it.month == YearMonth.of(2026, 9) }
        val salaryItems = september.items.filter { it.kind == ForecastItemKind.INCOME }

        assertTrue(
            "September must not expect a salary that has already been paid, got $salaryItems",
            salaryItems.none { it.subtitle != "Recorded in advance" }
        )
    }

    @Test
    fun `a receipt with no period key still falls back to its own month`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-26"),
            accounts = listOf(account(opening = 10_000)),
            incomeSources = listOf(incomeSource(id = 1, amount = 55_000, dayOfMonth = 1)),
            incomeTransactions = listOf(
                incomeTransaction(id = 1, sourceId = 1, amount = 55_000, date = "2026-08-01")
            )
        )

        val august = ForecastCalculator.forecast(snap, 1).first()
        assertTrue(
            "an untagged receipt must still cancel its own month",
            august.items.none { it.kind == ForecastItemKind.INCOME }
        )
    }

    // ---- Overdue savings goals -----------------------------------------------------

    @Test
    fun `an overdue goal does not demand its whole balance every month`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-18"),
            goals = listOf(goal(id = 1, target = 500_000, targetDate = "2026-05-01")),
            contributions = emptyList()
        )

        val progress = SavingsCalculator.allProgress(snap).single()
        assertTrue("the goal is past its date", progress.isBehindSchedule)

        assertEquals(
            "a missed deadline must not become a monthly plan",
            Money.ZERO,
            SavingsCalculator.totalRequiredMonthlySaving(snap)
        )
    }

    @Test
    fun `a goal still in the future keeps contributing to the monthly plan`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-18"),
            goals = listOf(goal(id = 1, target = 60_000, targetDate = "2027-08-01")),
            contributions = emptyList()
        )
        assertTrue(SavingsCalculator.totalRequiredMonthlySaving(snap).isPositive)
    }

    // ---- Overdue person balances ---------------------------------------------------

    @Test
    fun `a balance whose expected date has passed stays in the forecast`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-18"),
            accounts = listOf(account(opening = 10_000)),
            people = listOf(person(id = 1)),
            ledgerEntries = listOf(
                ledgerEntry(
                    id = 1,
                    personId = 1,
                    amount = 5_000,
                    direction = LedgerDirection.THEY_OWE_ME,
                    date = "2026-04-01",
                    expectedDate = "2026-05-01"
                )
            )
        )

        val items = ForecastCalculator.forecast(snap, 3).flatMap { it.items }
            .filter { it.kind == ForecastItemKind.PERSON_INCOMING }

        assertEquals("the overdue balance must still appear once", 1, items.size)
        assertTrue("and be marked overdue", items.single().subtitle.contains("overdue"))
    }

    @Test
    fun `a balance with no expected date is still left out`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-18"),
            accounts = listOf(account(opening = 10_000)),
            people = listOf(person(id = 1)),
            ledgerEntries = listOf(ledgerEntry(id = 1, personId = 1, amount = 5_000))
        )
        assertTrue(
            ForecastCalculator.forecast(snap, 3).flatMap { it.items }
                .none { it.kind == ForecastItemKind.PERSON_INCOMING }
        )
    }

    // ---- Like-for-like month comparison --------------------------------------------

    @Test
    fun `spending up to a day ignores what came after it`() {
        val snap = FinancialSnapshot(
            today = date("2026-08-26"),
            expenses = listOf(
                expense(id = 1, amount = 1_000, date = "2026-07-05"),
                expense(id = 2, amount = 9_000, date = "2026-07-28")
            )
        )
        assertEquals(
            rupees(1_000),
            SpendingAnalyzer.spendUpToDayIn(snap, YearMonth.of(2026, 7), 10)
        )
    }

    @Test
    fun `a partial month is not congratulated for being unfinished`() {
        // Same daily pace in both months. Comparing all of July against 26 days of August
        // used to report a saving; measured over the same window there is none.
        val july = (1..31).map { day ->
            expense(id = day.toLong(), amount = 1_000, date = "2026-07-%02d".format(day))
        }
        val august = (1..26).map { day ->
            expense(id = 100L + day, amount = 1_000, date = "2026-08-%02d".format(day))
        }
        val snap = FinancialSnapshot(
            today = date("2026-08-26"),
            categories = listOf(category(id = 1)),
            expenses = july + august
        )

        assertTrue(
            "an identical pace must not be reported as a win",
            InsightsCalculator.generate(snap).none { it.kind == InsightKind.GOOD_NEWS }
        )
    }

    @Test
    fun `a genuinely cheaper month is still reported`() {
        val july = (1..31).map { day ->
            expense(id = day.toLong(), amount = 1_000, date = "2026-07-%02d".format(day))
        }
        val august = (1..26).map { day ->
            expense(id = 100L + day, amount = 400, date = "2026-08-%02d".format(day))
        }
        val snap = FinancialSnapshot(
            today = date("2026-08-26"),
            categories = listOf(category(id = 1)),
            expenses = july + august
        )

        assertTrue(
            InsightsCalculator.generate(snap).any { it.kind == InsightKind.GOOD_NEWS }
        )
    }

    // ---- Amount rendering ----------------------------------------------------------

    @Test
    fun `a negative amount under one rupee keeps its sign`() {
        assertEquals("-0.50", Money(-50).toEditableText())
        assertEquals("-1.25", Money(-125).toEditableText())
        assertEquals("-2", Money(-200).toEditableText())
        assertEquals("0.50", Money(50).toEditableText())
        assertEquals("2", Money(200).toEditableText())
    }

    @Test
    fun `an editable amount survives a round trip through the parser`() {
        listOf(-50L, -125L, -200L, 0L, 50L, 125L, 123_456L).forEach { paise ->
            val text = Money(paise).toEditableText()
            assertEquals("round trip of $paise via \"$text\"", Money(paise), Money.parseOrNull(text))
        }
    }

    // ---- Nearest occurrence edge cases ---------------------------------------------

    @Test
    fun `nearest occurrence returns null when the schedule has ended`() {
        assertNull(
            RecurrenceCalculator.nearestOccurrence(
                around = date("2026-08-15"),
                start = date("2020-01-01"),
                end = date("2021-01-01"),
                dayOfMonth = 1,
                frequency = Frequency.MONTHLY
            )
        )
    }

    @Test
    fun `nearest occurrence prefers the closer of two due dates`() {
        assertEquals(
            date("2026-08-01"),
            RecurrenceCalculator.nearestOccurrence(
                around = date("2026-08-03"),
                start = date("2026-01-01"),
                end = null,
                dayOfMonth = 1,
                frequency = Frequency.MONTHLY
            )
        )
    }
}
