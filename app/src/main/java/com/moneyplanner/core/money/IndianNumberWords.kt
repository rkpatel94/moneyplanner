package com.moneyplanner.core.money

import kotlin.math.abs

/**
 * An amount spelled out the way it is written on an Indian cheque.
 *
 * Shown under the amount being typed, because the number alone hides the slip that matters
 * most: an extra zero. "₹12,500" and "₹1,25,000" look alike at a glance, while "Twelve
 * Thousand Five Hundred" and "One Lakh Twenty Five Thousand" do not. The grouping is the
 * Indian one, with lakh and crore rather than million.
 */
object IndianNumberWords {

    private val ones = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
        "Eighteen", "Nineteen"
    )

    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    /** "Rupees One Thousand Two Hundred Fifty", with "and Fifty Paise" when there are any. */
    fun rupees(money: Money): String {
        val paise = abs(money.paise)
        val whole = paise / 100
        val fraction = (paise % 100).toInt()
        val words = "Rupees " + if (whole == 0L) "Zero" else spell(whole)
        return if (fraction > 0) "$words and ${belowHundred(fraction)} Paise" else words
    }

    /**
     * The size of a large amount in lakh or crore, such as "1.25 lakh", or null below a lakh,
     * where the grouped figure already says it plainly.
     */
    fun scale(money: Money): String? {
        val whole = abs(money.paise) / 100
        return when {
            whole >= CRORE -> trimmed(whole.toDouble() / CRORE) + " crore"
            whole >= LAKH -> trimmed(whole.toDouble() / LAKH) + " lakh"
            else -> null
        }
    }

    /** A whole number of one or more, spelled in the Indian system. */
    fun spell(value: Long): String {
        require(value > 0) { "Only positive whole numbers are spelled" }
        val parts = mutableListOf<String>()
        var rest = value

        val crores = rest / CRORE
        rest %= CRORE
        val lakhs = rest / LAKH
        rest %= LAKH
        val thousands = rest / 1_000
        rest %= 1_000
        val hundreds = rest / 100
        rest %= 100

        // Crores can run past ninety-nine, and then they are counted in the same system:
        // a hundred crore is "One Hundred Crore", not a new unit.
        if (crores > 0) parts += spell(crores) + " Crore"
        if (lakhs > 0) parts += belowHundred(lakhs.toInt()) + " Lakh"
        if (thousands > 0) parts += belowHundred(thousands.toInt()) + " Thousand"
        if (hundreds > 0) parts += ones[hundreds.toInt()] + " Hundred"
        if (rest > 0) parts += belowHundred(rest.toInt())
        return parts.joinToString(" ")
    }

    private fun belowHundred(value: Int): String = when {
        value < 20 -> ones[value]
        value % 10 == 0 -> tens[value / 10]
        else -> tens[value / 10] + " " + ones[value % 10]
    }

    /** At most two decimals, with trailing zeros dropped: 1.25, 2.4, 3. */
    private fun trimmed(value: Double): String {
        val rounded = kotlin.math.floor(value * 100) / 100
        val text = String.format(java.util.Locale.ENGLISH, "%.2f", rounded)
        return text.trimEnd('0').trimEnd('.')
    }

    private const val LAKH = 1_00_000L
    private const val CRORE = 1_00_00_000L
}
