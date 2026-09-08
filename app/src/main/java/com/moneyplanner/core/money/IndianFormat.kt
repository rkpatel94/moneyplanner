package com.moneyplanner.core.money

import kotlin.math.abs

/**
 * Indian-style number and currency formatting.
 *
 * Grouping follows the Indian system: the last three digits are grouped together and
 * every group before that is a pair, so 1234567 renders as 12,34,567 rather than
 * 1,234,567. This is implemented directly instead of relying on a platform locale so
 * that the output is identical on every device and can be unit tested.
 */
object IndianFormat {

    private const val RUPEE = "₹"

    /** Groups the digits of a non-negative whole number, Indian style. */
    fun groupDigits(value: Long): String {
        val digits = abs(value).toString()
        if (digits.length <= 3) return digits

        val lastThree = digits.substring(digits.length - 3)
        val rest = digits.substring(0, digits.length - 3)

        val grouped = StringBuilder()
        var index = rest.length
        while (index > 2) {
            grouped.insert(0, "," + rest.substring(index - 2, index))
            index -= 2
        }
        if (index > 0) grouped.insert(0, rest.substring(0, index))

        return "$grouped,$lastThree"
    }

    /**
     * Formats an amount for display, for example "₹1,00,000" or "-₹2,500.50".
     * Paise are hidden when the amount is a whole number of rupees, which is what a
     * person expects to see for the vast majority of everyday entries.
     */
    fun format(money: Money, withSymbol: Boolean = true, forceDecimals: Boolean = false): String {
        val negative = money.paise < 0
        val absPaise = abs(money.paise)
        val rupees = absPaise / 100
        val paise = absPaise % 100

        val body = buildString {
            append(groupDigits(rupees))
            if (paise != 0L || forceDecimals) {
                append('.')
                append(paise.toString().padStart(2, '0'))
            }
        }

        return buildString {
            if (negative) append('-')
            if (withSymbol) append(RUPEE)
            append(body)
        }
    }

    /** Formats with an explicit sign, used for money-in and money-out rows. */
    fun formatSigned(money: Money): String =
        if (money.isNegative) format(money) else "+" + format(money)

    /**
     * A compact form for dashboard tiles where space is tight:
     * 1250 becomes "₹1.25K", 125000 becomes "₹1.25L", 12500000 becomes "₹1.25Cr".
     */
    fun formatCompact(money: Money): String {
        val negative = money.paise < 0
        val rupees = abs(money.paise) / 100
        val (value, suffix) = when {
            rupees >= 10_000_000L -> rupees / 10_000_000.0 to "Cr"
            rupees >= 100_000L -> rupees / 100_000.0 to "L"
            rupees >= 1_000L -> rupees / 1_000.0 to "K"
            else -> return format(money)
        }
        val rounded = Math.round(value * 100) / 100.0
        val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
        return buildString {
            if (negative) append('-')
            append(RUPEE)
            append(text)
            append(suffix)
        }
    }

    /** Plain digits with no symbol, used when exporting to CSV. */
    fun formatForExport(money: Money): String {
        val negative = money.paise < 0
        val absPaise = abs(money.paise)
        return buildString {
            if (negative) append('-')
            append(absPaise / 100)
            append('.')
            append((absPaise % 100).toString().padStart(2, '0'))
        }
    }
}
