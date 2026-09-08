package com.moneyplanner.core.money

import kotlin.math.abs

/**
 * A rupee amount held as an exact number of paise.
 *
 * Money is never represented as a floating point number anywhere in this app: every
 * amount entered by the user, stored in the database, and produced by a calculator is
 * an exact integer count of paise. That keeps every financial calculation deterministic
 * and reproducible, which is the single most important property of this application.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

    val isZero: Boolean get() = paise == 0L
    val isPositive: Boolean get() = paise > 0L
    val isNegative: Boolean get() = paise < 0L

    /** The whole-rupee part, rounded towards zero. */
    val wholeRupees: Long get() = paise / 100

    operator fun plus(other: Money) = Money(paise + other.paise)
    operator fun minus(other: Money) = Money(paise - other.paise)
    operator fun times(factor: Int) = Money(paise * factor)
    operator fun times(factor: Long) = Money(paise * factor)
    operator fun unaryMinus() = Money(-paise)

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    fun abs(): Money = Money(abs(paise))

    /** Clamps a negative amount to zero. Used wherever a shortfall is not meaningful. */
    fun coerceAtLeastZero(): Money = if (paise < 0) ZERO else this

    /**
     * Applies a percentage, rounding half-up to the nearest paisa.
     * Used for percentage splits and salary increments.
     */
    fun percent(percentage: Double): Money {
        val exact = paise * percentage / 100.0
        return Money(Math.round(exact))
    }

    /**
     * Divides into [parts] amounts that always sum back to exactly this amount.
     *
     * The remainder in paise is handed out one paisa at a time to the first shares, so
     * splitting 100.00 three ways gives 33.34 / 33.33 / 33.33 and never loses a paisa.
     */
    fun splitEvenly(parts: Int): List<Money> {
        require(parts > 0) { "Cannot split into $parts parts" }
        val base = paise / parts
        val remainder = (paise % parts).toInt()
        val sign = if (remainder < 0) -1 else 1
        val extras = abs(remainder)
        return List(parts) { index ->
            Money(base + if (index < extras) sign.toLong() else 0L)
        }
    }

    /**
     * Divides by [divisor], rounding half-up. Used for "monthly reserve" style figures
     * where the parts are not required to sum back exactly.
     */
    fun divideRounded(divisor: Int): Money {
        require(divisor != 0) { "Cannot divide money by zero" }
        return Money(Math.round(paise.toDouble() / divisor))
    }

    companion object {
        val ZERO = Money(0L)

        fun ofRupees(rupees: Long): Money = Money(rupees * 100)

        /**
         * Parses user input such as "1200", "1,200.50" or "₹1200.5".
         * Returns null when the text is not a valid amount, so callers can show
         * a validation message rather than silently recording a wrong number.
         */
        fun parseOrNull(input: String): Money? {
            val cleaned = input.trim()
                .removePrefix("₹")
                .replace(",", "")
                .replace(" ", "")
                .trim()
            if (cleaned.isEmpty()) return null

            val negative = cleaned.startsWith("-")
            val unsigned = cleaned.removePrefix("-").removePrefix("+")
            if (unsigned.isEmpty()) return null
            if (unsigned.count { it == '.' } > 1) return null
            if (!unsigned.all { it.isDigit() || it == '.' }) return null

            val parts = unsigned.split(".")
            val rupeePart = parts[0].ifEmpty { "0" }
            val paisePart = parts.getOrNull(1).orEmpty()
            if (paisePart.length > 2) return null

            val rupees = rupeePart.toLongOrNull() ?: return null
            val paise = when (paisePart.length) {
                0 -> 0L
                1 -> (paisePart.toLongOrNull() ?: return null) * 10
                else -> paisePart.toLongOrNull() ?: return null
            }
            val total = rupees * 100 + paise
            return Money(if (negative) -total else total)
        }
    }
}

fun Iterable<Money>.sumOfMoney(): Money = Money(sumOf { it.paise })

inline fun <T> Iterable<T>.sumOfMoney(selector: (T) -> Money): Money =
    Money(sumOf { selector(it).paise })
