package com.moneyplanner.domain.nlp

import com.moneyplanner.core.money.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads a bank alert SMS into a draft transaction.
 *
 * This is the single biggest reduction in manual entry the app can offer: nearly every
 * Indian bank texts an alert for every debit and credit, so the ledger can largely fill
 * itself. The formats vary between banks but are far more regular than speech, which makes
 * rule-based parsing both practical and — more importantly for money — predictable.
 *
 * Nothing is ever imported automatically. Every candidate is shown for review first,
 * because a mis-parsed amount written straight to the ledger is a corrupted record, and
 * because plenty of alerts (OTPs, balance enquiries, promotions) are not transactions at
 * all and must be discarded rather than guessed at.
 */
object BankSmsParser {

    /** Words that mark money leaving the account. */
    private val DEBIT_WORDS = listOf(
        "debited", "debit", "spent", "withdrawn", "paid", "purchase", "sent",
        "transferred to", "trf to", "deducted"
    )

    /** Words that mark money arriving. */
    private val CREDIT_WORDS = listOf(
        "credited", "credit", "received", "deposited", "refund", "cashback"
    )

    /**
     * Messages that mention money but are not transactions. Discarding these is what keeps
     * the review list short enough that a person will actually read it.
     */
    private val NOT_A_TRANSACTION = listOf(
        "otp", "one time password", "will be debited", "due on", "is due", "outstanding",
        "minimum amount due", "statement", "offer", "cashback offer", "eligible",
        "pre-approved", "loan offer", "apply now", "balance enquiry",
        "available balance is", "avl bal is", "reward points", "emi of", "kyc"
    )

    /**
     * Links, stripped out before a message is classified.
     *
     * A URL is not evidence of an advertisement. Most real debit alerts end with a fraud
     * reporting link — SBI's standard UPI alert closes with "report at https://sbi.co.in"
     * — so treating "http" as a promotional marker silently discarded genuine debits from
     * some of the largest banks in the country. Promotions are caught by what they say
     * instead, which still works once the link is out of the way.
     */
    private val URL_PATTERN = Regex("""\b(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)

    private val AMOUNT_PATTERNS = listOf(
        // Rs.1,500.00 / INR 2,500 / Rs 250.00 / ₹1,234.56
        Regex("""(?:rs|inr|₹)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // "debited by 500.0" — the amount trails the verb with no currency marker.
        Regex(
            """(?:debited|credited|spent|paid)\s+(?:by|for)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
    )

    private val BALANCE_PATTERN = Regex(
        """(?:avl|available|avlbl|a/c)\s*(?:bal|balance)[:\s]*(?:rs|inr|₹)?\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val ACCOUNT_PATTERN = Regex(
        """(?:a/c|acct|account|card)\s*(?:no\.?)?\s*[*x]{0,6}([0-9]{3,6})""",
        RegexOption.IGNORE_CASE
    )

    /** Merchant or counterparty, taken from whatever follows the connecting word. */
    private val MERCHANT_PATTERNS = listOf(
        Regex("""(?:trf to|transferred to)\s+([A-Za-z][A-Za-z0-9 &._-]{2,40})""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+(?:vpa\s+)?([A-Za-z][A-Za-z0-9 &._-]{2,40})""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+([A-Za-z][A-Za-z0-9 &._-]{2,40})""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9 &._-]{2,40})""", RegexOption.IGNORE_CASE)
    )

    /** Trailing noise that banks append after the merchant name. */
    private val MERCHANT_STOP_WORDS = listOf(
        " on ", " avl ", " available ", " bal ", " ref ", " refno ", " upi ", " info ",
        " not you", " call ", " your ", " a/c ", " account ", " txn ", " id ", " dated ",
        " from ", " to ", " via ", " using ", " for ", " if ", " sms ", " block ",
        " thru ", " through ", " rs ", " inr ", " limit ", " lmt ", " no ", " dt "
    )

    private val DATE_FORMATS = listOf(
        "dd-MM-yy", "dd-MM-yyyy", "dd/MM/yy", "dd/MM/yyyy",
        "dd-MMM-yy", "dd-MMM-yyyy", "ddMMMyy", "dd MMM yy", "dd MMM yyyy",
        "yyyy-MM-dd"
    )

    private val DATE_PATTERN = Regex(
        """\b(\d{1,2}[-/ ]?[A-Za-z]{3}[-/ ]?\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4}|\d{4}-\d{2}-\d{2})\b"""
    )

    /**
     * Parses one message.
     *
     * @param receivedOn when the SMS arrived, used when the text carries no date of its
     *        own — which is common for UPI alerts.
     */
    fun parse(body: String, receivedOn: LocalDate): ParsedSms {
        val raw = body.trim()
        if (raw.isEmpty()) return ParsedSms(originalText = body, ignoredReason = "Empty message")

        // Links come out before anything is judged or extracted. A fraud-reporting URL in
        // a real alert must not read as advertising, a promotional link must not smuggle
        // words like "offer" past the check inside a query string, and a path full of
        // digits must not be mistaken for a date or an amount.
        val text = URL_PATTERN.replace(raw, " ").trim()
        val lower = text.lowercase()

        // Promotions and reminders mention rupees without moving any, so they go first.
        if (NOT_A_TRANSACTION.any { lower.contains(it) }) {
            return ParsedSms(
                originalText = body,
                ignoredReason = "Not a transaction alert"
            )
        }

        val direction = detectDirection(lower)
            ?: return ParsedSms(originalText = body, ignoredReason = "No debit or credit found")

        val amount = extractAmount(text)
            ?: return ParsedSms(originalText = body, ignoredReason = "No amount found")

        return ParsedSms(
            originalText = body,
            amount = amount,
            direction = direction,
            date = extractDate(text) ?: receivedOn,
            merchant = extractMerchant(text),
            accountTail = ACCOUNT_PATTERN.find(text)?.groupValues?.getOrNull(1),
            balanceAfter = BALANCE_PATTERN.find(text)?.groupValues?.getOrNull(1)
                ?.let { parseAmountText(it) }
        )
    }

    private fun detectDirection(lower: String): SmsDirection? {
        // Debit is checked first: "debited ... avl bal credited" style messages are debits.
        val debitAt = DEBIT_WORDS.mapNotNull { w -> lower.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val creditAt = CREDIT_WORDS.mapNotNull { w -> lower.indexOf(w).takeIf { it >= 0 } }.minOrNull()

        return when {
            debitAt != null && creditAt != null ->
                if (debitAt <= creditAt) SmsDirection.DEBIT else SmsDirection.CREDIT
            debitAt != null -> SmsDirection.DEBIT
            creditAt != null -> SmsDirection.CREDIT
            else -> null
        }
    }

    /**
     * Takes the first currency amount. Any balance figure is excluded, because "Avl Bal
     * Rs.42,500" would otherwise be mistaken for a 42,500 rupee transaction.
     */
    private fun extractAmount(text: String): Money? {
        val balanceRange = BALANCE_PATTERN.find(text)?.range

        for (pattern in AMOUNT_PATTERNS) {
            for (match in pattern.findAll(text)) {
                if (balanceRange != null && match.range.first in balanceRange) continue
                val parsed = parseAmountText(match.groupValues[1])
                if (parsed != null && parsed.isPositive) return parsed
            }
        }
        return null
    }

    private fun parseAmountText(raw: String): Money? =
        Money.parseOrNull(raw.replace(",", ""))

    private fun extractDate(text: String): LocalDate? {
        val raw = DATE_PATTERN.find(text)?.value?.trim() ?: return null
        val normalised = raw.replace("/", "-").replace(" ", "-")

        for (format in DATE_FORMATS) {
            val candidate = runCatching {
                LocalDate.parse(
                    normalised,
                    DateTimeFormatter.ofPattern(format, Locale.ENGLISH)
                )
            }.getOrNull()
            if (candidate != null) return candidate

            // Formats without separators, such as 18Aug26.
            val compact = runCatching {
                LocalDate.parse(
                    raw.replace("-", "").replace("/", "").replace(" ", ""),
                    DateTimeFormatter.ofPattern(format.replace("-", "").replace(" ", ""), Locale.ENGLISH)
                )
            }.getOrNull()
            if (compact != null) return compact
        }
        return null
    }

    private fun extractMerchant(text: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val candidate = pattern.find(text)?.groupValues?.getOrNull(1)?.trim() ?: continue
            val cleaned = trimAtStopWord(candidate)
                .trim()
                .trim('.', ',', '-', '_')
            if (cleaned.length >= 3 && !cleaned.all { it.isDigit() }) {
                return cleaned.split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { part ->
                        part.lowercase().replaceFirstChar { it.uppercase() }
                    }
            }
        }
        return null
    }

    /**
     * Cuts the merchant name at the first trailing noise word.
     *
     * The stop words are written with surrounding spaces so that "on" only matches as a
     * whole word, and the text is padded to match one at either end. That padding shifts
     * every position by one, which is corrected here rather than left to cancel out
     * against the space the match itself starts with.
     */
    private fun trimAtStopWord(value: String): String {
        val padded = " ${value.lowercase()} "
        var cut = value.length
        MERCHANT_STOP_WORDS.forEach { stop ->
            val index = padded.indexOf(stop)
            if (index >= 0) {
                // A hit at padded index i is a space sitting at value index i - 1.
                val cutInValue = (index - 1).coerceAtLeast(0)
                if (cutInValue < cut) cut = cutInValue
            }
        }
        return value.take(cut)
    }
}

enum class SmsDirection { DEBIT, CREDIT }

/**
 * A candidate transaction read from a message. Always reviewed before it becomes a record.
 */
data class ParsedSms(
    val originalText: String,
    val amount: Money? = null,
    val direction: SmsDirection? = null,
    val date: LocalDate? = null,
    val merchant: String? = null,
    val accountTail: String? = null,
    val balanceAfter: Money? = null,
    /** Set when the message was recognised as something other than a transaction. */
    val ignoredReason: String? = null
) {
    val isTransaction: Boolean
        get() = ignoredReason == null && amount != null && amount.isPositive && direction != null

    val isDebit: Boolean get() = direction == SmsDirection.DEBIT

    /** The description to prefill, falling back to something honest when no merchant read. */
    fun suggestedDescription(): String = merchant ?: if (isDebit) "Card or UPI payment" else "Money received"
}
