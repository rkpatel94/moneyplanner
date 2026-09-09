package com.moneyplanner.ui.screens.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.data.repo.CategoryRepository
import com.moneyplanner.data.repo.ExpenseRepository
import com.moneyplanner.data.repo.IncomeRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.data.sms.SmsCandidate
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.Expense
import com.moneyplanner.domain.model.IncomeTransaction
import com.moneyplanner.domain.model.IncomeType
import com.moneyplanner.domain.model.PaymentMethod
import com.moneyplanner.domain.nlp.BankSmsParser
import com.moneyplanner.domain.nlp.DuplicateVerdict
import com.moneyplanner.domain.nlp.SmsDuplicateDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Turning bank alerts into entries, from text the user pastes in.
 *
 * Reading the inbox was removed rather than kept behind a permission prompt. `READ_SMS` is
 * a restricted permission that Play grants almost exclusively to default SMS handler apps,
 * so it was a distribution blocker for a feature that works without it; and an app whose
 * whole claim is that it holds your money data and can send nothing anywhere is stronger
 * for not asking to read your messages at all.
 *
 * Pasting costs a copy and a tap and gives the user exactly the control the review list was
 * always there to provide. Several messages can be pasted at once, separated by blank
 * lines, which is how a batch actually arrives when someone catches up on a week.
 *
 * Nothing imports without being seen. Anything that looks like something already recorded
 * arrives unticked and says why: a missed import is visibly absent and gets fixed, while a
 * duplicate is invisible and quietly makes every balance downstream wrong.
 */
@HiltViewModel
class SmsImportViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val today: TodayProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SmsImportState())
    val state: StateFlow<SmsImportState> = _state.asStateFlow()

    private var nextId = 1L

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

            val categories = categoryRepository.expenseCategories.first()
            val existing = expenseRepository.all.first()
            val now = today.today()

            val accepted = mutableListOf<SmsImportRow>()
            val seen = mutableListOf<Pair<com.moneyplanner.core.money.Money, java.time.LocalDate>>()
            var rejected = 0
            var lastReason: String? = null

            blocks.forEach { block ->
                val parsed = BankSmsParser.parse(block, now)
                if (!parsed.isTransaction) {
                    rejected++
                    lastReason = parsed.ignoredReason
                    return@forEach
                }

                val amount = parsed.amount ?: return@forEach
                val date = parsed.date ?: now

                val verdict = SmsDuplicateDetector.check(
                    amount = amount,
                    date = date,
                    merchant = parsed.merchant,
                    existing = existing,
                    earlierInBatch = seen
                )
                seen += amount to date

                val candidate = SmsCandidate(
                    smsId = nextId++,
                    sender = "Pasted",
                    receivedOn = now,
                    parsed = parsed
                )
                accepted += SmsImportRow(
                    candidate = candidate,
                    suggestedCategoryId = suggestCategory(candidate, categories),
                    duplicate = verdict,
                    // Only something with no sign of being a duplicate is ready to go.
                    isSelected = !verdict.isDuplicate
                )
            }

            _state.update { current ->
                current.copy(
                    rows = current.rows + accepted,
                    categories = categories,
                    hasParsed = true,
                    lastAccepted = accepted.size,
                    lastRejected = rejected,
                    pastedError = when {
                        accepted.isNotEmpty() -> null
                        rejected == 1 -> lastReason
                            ?: "That does not look like a transaction alert."
                        else -> "None of those looked like transaction alerts."
                    }
                )
            }
        }
    }

    /**
     * Matches the message against the user's own categories first, then against everyday
     * words, so somebody who renamed a category keeps their own naming.
     */
    private fun suggestCategory(candidate: SmsCandidate, categories: List<Category>): Long? {
        val haystack = listOfNotNull(
            candidate.parsed.merchant,
            candidate.parsed.originalText
        ).joinToString(" ").lowercase()

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

    fun toggle(id: Long) {
        _state.update { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (row.candidate.smsId == id) row.copy(isSelected = !row.isSelected) else row
                }
            )
        }
    }

    fun setCategory(id: Long, categoryId: Long) {
        _state.update { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (row.candidate.smsId == id) {
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

    fun remove(id: Long) {
        _state.update { current ->
            current.copy(rows = current.rows.filterNot { it.candidate.smsId == id })
        }
    }

    fun clearAll() {
        _state.update {
            it.copy(rows = emptyList(), pastedError = null, hasParsed = false)
        }
    }

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
                    val categoryId = row.suggestedCategoryId
                        ?: fallbackCategory?.id
                        ?: return@forEach
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
}

data class SmsImportRow(
    val candidate: SmsCandidate,
    val suggestedCategoryId: Long?,
    /** How sure the app is that this is something already recorded. */
    val duplicate: DuplicateVerdict,
    val isSelected: Boolean
) {
    val isDuplicate: Boolean get() = duplicate.isDuplicate
}

data class SmsImportState(
    val rows: List<SmsImportRow> = emptyList(),
    val categories: List<Category> = emptyList(),
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
}
