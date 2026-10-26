package com.moneyplanner

import com.moneyplanner.core.money.IndianNumberWords
import com.moneyplanner.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Amounts spelled the way an Indian cheque spells them.
 *
 * The words sit under the amount being typed so an extra zero is caught before it is
 * saved, which only works if the words are exactly right, in lakh and crore.
 */
class IndianNumberWordsTest {

    private fun paise(value: Long) = Money(value)

    @Test
    fun `a typical expense reads in full`() {
        assertEquals(
            "Rupees One Thousand Two Hundred Fifty",
            IndianNumberWords.rupees(rupees(1_250))
        )
    }

    @Test
    fun `lakh and crore are used rather than million`() {
        assertEquals(
            "Rupees One Crore Twenty Three Lakh Forty Five Thousand Six Hundred Seventy Eight",
            IndianNumberWords.rupees(rupees(1_23_45_678))
        )
        assertEquals("Rupees One Lakh", IndianNumberWords.rupees(rupees(1_00_000)))
    }

    @Test
    fun `round tens and teens are spelled as single words`() {
        assertEquals("Rupees Twenty", IndianNumberWords.rupees(rupees(20)))
        assertEquals("Rupees Fourteen", IndianNumberWords.rupees(rupees(14)))
        assertEquals("Rupees Ninety Nine", IndianNumberWords.rupees(rupees(99)))
        assertEquals("Rupees Five Hundred", IndianNumberWords.rupees(rupees(500)))
    }

    @Test
    fun `crores past ninety nine are counted in the same system`() {
        assertEquals("Rupees One Hundred Crore", IndianNumberWords.rupees(rupees(100_00_00_000)))
    }

    @Test
    fun `paise are added after the rupees`() {
        assertEquals(
            "Rupees Twelve and Fifty Paise",
            IndianNumberWords.rupees(paise(1_250))
        )
        assertEquals("Rupees Zero and Five Paise", IndianNumberWords.rupees(paise(5)))
    }

    @Test
    fun `nothing reads as zero`() {
        assertEquals("Rupees Zero", IndianNumberWords.rupees(Money.ZERO))
    }

    @Test
    fun `large amounts are also sized in lakh or crore`() {
        assertEquals("1.25 lakh", IndianNumberWords.scale(rupees(1_25_000)))
        assertEquals("2.4 crore", IndianNumberWords.scale(rupees(2_40_00_000)))
        assertEquals("1 lakh", IndianNumberWords.scale(rupees(1_00_000)))
        // Rounded down, never up: 1,99,999 is not yet two lakh.
        assertEquals("1.99 lakh", IndianNumberWords.scale(rupees(1_99_999)))
    }

    @Test
    fun `amounts under a lakh need no scale`() {
        assertNull(IndianNumberWords.scale(rupees(99_999)))
    }
}
