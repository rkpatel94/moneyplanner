package com.moneyplanner.ui.screens.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.data.repo.CategoryRepository
import com.moneyplanner.data.repo.CreditCardRepository
import com.moneyplanner.data.repo.ExpenseRepository
import com.moneyplanner.data.repo.IncomeRepository
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.data.sms.ImportedSmsIds
import com.moneyplanner.data.sms.SmsCandidate
import com.moneyplanner.data.sms.SmsInboxReader
import com.moneyplanner.data.sms.SmsScanPeriod
import com.moneyplanner.data.sms.SmsScanResult
import com.moneyplanner.data.sms.SmsScanWindow
import com.moneyplanner.data.sms.SmsSource
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.IncomeTransaction
import com.moneyplanner.domain.model.IncomeType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.nlp.BankSmsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Turning bank alerts into entries, from the SMS inbox or from text the user pastes in.
 *
 * Scanning reads the inbox, which needs `READ_SMS`. The permission is asked for only when
 * the user taps Scan, and nothing here reads a message before then: opening the screen
 * reads only when the last scan happened. Anyone who declines keeps pasting, which needs
 * no permission and feeds the same review list. Play treats SMS access as restricted, so
 * distributing this build there needs a permissions declaration; sideloading does not.
 *
 * Several messages can be pasted at once, separated by blank lines, which is how a batch
 * actually arrives when someone catches up on a week.
 *
 * Nothing imports without being seen. Anything that looks like something already recorded
 * arrives unticked and says why: a missed import is visibly absent and gets fixed, while a
 * duplicate is invisible and quietly makes every balance downstream wrong. The review list
 * is one batch however it was filled, so a message pasted and then scanned, or scanned
 * twice, is offered once.
 */
@HiltViewModel
class SmsImportViewModel @Inject constructor(
    private val smsInboxReader: SmsInboxReader,
    private val settingsStore: SettingsStore,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val profileRepository: ProfileRepository,
    private val creditCardRepository: CreditCardRepository,
    private val today: TodayProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SmsImportState())
    val state: StateFlow<SmsImportState> = _state.asStateFlow()

    /**
     * Held while the list is being added to or imported from. A scan landing in the middle
     * of an import would otherwise check its messages against records from before the
     * import, and offer again the very rows that were just saved.
     */
    private val reviewLock = Mutex()

    /** Pasted messages count down from -1, so their ids can never meet a real inbox id. */
    private var nextPastedId = -1L

    init {
        // Only the time of the last scan is read here. The inbox is never touched until
        // the user asks for a scan.
        viewModelScope.launch {
            settingsStore.settings
                .map { it.lastSmsScanEpochMillis }
                .distinctUntilChanged()
                .collect { millis ->
                    _state.update { it.copy(lastScanAt = millis?.let(Instant::ofEpochMilli)) }
                }
        }
    }

    fun selectPeriod(period: SmsScanPeriod) = _state.update { it.copy(period = period) }

    /**
     * The user turned the permission down.
     *
     * @param canAskAgain false once the system will no longer show the request, which is
     *        when the only way left is the app's page in system settings.
     */
    fun onPermissionDenied(canAskAgain: Boolean) = _state.update {
        it.copy(isScanning = false, permissionDenied = true, canAskPermissionAgain = canAskAgain)
    }

    /** Reads the inbox for the chosen period. Only ever called once the permission is held. */
    fun scan() {
        if (_state.value.isScanning) return
        _state.update {
            it.copy(isScanning = true, scanError = null, scanSummary = null, permissionDenied = false)
        }

        viewModelScope.launch {
            try {
                val startedAt = Instant.now()
                val period = _state.value.period
                val lastScan = settingsStore.settings.first().lastSmsScanEpochMillis
                    ?.let(Instant::ofEpochMilli)
                val since = SmsScanWindow.startFor(period, lastScan, startedAt)

                when (val result = smsInboxReader.readSince(since)) {
                    SmsScanResult.PermissionMissing -> _state.update {
                        it.copy(permissionDenied = true, canAskPermissionAgain = true)
                    }
                    is SmsScanResult.Failed -> _state.update {
                        it.copy(
                            scanError = "Your messages could not be read. ${result.reason} " +
                                "You can still paste a message below."
                        )
                    }
                    is SmsScanResult.Scanned -> {
                        // The start of this scan, not its end, so a message arriving while
                        // it ran is still ahead of the next one.
                        settingsStore.recordSmsScan(startedAt.toEpochMilli())
                        val outcome = addToReview(result.candidates)
                        _state.update {
                            it.copy(
                                hasScanned = true,
                                scanSummary = scanSummary(result, outcome, period, since)
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update {
                    it.copy(scanError = "The scan stopped part way. You can try again, or paste a message below.")
                }
            } finally {
                _state.update { it.copy(isScanning = false) }
            }
        }
    }

    private fun scanSummary(
        result: SmsScanResult.Scanned,
        outcome: SmsReviewBuilder.Result,
        period: SmsScanPeriod,
        since: Instant
    ): String {
        val window = when (period.days) {
            null -> "since ${SCAN_TIME.format(since.atZone(ZoneId.systemDefault()))}"
            else -> "from the last ${period.days} days"
        }
        val found = result.candidates.size
        return when {
            result.messagesInspected == 0 -> "No messages arrived $window."
            result.messagesFromBanks == 0 ->
                "Read ${count(result.messagesInspected, "message")} $window. None came from a bank."
            found == 0 ->
                "Read ${count(result.messagesFromBanks, "bank message")} $window. " +
                    "None of them was a payment or a credit."
            else -> buildString {
                append("Found ${count(found, "transaction")} in ")
                append("${count(result.messagesFromBanks, "bank message")} $window.")
                if (outcome.repeats > 0) append(" ${outcome.repeats} already in the list below.")
                if (outcome.alreadyImported > 0) {
                    append(" ${outcome.alreadyImported} imported before, left unticked.")
                }
            }
        }
    }

    /**
     * Parses everything in the pasted text.
     *
     * Messages are separated by a blank line, which is what pasting several in a row
     * naturally produces. A single message with no blank line in it is the ordinary case
     * and still works.
     */
    fun parsePasted(text: String) {
        viewModelScope.launch {
            val blocks = text.split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (blocks.isEmpty()) {
                _state.update { it.copy(pastedError = "Paste a bank message first.") }
                return@launch
            }

            val now = today.today()
            var rejected = 0
            var lastReason: String? = null
            val candidates = mutableListOf<SmsCandidate>()

            blocks.forEach { block ->
                val parsed = BankSmsParser.parse(block, now)
                if (!parsed.isTransaction) {
                    rejected++
                    lastReason = parsed.ignoredReason
                    return@forEach
                }
                candidates += SmsCandidate(
                    smsId = nextPastedId--,
                    sender = PASTED_SENDER,
                    receivedOn = now,
                    parsed = parsed,
                    source = SmsSource.PASTED
                )
            }

            val outcome = addToReview(candidates)

            _state.update { current ->
                current.copy(
                    hasParsed = true,
                    lastAccepted = outcome.added,
                    lastRejected = rejected,
                    pastedError = when {
                        outcome.added > 0 -> null
                        outcome.repeats > 0 && rejected == 0 ->
                            if (outcome.repeats == 1) "That message is already in the list."
                            else "Those messages are already in the list."
                        rejected == 1 -> lastReason
                            ?: "That does not look like a transaction alert."
                        else -> "None of those looked like transaction alerts."
                    }
                )
            }
        }
    }

    private suspend fun addToReview(candidates: List<SmsCandidate>): SmsReviewBuilder.Result =
        reviewLock.withLock {
            val context = loadReviewContext()
            var outcome = SmsReviewBuilder.Result(_state.value.rows, 0, 0, 0)
            // Built inside the update, so a tick or a category picked while the records
            // were loading is part of the list the new rows join rather than overwritten.
            _state.update { current ->
                outcome = SmsReviewBuilder.add(current.rows, candidates, context)
                current.copy(rows = outcome.rows, categories = context.categories)
            }
            outcome
        }

    private suspend fun loadReviewContext() = SmsReviewContext(
        categories = categoryRepository.expenseCategories.first(),
        expenses = expenseRepository.all.first(),
        incomeTransactions = incomeRepository.transactions.first(),
        transfers = profileRepository.transfers.first(),
        cardPayments = creditCardRepository.payments.first(),
        accounts = profileRepository.accounts.first(),
        creditCards = creditCardRepository.all.first(),
        incomeSources = incomeRepository.sources.first(),
        importedSmsIds = ImportedSmsIds.parse(settingsStore.settings.first().importedSmsIds)
    )

    fun toggle(id: Long) {
        _state.update { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (row.key == id) row.copy(isSelected = !row.isSelected) else row
                }
            )
        }
    }

    fun setCategory(id: Long, categoryId: Long) {
        _state.update { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (row.key == id) row.copy(suggestedCategoryId = categoryId) else row
                }
            )
        }
    }

    fun selectAll(selected: Boolean) {
        _state.update { current ->
            current.copy(rows = current.rows.map { it.copy(isSelected = selected) })
        }
    }

    fun remove(id: Long) {
        _state.update { current ->
            current.copy(rows = current.rows.filterNot { it.key == id })
        }
    }

    fun clearAll() {
        _state.update {
            it.copy(
                rows = emptyList(),
                pastedError = null,
                hasParsed = false,
                scanSummary = null,
                importError = null
            )
        }
    }

    /**
     * Saves every ticked row as the record its plan names.
     *
     * Runs to the end even if the screen is closed part way, because the ids of what was
     * saved have to be remembered with it: a half-finished import that forgot them would
     * offer the same messages again as if they were new.
     */
    fun importSelected(onDone: (Int) -> Unit) {
        if (_state.value.isImporting) return
        _state.update { it.copy(isImporting = true, importError = null) }

        viewModelScope.launch {
            val imported = withContext(NonCancellable) {
                try {
                    reviewLock.withLock { importTickedRows() }
                } finally {
                    _state.update { it.copy(isImporting = false) }
                }
            }
            onDone(imported)
        }
    }

    private suspend fun importTickedRows(): Int {
        val selected = _state.value.rows.filter { it.isSelected }
        val categories = _state.value.categories
        // An expense needs a category. One left unpicked goes to "Other" rather than to
        // whichever category happens to sort first.
        val fallbackCategoryId = categories.firstOrNull { it.name.equals("Other", ignoreCase = true) }?.id
            ?: categories.firstOrNull()?.id

        val saved = mutableListOf<SmsImportRow>()
        var failed = 0
        selected.forEach { row ->
            val ok = try {
                write(row, fallbackCategoryId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                false
            }
            if (ok) saved += row else failed++
        }

        // If the ids cannot be written the records still stand, and a later scan still
        // flags these messages through the duplicate check, just less certainly.
        val inboxIds = saved.filter { it.candidate.isFromInbox }.map { it.candidate.smsId }
        if (inboxIds.isNotEmpty()) {
            runCatching { settingsStore.rememberImportedSms(inboxIds) }
        }

        // Only what was actually saved leaves the list; anything that failed stays, still
        // ticked, so it can be tried again rather than vanishing unrecorded.
        val savedKeys = saved.mapTo(HashSet()) { it.key }
        _state.update { current ->
            current.copy(
                rows = current.rows.filterNot { it.key in savedKeys },
                isImporting = false,
                importError = when (failed) {
                    0 -> null
                    1 -> "One entry could not be saved and is still in the list."
                    else -> "$failed entries could not be saved and are still in the list."
                }
            )
        }
        return saved.size
    }

    private suspend fun write(row: SmsImportRow, fallbackCategoryId: Long?): Boolean {
        val parsed = row.candidate.parsed
        val amount = parsed.amount ?: return false
        val date = row.date

        when (val plan = row.plan) {
            is SmsImportPlan.CardSpend -> {
                val categoryId = row.suggestedCategoryId ?: fallbackCategoryId ?: return false
                // No account: a card purchase leaves the bank only when the bill is paid.
                expenseRepository.add(
                    Expense(
                        id = 0,
                        amount = amount,
                        description = parsed.suggestedDescription(),
                        categoryId = categoryId,
                        date = date,
                        paymentMethod = PaymentMethod.CREDIT_CARD,
                        accountId = null,
                        creditCardId = plan.card?.id,
                        notes = IMPORT_NOTE
                    )
                )
            }
            is SmsImportPlan.BankDebit -> {
                val categoryId = row.suggestedCategoryId ?: fallbackCategoryId ?: return false
                expenseRepository.add(
                    Expense(
                        id = 0,
                        amount = amount,
                        description = parsed.suggestedDescription(),
                        categoryId = categoryId,
                        date = date,
                        paymentMethod = plan.method,
                        accountId = plan.account?.id,
                        notes = IMPORT_NOTE
                    )
                )
            }
            is SmsImportPlan.CashWithdrawal -> {
                profileRepository.transfer(
                    fromAccountId = plan.from.id,
                    toAccountId = plan.to.id,
                    amount = amount,
                    on = date,
                    notes = "Cash withdrawal, imported from an SMS alert"
                ) ?: return false
            }
            is SmsImportPlan.CardBillPayment -> {
                // The card as it is now, since the payment is taken off its current figure.
                val card = creditCardRepository.getById(plan.card.id) ?: return false
                creditCardRepository.payBill(
                    card = card,
                    amount = amount,
                    paidOn = date,
                    accountId = plan.account?.id,
                    notes = IMPORT_NOTE
                )
            }
            is SmsImportPlan.ScheduledIncome -> {
                // Through the same call as "mark received", so the receipt carries the
                // income's id and the period it settles, and the forecast stops expecting it.
                val source = incomeRepository.getSource(plan.source.id) ?: plan.source
                incomeRepository.markSourceReceived(source, amount, receivedOn = date)
            }
            is SmsImportPlan.OtherIncome -> {
                incomeRepository.addTransaction(
                    IncomeTransaction(
                        id = 0,
                        sourceId = null,
                        name = parsed.suggestedDescription(),
                        type = IncomeType.OTHER,
                        amount = amount,
                        date = date,
                        accountId = plan.account?.id,
                        periodKey = null,
                        notes = IMPORT_NOTE
                    )
                )
            }
        }
        return true
    }

    private companion object {
        const val IMPORT_NOTE = "Imported from an SMS alert"
        const val PASTED_SENDER = "Pasted"
        val SCAN_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

        fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
    }
}

data class SmsImportState(
    val rows: List<SmsImportRow> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** How far back the next scan reads. */
    val period: SmsScanPeriod = SmsScanPeriod.SINCE_LAST_SCAN,
    val lastScanAt: Instant? = null,
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    /** What the last scan read and found, so an empty list is never left unexplained. */
    val scanSummary: String? = null,
    val scanError: String? = null,
    val permissionDenied: Boolean = false,
    val canAskPermissionAgain: Boolean = true,
    val isImporting: Boolean = false,
    val importError: String? = null,
    val hasParsed: Boolean = false,
    /** How the last paste went, so the screen can say what happened to it. */
    val lastAccepted: Int = 0,
    val lastRejected: Int = 0,
    val pastedError: String? = null
) {
    val selectedCount: Int get() = rows.count { it.isSelected }
    val duplicateCount: Int get() = rows.count { it.isDuplicate }

    /** Set when a paste held messages that were not transactions, so it can be explained. */
    val skippedNote: String?
        get() = when {
            lastRejected == 0 -> null
            lastAccepted == 0 -> null
            lastRejected == 1 -> "One message was not a transaction and was skipped."
            else -> "$lastRejected messages were not transactions and were skipped."
        }

    /** What the chosen period will read, in words, shown under the period chips. */
    val periodDescription: String
        get() {
            val days = period.days
            if (days != null) return "Reads the bank messages from the last $days days."
            val now = Instant.now()
            val last = lastScanAt?.takeUnless { it.isAfter(now) }
                ?: return "This is your first scan, so it reads the last " +
                    "${SmsScanWindow.FIRST_SCAN_DAYS} days."
            val longest = now.minus(Duration.ofDays(SmsScanWindow.LONGEST_DAYS))
            return if (last.minus(SmsScanWindow.OVERLAP).isBefore(longest)) {
                "Your last scan was over ${SmsScanWindow.LONGEST_DAYS} days ago, so this reads " +
                    "the last ${SmsScanWindow.LONGEST_DAYS} days."
            } else {
                "Reads what arrived since your last scan, " +
                    "${PERIOD_TIME.format(last.atZone(ZoneId.systemDefault()))}."
            }
        }

    private companion object {
        val PERIOD_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
    }
}
