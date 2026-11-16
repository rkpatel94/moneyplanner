package com.moneyplanner.domain.nlp

import com.moneyplanner.core.money.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
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

    /**
     * Words that mark money leaving the account.
     *
     * Drawn from the wording the major Indian banks actually send. "withdrawn at" covers
     * ATM slips, "towards" appears in standing instructions and auto-debits, and the
     * short forms are what the length-limited alerts fall back on.
     *
     * Card alerts often never say "debited" at all. ICICI writes that a card "has been
     * used for a transaction of", and older card alerts open with "thank you for using
     * your card", so those phrases count as money leaving too.
     */
    private val DEBIT_WORDS = listOf(
        "debited", "debit", "spent", "withdrawn", "paid", "purchase", "sent",
        "transferred to", "trf to", "deducted", "withdrawal", "w/d",
        "dr ", "dr.", "towards", "charged", "swiped", "utilised", "utilized",
        "payment of", "auto-debit", "auto debit", "standing instruction", "si executed",
        "used for", "used at", "for using your"
    )

    /** Words that mark money arriving. */
    private val CREDIT_WORDS = listOf(
        "credited", "credit", "received", "deposited", "refund", "cashback",
        "cr ", "cr.", "has been credited", "money received", "reversed",
        "reversal", "salary", "neft in", "imps in"
    )

    /**
     * Messages that mention money but are not transactions. Discarding these is what keeps
     * the review list short enough that a person will actually read it.
     */
    private val NOT_A_TRANSACTION = listOf(
        "otp", "one time password", "will be debited", "due on", "is due", "outstanding",
        "minimum amount due", "statement", "offer", "cashback offer", "eligible",
        "pre-approved", "loan offer", "apply now", "balance enquiry",
        "reward points", "emi of", "kyc",
        // Requests are not movements, and they mention an amount.
        "has requested", "requesting", "collect request", "payment request",
        "reversed due to", "insufficient", "will be credited", "scheduled", "shall be debited",
        // Marketing that survives the link being stripped.
        "download the app", "click here", "t&c apply", "terms apply", "limited period",
        "congratulations", "you are eligible", "upgrade your", "activate now",
        // Mandates and returned cheques.
        "e-mandate", "mandate registered", "cheque returned", "min amt due"
    )

    /**
     * Balance wording, which ends many genuine alerts as well as every balance enquiry.
     *
     * ICICI closes its credit alerts with "Available Balance is Rs. ...", so treating the
     * phrase as proof of a balance enquiry threw away real salary credits. A message only
     * counts as a balance reply when it has not already said that money moved; the balance
     * figure itself is kept out of the amount either way.
     */
    private val BALANCE_ONLY = listOf(
        "available balance is", "avl bal is", "closing balance", "opening balance"
    )

    /**
     * Failure wording. A failed payment names an amount that never moved, so it is thrown
     * away; but the message that hands the money back after a failure usually says
     * "failed" too, and that one is a real credit. Without it, the debit alert sent before
     * the failure was noticed stays in the ledger with nothing to cancel it.
     */
    private val FAILURE_WORDS = listOf(
        "failed", "declined", "unsuccessful", "could not be processed"
    )

    /** Money that has not moved yet, in each of the tenses banks announce it in. */
    private val FUTURE_TENSE = Regex(
        """\b(?:will|shall|would|to)\s+be\s+(?:auto[- ]?)?""" +
            """(?:debited|credited|deducted|charged|paid|processed|reversed|refunded|transferred)\b""",
        RegexOption.IGNORE_CASE
    )

    /** A mandate being set up or changed is a promise to debit later, not a debit. */
    private val MANDATE_SETUP = Regex(
        """\b(?:mandate|autopay|auto\s+pay)\b.{0,60}?\b""" +
            """(?:created|registered|set\s*up|activated|approved|modified|revoked|cancell?ed|paused)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Past-tense proof that money moved, which outranks a trailing balance line. */
    private val MOVED = Regex(
        """\b(?:debited|credited|spent|withdrawn|deposited|sent|received|paid|transferred|deducted)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Money coming back. These alerts usually describe the original debit before saying it
     * was returned — "Rs.500 debited on 18-08-26 has been reversed" — so reading the first
     * direction word recorded a refund as a second expense.
     */
    private val REVERSAL = Regex(
        """\b(?:has\s+been|have\s+been|is|was|been)\s+(?:reversed|refunded)\b|""" +
            """\bcredited\s+back\b|\brefund(?:ed)?\s+(?:of|for)\b|\breversal\s+of\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Card names carry a direction word inside them.
     *
     * "Credit card" contains "credit", so an ICICI alert saying a credit card "has been
     * used for a transaction of INR 1,299" used to come out as money received. The name of
     * the card says nothing about which way the money went, so it is taken out before the
     * direction is judged.
     */
    private val CARD_PRODUCT = Regex(
        """\b(?:credit|debit)\s*(?:cum\s+atm\s+)?card\b|\bcredit\s+limit\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * A credit card named outright. HDFC's card alerts say only "HDFC Bank Card 1234", but
     * end with "SMS BLOCK CC 1234" for a credit card and "BLOCK DC" for a debit card.
     */
    private val CREDIT_CARD = Regex("""\bcredit\s*card\b|\bblock\s+cc\b""", RegexOption.IGNORE_CASE)

    private val DEBIT_CARD = Regex(
        """\bdebit\s*(?:cum\s+atm\s+)?card\b|\batm\s*(?:cum\s+debit\s+)?card\b|\bblock\s+dc\b|\bpos\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * UPI says so in nearly every alert, and when it does not, a payee handle such as
     * "swiggy@icici" gives it away. An email address is not a handle, which is what the
     * check for a dot after the provider is for.
     */
    private val UPI = Regex(
        """\bupi\b|\bvpa\b|[a-z0-9._-]{2,}@[a-z]{2,}(?![a-z0-9.])""",
        RegexOption.IGNORE_CASE
    )

    private val ATM_WITHDRAWAL = Regex(
        """\batm\b.{0,80}?\b(?:withdrawn|withdrawal|w/d|wdl)\b|""" +
            """\b(?:withdrawn|withdrawal|w/d|wdl)\b.{0,80}?\batm\b|""" +
            """\bcash\s+(?:withdrawal|withdrawn|wdl)\b""",
        RegexOption.IGNORE_CASE
    )

    private val AUTO_DEBIT = Regex(
        """\bauto[- ]?debit|\bstanding\s+instruction|\bsi\s+executed|\bnach\b|\becs\b|""" +
            """\bautopay\b|\bauto\s+pay\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * A credit card bill being paid from the bank, which names both the card and the
     * account the money came from. It is a real outflow from the account, but it settles
     * spending that was already recorded against the card, so it must never be read as a
     * fresh purchase on the card.
     */
    private val CARD_BILL = Regex(
        """\bbill\b|\bpayment\b|\btowards\b|\brepay|""" +
            """\bcredited\s+to\s+(?:your\s+)?(?:\w+\s+){0,3}?credit\s*card""",
        RegexOption.IGNORE_CASE
    )
    private val OWN_ACCOUNT = Regex(
        """(?<!card\s)\b(?:a/c|ac|acct|account|savings)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * The card issuer thanking the user for paying the bill. The money left the bank in a
     * separate alert, so this one is the same payment seen from the other side.
     */
    private val CARD_PAYMENT_ACK = Regex(
        """\bpayment\b.{0,80}?\b(?:received|credited)\b|""" +
            """\b(?:received|credited)\b.{0,60}?\bpayment\b|""" +
            """\bthank\s+you\s+for\s+(?:your|the)\s+payment\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * An amount in another currency. The rupee figure for an international card spend is
     * not known until the bank converts it, and the only rupee figure such an alert carries
     * is the available limit — which is exactly the number that must never be recorded.
     * The dollar sign sits in a character class, where it means the sign and not the end
     * of the text.
     */
    private val FOREIGN_AMOUNT = Regex(
        """(?<![A-Za-z])(?:usd|eur|gbp|aed|sgd|aud|cad|jpy|chf|thb|myr|sar|qar|hkd|cny|nzd|lkr|""" +
            """npr|bdt|kwd|omr|bhd|idr|php|vnd|krw|zar|us[$]|[$]|€|£)\s*\.?\s*[0-9]""",
        RegexOption.IGNORE_CASE
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

    /**
     * Line breaks, folded into spaces. HDFC's current UPI alert puts every field on a line
     * of its own, and the wording rules below are written for the one-line form.
     */
    private val WHITESPACE = Regex("""\s+""")

    private val AMOUNT_PATTERNS = listOf(
        // Rs.1,500.00 / INR 2,500 / Rs 250.00 / ₹1,234.56. Not inside a word, so the "rs"
        // of "hrs" in a timestamp cannot pass for a currency.
        Regex(
            """(?<![A-Za-z])(?:rs|inr|₹)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        ),
        // Amount before the currency, as several banks write it: "1,500.00 INR".
        Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:rs|inr|₹)\b""", RegexOption.IGNORE_CASE),
        // "debited by 500.0" — the amount trails the verb with no currency marker.
        Regex(
            """(?:debited|credited|spent|paid|withdrawn|charged)\s+(?:by|for|with)?\s*""" +
                """([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        ),
        // "of Rs" dropped entirely: "a/c debited of 1200 on 05-09-26".
        Regex("""\bof\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""", RegexOption.IGNORE_CASE)
    )

    /** The patterns that only ever match a figure written with its currency. */
    private val RUPEE_AMOUNT_PATTERNS = AMOUNT_PATTERNS.take(2)

    /**
     * The balance quoted after a transaction, which must never be read as the amount.
     *
     * Banks abbreviate this a dozen ways and the shorter forms are the dangerous ones: a
     * message ending "Bal 42,500" would otherwise contribute a 42,500 rupee expense.
     */
    private val BALANCE_PATTERN = Regex(
        """(?:avl|available|avlbl|avbl|a/c|acct|account|clear|closing|net)?\s*""" +
            """(?:bal|balance|bal\.)[:\s-]*(?:is|of)?[:\s]*""" +
            """(?:rs|inr|₹)?\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * A card's available limit, which is to a card alert what the balance is to a bank
     * alert: a large figure next to the real one that must never be taken for it.
     */
    private val LIMIT_PATTERN = Regex(
        """(?:avl|available|avlbl|avbl|total|credit|card)?\s*(?:limit|lmt)\b[:\s.-]*(?:is)?[:\s]*""" +
            """(?:rs|inr|₹)?\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val ACCOUNT_PATTERN = Regex(
        """(?:a/c|ac|acct|account|card|xx|ending)\s*(?:no\.?|number)?\s*[*xX#]{0,6}([0-9]{3,6})\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * The last four digits of the card used, kept apart from the account tail because a
     * bill payment names both: "debited from A/c XX1234 towards Credit Card XX5678".
     */
    private val CARD_TAIL = Regex(
        """\bcard\b(?:\s+(?:no\.?|number|ending(?:\s+with)?|account|a/c|acct))?[\s:.#-]*""" +
            """(?:[0-9]{0,6}[*xX#]{1,12})?\s*([0-9]{4})\b""",
        RegexOption.IGNORE_CASE
    )

    /** What a merchant or payer name may contain. */
    private const val NAME = """([A-Za-z][A-Za-z0-9 &._-]{2,40})"""

    /** The card network's country prefix on a merchant, as in ICICI's "on IND*FLIPKART". */
    private const val NETWORK_PREFIX = """(?:[A-Za-z]{2,4}\*)?"""

    /**
     * The payee or payer written as a path, as Axis and most statements do:
     * "UPI/P2M/422334455667/SWIGGY" or "NEFT/ICIC0000001/ACME TECHNOLOGIES". The segments
     * before the name are a rail marker, a reference number or an IFSC code.
     */
    private val RAIL_PATH = Regex(
        """\b(?:upi|neft|imps|rtgs)(?:/(?:p2[am]|dr|cr|[a-z]{4}0[a-z0-9]{6}|\d+))+/""" +
            """([a-z][a-z0-9 &._-]{2,40})""",
        RegexOption.IGNORE_CASE
    )

    /** "Info: SALARY" and "Info NEFT-ACME TECHNOLOGIES", with the rail name dropped. */
    private val INFO_TRAILER = Regex(
        """\b(?:info|ref|remarks?)[:\s-]+(?:(?:neft|imps|rtgs|upi)[-/:\s]+)?$NAME""",
        RegexOption.IGNORE_CASE
    )

    /** Merchant or counterparty of a debit, taken from whatever follows the connecting word. */
    private val MERCHANT_PATTERNS = listOf(
        Regex("""(?:trf to|transferred to)\s+$NAME""", RegexOption.IGNORE_CASE),
        // ICICI names the payee after a semicolon: "debited for Rs 500.00 on 18-Aug-26;
        // SWIGGY credited."
        Regex(""";\s*([A-Za-z][A-Za-z0-9 &._-]{1,40}?)\s+credited\b""", RegexOption.IGNORE_CASE),
        RAIL_PATH,
        // A UPI handle needs no pattern of its own: the character class stops at the "@",
        // turning "bigbasket@icici" into the readable "Bigbasket" rather than the handle.
        Regex("""\bto\s+(?:vpa\s+)?$NAME""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:at|towards)\s+$NETWORK_PREFIX$NAME""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+$NAME""", RegexOption.IGNORE_CASE),
        // "Info: NEFT-ACME TRADERS" and "Ref: SWIGGY" style trailers.
        INFO_TRAILER,
        // Axis card alerts have no connecting word at all; the merchant follows the time:
        // "18-08-26 14:32:10 IST AMAZON Avl Limit".
        Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\s+(?:ist\s+)?$NAME""", RegexOption.IGNORE_CASE),
        // ICICI card alerts name the merchant after a second "on": "spent using ICICI Bank
        // Card XX9012 on 15-Nov-26 on IND*FLIPKART". Last, because "on" is everywhere.
        Regex("""\bon\s+$NETWORK_PREFIX$NAME""", RegexOption.IGNORE_CASE)
    )

    /**
     * Who paid, for a credit. These read differently from a debit: "to" names the user's
     * own account, so it is left out, and the payer follows "from", "by" or the NEFT
     * remitter field.
     */
    private val PAYER_PATTERNS = listOf(
        // HDFC NEFT credits: "for NEFT Cr-ICIC0000001-ACME TECHNOLOGIES PVT LTD-RAJ KUMAR-…".
        Regex(
            """\b(?:neft|imps|rtgs)\s*cr-[a-z]{4}0[a-z0-9]{6}-([a-z][a-z0-9 &.]{2,40})""",
            RegexOption.IGNORE_CASE
        ),
        RAIL_PATH,
        Regex("""\bfrom\s+(?:vpa\s+)?$NAME""", RegexOption.IGNORE_CASE),
        Regex("""\bvpa\s+([a-z][a-z0-9._-]{2,40})@""", RegexOption.IGNORE_CASE),
        Regex(
            """\bby\s+(?!(?:neft|imps|rtgs|upi|a/c|ac|acct|account|transfer|trf|cash|cheque|chq|""" +
                """clearing|mobile|vpa)\b)$NAME""",
            RegexOption.IGNORE_CASE
        ),
        INFO_TRAILER
    )

    /**
     * Where the trailer starts: the fraud-reporting line and the boilerplate after it. It
     * names things that are not the merchant — HDFC's deposit alert ends "Cheque deposits in
     * A/c are subject to clearing", which used to make the payer "Clearing" — so names are
     * only looked for before it.
     */
    private val TRAILER = Regex(
        """\b(?:not\s+you|not\s+u\b|if\s+not\b|not\s+done\s+by|sms\s+block|to\s+block\b|""" +
            """call\s+(?:us\s+)?(?:on\s+|at\s+)?\+?\d|report\s+(?:at|to|it)\b|for\s+dispute|""" +
            """cheque\s+deposits|subject\s+to\s+clearing|never\s+share|do\s+not\s+share)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Candidates that are the user's own account or card, or the amount itself, rather
     * than a counterparty: "from HDFC Bank A/C" names where the money came from, and
     * SBI's "credited by Rs1500" puts the amount where the payer would be.
     */
    private val NOT_A_NAME = Regex(
        """^(?:a/c|ac|acct|account|your|you|block|mobile|card|no|ref)\b|^(?:rs|inr)\.?\s*\d|""" +
            """\bbank\b|\ba/c\b|\bacct\b|\baccount\b|\bcard\b""",
        RegexOption.IGNORE_CASE
    )

    /** Trailing noise that banks append after the merchant name. */
    private val MERCHANT_STOP_WORDS = listOf(
        " on ", " avl ", " available ", " bal ", " ref ", " refno ", " upi ", " info ",
        " not you", " call ", " your ", " a/c ", " account ", " txn ", " id ", " dated ",
        " from ", " to ", " via ", " using ", " for ", " if ", " sms ", " block ",
        " thru ", " through ", " rs ", " inr ", " limit ", " lmt ", " no ", " dt ",
        " avbl ", " avlbl ", " by ", " is ", " has ", " was ", " with ", " ist ", " at ",
        " in ", " linked ", " ending ", ". "
    )

    private val DATE_PATTERN = Regex(
        """\b(?:\d{1,2}[-/ ]?[A-Za-z]{3,9}[-/ ]?\d{2,4}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|""" +
            """\d{4}-\d{1,2}-\d{1,2}|[A-Za-z]{3,9} \d{1,2},? \d{4})\b"""
    )

    /**
     * Month names are matched without regard to case. Banks write "01-NOV-26" as often as
     * "01-Nov-26", and a formatter that insisted on "Nov" quietly dated those alerts to the
     * day they arrived rather than the day they happened.
     */
    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .toFormatter(Locale.ENGLISH)

    private val DATE_FORMATS = listOf(
        "d-M-yy", "d-M-yyyy", "d-MMM-yy", "d-MMM-yyyy", "d-MMMM-yy", "d-MMMM-yyyy",
        "MMM-d-yyyy", "MMMM-d-yyyy", "yyyy-M-d"
    ).map(::formatter)

    /** Formats without separators, such as SBI's 18Aug26. */
    private val COMPACT_DATE_FORMATS = listOf("dMMMyy", "dMMMyyyy").map(::formatter)

    private val DATE_SEPARATORS = Regex("""[-/., ]+""")

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
        val text = URL_PATTERN.replace(raw, " ").replace(WHITESPACE, " ").trim()
        val lower = text.lowercase()

        // Promotions and reminders mention rupees without moving any, so they go first.
        if (isNotATransaction(lower)) {
            return ParsedSms(
                originalText = body,
                ignoredReason = "Not a transaction alert"
            )
        }

        val notTheAmount = figuresThatAreNotTheAmount(text)
        if (isInForeignCurrency(text, notTheAmount)) {
            return ParsedSms(
                originalText = body,
                ignoredReason = "Charged in another currency, so the rupee amount is not known yet"
            )
        }

        val direction = detectDirection(lower)
            ?: return ParsedSms(originalText = body, ignoredReason = "No debit or credit found")

        val isCreditCard = CREDIT_CARD.containsMatchIn(text)
        val isCardBillPayment = isCreditCard &&
            direction == SmsDirection.DEBIT &&
            CARD_BILL.containsMatchIn(text) &&
            OWN_ACCOUNT.containsMatchIn(text)

        // Money arriving on a credit card lowers what is owed on it; it never reaches an
        // account, so recording it as income would add cash the user does not have.
        if (isCreditCard && !isCardBillPayment) {
            if (direction == SmsDirection.CREDIT) {
                return ParsedSms(
                    originalText = body,
                    ignoredReason = "Money back on a credit card, not paid into an account"
                )
            }
            if (CARD_PAYMENT_ACK.containsMatchIn(text)) {
                return ParsedSms(
                    originalText = body,
                    ignoredReason = "A card bill payment, already counted when it left the bank"
                )
            }
        }

        val amount = extractAmount(text, notTheAmount)
            ?: return ParsedSms(originalText = body, ignoredReason = "No amount found")

        return ParsedSms(
            originalText = body,
            amount = amount,
            direction = direction,
            date = extractDate(text, receivedOn),
            merchant = extractMerchant(text, direction),
            accountTail = ACCOUNT_PATTERN.find(text)?.groupValues?.getOrNull(1),
            balanceAfter = BALANCE_PATTERN.find(text)?.groupValues?.getOrNull(1)
                ?.let { parseAmountText(it) },
            cardTail = CARD_TAIL.find(text)?.groupValues?.getOrNull(1),
            isCreditCard = isCreditCard,
            isDebitCard = DEBIT_CARD.containsMatchIn(text),
            isUpi = UPI.containsMatchIn(text),
            isAtmWithdrawal = direction == SmsDirection.DEBIT && ATM_WITHDRAWAL.containsMatchIn(text),
            isAutoDebit = AUTO_DEBIT.containsMatchIn(text),
            isCardBillPayment = isCardBillPayment
        )
    }

    private fun isNotATransaction(lower: String): Boolean {
        if (NOT_A_TRANSACTION.any { lower.contains(it) }) return true
        if (FUTURE_TENSE.containsMatchIn(lower) || MANDATE_SETUP.containsMatchIn(lower)) return true

        val balanceAt = BALANCE_ONLY.mapNotNull { phrase ->
            lower.indexOf(phrase).takeIf { it >= 0 }
        }.minOrNull()
        if (balanceAt != null) {
            val movedAt = MOVED.find(lower)?.range?.first
            if (movedAt == null || movedAt > balanceAt) return true
        }

        return FAILURE_WORDS.any { lower.contains(it) } && !REVERSAL.containsMatchIn(lower)
    }

    private fun detectDirection(lower: String): SmsDirection? {
        // Money coming back is a credit however much of the original debit is retold first.
        if (REVERSAL.containsMatchIn(lower)) return SmsDirection.CREDIT

        val neutral = CARD_PRODUCT.replace(lower, "card")

        // Debit is checked first: "debited ... avl bal credited" style messages are debits.
        val debitAt = DEBIT_WORDS.mapNotNull { w -> neutral.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val creditAt = CREDIT_WORDS.mapNotNull { w -> neutral.indexOf(w).takeIf { it >= 0 } }.minOrNull()

        return when {
            debitAt != null && creditAt != null ->
                if (debitAt <= creditAt) SmsDirection.DEBIT else SmsDirection.CREDIT
            debitAt != null -> SmsDirection.DEBIT
            creditAt != null -> SmsDirection.CREDIT
            else -> null
        }
    }

    /**
     * Where the balance and the available limit sit in the text. Every occurrence is
     * collected rather than the first, because an alert can quote a balance twice.
     */
    private fun figuresThatAreNotTheAmount(text: String): List<IntRange> =
        (BALANCE_PATTERN.findAll(text) + LIMIT_PATTERN.findAll(text))
            .mapNotNull { it.groups[1]?.range }
            .toList()

    /**
     * True when the first amount in the message is in another currency. A rupee figure
     * written first wins, so an alert quoting the converted amount with the original
     * beside it still imports in rupees.
     */
    private fun isInForeignCurrency(text: String, notTheAmount: List<IntRange>): Boolean {
        val foreignAt = FOREIGN_AMOUNT.find(text)?.range?.first ?: return false
        val rupeeAt = RUPEE_AMOUNT_PATTERNS
            .flatMap { pattern -> pattern.findAll(text).toList() }
            .filter { match -> match.groups[1]?.range?.let { !it.overlapsAny(notTheAmount) } ?: false }
            .minOfOrNull { it.range.first }
        return rupeeAt == null || foreignAt < rupeeAt
    }

    /**
     * Takes the first currency amount. Any balance or limit figure is excluded, because
     * "Avl Bal Rs.42,500" would otherwise be mistaken for a 42,500 rupee transaction.
     */
    private fun extractAmount(text: String, notTheAmount: List<IntRange>): Money? {
        for (pattern in AMOUNT_PATTERNS) {
            for (match in pattern.findAll(text)) {
                val figure = match.groups[1]?.range ?: continue
                if (figure.overlapsAny(notTheAmount)) continue
                val parsed = parseAmountText(match.groupValues[1])
                if (parsed != null && parsed.isPositive) return parsed
            }
        }
        return null
    }

    private fun IntRange.overlapsAny(ranges: List<IntRange>): Boolean =
        ranges.any { it.first <= last && first <= it.last }

    private fun parseAmountText(raw: String): Money? =
        Money.parseOrNull(raw.replace(",", ""))

    /**
     * The first date in the text that reads as a date at all and is not after the message
     * arrived. A completed transaction cannot postdate its own alert, so a later date is a
     * validity or due date mentioned in passing; a day of grace allows for time zones.
     */
    private fun extractDate(text: String, receivedOn: LocalDate): LocalDate {
        val latestPossible = receivedOn.plusDays(1)
        for (match in DATE_PATTERN.findAll(text)) {
            val candidate = parseDate(match.value.trim()) ?: continue
            if (candidate.isAfter(latestPossible)) continue
            return candidate
        }
        return receivedOn
    }

    private fun parseDate(raw: String): LocalDate? {
        val normalised = raw.replace(DATE_SEPARATORS, "-").trim('-')
        DATE_FORMATS.firstNotNullOfOrNull { format ->
            runCatching { LocalDate.parse(normalised, format) }.getOrNull()
        }?.let { return it }

        val compact = normalised.replace("-", "")
        return COMPACT_DATE_FORMATS.firstNotNullOfOrNull { format ->
            runCatching { LocalDate.parse(compact, format) }.getOrNull()
        }
    }

    private fun extractMerchant(text: String, direction: SmsDirection): String? {
        val beforeTrailer = TRAILER.find(text)?.let { text.substring(0, it.range.first) } ?: text
        val patterns = if (direction == SmsDirection.CREDIT) PAYER_PATTERNS else MERCHANT_PATTERNS

        for (pattern in patterns) {
            for (match in pattern.findAll(beforeTrailer)) {
                val candidate = match.groupValues.getOrNull(1)?.trim() ?: continue
                val cleaned = trimAtStopWord(candidate)
                    .trim()
                    .trim('.', ',', '-', '_')
                    .trim()
                if (cleaned.length < 3 || cleaned.all { it.isDigit() }) continue
                if (NOT_A_NAME.containsMatchIn(cleaned)) continue
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
 *
 * The flags describe how the money moved, as far as the wording says, so the importer can
 * file it the way the user would have: a card purchase against the card rather than the
 * bank, a cash withdrawal as a move into the cash account rather than as spending.
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
    val ignoredReason: String? = null,
    /** The last four digits of the card named in the message, when one is. */
    val cardTail: String? = null,
    /** The message says outright that a credit card was used. */
    val isCreditCard: Boolean = false,
    /** The message names a debit card or a point-of-sale terminal. */
    val isDebitCard: Boolean = false,
    val isUpi: Boolean = false,
    /** Cash taken out at an ATM, which moves money between the user's own pockets. */
    val isAtmWithdrawal: Boolean = false,
    /** A standing instruction, NACH or autopay debit. */
    val isAutoDebit: Boolean = false,
    /** A credit card bill paid from a bank account, rather than a purchase on the card. */
    val isCardBillPayment: Boolean = false
) {
    val isTransaction: Boolean
        get() = ignoredReason == null && amount != null && amount.isPositive && direction != null

    val isDebit: Boolean get() = direction == SmsDirection.DEBIT

    /** The description to prefill, falling back to something honest when no merchant read. */
    fun suggestedDescription(): String = when {
        isAtmWithdrawal -> "Cash withdrawal"
        isCardBillPayment -> "Credit card bill"
        merchant != null -> merchant
        !isDebit -> "Money received"
        isUpi -> "UPI payment"
        isCreditCard || isDebitCard || cardTail != null -> "Card payment"
        else -> "Card or UPI payment"
    }
}
