package com.moneyplanner.domain.nlp

import com.moneyplanner.core.money.Money
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.PaymentMethod
import java.time.LocalDate

/**
 * Turns a spoken sentence into a draft expense.
 *
 * This is deliberately a rule-based parser rather than anything learned. It runs entirely
 * on the device, it is fast, and — most importantly for money — it is deterministic: the
 * same sentence always produces the same draft, and every rule it applies can be read and
 * tested.
 *
 * The result is always a *draft*. Speech recognition mishears numbers, and a mis-heard
 * amount silently saved is a corrupted financial record. So the parser fills the form and
 * the user confirms; nothing is ever written from a voice input alone.
 */
object SpokenExpenseParser {

    /** Words that carry no meaning for us and only get in the way of the description. */
    private val FILLER = setOf(
        "spent", "spend", "paid", "pay", "add", "added", "for", "on", "of", "the", "a",
        "an", "rs", "rupees", "rupee", "inr", "to", "at", "in", "i", "my", "me", "was",
        "were", "is", "it", "this", "that", "please", "expense", "record", "note", "put",
        "and", "with", "by", "some", "just", "today", "new"
    )

    /** Spoken multipliers, which people use constantly for larger amounts. */
    private val MULTIPLIERS = mapOf(
        "hundred" to 100L,
        "thousand" to 1_000L,
        "k" to 1_000L,
        "lakh" to 100_000L,
        "lakhs" to 100_000L,
        "lac" to 100_000L,
        "crore" to 10_000_000L,
        "crores" to 10_000_000L
    )

    private val PAYMENT_HINTS = listOf(
        setOf("upi", "gpay", "googlepay", "phonepe", "paytm", "bhim") to PaymentMethod.UPI,
        setOf("cash") to PaymentMethod.CASH,
        setOf("creditcard", "credit") to PaymentMethod.CREDIT_CARD,
        setOf("debitcard", "debit") to PaymentMethod.DEBIT_CARD,
        setOf("netbanking", "transfer", "neft", "imps", "bank") to PaymentMethod.BANK_TRANSFER,
        setOf("cheque", "check") to PaymentMethod.CHEQUE,
        setOf("wallet") to PaymentMethod.WALLET,
        setOf("autodebit", "auto") to PaymentMethod.AUTO_DEBIT
    )

    /**
     * Everyday words that point at a category without naming it. Matching is only ever a
     * suggestion: the category chip stays editable in the form.
     */
    private val CATEGORY_HINTS = mapOf(
        "Food" to setOf(
            "food", "lunch", "dinner", "breakfast", "snack", "snacks", "restaurant",
            "hotel", "tea", "coffee", "chai", "tiffin", "canteen", "swiggy", "zomato",
            "pizza", "meal", "eating", "ate"
        ),
        "Grocery" to setOf(
            "grocery", "groceries", "vegetables", "vegetable", "sabzi", "kirana",
            "supermarket", "milk", "provisions", "bigbasket", "blinkit"
        ),
        "Travel" to setOf(
            "travel", "trip", "bus", "train", "flight", "taxi", "cab", "uber", "ola",
            "auto rickshaw", "rickshaw", "ticket", "metro"
        ),
        "Fuel" to setOf("fuel", "petrol", "diesel", "cng", "gas", "pump", "filling"),
        "Shopping" to setOf(
            "shopping", "clothes", "shirt", "shoes", "dress", "amazon", "flipkart",
            "myntra", "mall"
        ),
        "Rent" to setOf("rent", "landlord"),
        "Utilities" to setOf(
            "electricity", "water", "internet", "wifi", "broadband", "mobile", "recharge",
            "dth", "cylinder", "utility", "utilities", "bill"
        ),
        "Education" to setOf(
            "school", "college", "tuition", "fees", "fee", "books", "stationery",
            "education", "class", "coaching"
        ),
        "Medical" to setOf(
            "medical", "medicine", "medicines", "doctor", "hospital", "clinic", "pharmacy",
            "chemist", "test", "checkup"
        ),
        "Entertainment" to setOf(
            "movie", "cinema", "netflix", "prime", "hotstar", "ott", "game", "outing",
            "entertainment", "party"
        ),
        "Family" to setOf("family", "kids", "children", "parents", "wife", "husband"),
        "Insurance" to setOf("insurance", "premium", "policy"),
        "Investment" to setOf("investment", "sip", "mutual", "stocks", "shares", "invest"),
        "Vehicle" to setOf("service", "servicing", "puncture", "repair", "parking", "fastag"),
        "Gifts" to setOf("gift", "gifts", "shagun", "present")
    )

    /**
     * Parses [spoken] against the user's own [categories].
     *
     * @param today used to resolve relative dates, passed in so the result is testable.
     */
    fun parse(
        spoken: String,
        categories: List<Category>,
        today: LocalDate
    ): ParsedExpense {
        val cleaned = spoken.lowercase()
            .replace("₹", " ")
            .replace(",", "")
            .replace(Regex("[^a-z0-9. ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isEmpty()) return ParsedExpense(originalText = spoken)

        val words = cleaned.split(" ")
        val amountResult = extractAmount(words)
        val paymentMethod = extractPaymentMethod(words)
        val date = extractDate(words, today)
        val categoryMatch = matchCategory(words, categories)

        val description = buildDescription(
            words = words,
            consumedIndices = amountResult.consumedIndices + categoryMatch.consumedIndices
        )

        return ParsedExpense(
            originalText = spoken,
            amount = amountResult.amount,
            categoryId = categoryMatch.categoryId,
            categoryName = categoryMatch.categoryName,
            description = description,
            paymentMethod = paymentMethod,
            date = date
        )
    }

    // ---- Amount ------------------------------------------------------------------

    private data class AmountResult(
        val amount: Money?,
        val consumedIndices: Set<Int>
    )

    /**
     * Finds the amount, handling both "1500" and "fifteen hundred" style multipliers.
     *
     * The first number wins. People lead with the amount far more often than not, and
     * guessing between several numbers in one sentence would be worse than taking the
     * obvious one and letting the user correct it.
     */
    private fun extractAmount(words: List<String>): AmountResult {
        for (index in words.indices) {
            val value = words[index].toDoubleOrNull() ?: continue

            // Look ahead for a multiplier, so "2 thousand" reads as 2000.
            val next = words.getOrNull(index + 1)
            val multiplier = next?.let { MULTIPLIERS[it] }
            if (multiplier != null) {
                val total = value * multiplier
                return AmountResult(
                    amount = Money(Math.round(total * 100)),
                    consumedIndices = setOf(index, index + 1)
                )
            }

            return AmountResult(
                amount = Money(Math.round(value * 100)),
                consumedIndices = setOf(index)
            )
        }
        return AmountResult(null, emptySet())
    }

    // ---- Category ----------------------------------------------------------------

    private data class CategoryMatch(
        val categoryId: Long?,
        val categoryName: String?,
        val consumedIndices: Set<Int>
    )

    /**
     * Matches a category by its own name first, then by everyday synonyms.
     *
     * Matching on the user's real category list means someone who renamed "Food" to
     * "Khana" still gets a hit, and a category the user invented is preferred over a
     * built-in synonym.
     */
    private fun matchCategory(words: List<String>, categories: List<Category>): CategoryMatch {
        // A direct hit on the category's own name is the strongest signal.
        for (category in categories) {
            val name = category.name.lowercase()
            val index = words.indexOf(name)
            if (index >= 0) {
                return CategoryMatch(category.id, category.name, setOf(index))
            }
        }

        // Otherwise fall back to everyday words that imply a category.
        for (index in words.indices) {
            val word = words[index]
            val hintedName = CATEGORY_HINTS.entries
                .firstOrNull { (_, synonyms) -> word in synonyms }
                ?.key
                ?: continue

            val category = categories.firstOrNull { it.name.equals(hintedName, true) }
            if (category != null) {
                // The trigger word is kept in the description when it is a real thing that
                // was bought ("dinner") rather than the category label itself.
                val consumed = if (word == category.name.lowercase()) setOf(index) else emptySet()
                return CategoryMatch(category.id, category.name, consumed)
            }
        }

        return CategoryMatch(null, null, emptySet())
    }

    // ---- Payment method and date --------------------------------------------------

    private fun extractPaymentMethod(words: List<String>): PaymentMethod? {
        for ((hints, method) in PAYMENT_HINTS) {
            if (words.any { it in hints }) return method
        }
        return null
    }

    private fun extractDate(words: List<String>, today: LocalDate): LocalDate = when {
        words.contains("yesterday") -> today.minusDays(1)
        words.contains("tomorrow") -> today.plusDays(1)
        else -> today
    }

    // ---- Description --------------------------------------------------------------

    /**
     * Whatever is left once the amount, category label and filler words are removed.
     * That remainder is almost always the thing the money was actually spent on.
     */
    private fun buildDescription(words: List<String>, consumedIndices: Set<Int>): String {
        val kept = words.filterIndexed { index, word ->
            index !in consumedIndices &&
                word !in FILLER &&
                word !in MULTIPLIERS &&
                word.toDoubleOrNull() == null &&
                PAYMENT_HINTS.none { (hints, _) -> word in hints } &&
                word !in setOf("yesterday", "tomorrow")
        }
        return kept.joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }.trim()
    }
}

/**
 * A draft expense produced from speech, always shown for confirmation before saving.
 */
data class ParsedExpense(
    val originalText: String,
    val amount: Money? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val description: String = "",
    val paymentMethod: PaymentMethod? = null,
    val date: LocalDate? = null
) {
    /** True when there is at least an amount, which is the one thing an expense must have. */
    val isUsable: Boolean get() = amount != null && amount.isPositive

    /** A short summary of what was understood, shown back to the user before they save. */
    fun understoodSummary(): String {
        if (!isUsable) return "No amount was recognised."
        return buildString {
            append(com.moneyplanner.core.money.IndianFormat.format(amount!!))
            if (categoryName != null) append(" · $categoryName")
            if (description.isNotBlank()) append(" · $description")
            if (paymentMethod != null) append(" · ${paymentMethod.label}")
        }
    }
}
