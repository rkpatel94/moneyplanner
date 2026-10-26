package com.moneyplanner

import com.moneyplanner.ui.screens.expenses.typedAmountAfter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The expense keypad writes the amount one key at a time, so it has to refuse the keys
 * that would make the figure unreadable as rupees, rather than leave it to the save.
 */
class AmountKeypadTest {

    private fun type(keys: String): String = keys.fold("") { text, key -> typedAmountAfter(text, key) }

    @Test
    fun `digits build the amount in order`() {
        assertEquals("1250", type("1250"))
    }

    @Test
    fun `a leading zero is replaced rather than kept`() {
        assertEquals("5", type("05"))
        assertEquals("0", type("00"))
    }

    @Test
    fun `a decimal point on its own starts at zero`() {
        assertEquals("0.", type("."))
        assertEquals("0.5", type(".5"))
    }

    @Test
    fun `only one decimal point is accepted`() {
        assertEquals("12.5", type("12..5"))
        assertEquals("12.50", type("12.5.0"))
    }

    @Test
    fun `paise stop at two places`() {
        assertEquals("99.99", type("99.999"))
    }

    @Test
    fun `rupees stop at nine digits`() {
        assertEquals("999999999", type("9999999999"))
        // Paise can still be added to the largest figure.
        assertEquals("999999999.75", type("9999999999.75"))
    }

    @Test
    fun `anything but a digit or a point is ignored`() {
        assertEquals("12", typedAmountAfter("12", '-'))
    }
}
