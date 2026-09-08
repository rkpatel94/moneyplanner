package com.moneyplanner

import com.moneyplanner.domain.calc.AffordabilityCalculator
import com.moneyplanner.domain.calc.EmergencyFundCalculator
import com.moneyplanner.domain.calc.SavingsCalculator
import com.moneyplanner.domain.model.AffordabilityVerdict
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsCalculatorTest {

    private val today = date("2026-08-18")

    @Test
    fun `a goal balance is the sum of its contributions`() {
        val contributions = listOf(
            contribution(id = 1, amount = 5_000, date = "2026-06-01"),
            contribution(id = 2, amount = 3_000, date = "2026-07-01")
        )
        assertEquals(rupees(8_000), SavingsCalculator.balanceOf(1, contributions))
    }

    @Test
    fun `a withdrawal reduces the goal balance`() {
        val contributions = listOf(
            contribution(id = 1, amount = 5_000),
            contribution(id = 2, amount = -2_000)
        )
        assertEquals(rupees(3_000), SavingsCalculator.balanceOf(1, contributions))
    }

    @Test
    fun `progress reports what is left and what it takes each month`() {
        val target = goal(target = 50_000, targetDate = "2026-12-18")
        val progress = SavingsCalculator.progressOf(
            target, listOf(contribution(amount = 20_000)), today
        )

        assertEquals(rupees(20_000), progress.saved)
        assertEquals(rupees(30_000), progress.remaining)
        assertEquals(40, progress.progressPercent)
        assertEquals(4, progress.monthsRemaining)
        assertEquals(rupees(7_500), progress.requiredMonthlySaving)
    }

    @Test
    fun `a goal with no deadline does not demand a monthly amount`() {
        val progress = SavingsCalculator.progressOf(
            goal(target = 50_000, targetDate = null), emptyList(), today
        )
        assertEquals(rupees(0), progress.requiredMonthlySaving)
        assertNull(progress.monthsRemaining)
    }

    @Test
    fun `an achieved goal asks for nothing more`() {
        val progress = SavingsCalculator.progressOf(
            goal(target = 50_000, targetDate = "2026-12-18"),
            listOf(contribution(amount = 50_000)),
            today
        )
        assertTrue(progress.isAchieved)
        assertEquals(rupees(0), progress.requiredMonthlySaving)
        assertEquals(100, progress.progressPercent)
    }

    @Test
    fun `a goal whose date has passed is flagged as behind schedule`() {
        val progress = SavingsCalculator.progressOf(
            goal(target = 50_000, targetDate = "2026-05-01"),
            listOf(contribution(amount = 10_000)),
            today
        )
        assertTrue(progress.isBehindSchedule)
        assertEquals(rupees(40_000), progress.requiredMonthlySaving)
    }

    @Test
    fun `saving more than the target does not push progress past one hundred percent`() {
        val progress = SavingsCalculator.progressOf(
            goal(target = 10_000), listOf(contribution(amount = 15_000)), today
        )
        assertEquals(100, progress.progressPercent)
        assertEquals(rupees(0), progress.remaining)
    }
}

class EmergencyFundCalculatorTest {

    private fun snapshot(months: Int = 6) = FinancialSnapshot(
        today = date("2026-08-18"),
        profile = UserProfile(emergencyFundMonths = months),
        accounts = listOf(account(opening = 50_000)),
        categories = listOf(
            category(1, "Grocery", essential = true),
            category(2, "Entertainment", essential = false)
        )
    )

    @Test
    fun `essential monthly cost combines bills, loans and essential everyday spending`() {
        val snap = snapshot().copy(
            bills = listOf(
                bill(id = 1, name = "Rent", amount = 13_000, essential = true),
                bill(id = 2, name = "OTT", amount = 500, essential = false)
            ),
            emis = listOf(emi(emiAmount = 4_500, firstDueDate = "2026-01-05", totalInstallments = 36)),
            expenses = listOf(
                expense(id = 1, amount = 6_000, date = "2026-07-05", categoryId = 1),
                expense(id = 2, amount = 4_000, date = "2026-07-20", categoryId = 2)
            )
        )
        val status = EmergencyFundCalculator.calculate(snap)

        assertEquals(rupees(13_000), status.essentialBills)
        assertEquals(rupees(4_500), status.emiBurden)
        assertEquals(
            "only spending in essential categories counts towards survival costs",
            rupees(6_000),
            status.essentialEverydaySpend
        )
        assertEquals(rupees(23_500), status.monthlyEssentialExpenses)
    }

    @Test
    fun `the target follows the number of months the user chose`() {
        val snap = snapshot(months = 6).copy(
            bills = listOf(bill(amount = 30_000, essential = true))
        )
        val status = EmergencyFundCalculator.calculate(snap)

        assertEquals(rupees(30_000), status.monthlyEssentialExpenses)
        assertEquals(rupees(180_000), status.targetAmount)
    }

    @Test
    fun `the worked example from the brief reports the right shortfall`() {
        val snap = snapshot(months = 6).copy(
            bills = listOf(bill(amount = 30_000, essential = true)),
            goals = listOf(goal(id = 1, name = "Emergency fund", target = 180_000, isEmergencyFund = true)),
            contributions = listOf(contribution(goalId = 1, amount = 70_000))
        )
        val status = EmergencyFundCalculator.calculate(snap)

        assertEquals(rupees(180_000), status.targetAmount)
        assertEquals(rupees(70_000), status.currentAmount)
        assertEquals(rupees(110_000), status.remainingAmount)
        assertEquals(38, status.progressPercent)
    }

    @Test
    fun `a yearly commitment contributes its monthly reserve`() {
        val snap = snapshot().copy(
            annualExpenses = listOf(annualExpense(amount = 12_000, dueMonth = 1))
        )
        assertEquals(rupees(1_000), EmergencyFundCalculator.calculate(snap).annualReserve)
    }

    @Test
    fun `an empty setup produces zeros rather than a misleading target`() {
        val status = EmergencyFundCalculator.calculate(snapshot())
        assertEquals(rupees(0), status.monthlyEssentialExpenses)
        assertEquals(rupees(0), status.targetAmount)
        assertEquals(0, status.progressPercent)
    }
}

class AffordabilityCalculatorTest {

    private fun snapshot(opening: Long) = FinancialSnapshot(
        today = date("2026-08-18"),
        accounts = listOf(account(opening = opening)),
        categories = listOf(category(1, "Grocery", essential = true))
    )

    @Test
    fun `a comfortable purchase is reported as safe`() {
        val snap = snapshot(200_000).copy(
            incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 1))
        )
        val result = AffordabilityCalculator.check(snap, rupees(25_000))

        assertEquals(AffordabilityVerdict.SAFE, result.verdict)
        assertEquals(rupees(175_000), result.remainingAfterPurchase)
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test
    fun `spending more than is available is not recommended`() {
        val result = AffordabilityCalculator.check(snapshot(20_000), rupees(25_000))

        assertEquals(AffordabilityVerdict.NOT_RECOMMENDED, result.verdict)
        assertTrue(result.reasons.any { it.contains("more than the money you have") })
    }

    @Test
    fun `a purchase that breaks an upcoming commitment is not recommended`() {
        val snap = snapshot(60_000).copy(
            bills = listOf(bill(amount = 20_000, dueDay = 5, essential = true))
        )
        val result = AffordabilityCalculator.check(snap, rupees(50_000))

        assertEquals(AffordabilityVerdict.NOT_RECOMMENDED, result.verdict)
        assertTrue(result.lowestProjectedBalance.isNegative)
    }

    @Test
    fun `the brief's worked example warns about upcoming pressure`() {
        // 60,000 available, 42,000 of commitments over the horizon, 25,000 purchase.
        val snap = snapshot(60_000).copy(
            emis = listOf(
                emi(id = 1, emiAmount = 14_000, firstDueDate = "2026-08-20", totalInstallments = 3)
            )
        )
        val result = AffordabilityCalculator.check(snap, rupees(25_000), horizonMonths = 3)

        assertEquals(rupees(60_000), result.availableNow)
        assertEquals(rupees(42_000), result.upcomingCommitments)
        assertEquals(rupees(35_000), result.remainingAfterPurchase)
        assertTrue(
            "35,000 left against 42,000 of commitments cannot be called safe",
            result.verdict != AffordabilityVerdict.SAFE
        )
    }

    @Test
    fun `dipping into the emergency fund is flagged even when the money is there`() {
        val snap = snapshot(100_000).copy(
            goals = listOf(goal(id = 1, name = "Emergency fund", target = 100_000, isEmergencyFund = true)),
            contributions = listOf(contribution(goalId = 1, amount = 80_000)),
            incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 1))
        )
        val result = AffordabilityCalculator.check(snap, rupees(30_000))

        assertEquals(AffordabilityVerdict.BE_CAREFUL, result.verdict)
        assertTrue(result.reasons.any { it.contains("emergency fund") })
    }

    @Test
    fun `the explanation only uses figures the user can verify elsewhere`() {
        val snap = snapshot(100_000).copy(
            incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 1)),
            bills = listOf(bill(amount = 13_000, dueDay = 5))
        )
        val result = AffordabilityCalculator.check(snap, rupees(20_000), horizonMonths = 3)

        assertEquals(rupees(100_000), result.availableNow)
        assertEquals(rupees(165_000), result.expectedIncoming)
        assertEquals(rupees(39_000), result.upcomingCommitments)
        assertEquals(3, result.horizonMonths)
    }

    @Test
    fun `a suggestion to wait is only offered when there is a surplus to build on`() {
        val noSurplus = snapshot(30_000).copy(
            bills = listOf(bill(amount = 10_000, dueDay = 5, essential = true))
        )
        assertNull(AffordabilityCalculator.check(noSurplus, rupees(25_000)).monthsToWaitForComfort)
    }
}
