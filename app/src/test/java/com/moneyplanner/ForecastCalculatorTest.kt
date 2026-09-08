package com.moneyplanner

import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.ForecastItemKind
import com.moneyplanner.domain.model.AnnualExpensePayment
import com.moneyplanner.domain.model.BillPayment
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * The forecast is the reason this application exists, so these tests focus on the rules
 * that keep it honest: an obligation is counted once, a credit card purchase is not a
 * cash outflow, and the current month only projects what is still to come.
 */
class ForecastCalculatorTest {

    private val today = "2026-08-18"

    private fun base(opening: Long = 50_000) = FinancialSnapshot(
        today = date(today),
        accounts = listOf(account(opening = opening))
    )

    private fun monthOf(snapshot: FinancialSnapshot, offset: Int) =
        ForecastCalculator.forecast(snapshot, offset + 1)[offset]

    @Test
    fun `a salary appears in every future month`() {
        val snap = base().copy(incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 1)))
        val september = monthOf(snap, 1)

        assertEquals(YearMonth.of(2026, 9), september.month)
        assertEquals(rupees(55_000), september.expectedIncome)
    }

    @Test
    fun `a salary already received this month is not expected again`() {
        val snap = base().copy(
            incomeSources = listOf(incomeSource(id = 1, amount = 55_000, dayOfMonth = 1)),
            incomeTransactions = listOf(
                incomeTransaction(sourceId = 1, amount = 55_000, date = "2026-08-01")
            )
        )
        val august = monthOf(snap, 0)
        assertEquals(
            "money already in the balance must not be counted a second time",
            rupees(0),
            august.expectedIncome
        )
    }

    @Test
    fun `a salary that has not arrived yet is still expected even if the date has passed`() {
        val snap = base().copy(
            incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 1))
        )
        assertEquals(rupees(55_000), monthOf(snap, 0).expectedIncome)
    }

    @Test
    fun `an annual increment is applied on the anniversary of the start date`() {
        val snap = base().copy(
            incomeSources = listOf(
                incomeSource(
                    amount = 100_000, dayOfMonth = 1,
                    startDate = "2026-04-01", incrementPercent = 10.0
                )
            )
        )
        // March 2027 is still before the anniversary.
        assertEquals(rupees(100_000), monthOf(snap, 7).expectedIncome)
        // April 2027 is the first month at the new rate.
        assertEquals(rupees(110_000), monthOf(snap, 8).expectedIncome)
    }

    @Test
    fun `an emi appears in the month its installment falls due`() {
        val snap = base().copy(
            emis = listOf(emi(emiAmount = 4_500, firstDueDate = "2026-01-05", totalInstallments = 36))
        )
        val september = monthOf(snap, 1)
        assertEquals(rupees(4_500), september.emiOutflow)
        assertTrue(september.items.any { it.kind == ForecastItemKind.EMI })
    }

    @Test
    fun `a finished loan drops out of the forecast`() {
        val loan = emi(emiAmount = 4_500, firstDueDate = "2026-01-05", totalInstallments = 3)
        val payments = (1..3).map {
            emiPayment(
                id = it.toLong(), installmentNumber = it,
                dueDate = "2026-0$it-05", paidDate = "2026-0$it-05"
            )
        }
        val snap = base().copy(emis = listOf(loan), emiPayments = payments)

        val forecast = ForecastCalculator.forecast(snap, 6)
        assertTrue(
            "a repaid loan must stop appearing in future months",
            forecast.all { it.emiOutflow.isZero }
        )
    }

    @Test
    fun `a bill already paid for the month is not charged again`() {
        val rent = bill(id = 1, amount = 13_000, dueDay = 5)
        val unpaid = base().copy(bills = listOf(rent))
        assertEquals(rupees(13_000), monthOf(unpaid, 0).billsOutflow)

        val paid = unpaid.copy(
            billPayments = listOf(
                BillPayment(
                    id = 1, billId = 1, periodKey = "2026-08",
                    amount = rupees(13_000), dueDate = date("2026-08-05"),
                    paidDate = date("2026-08-05"), notes = ""
                )
            )
        )
        assertEquals(
            "paying rent must remove it from the month, not leave it pending",
            rupees(0),
            monthOf(paid, 0).billsOutflow
        )
        assertEquals(
            "next month's rent is still due",
            rupees(13_000),
            monthOf(paid, 1).billsOutflow
        )
    }

    @Test
    fun `an annual expense lands only in the month it is due`() {
        val snap = base().copy(
            annualExpenses = listOf(annualExpense(amount = 8_000, dueMonth = 1, dueDay = 15))
        )
        val forecast = ForecastCalculator.forecast(snap, 12)

        val january = forecast.first { it.month == YearMonth.of(2027, 1) }
        assertEquals(rupees(8_000), january.annualOutflow)

        val others = forecast.filterNot { it.month == YearMonth.of(2027, 1) }
        assertTrue(others.all { it.annualOutflow.isZero })
    }

    @Test
    fun `an annual expense paid for the year does not reappear`() {
        val snap = base().copy(
            annualExpenses = listOf(annualExpense(id = 1, amount = 8_000, dueMonth = 9, dueDay = 15)),
            annualExpensePayments = listOf(
                AnnualExpensePayment(
                    id = 1, annualExpenseId = 1, year = 2026,
                    amount = rupees(8_000), paidDate = date("2026-09-15")
                )
            )
        )
        assertEquals(rupees(0), monthOf(snap, 1).annualOutflow)
    }

    @Test
    fun `the recommended monthly reserve for a yearly bill is one twelfth`() {
        assertEquals(rupees(8_000).divideRounded(12), annualExpense(amount = 8_000).monthlyReserve)
        assertEquals(66_667L, annualExpense(amount = 8_000).monthlyReserve.paise)
    }

    @Test
    fun `a credit card due is counted once and not repeated every month`() {
        val snap = base().copy(creditCards = listOf(creditCard(outstanding = 8_000, dueDay = 15)))
        val forecast = ForecastCalculator.forecast(snap, 6)

        val monthsCharged = forecast.count { it.creditCardOutflow.isPositive }
        assertEquals(
            "the card outstanding is a single known debt, not a monthly subscription",
            1,
            monthsCharged
        )
        assertEquals(rupees(8_000), forecast.first { it.creditCardOutflow.isPositive }.creditCardOutflow)
    }

    @Test
    fun `a card purchase is never counted as both spending and a card due`() {
        val snap = base().copy(
            categories = listOf(category(1)),
            creditCards = listOf(creditCard(outstanding = 5_000, dueDay = 15)),
            expenses = listOf(
                expense(amount = 5_000, date = "2026-08-10", method = PaymentMethod.CREDIT_CARD)
            )
        )
        val forecast = ForecastCalculator.forecast(snap, 3)
        val totalOut = forecast.fold(rupees(0)) { acc, month -> acc + month.totalOutflow }

        assertEquals(
            "the card purchase must leave the balance exactly once, as the card bill",
            rupees(5_000),
            totalOut
        )
    }

    @Test
    fun `money expected from a friend counts as incoming in the month it is due`() {
        val snap = base().copy(
            people = listOf(person(1, "Amit")),
            ledgerEntries = listOf(
                ledgerEntry(amount = 5_000, date = "2026-08-01", expectedDate = "2026-09-10")
            )
        )
        assertEquals(rupees(5_000), monthOf(snap, 1).expectedIncomingFromPeople)
    }

    @Test
    fun `only the unsettled part of a loan to a friend is expected`() {
        val snap = base().copy(
            people = listOf(person(1, "Amit")),
            ledgerEntries = listOf(
                ledgerEntry(amount = 5_000, date = "2026-08-01", expectedDate = "2026-09-10")
            ),
            settlements = listOf(settlement(amount = 2_000, date = "2026-08-15"))
        )
        assertEquals(rupees(3_000), monthOf(snap, 1).expectedIncomingFromPeople)
    }

    @Test
    fun `money the user owes is projected as an outflow`() {
        val snap = base().copy(
            people = listOf(person(1, "Rahul")),
            ledgerEntries = listOf(
                ledgerEntry(
                    amount = 2_500, direction = LedgerDirection.I_OWE_THEM,
                    date = "2026-08-01", expectedDate = "2026-09-05"
                )
            )
        )
        assertEquals(rupees(2_500), monthOf(snap, 1).owedToPeopleOutflow)
    }

    @Test
    fun `a balance with no expected date is not guessed into a month`() {
        val snap = base().copy(
            people = listOf(person(1, "Amit")),
            ledgerEntries = listOf(ledgerEntry(amount = 5_000, expectedDate = null))
        )
        val forecast = ForecastCalculator.forecast(snap, 6)
        assertTrue(forecast.all { it.expectedIncomingFromPeople.isZero })
    }

    @Test
    fun `each month opens with the closing balance of the month before`() {
        val snap = base(opening = 50_000).copy(
            incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 1)),
            bills = listOf(bill(amount = 13_000, dueDay = 5))
        )
        val forecast = ForecastCalculator.forecast(snap, 4)

        forecast.zipWithNext().forEach { (earlier, later) ->
            assertEquals(earlier.closingBalance, later.openingBalance)
        }
        assertEquals(rupees(50_000), forecast.first().openingBalance)
    }

    @Test
    fun `a shortfall is carried forward rather than reset each month`() {
        val snap = base(opening = 5_000).copy(
            bills = listOf(bill(amount = 13_000, dueDay = 5))
        )
        val forecast = ForecastCalculator.forecast(snap, 3)

        assertTrue(forecast[0].isShortfall)
        assertTrue(
            "a deficit must deepen, not silently disappear at the month boundary",
            forecast[2].closingBalance < forecast[0].closingBalance
        )
    }

    @Test
    fun `an expense linked to a bill does not also inflate everyday spending`() {
        val withLink = base().copy(
            categories = listOf(category(1)),
            expenses = listOf(
                expense(id = 1, amount = 13_000, date = "2026-06-05", linkType = ExpenseLinkType.RECURRING_BILL, linkId = 1),
                expense(id = 2, amount = 13_000, date = "2026-07-05", linkType = ExpenseLinkType.RECURRING_BILL, linkId = 1)
            ),
            bills = listOf(bill(id = 1, amount = 13_000, dueDay = 5))
        )
        val september = monthOf(withLink, 1)

        assertEquals(rupees(13_000), september.billsOutflow)
        assertEquals(
            "rent is already projected from its own schedule, so it must not be averaged in too",
            rupees(0),
            september.estimatedEverydaySpend
        )
    }

    @Test
    fun `everyday spending is projected from the user's own history`() {
        val snap = base().copy(
            categories = listOf(category(1)),
            expenses = listOf(
                expense(id = 1, amount = 9_000, date = "2026-06-10"),
                expense(id = 2, amount = 11_000, date = "2026-07-10")
            )
        )
        assertEquals(
            "the average of June and July is what September should expect",
            rupees(10_000),
            monthOf(snap, 1).estimatedEverydaySpend
        )
        assertTrue(monthOf(snap, 1).isProjectionBasedOnHistory)
    }

    @Test
    fun `the current month only projects the days that are left`() {
        val snap = base().copy(
            categories = listOf(category(1)),
            expenses = listOf(
                expense(id = 1, amount = 3_100, date = "2026-07-10")
            )
        )
        // 3,100 over the 31 days of July is 100 a day, and 13 days remain in August.
        assertEquals(rupees(1_300), monthOf(snap, 0).estimatedEverydaySpend)
    }

    @Test
    fun `a brand new install projects no spending rather than inventing a figure`() {
        val snap = base()
        val forecast = ForecastCalculator.forecast(snap, 3)

        assertTrue(forecast.all { it.estimatedEverydaySpend.isZero })
        assertFalse(forecast.first().isProjectionBasedOnHistory)
    }

    @Test
    fun `the worked example from the brief produces the expected remainder`() {
        // Income 55,000, incoming 5,000, EMIs 15,000, rent 13,000, bills 3,000,
        // credit card 8,000 and another 2,000 owed, leaving 19,000.
        val snap = FinancialSnapshot(
            today = date("2026-08-31"),
            accounts = listOf(account(opening = 0, openingDate = "2026-01-01")),
            people = listOf(person(1, "Amit"), person(2, "Rahul")),
            incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 1)),
            ledgerEntries = listOf(
                ledgerEntry(id = 1, personId = 1, amount = 5_000, expectedDate = "2026-09-12"),
                ledgerEntry(
                    id = 2, personId = 2, amount = 2_000,
                    direction = LedgerDirection.I_OWE_THEM, expectedDate = "2026-09-20"
                )
            ),
            emis = listOf(
                emi(id = 1, emiAmount = 15_000, firstDueDate = "2026-01-05", totalInstallments = 36)
            ),
            bills = listOf(
                bill(id = 1, name = "Rent", amount = 13_000, dueDay = 5),
                bill(id = 2, name = "Utilities", amount = 3_000, dueDay = 10)
            ),
            creditCards = listOf(creditCard(outstanding = 8_000, dueDay = 15))
        )

        val september = ForecastCalculator.forecast(snap, 2)[1]

        assertEquals(rupees(55_000), september.expectedIncome)
        assertEquals(rupees(5_000), september.expectedIncomingFromPeople)
        assertEquals(rupees(15_000), september.emiOutflow)
        assertEquals(rupees(16_000), september.billsOutflow)
        assertEquals(rupees(8_000), september.creditCardOutflow)
        assertEquals(rupees(2_000), september.owedToPeopleOutflow)
        assertEquals(
            "60,000 coming in against 41,000 of commitments leaves 19,000 for the month",
            rupees(19_000),
            september.netFlow
        )
    }

    @Test
    fun `forecast items are ordered by date so the calendar reads correctly`() {
        val snap = base().copy(
            incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 1)),
            bills = listOf(bill(amount = 13_000, dueDay = 5)),
            emis = listOf(emi(emiAmount = 4_500, firstDueDate = "2026-01-20", totalInstallments = 36))
        )
        val items = monthOf(snap, 1).items
        assertEquals(items.map { it.date }.sorted(), items.map { it.date })
    }

    @Test
    fun `a month asked for directly matches the same month in the full projection`() {
        val snap = base().copy(
            incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 1)),
            bills = listOf(bill(amount = 13_000, dueDay = 5))
        )
        val fromSeries = ForecastCalculator.forecast(snap, 4)[3]
        val direct = ForecastCalculator.forecastMonth(snap, YearMonth.of(2026, 11))

        assertEquals(fromSeries.month, direct.month)
        assertEquals(fromSeries.openingBalance, direct.openingBalance)
        assertEquals(fromSeries.closingBalance, direct.closingBalance)
    }
}
