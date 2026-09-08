package com.moneyplanner

import com.moneyplanner.domain.model.FinancialSnapshot
import com.moneyplanner.domain.model.LedgerDirection
import com.moneyplanner.domain.nlp.AssistantAction
import com.moneyplanner.domain.nlp.MoneyAssistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assistant must never state a figure the data does not support, and must say plainly
 * when it has not understood rather than guessing. Both properties are pinned here.
 */
class MoneyAssistantTest {

    private fun snapshot() = FinancialSnapshot(
        today = date("2026-08-18"),
        accounts = listOf(account(opening = 50_000)),
        categories = listOf(category(1, "Food"), category(2, "Rent", essential = true)),
        people = listOf(person(1, "Amit"), person(2, "Rahul")),
        incomeSources = listOf(incomeSource(amount = 55_000, dayOfMonth = 1)),
        emis = listOf(emi(id = 1, emiAmount = 4_500, firstDueDate = "2026-01-05", totalInstallments = 36)),
        bills = listOf(bill(id = 1, name = "Rent", amount = 13_000, dueDay = 25)),
        ledgerEntries = listOf(
            ledgerEntry(id = 1, personId = 1, amount = 5_000),
            ledgerEntry(id = 2, personId = 2, amount = 2_000, direction = LedgerDirection.I_OWE_THEM)
        )
    )

    private fun ask(question: String) = MoneyAssistant.answer(question, snapshot())

    @Test
    fun `answers how much money is available`() {
        val answer = ask("how much money do I have")
        assertEquals("₹50,000", answer.headline)
        assertTrue(answer.isUnderstood)
        assertEquals(AssistantAction.OPEN_DASHBOARD, answer.action)
    }

    @Test
    fun `answers who owes me money and names them`() {
        val answer = ask("who owes me money")
        assertEquals("₹5,000", answer.headline)
        assertTrue(answer.detail.contains("Amit"))
        assertFalse(
            "someone the user owes must not appear as a debtor",
            answer.detail.contains("Rahul")
        )
    }

    @Test
    fun `answers whom I owe`() {
        val answer = ask("whom do I owe")
        assertEquals("₹2,000", answer.headline)
        assertTrue(answer.detail.contains("Rahul"))
    }

    @Test
    fun `answers the emi question with the monthly burden`() {
        val answer = ask("what are my emis")
        assertEquals("₹4,500 a month", answer.headline)
        assertEquals(AssistantAction.OPEN_PLANS, answer.action)
    }

    @Test
    fun `answers what is due this month`() {
        val answer = ask("what is due this month")
        assertTrue(answer.isUnderstood)
        assertEquals(AssistantAction.OPEN_CALENDAR, answer.action)
    }

    @Test
    fun `answers the next month forecast`() {
        val answer = ask("what will I have next month")
        assertTrue(answer.isUnderstood)
        assertEquals(AssistantAction.OPEN_FORECAST, answer.action)
    }

    @Test
    fun `answers an affordability question and reads the amount out of it`() {
        val answer = ask("can I afford 25000")
        assertTrue(answer.isUnderstood)
        assertEquals(AssistantAction.OPEN_AFFORDABILITY, answer.action)
        assertTrue(
            "the verdict should be one of the three the calculator produces",
            answer.headline in listOf("Safe", "Be careful", "Not recommended")
        )
    }

    @Test
    fun `asks for the amount when an affordability question has none`() {
        val answer = ask("can I afford it")
        assertEquals("How much?", answer.headline)
    }

    @Test
    fun `understands a spoken multiplier in an affordability question`() {
        val withMultiplier = ask("can I afford 1 lakh")
        val plain = ask("can I afford 100000")
        assertEquals(withMultiplier.headline, plain.headline)
    }

    @Test
    fun `says plainly when it has not understood`() {
        val answer = ask("what is the weather tomorrow")
        assertFalse(answer.isUnderstood)
        assertTrue(
            "an unknown question should offer what it can actually do",
            answer.detail.contains("How much do I have", ignoreCase = true)
        )
    }

    @Test
    fun `handles an empty question`() {
        assertFalse(MoneyAssistant.answer("", snapshot()).isUnderstood)
    }

    @Test
    fun `reports honestly when there is nothing to report`() {
        val empty = FinancialSnapshot(today = date("2026-08-18"))
        val answer = MoneyAssistant.answer("who owes me money", empty)
        assertEquals("Nobody", answer.headline)
        assertTrue(answer.isUnderstood)
    }

    @Test
    fun `is deterministic for the same question and data`() {
        val snap = snapshot()
        val first = MoneyAssistant.answer("how much can I safely spend", snap)
        repeat(5) {
            val again = MoneyAssistant.answer("how much can I safely spend", snap)
            assertEquals(first.headline, again.headline)
            assertEquals(first.detail, again.detail)
        }
    }

    @Test
    fun `every offered suggestion is actually understood`() {
        val snap = snapshot()
        MoneyAssistant.SUGGESTIONS.forEach { suggestion ->
            val answer = MoneyAssistant.answer(suggestion, snap)
            assertTrue(
                "the assistant offers \"$suggestion\" so it must be able to answer it",
                answer.isUnderstood
            )
        }
    }
}
