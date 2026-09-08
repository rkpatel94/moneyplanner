package com.moneyplanner.ui.screens.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.repo.CategoryRepository
import com.moneyplanner.data.repo.ExpenseRepository
import com.moneyplanner.data.repo.IncomeRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.data.sms.SmsCandidate
import com.moneyplanner.data.sms.SmsInboxReader
import com.moneyplanner.data.sms.SmsScanResult
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.IncomeTransaction
import com.moneyplanner.domain.model.IncomeType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.nlp.BankSmsParser
import com.moneyplanner.domain.nlp.SpokenExpenseParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reviewing bank alerts before they become records.
 *
 * Every candidate is opt-in. Anything that looks like it has already been entered by hand
 * is flagged and left unselected, because a duplicated expense is worse than a missing one:
 * a missing entry is visibly absent, while a duplicate quietly makes the balance wrong.
 */
@HiltViewModel
class SmsImportViewModel @Inject constructor(
    private val smsInboxReader: SmsInboxReader,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val today: TodayProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SmsImportState())
    val state: StateFlow<SmsImportState> = _state.asStateFlow()

    fun refreshPermission() {
        _state.update { it.copy(hasPermission = smsInboxReader.hasPermission()) }
    }

    fun scan() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isScanning = true,
                    scanError = null,
                    hasPermission = smsInboxReader.hasPermission()
                )
            }

            val result = smsInboxReader.readRecent()

            val candidates = when (result) {
                is SmsScanResult.Scanned -> result.candidates
                SmsScanResult.PermissionMissing -> {
                    _state.update {
                        it.copy(isScanning = false, hasPermission = false, hasScanned = false)
                    }
                    return@launch
                }
                is SmsScanResult.Failed -> {
                    _state.update {
                        it.copy(
                            isScanning = false,
                            hasScanned = true,
                            scanError = "Your messages could not be read (${result.reason}). " +
                                "You can still paste a message instead."
                        )
                    }
                    return@launch
                }
            }

            val categories = categoryRepository.expenseCategories.first()
            val existing = expenseRepository.all.first()

            val rows = candidates.map { candidate ->
                val amount = candidate.parsed.amount ?: Money.ZERO
                val date = candidate.parsed.date ?: candidate.receivedOn

                // An expense on the same day for the same amount is almost certainly the
                // same transaction already entered by hand.
                val duplicate = existing.any { it.amount == amount && it.date == date }

                SmsImportRow(
                    candidate = candidate,
                    suggestedCategoryId = suggestCategory(candidate, categories),
                    isDuplicate = duplicate,
                    // Duplicates start unselected; everything else is ready to import.
                    isSelected = !duplicate
                )
            }

            _state.update {
                it.copy(
                    rows = rows,
                    categories = categories,
                    isScanning = false,
                    hasScanned = true,
                    scanError = null,
                    messagesInspected = (result as SmsScanResult.Scanned).messagesInspected,
                    messagesFromBanks = result.messagesFromBanks
                )
            }
        }
    }

    /**
     * Guesses a category from the merchant name by reusing the voice parser, so "SWIGGY"
     * lands on Food through exactly the same synonym table the rest of the app uses.
     */
    private fun suggestCategory(candidate: SmsCandidate, categories: List<Category>): Long? {
        val merchant = candidate.parsed.merchant ?: return null
        return SpokenExpenseParser.parse(
            spoken = "0 $merchant",
            categories = categories,
            today = today.today()
        ).categoryId
    }

    fun toggle(smsId: Long) {
        _state.update { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (row.candidate.smsId == smsId) row.copy(isSelected = !row.isSelected) else row
                }
            )
        }
    }

    fun setCategory(smsId: Long, categoryId: Long) {
        _state.update { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (row.candidate.smsId == smsId) {
                        row.copy(suggestedCategoryId = categoryId)
                    } else {
                        row
                    }
                }
            )
        }
    }

    fun selectAll(selected: Boolean) {
        _state.update { current ->
            current.copy(rows = current.rows.map { it.copy(isSelected = selected) })
        }
    }

    /**
     * Writes the selected rows. Debits become expenses and credits become income receipts,
     * so a salary alert does not land in the ledger as negative spending.
     */
    fun importSelected(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val selected = _state.value.rows.filter { it.isSelected }
            val fallbackCategory = _state.value.categories.firstOrNull()

            var imported = 0
            selected.forEach { row ->
                val parsed = row.candidate.parsed
                val amount = parsed.amount ?: return@forEach
                val date = parsed.date ?: row.candidate.receivedOn

                if (parsed.isDebit) {
                    val categoryId = row.suggestedCategoryId ?: fallbackCategory?.id ?: return@forEach
                    expenseRepository.add(
                        Expense(
                            id = 0,
                            amount = amount,
                            description = parsed.suggestedDescription(),
                            categoryId = categoryId,
                            date = date,
                            paymentMethod = PaymentMethod.BANK_TRANSFER,
                            notes = "Imported from an SMS alert"
                        )
                    )
                } else {
                    incomeRepository.addTransaction(
                        IncomeTransaction(
                            id = 0,
                            sourceId = null,
                            name = parsed.suggestedDescription(),
                            type = IncomeType.OTHER,
                            amount = amount,
                            date = date,
                            accountId = null,
                            periodKey = null,
                            notes = "Imported from an SMS alert"
                        )
                    )
                }
                imported++
            }

            _state.update { current ->
                current.copy(rows = current.rows.filterNot { it.isSelected })
            }
            onDone(imported)
        }
    }

    /**
     * Parses a single pasted message.
     *
     * This path needs no permission at all, so someone who would rather not grant SMS
     * access can still avoid typing an amount by hand.
     */
    fun parsePasted(text: String) {
        viewModelScope.launch {
            val parsed = BankSmsParser.parse(text, today.today())
            val categories = categoryRepository.expenseCategories.first()

            if (!parsed.isTransaction) {
                _state.update {
                    it.copy(
                        pastedError = parsed.ignoredReason
                            ?: "That does not look like a transaction alert.",
                        pastedRow = null
                    )
                }
                return@launch
            }

            val candidate = SmsCandidate(
                smsId = -1L,
                sender = "Pasted",
                receivedOn = today.today(),
                parsed = parsed
            )
            _state.update {
                it.copy(
                    pastedError = null,
                    pastedRow = SmsImportRow(
                        candidate = candidate,
                        suggestedCategoryId = suggestCategory(candidate, categories),
                        isDuplicate = false,
                        isSelected = true
                    ),
                    categories = categories
                )
            }
        }
    }

    fun importPasted(onDone: (Int) -> Unit) {
        val row = _state.value.pastedRow ?: return
        _state.update { it.copy(rows = it.rows + row, pastedRow = null) }
        importSelected(onDone)
    }

    fun clearPasted() {
        _state.update { it.copy(pastedRow = null, pastedError = null) }
    }
}

data class SmsImportRow(
    val candidate: SmsCandidate,
    val suggestedCategoryId: Long?,
    /** True when an expense with the same amount and date already exists. */
    val isDuplicate: Boolean,
    val isSelected: Boolean
)

data class SmsImportState(
    val rows: List<SmsImportRow> = emptyList(),
    val categories: List<Category> = emptyList(),
    val hasPermission: Boolean = false,
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    /** Set when the inbox could not be read at all, as opposed to holding nothing. */
    val scanError: String? = null,
    val messagesInspected: Int = 0,
    val messagesFromBanks: Int = 0,
    val pastedRow: SmsImportRow? = null,
    val pastedError: String? = null
) {
    val selectedCount: Int get() = rows.count { it.isSelected }
    val duplicateCount: Int get() = rows.count { it.isDuplicate }

    /**
     * Why an empty result was empty, so the screen can say something useful instead of
     * "nothing found" three different ways.
     */
    val emptyExplanation: String?
        get() = when {
            !hasScanned || rows.isNotEmpty() || scanError != null -> null
            messagesInspected == 0 ->
                "No messages at all in the last 30 days."
            messagesFromBanks == 0 ->
                "Looked at $messagesInspected messages, but none came from a bank " +
                    "shortcode. Alerts sent from an ordinary phone number are skipped."
            else ->
                "Read $messagesFromBanks bank messages, but none looked like a debit or " +
                    "credit that is not already recorded."
        }
}
