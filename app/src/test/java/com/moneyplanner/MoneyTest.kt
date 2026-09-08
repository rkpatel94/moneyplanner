package com.moneyplanner

import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.Money
import com.moneyplanner.core.money.sumOfMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses plain rupee amounts`() {
        assertEquals(Money(120_000), Money.parseOrNull("1200"))
        assertEquals(Money(120_050), Money.parseOrNull("1200.50"))
        assertEquals(Money(120_050), Money.parseOrNull("1,200.50"))
        assertEquals(Money(120_050), Money.parseOrNull("₹1,200.50"))
        assertEquals(Money(120_005), Money.parseOrNull("1200.05"))
    }

    @Test
    fun `treats a single decimal place as tens of paise`() {
        assertEquals(Money(120_050), Money.parseOrNull("1200.5"))
    }

    @Test
    fun `rejects input that is not a valid amount`() {
        assertNull(Money.parseOrNull(""))
        assertNull(Money.parseOrNull("abc"))
        assertNull(Money.parseOrNull("12.345"))
        assertNull(Money.parseOrNull("1.2.3"))
        assertNull(Money.parseOrNull("-"))
    }

    @Test
    fun `splitting never loses a paisa`() {
        val parts = Money(10_000).splitEvenly(3)
        assertEquals(3, parts.size)
        assertEquals(Money(10_000), parts.sumOfMoney())
        assertEquals(Money(3_334), parts[0])
        assertEquals(Money(3_333), parts[1])
        assertEquals(Money(3_333), parts[2])
    }

    @Test
    fun `splitting an awkward amount seven ways still adds back to the total`() {
        val total = Money(999_999)
        val parts = total.splitEvenly(7)
        assertEquals(total, parts.sumOfMoney())
    }

    @Test
    fun `percentage rounds to the nearest paisa`() {
        assertEquals(Money(3_333), Money(10_000).percent(33.33))
        assertEquals(Money(5_000), Money(10_000).percent(50.0))
    }

    @Test
    fun `negative balances are preserved rather than clamped`() {
        val result = Money(1_000) - Money(2_500)
        assertTrue(result.isNegative)
        assertEquals(Money(-1_500), result)
        assertEquals(Money.ZERO, result.coerceAtLeastZero())
    }

    @Test
    fun `indian grouping follows the lakh and crore pattern`() {
        assertEquals("1,000", IndianFormat.groupDigits(1_000))
        assertEquals("10,000", IndianFormat.groupDigits(10_000))
        assertEquals("1,00,000", IndianFormat.groupDigits(100_000))
        assertEquals("10,00,000", IndianFormat.groupDigits(1_000_000))
        assertEquals("1,00,00,000", IndianFormat.groupDigits(10_000_000))
        assertEquals("12,34,567", IndianFormat.groupDigits(1_234_567))
        assertEquals("999", IndianFormat.groupDigits(999))
    }

    @Test
    fun `currency formatting hides paise when the amount is whole`() {
        assertEquals("₹1,00,000", IndianFormat.format(Money.ofRupees(100_000)))
        assertEquals("₹2,500.50", IndianFormat.format(Money(250_050)))
        assertEquals("-₹2,500", IndianFormat.format(Money(-250_000)))
        assertEquals("₹1,000.00", IndianFormat.format(Money(100_000), forceDecimals = true))
    }

    @Test
    fun `compact formatting uses indian units`() {
        assertEquals("₹1.25K", IndianFormat.formatCompact(Money.ofRupees(1_250)))
        assertEquals("₹1.25L", IndianFormat.formatCompact(Money.ofRupees(125_000)))
        assertEquals("₹1.25Cr", IndianFormat.formatCompact(Money.ofRupees(12_500_000)))
        assertEquals("₹500", IndianFormat.formatCompact(Money.ofRupees(500)))
    }

    @Test
    fun `export formatting always carries two decimals and no symbol`() {
        assertEquals("1200.00", IndianFormat.formatForExport(Money.ofRupees(1_200)))
        assertEquals("1200.05", IndianFormat.formatForExport(Money(120_005)))
        assertEquals("-450.00", IndianFormat.formatForExport(Money(-45_000)))
    }
}
