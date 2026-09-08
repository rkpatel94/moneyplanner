package com.moneyplanner

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.domain.calc.SplitCalculator
import com.moneyplanner.domain.calc.SplitParticipant
import com.moneyplanner.domain.calc.SplitValidation
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.model.SplitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    private val me = SplitParticipant(personId = null)

    @Test
    fun `an equal split adds back to the total exactly`() {
        val result = SplitCalculator.split(
            totalAmount = rupees(1_000),
            splitType = SplitType.EQUAL,
            participants = listOf(me, SplitParticipant(1), SplitParticipant(2)),
            paidByPersonId = null
        )
        assertEquals(rupees(1_000), result.shares.sumOfMoney { it.amount })
        assertEquals(Money(33_334), result.myShare)
    }

    @Test
    fun `when the user pays everyone else owes their share`() {
        val result = SplitCalculator.split(
            totalAmount = rupees(900),
            splitType = SplitType.EQUAL,
            participants = listOf(me, SplitParticipant(1), SplitParticipant(2)),
            paidByPersonId = null
        )
        assertEquals(2, result.obligations.size)
        assertTrue(result.obligations.all { it.direction == LedgerDirection.THEY_OWE_ME })
        assertEquals(rupees(300), result.obligations.first().amount)
        assertEquals(
            "paying the bill means the whole amount left the user's pocket",
            rupees(900),
            result.amountPaidByUser
        )
    }

    @Test
    fun `when somebody else pays only the user's own share becomes a debt`() {
        val result = SplitCalculator.split(
            totalAmount = rupees(900),
            splitType = SplitType.EQUAL,
            participants = listOf(me, SplitParticipant(1), SplitParticipant(2)),
            paidByPersonId = 1
        )
        val obligation = result.obligations.single()
        assertEquals(1L, obligation.personId)
        assertEquals(LedgerDirection.I_OWE_THEM, obligation.direction)
        assertEquals(rupees(300), obligation.amount)
        assertEquals(
            "the user did not pay the bill, so no cash left their account",
            rupees(0),
            result.amountPaidByUser
        )
    }

    @Test
    fun `the user is never recorded as owing themselves`() {
        val result = SplitCalculator.split(
            totalAmount = rupees(600),
            splitType = SplitType.EQUAL,
            participants = listOf(me, SplitParticipant(1)),
            paidByPersonId = null
        )
        assertTrue(result.obligations.none { it.personId == 0L })
        assertEquals(1, result.obligations.size)
    }

    @Test
    fun `a percentage split distributes the rounding remainder`() {
        val result = SplitCalculator.split(
            totalAmount = rupees(1_000),
            splitType = SplitType.PERCENTAGE,
            participants = listOf(
                SplitParticipant(personId = null, percent = 33.33),
                SplitParticipant(personId = 1, percent = 33.33),
                SplitParticipant(personId = 2, percent = 33.34)
            ),
            paidByPersonId = null
        )
        assertEquals(
            "percentages that do not divide cleanly must still add up to the bill",
            rupees(1_000),
            result.shares.sumOfMoney { it.amount }
        )
    }

    @Test
    fun `an exact split uses the amounts entered`() {
        val result = SplitCalculator.split(
            totalAmount = rupees(1_000),
            splitType = SplitType.EXACT,
            participants = listOf(
                SplitParticipant(personId = null, exactAmount = rupees(400)),
                SplitParticipant(personId = 1, exactAmount = rupees(600))
            ),
            paidByPersonId = null
        )
        assertEquals(rupees(400), result.myShare)
        assertEquals(rupees(600), result.obligations.single().amount)
    }

    @Test
    fun `one person carrying the whole bill leaves the others with nothing to pay`() {
        val result = SplitCalculator.split(
            totalAmount = rupees(1_000),
            splitType = SplitType.FULL_ON_ONE,
            participants = listOf(
                me,
                SplitParticipant(personId = 1, bearsFullAmount = true),
                SplitParticipant(personId = 2)
            ),
            paidByPersonId = null
        )
        assertEquals(rupees(0), result.myShare)
        assertEquals(rupees(1_000), result.obligations.single().amount)
        assertEquals(1L, result.obligations.single().personId)
    }

    @Test
    fun `custom amounts are validated against the bill total`() {
        val short = listOf(
            SplitParticipant(personId = null, exactAmount = rupees(400)),
            SplitParticipant(personId = 1, exactAmount = rupees(500))
        )
        assertEquals(
            SplitValidation.Short(rupees(100)),
            SplitCalculator.validateExact(rupees(1_000), short)
        )

        val over = listOf(
            SplitParticipant(personId = null, exactAmount = rupees(700)),
            SplitParticipant(personId = 1, exactAmount = rupees(500))
        )
        assertEquals(
            SplitValidation.Over(rupees(200)),
            SplitCalculator.validateExact(rupees(1_000), over)
        )

        val exact = listOf(
            SplitParticipant(personId = null, exactAmount = rupees(400)),
            SplitParticipant(personId = 1, exactAmount = rupees(600))
        )
        assertEquals(
            SplitValidation.Valid,
            SplitCalculator.validateExact(rupees(1_000), exact)
        )
    }

    @Test
    fun `percentages are validated against one hundred`() {
        assertEquals(
            SplitValidation.Valid,
            SplitCalculator.validatePercentages(
                listOf(
                    SplitParticipant(personId = null, percent = 60.0),
                    SplitParticipant(personId = 1, percent = 40.0)
                )
            )
        )
        assertTrue(
            SplitCalculator.validatePercentages(
                listOf(SplitParticipant(personId = null, percent = 60.0))
            ) is SplitValidation.ShortPercent
        )
    }

    @Test
    fun `a split between many people still balances to the paisa`() {
        val participants = listOf(me) + (1L..6L).map { SplitParticipant(it) }
        val result = SplitCalculator.split(
            totalAmount = Money(999_999),
            splitType = SplitType.EQUAL,
            participants = participants,
            paidByPersonId = null
        )
        assertEquals(Money(999_999), result.shares.sumOfMoney { it.amount })
    }
}
