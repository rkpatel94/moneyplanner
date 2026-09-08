package com.moneyplanner

import com.moneyplanner.domain.model.CategoryType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.nlp.SpokenExpenseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Voice entry is only safe if the parse is predictable, so every rule it applies is
 * pinned here. A mis-heard amount that silently became a record would be worse than no
 * voice entry at all.
 */
class SpokenExpenseParserTest {

    private val today = date("2026-08-18")

    private val categories = listOf(
        category(1, "Food"),
        category(2, "Grocery", essential = true),
        category(3, "Fuel", essential = true),
        category(4, "Travel"),
        category(5, "Medical", essential = true),
        category(6, "Salary", type = CategoryType.INCOME)
    )

    private fun parse(text: String) = SpokenExpenseParser.parse(text, categories, today)

    @Test
    fun `reads the plainest possible sentence`() {
        val result = parse("1500 dinner")
        assertEquals(rupees(1_500), result.amount)
        assertEquals("Dinner", result.description)
        assertEquals("Food", result.categoryName)
        assertTrue(result.isUsable)
    }

    @Test
    fun `reads a natural sentence with filler words`() {
        val result = parse("I spent 250 on fuel today")
        assertEquals(rupees(250), result.amount)
        assertEquals("Fuel", result.categoryName)
        assertEquals(
            "the category label is not repeated back as the description",
            "",
            result.description
        )
    }

    @Test
    fun `keeps what was bought as the description`() {
        val result = parse("paid 1200 for medicines")
        assertEquals(rupees(1_200), result.amount)
        assertEquals("Medical", result.categoryName)
        assertEquals("Medicines", result.description)
    }

    @Test
    fun `handles spoken multipliers`() {
        assertEquals(rupees(2_000), parse("2 thousand rent").amount)
        assertEquals(rupees(1_500), parse("1.5 thousand shopping").amount)
        assertEquals(rupees(100_000), parse("1 lakh insurance").amount)
        assertEquals(rupees(500), parse("5 hundred snacks").amount)
    }

    @Test
    fun `handles amounts spoken with commas and a rupee symbol`() {
        assertEquals(rupees(12_500), parse("₹12,500 laptop repair").amount)
        assertEquals(rupees(1_200), parse("rs 1200 groceries").amount)
    }

    @Test
    fun `keeps paise when they are spoken`() {
        assertEquals(com.moneyplanner.core.money.Money(125_050), parse("1250.50 dinner").amount)
    }

    @Test
    fun `picks up the payment method when it is mentioned`() {
        assertEquals(PaymentMethod.UPI, parse("500 lunch by upi").paymentMethod)
        assertEquals(PaymentMethod.CASH, parse("200 tea cash").paymentMethod)
        assertEquals(PaymentMethod.CREDIT_CARD, parse("3000 shopping credit card").paymentMethod)
        assertEquals(PaymentMethod.UPI, parse("450 groceries phonepe").paymentMethod)
    }

    @Test
    fun `leaves the payment method unset when nothing is said about it`() {
        assertNull(parse("500 lunch").paymentMethod)
    }

    @Test
    fun `understands yesterday`() {
        assertEquals(date("2026-08-17"), parse("300 auto yesterday").date)
        assertEquals(today, parse("300 auto").date)
    }

    @Test
    fun `matches a category the user renamed`() {
        val custom = listOf(category(10, "Khana"))
        val result = SpokenExpenseParser.parse("400 khana", custom, today)
        assertEquals("Khana", result.categoryName)
        assertEquals(10L, result.categoryId)
    }

    @Test
    fun `does not invent a category that the user does not have`() {
        val onlyFood = listOf(category(1, "Food"))
        val result = SpokenExpenseParser.parse("900 petrol", onlyFood, today)
        assertNull(
            "fuel is not one of this user's categories, so nothing should be guessed",
            result.categoryName
        )
        assertEquals(rupees(900), result.amount)
        assertEquals("Petrol", result.description)
    }

    @Test
    fun `takes the first number when several are spoken`() {
        val result = parse("500 for 2 movie tickets")
        assertEquals(rupees(500), result.amount)
    }

    @Test
    fun `reports that nothing is usable when no amount was heard`() {
        val result = parse("add an expense for lunch")
        assertNull(result.amount)
        assertFalse(result.isUsable)
        assertEquals("No amount was recognised.", result.understoodSummary())
    }

    @Test
    fun `handles empty and noise input without failing`() {
        assertFalse(parse("").isUsable)
        assertFalse(parse("   ").isUsable)
        assertFalse(parse("umm err").isUsable)
    }

    @Test
    fun `summary reports back exactly what was understood`() {
        val result = parse("1500 dinner by upi")
        assertEquals("₹1,500 · Food · Dinner · UPI", result.understoodSummary())
    }

    @Test
    fun `keeps the original text so the user can see what was heard`() {
        val spoken = "Spent 250 on Fuel"
        assertEquals(spoken, parse(spoken).originalText)
    }

    @Test
    fun `is deterministic across repeated parses`() {
        val sentence = "paid 1750 for grocery by upi yesterday"
        val first = parse(sentence)
        repeat(5) {
            val again = parse(sentence)
            assertEquals(first.amount, again.amount)
            assertEquals(first.categoryName, again.categoryName)
            assertEquals(first.description, again.description)
            assertEquals(first.paymentMethod, again.paymentMethod)
            assertEquals(first.date, again.date)
        }
    }
}
