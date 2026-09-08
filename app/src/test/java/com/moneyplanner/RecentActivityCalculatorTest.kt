package com.moneyplanner

import com.moneyplanner.domain.calc.ActivityDirection
import com.moneyplanner.domain.calc.ActivityKind
import com.moneyplanner.domain.calc.RecentActivityCalculator
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.ExpenseLinkType
import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.model.SettlementDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recent activity feed.
 *
 * Two properties matter more than the rest. The newest thing is first, because the whole
 * point of the list is to reach what was just entered. And an obligation payment carries
 * enough of its link to be undone properly, because deleting one as a plain expense would
 * leave the payment record behind and the forecast counting the money twice.
 */
class RecentActivityCalculatorTest {

    private fun snapshot() = FinancialSnapshot(
        today = date("2026-08-18"),
        accounts = listOf(
            account(id = 1).copy(name = "Bank account"),
            account(id = 2).copy(name = "Cash", type = AccountType.CASH)
        ),
        categories = listOf(category(id = 1, name = "Food")),
        people = listOf(person(id = 1, name = "Amit")),
        goals = listOf(goal(id = 1, name = "New phone"))
    )

    @Test
    fun `the newest movement comes first whatever kind it is`() {
        val snap = snapshot().copy(
            expenses = listOf(expense(id = 1, amount = 400, date = "2026-08-10")),
            incomeTransactions = listOf(
                incomeTransaction(id = 1, amount = 55_000, date = "2026-08-16")
            ),
            settlements = listOf(
                settlement(id = 1, amount = 1_000, date = "2026-08-12")
            )
        )
        val items = RecentActivityCalculator.recent(snap)

        assertEquals(3, items.size)
        assertEquals(ActivityKind.INCOME, items[0].kind)
        assertEquals(ActivityKind.SETTLEMENT, items[1].kind)
        assertEquals(ActivityKind.EXPENSE, items[2].kind)
    }

    @Test
    fun `only the requested number of movements is returned`() {
        val snap = snapshot().copy(
            expenses = (1..25).map { n ->
                expense(id = n.toLong(), amount = 100L * n, date = "2026-08-%02d".format(n % 28 + 1))
            }
        )
        assertEquals(10, RecentActivityCalculator.recent(snap).size)
        assertEquals(3, RecentActivityCalculator.recent(snap, limit = 3).size)
    }

    @Test
    fun `records sharing a date keep a stable order between reads`() {
        val snap = snapshot().copy(
            expenses = listOf(
                expense(id = 1, amount = 100, date = "2026-08-15"),
                expense(id = 2, amount = 200, date = "2026-08-15"),
                expense(id = 3, amount = 300, date = "2026-08-15")
            )
        )
        val first = RecentActivityCalculator.recent(snap).map { it.key }
        val second = RecentActivityCalculator.recent(snap).map { it.key }

        assertEquals(first, second)
        // Newest id first, so the one just entered is at the top.
        assertEquals(listOf("EXPENSE-3", "EXPENSE-2", "EXPENSE-1"), first)
    }

    @Test
    fun `a bill payment carries the link needed to undo it properly`() {
        val snap = snapshot().copy(
            expenses = listOf(
                expense(
                    id = 1,
                    amount = 13_000,
                    date = "2026-08-05",
                    linkType = ExpenseLinkType.RECURRING_BILL,
                    linkId = 7
                ).copy(linkPeriodKey = "2026-08")
            )
        )
        val item = RecentActivityCalculator.recent(snap).single()

        assertTrue(item.isObligationPayment)
        assertEquals(ExpenseLinkType.RECURRING_BILL, item.linkType)
        assertEquals(7L, item.linkId)
        assertEquals("2026-08", item.linkPeriodKey)
    }

    @Test
    fun `a card purchase is shown as not having moved cash yet`() {
        val snap = snapshot().copy(
            expenses = listOf(
                expense(
                    id = 1,
                    amount = 2_000,
                    date = "2026-08-14",
                    method = PaymentMethod.CREDIT_CARD
                )
            )
        )
        val item = RecentActivityCalculator.recent(snap).single()

        assertFalse(item.movesCashNow)
        assertEquals(ActivityDirection.OUT, item.direction)
    }

    @Test
    fun `a transfer is neither money in nor money out`() {
        val snap = snapshot().copy(
            transfers = listOf(
                AccountTransfer(
                    id = 1,
                    fromAccountId = 1,
                    toAccountId = 2,
                    amount = rupees(5_000),
                    date = date("2026-08-14"),
                    notes = ""
                )
            )
        )
        val item = RecentActivityCalculator.recent(snap).single()

        assertEquals(ActivityKind.TRANSFER, item.kind)
        assertEquals(ActivityDirection.NEUTRAL, item.direction)
        assertEquals("Bank account → Cash", item.title)
    }

    @Test
    fun `money put into a goal is set aside rather than spent`() {
        val snap = snapshot().copy(
            contributions = listOf(contribution(id = 1, amount = 5_000, date = "2026-08-14"))
        )
        val item = RecentActivityCalculator.recent(snap).single()

        assertEquals(ActivityDirection.NEUTRAL, item.direction)
        assertEquals("Put towards New phone", item.title)
    }

    @Test
    fun `taking money back out of a goal reads the other way round`() {
        val snap = snapshot().copy(
            contributions = listOf(contribution(id = 1, amount = -2_000, date = "2026-08-14"))
        )
        val item = RecentActivityCalculator.recent(snap).single()

        assertEquals("Taken from New phone", item.title)
        // Shown as a positive figure; the direction of travel is in the wording.
        assertEquals(rupees(2_000), item.amount)
    }

    @Test
    fun `a settlement names the person and which way the money went`() {
        val snap = snapshot().copy(
            settlements = listOf(
                settlement(
                    id = 1,
                    amount = 1_000,
                    direction = SettlementDirection.PAID_TO_THEM,
                    date = "2026-08-14"
                )
            )
        )
        val item = RecentActivityCalculator.recent(snap).single()

        assertEquals("Paid Amit", item.title)
        assertEquals(ActivityDirection.OUT, item.direction)
    }

    @Test
    fun `expenses and receipts open an editor, other records do not`() {
        val snap = snapshot().copy(
            expenses = listOf(expense(id = 1, amount = 400, date = "2026-08-14")),
            incomeTransactions = listOf(
                incomeTransaction(id = 1, amount = 55_000, date = "2026-08-14")
            ),
            settlements = listOf(settlement(id = 1, amount = 1_000, date = "2026-08-14")),
            contributions = listOf(contribution(id = 1, amount = 500, date = "2026-08-14"))
        )
        val items = RecentActivityCalculator.recent(snap)

        assertTrue(items.first { it.kind == ActivityKind.EXPENSE }.isEditable)
        assertTrue(items.first { it.kind == ActivityKind.INCOME }.isEditable)
        assertTrue(items.first { it.kind == ActivityKind.SETTLEMENT }.isEditable)
        assertFalse(items.first { it.kind == ActivityKind.SAVING }.isEditable)
    }

    @Test
    fun `a settlement carries the person whose screen holds its editor`() {
        val snap = snapshot().copy(
            settlements = listOf(
                settlement(id = 4, personId = 1, amount = 1_000, date = "2026-08-14")
            )
        )
        val item = RecentActivityCalculator.recent(snap).single()

        assertEquals(1L, item.parentId)
        assertEquals(4L, item.recordId)
    }

    @Test
    fun `keys are unique even when ids repeat across record types`() {
        val snap = snapshot().copy(
            expenses = listOf(expense(id = 1, amount = 400, date = "2026-08-14")),
            incomeTransactions = listOf(
                incomeTransaction(id = 1, amount = 55_000, date = "2026-08-14")
            ),
            settlements = listOf(settlement(id = 1, amount = 1_000, date = "2026-08-14")),
            contributions = listOf(contribution(id = 1, amount = 500, date = "2026-08-14"))
        )
        val keys = RecentActivityCalculator.recent(snap).map { it.key }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `an entry dated in the future is still listed so it can be corrected`() {
        val snap = snapshot().copy(
            expenses = listOf(expense(id = 1, amount = 400, date = "2026-09-10"))
        )
        assertEquals(1, RecentActivityCalculator.recent(snap).size)
    }

    @Test
    fun `nothing recorded means nothing listed`() {
        assertTrue(RecentActivityCalculator.recent(snapshot()).isEmpty())
    }
}
