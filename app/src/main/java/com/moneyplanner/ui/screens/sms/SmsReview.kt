package com.moneyplanner.ui.screens.sms

import com.moneyplanner.core.money.Money
import com.moneyplanner.core.time.parsePeriodKey
import com.moneyplanner.data.sms.SmsCandidate
import com.moneyplanner.domain.calc.RecurrenceCalculator
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.CreditCard
import com.moneyplanner.domain.model.CreditCardPayment
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.IncomeSource
import com.moneyplanner.domain.model.IncomeTransaction
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.nlp.DuplicateVerdict
import com.moneyplanner.domain.nlp.ParsedSms
import com.moneyplanner.domain.nlp.SmsDuplicateDetector
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * What importing one reviewed message will write, decided before the user is asked.
 *
 * A bank alert says more than an amount and a direction. A card purchase belongs on the
 * card, where it waits for the bill rather than leaving the bank today; an ATM withdrawal
 * is money moving into the cash pocket, not money spent; a salary credit is the month's
 * salary arriving, which the forecast has been expecting. Filing all of them as a bank
 * transfer or "other income" made the balance and the forecast wrong in ways the user
 * could not see, so each row is given the record it really is, and says so on screen.
 */
sealed interface SmsImportPlan {

    /** True when the plan writes an expense, which is what the category picker is for. */
    val writesExpense: Boolean get() = false

    /** Why the row should start unticked even though it is not a duplicate. */
    val caution: String? get() = null

    /** A purchase on a credit card. It touches no account: cash leaves when the bill is paid. */
    data class CardSpend(val card: CreditCard?, val tail: String?) : SmsImportPlan {
        override val writesExpense: Boolean get() = true
    }

    /** Money out of a bank account, by whichever rail the message names. */
    data class BankDebit(
        val method: PaymentMethod,
        val account: Account?,
        /** An ATM withdrawal recorded as spending, because there is no cash account to move it to. */
        val isCashWithdrawal: Boolean = false,
        override val caution: String? = null
    ) : SmsImportPlan {
        override val writesExpense: Boolean get() = true
    }

    /** Cash taken out at an ATM: a move between two of the user's own accounts. */
    data class CashWithdrawal(val from: Account, val to: Account) : SmsImportPlan

    /** A credit card bill paid from the bank, which lowers what is owed on the card. */
    data class CardBillPayment(val card: CreditCard, val account: Account?) : SmsImportPlan

    /** A regular income arriving for the period the forecast was expecting it. */
    data class ScheduledIncome(val source: IncomeSource, val period: YearMonth) : SmsImportPlan

    /** Any other money in, to the default bank account. */
    data class OtherIncome(
        val account: Account?,
        override val caution: String? = null
    ) : SmsImportPlan
}

/** What the row says will happen, in the words the rest of the app uses. */
fun SmsImportPlan.summary(on: LocalDate): String = when (this) {
    is SmsImportPlan.CardSpend -> when {
        card != null -> cardLabel(card, tail)
        tail != null -> "Credit card ••$tail, not one you have added"
        else -> "Credit card, not one you have added"
    }
    is SmsImportPlan.BankDebit -> {
        val what = if (isCashWithdrawal) "Cash withdrawal" else method.label
        account?.let { "$what from ${it.name}" } ?: "$what, no account set"
    }
    is SmsImportPlan.CashWithdrawal -> "Cash withdrawal → ${to.name}"
    is SmsImportPlan.CardBillPayment -> {
        val bill = "Bill for ${cardLabel(card, null)}"
        account?.let { "$bill, paid from ${it.name}" } ?: bill
    }
    is SmsImportPlan.ScheduledIncome -> "${source.name} for ${monthLabel(period, on)}"
    is SmsImportPlan.OtherIncome -> account?.let { "Income to ${it.name}" } ?: "Income, no account set"
}

private fun cardLabel(card: CreditCard, tail: String?): String {
    val digits = card.lastFour() ?: tail
    return if (digits != null) "Card ••$digits" else card.name
}

/** "November", or "November 2025" when the period is not in the year the money arrived. */
internal fun monthLabel(period: YearMonth, on: LocalDate): String {
    val name = period.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    return if (period.year == on.year) name else "$name ${period.year}"
}

/** The last four digits of a card as the user recorded them, ignoring spaces and dashes. */
internal fun CreditCard.lastFour(): String? =
    lastFourDigits?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }

/** A regular income and the scheduled month a receipt is stamped with. */
data class SourcePeriod(val sourceId: Long, val period: YearMonth)

/** Everything a review decision depends on, gathered once per scan or paste. */
data class SmsReviewContext(
    val categories: List<Category> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val incomeTransactions: List<IncomeTransaction> = emptyList(),
    val transfers: List<AccountTransfer> = emptyList(),
    val cardPayments: List<CreditCardPayment> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(),
    /** Inbox ids of messages an earlier scan already imported. */
    val importedSmsIds: Set<Long> = emptySet()
) {
    /** The first active bank account, which is the account a bank alert is about. */
    val defaultBankAccount: Account?
        get() = accounts.firstOrNull { it.type == AccountType.BANK && !it.isArchived }

    val cashAccount: Account?
        get() = accounts.firstOrNull { it.type == AccountType.CASH && !it.isArchived }
}

/**
 * Decides which record each message becomes.
 *
 * Every rule here errs towards the record that cannot make cash wrong. A card purchase that
 * cannot be matched to a card is still filed as a card purchase, because pretending it left
 * the bank would understate the balance until the bill is paid; a credit that cannot be
 * matched to a regular income is plain income rather than a guess at which salary it was.
 */
object SmsImportPlanner {

    /** How far a credit may differ from the income it is matched to. */
    const val INCOME_AMOUNT_TOLERANCE_PERCENT = 10L

    /** How many days either side of the due date a regular income may land. */
    const val INCOME_DATE_TOLERANCE_DAYS = 5L

    fun plan(
        parsed: ParsedSms,
        on: LocalDate,
        context: SmsReviewContext,
        receipts: Map<SourcePeriod, Int> = receiptsByPeriod(context.incomeTransactions)
    ): SmsImportPlan {
        val bank = context.defaultBankAccount
        return if (parsed.isDebit) {
            planDebit(parsed, bank, context)
        } else {
            planCredit(parsed, on, bank, context, receipts)
        }
    }

    private fun planDebit(parsed: ParsedSms, bank: Account?, context: SmsReviewContext): SmsImportPlan {
        if (parsed.isCardBillPayment) {
            val card = cardFor(parsed, context.creditCards)
            return if (card != null) {
                SmsImportPlan.CardBillPayment(card, bank)
            } else {
                // The digits in a bill payment are the credit card's, not a debit card's,
                // so they say nothing about how the money left the account.
                SmsImportPlan.BankDebit(
                    method = if (parsed.isUpi) PaymentMethod.UPI else PaymentMethod.BANK_TRANSFER,
                    account = bank,
                    caution = "Looks like a credit card bill. Record it as a payment on the " +
                        "card instead, so it is not counted as spending twice."
                )
            }
        }

        val card = cardFor(parsed, context.creditCards)
        if (parsed.isCreditCard) return SmsImportPlan.CardSpend(card, parsed.cardTail)

        // A withdrawal on a card that is not named as a credit card is the debit card at
        // the ATM, whatever its last digits happen to share with a credit card.
        if (parsed.isAtmWithdrawal) {
            val cash = context.cashAccount
            return if (bank != null && cash != null && bank.id != cash.id) {
                SmsImportPlan.CashWithdrawal(from = bank, to = cash)
            } else {
                SmsImportPlan.BankDebit(
                    method = if (parsed.isUpi) PaymentMethod.UPI else PaymentMethod.DEBIT_CARD,
                    account = bank,
                    isCashWithdrawal = true
                )
            }
        }

        if (card != null) return SmsImportPlan.CardSpend(card, parsed.cardTail)
        return SmsImportPlan.BankDebit(methodFor(parsed), bank)
    }

    /**
     * Which of the user's cards a message is about.
     *
     * The last four digits decide it when they match a card. Without a match, a message
     * that says "credit card" goes to the one card it could be — the only card the user
     * has, or the only one from the bank the message names — and to none when that is
     * ambiguous. A debit card is never matched on digits alone, since a debit card and a
     * credit card can share their last four.
     */
    fun cardFor(parsed: ParsedSms, cards: List<CreditCard>): CreditCard? {
        val tail = parsed.cardTail
        if (tail != null && (parsed.isCreditCard || !parsed.isDebitCard)) {
            val byTail = cards.filter { it.lastFour() == tail }
            (byTail.firstOrNull { it.isActive } ?: byTail.firstOrNull())?.let { return it }
        }
        if (!parsed.isCreditCard) return null

        // A card whose digits are recorded and differ cannot be the card in the message.
        val possible = cards.filter { it.isActive && (tail == null || it.lastFour() == null) }
        val text = parsed.originalText.lowercase()
        val byBank = possible.filter { card ->
            val bankWord = card.bank.trim().substringBefore(' ').lowercase()
            bankWord.length >= 2 && text.contains(bankWord)
        }
        if (byBank.isNotEmpty()) return byBank.singleOrNull()

        // No card's bank is named. The only card there is gets the purchase, unless the
        // message names a different issuer, which would make it somebody else's card.
        val only = possible.singleOrNull() ?: return null
        if (tail != null) return null
        val namesAnIssuer = CARD_ISSUERS.any { text.contains(it) }
        return if (!namesAnIssuer || only.bank.isBlank()) only else null
    }

    /** Card issuers' names as they appear in alerts, for telling one bank's card from another's. */
    private val CARD_ISSUERS = listOf(
        "hdfc", "icici", "sbi", "axis", "kotak", "indusind", "idfc", "rbl", "yes bank",
        "american express", "amex", "citi", "hsbc", "standard chartered", "baroda",
        "federal", "au bank", "onecard"
    )

    /** How the money left, as the message describes it. */
    fun methodFor(parsed: ParsedSms): PaymentMethod = when {
        parsed.isUpi -> PaymentMethod.UPI
        parsed.isDebitCard || parsed.cardTail != null -> PaymentMethod.DEBIT_CARD
        parsed.isAutoDebit -> PaymentMethod.AUTO_DEBIT
        else -> PaymentMethod.BANK_TRANSFER
    }

    private fun planCredit(
        parsed: ParsedSms,
        on: LocalDate,
        bank: Account?,
        context: SmsReviewContext,
        receipts: Map<SourcePeriod, Int>
    ): SmsImportPlan {
        val amount = parsed.amount ?: return SmsImportPlan.OtherIncome(bank)
        val (source, due) = matchIncomeSource(amount, on, context.incomeSources)
            ?: return SmsImportPlan.OtherIncome(bank)
        val period = YearMonth.from(due)

        // Stamping a second receipt on a period already received would leave the forecast
        // counting one salary twice, so the credit is offered as plain income instead, and
        // left unticked for the user to decide whether it really is something extra.
        val expected = RecurrenceCalculator.occurrencesIn(
            month = period,
            start = source.startDate,
            end = source.endDate,
            dayOfMonth = source.dayOfMonth,
            frequency = source.frequency
        ).size.coerceAtLeast(1)
        if ((receipts[SourcePeriod(source.id, period)] ?: 0) >= expected) {
            return SmsImportPlan.OtherIncome(
                account = bank,
                caution = "${source.name} for ${monthLabel(period, on)} is already marked received"
            )
        }
        return SmsImportPlan.ScheduledIncome(source, period)
    }

    /**
     * The regular income a credit most likely is: within a tenth of the expected amount
     * and within five days of a due date, the closest amount winning a tie. Salaries move
     * a little with deductions and arrive early before holidays, but rarely by more.
     */
    fun matchIncomeSource(
        amount: Money,
        on: LocalDate,
        sources: List<IncomeSource>
    ): Pair<IncomeSource, LocalDate>? {
        data class Match(val source: IncomeSource, val due: LocalDate, val amountGap: Long, val dayGap: Long)

        return sources.asSequence()
            .filter { it.isActive && it.amount.isPositive && isCloseInAmount(amount, it.amount) }
            .mapNotNull { source ->
                val due = RecurrenceCalculator.nearestOccurrence(
                    around = on,
                    start = source.startDate,
                    end = source.endDate,
                    dayOfMonth = source.dayOfMonth,
                    frequency = source.frequency
                ) ?: return@mapNotNull null
                val dayGap = abs(ChronoUnit.DAYS.between(due, on))
                if (dayGap > INCOME_DATE_TOLERANCE_DAYS) return@mapNotNull null
                Match(source, due, (amount - source.amount).abs().paise, dayGap)
            }
            .minWithOrNull(compareBy<Match>({ it.amountGap }, { it.dayGap }))
            ?.let { it.source to it.due }
    }

    /** Within the tolerance, worked in whole paise so no rounding can decide it. */
    private fun isCloseInAmount(received: Money, expected: Money): Boolean =
        (received - expected).abs().paise * 100 <= expected.paise * INCOME_AMOUNT_TOLERANCE_PERCENT

    /**
     * How many receipts each regular income already has for each period, read the way the
     * forecast reads them: by the stamp when there is one, by the date when there is not.
     */
    fun receiptsByPeriod(transactions: List<IncomeTransaction>): Map<SourcePeriod, Int> =
        transactions
            .mapNotNull { transaction ->
                val sourceId = transaction.sourceId ?: return@mapNotNull null
                val period = transaction.periodKey?.let(::parsePeriodKey)
                    ?: YearMonth.from(transaction.date)
                SourcePeriod(sourceId, period)
            }
            .groupingBy { it }
            .eachCount()
}

/** One message waiting for the user's decision. */
data class SmsImportRow(
    val candidate: SmsCandidate,
    val plan: SmsImportPlan,
    val suggestedCategoryId: Long?,
    /** How sure the app is that this is something already recorded. */
    val duplicate: DuplicateVerdict,
    val isSelected: Boolean
) {
    val key: Long get() = candidate.smsId
    val amount: Money get() = candidate.parsed.amount ?: Money.ZERO
    val date: LocalDate get() = candidate.parsed.date ?: candidate.receivedOn
    val isDuplicate: Boolean get() = duplicate.isDuplicate
}

/**
 * Adds scanned or pasted messages to the review list.
 *
 * The list is one batch however it was filled. Pasting a message and then scanning the
 * inbox that holds it, or scanning the same week twice, must not offer the same money
 * twice, and a message the user already imported must say so rather than look new.
 */
object SmsReviewBuilder {

    data class Result(
        /** The whole list after the additions, existing rows first. */
        val rows: List<SmsImportRow>,
        val added: Int,
        /** Messages left out because the list already holds them. */
        val repeats: Int,
        /** Messages an earlier scan imported, added unticked. */
        val alreadyImported: Int
    )

    fun add(
        current: List<SmsImportRow>,
        incoming: List<SmsCandidate>,
        context: SmsReviewContext
    ): Result {
        val rows = current.toMutableList()
        val receipts = SmsImportPlanner.receiptsByPeriod(context.incomeTransactions).toMutableMap()
        current.forEach { row -> row.claimPeriod(receipts) }

        var added = 0
        var repeats = 0
        var alreadyImported = 0

        for (candidate in incoming) {
            val parsed = candidate.parsed
            if (!parsed.isTransaction) continue
            val amount = parsed.amount ?: continue
            val date = parsed.date ?: candidate.receivedOn

            if (rows.any { it.isSameMessageAs(candidate, date) }) {
                repeats++
                continue
            }

            val plan = SmsImportPlanner.plan(parsed, date, context, receipts)
            val verdict = verdictFor(candidate, plan, amount, date, rows, context)
            if (verdict == DuplicateVerdict.AlreadyImported) alreadyImported++

            val row = SmsImportRow(
                candidate = candidate,
                plan = plan,
                suggestedCategoryId = if (plan.writesExpense) {
                    suggestCategory(parsed, context.categories)
                } else {
                    null
                },
                duplicate = verdict,
                // Only something with no sign of being a duplicate is ready to go.
                isSelected = !verdict.isDuplicate && plan.caution == null
            )
            row.claimPeriod(receipts)
            rows += row
            added++
        }
        return Result(rows, added, repeats, alreadyImported)
    }

    /** A salary row waiting in the list has a claim on its month, like one already recorded. */
    private fun SmsImportRow.claimPeriod(receipts: MutableMap<SourcePeriod, Int>) {
        val scheduled = plan as? SmsImportPlan.ScheduledIncome ?: return
        receipts.merge(SourcePeriod(scheduled.source.id, scheduled.period), 1, Int::plus)
    }

    /**
     * True when [other] is a message already in the list.
     *
     * The inbox id settles it between two scans. Otherwise the same amount, day, direction
     * and counterparty is the same message when it came in through the other door — pasted
     * once and scanned once — or when the text is identical, as when a bank delivers one
     * alert twice. Two different alerts that merely look alike stay, flagged for a decision.
     */
    private fun SmsImportRow.isSameMessageAs(other: SmsCandidate, otherDate: LocalDate): Boolean {
        val mine = candidate
        if (mine.isFromInbox && other.isFromInbox && mine.smsId == other.smsId) return true

        val sameTransaction = mine.parsed.amount == other.parsed.amount &&
            date == otherDate &&
            mine.parsed.direction == other.parsed.direction &&
            mine.parsed.merchant.orEmpty().equals(other.parsed.merchant.orEmpty(), ignoreCase = true)
        if (!sameTransaction) return false

        return mine.source != other.source ||
            mine.parsed.originalText.normalisedText() == other.parsed.originalText.normalisedText()
    }

    private fun String.normalisedText(): String =
        trim().replace(Regex("""\s+"""), " ").lowercase()

    private fun verdictFor(
        candidate: SmsCandidate,
        plan: SmsImportPlan,
        amount: Money,
        date: LocalDate,
        rows: List<SmsImportRow>,
        context: SmsReviewContext
    ): DuplicateVerdict {
        if (candidate.isFromInbox && candidate.smsId in context.importedSmsIds) {
            return DuplicateVerdict.AlreadyImported
        }

        // Everything already in the list counts, from this paste or scan and every one
        // before it: the list is what will be imported, so that is the batch.
        val direction = candidate.parsed.direction
        val earlier = rows
            .filter { it.candidate.parsed.direction == direction }
            .map { it.amount to it.date }
        val merchant = candidate.parsed.merchant

        return when (plan) {
            is SmsImportPlan.CardSpend, is SmsImportPlan.BankDebit ->
                SmsDuplicateDetector.check(amount, date, merchant, context.expenses, earlier)
            is SmsImportPlan.CashWithdrawal -> SmsDuplicateDetector.strongest(
                SmsDuplicateDetector.check(amount, date, null, context.expenses, earlier),
                SmsDuplicateDetector.checkTransfer(amount, date, context.transfers)
            )
            is SmsImportPlan.CardBillPayment -> SmsDuplicateDetector.strongest(
                SmsDuplicateDetector.check(amount, date, null, context.expenses, earlier),
                SmsDuplicateDetector.checkCardPayment(amount, date, context.cardPayments)
            )
            is SmsImportPlan.ScheduledIncome, is SmsImportPlan.OtherIncome ->
                SmsDuplicateDetector.checkIncome(
                    amount, date, merchant, context.incomeTransactions, earlier
                )
        }
    }

    /**
     * Matches the message against the user's own categories first, then against everyday
     * words, so somebody who renamed a category keeps their own naming.
     */
    fun suggestCategory(parsed: ParsedSms, categories: List<Category>): Long? {
        val haystack = listOfNotNull(parsed.merchant, parsed.originalText)
            .joinToString(" ")
            .lowercase()

        categories.firstOrNull { haystack.contains(it.name.lowercase()) }?.let { return it.id }

        val hints = mapOf(
            "food" to listOf("swiggy", "zomato", "restaurant", "cafe", "hotel", "eatery"),
            "grocery" to listOf("bigbasket", "dmart", "blinkit", "zepto", "grocer", "kirana"),
            "travel" to listOf("uber", "ola", "irctc", "rapido", "metro", "indigo"),
            "fuel" to listOf("petrol", "fuel", "hpcl", "iocl", "bpcl", "shell"),
            "shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho"),
            "utilities" to listOf("electricity", "recharge", "broadband", "airtel", "jio")
        )
        hints.forEach { (categoryName, words) ->
            if (words.any { haystack.contains(it) }) {
                categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
                    ?.let { return it.id }
            }
        }
        return null
    }
}
