package com.moneyplanner.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.di.DefaultDispatcher
import com.moneyplanner.domain.calc.AccountBalances
import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.model.Account
import com.moneyplanner.domain.model.AccountTransfer
import com.moneyplanner.domain.model.AccountType
import com.moneyplanner.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Where the money actually sits.
 *
 * The dashboard answers "how much do I have"; this answers "and where is it", which is the
 * question that decides whether a payment will actually go through on Tuesday. Every figure
 * is derived from the same records as the overall balance, so the two can never disagree.
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val today: TodayProvider,
    snapshotRepository: SnapshotRepository,
    savedStateHandle: SavedStateHandle,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    private val _form = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = _form.asStateFlow()

    private val _transferForm = MutableStateFlow(TransferForm())
    val transferForm: StateFlow<TransferForm> = _transferForm.asStateFlow()

    init {
        // Arrived from the activity feed, which names a transfer to correct. The records
        // have to load first, since the wording is chosen from the accounts involved.
        val requested = savedStateHandle.get<String>(Routes.ARG_TRANSFER_ID)?.toLongOrNull()
        if (requested != null) {
            viewModelScope.launch {
                state.filterNot { it.isLoading }.first()
                    .transfers.firstOrNull { it.id == requested }
                    ?.let(::startEditingTransfer)
            }
        }
    }

    val state: StateFlow<AccountsState> = snapshotRepository.snapshot
        .map { snapshot ->
            AccountsState(
                balances = BalanceCalculator.accountBalances(snapshot),
                transfers = snapshot.transfers.sortedWith(
                    compareByDescending<AccountTransfer> { it.date }.thenByDescending { it.id }
                ),
                accountNames = snapshot.accounts.associate { it.id to it.name },
                isLoading = false
            )
        }
        .flowOn(computation)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccountsState()
        )

    // ---- Add or edit an account ----------------------------------------------------

    fun startAdding() {
        _form.value = AccountForm(isOpen = true)
    }

    fun startEditing(account: Account) {
        _form.value = AccountForm(
            isOpen = true,
            editingId = account.id,
            name = account.name,
            type = account.type,
            openingBalanceText = account.openingBalance.toPlainText(),
            openingDate = account.openingDate
        )
    }

    fun dismissForm() {
        _form.value = AccountForm()
    }

    fun updateName(value: String) = _form.update { it.copy(name = value, nameError = null) }
    fun updateType(value: AccountType) = _form.update { it.copy(type = value) }
    fun updateOpeningBalance(value: String) =
        _form.update { it.copy(openingBalanceText = value, amountError = null) }

    fun updateOpeningDate(value: LocalDate) = _form.update { it.copy(openingDate = value) }

    fun saveAccount() {
        val current = _form.value
        if (current.name.isBlank()) {
            _form.update { it.copy(nameError = "Give this account a name") }
            return
        }
        // Blank is allowed and means zero; anything else has to be a real number, because
        // an unparseable opening balance would silently become zero and quietly misstate
        // every balance derived from it afterwards.
        val opening = if (current.openingBalanceText.isBlank()) {
            Money.ZERO
        } else {
            Money.parseOrNull(current.openingBalanceText) ?: run {
                _form.update { it.copy(amountError = "Enter a valid amount") }
                return
            }
        }

        viewModelScope.launch {
            val account = Account(
                id = current.editingId ?: 0,
                name = current.name.trim(),
                type = current.type,
                openingBalance = opening,
                openingDate = current.openingDate
            )
            if (current.editingId == null) {
                profileRepository.addAccount(account)
            } else {
                profileRepository.updateAccount(account)
            }
            _form.value = AccountForm()
        }
    }

    /**
     * Archives rather than deletes.
     *
     * Expenses point at the account they were paid from. Removing the row would either
     * break those references or take the history with it, and a closed bank account is
     * still where last year's rent actually went.
     */
    fun archive(account: Account) {
        viewModelScope.launch {
            profileRepository.updateAccount(account.copy(isArchived = true))
        }
    }

    // ---- Transfers -----------------------------------------------------------------

    fun startTransfer() {
        _transferForm.value = TransferForm(isOpen = true, date = today.today())
    }

    /**
     * Taking cash out of the bank, and putting it back in.
     *
     * Both are ordinary transfers between two of the user's own accounts, and are offered
     * as their own actions because that is not how anyone thinks of them. Nobody
     * "transfers to their cash account" at an ATM, they withdraw; naming the action the
     * way the user names it is the difference between a feature being found and not.
     *
     * A withdrawal is emphatically not an expense. The money has moved, not gone, and
     * recording it as spending would understate the balance until the cash was spent and
     * then double count it when it was.
     */
    fun startWithdrawal() = startBetween(AccountType.BANK, AccountType.CASH)

    fun startDeposit() = startBetween(AccountType.CASH, AccountType.BANK)

    private fun startBetween(from: AccountType, to: AccountType) {
        val accounts = state.value.accounts
        _transferForm.value = TransferForm(
            isOpen = true,
            fromId = accounts.firstOrNull { it.type == from }?.id,
            toId = accounts.firstOrNull { it.type == to }?.id,
            date = today.today(),
            mode = if (from == AccountType.BANK) TransferMode.WITHDRAW else TransferMode.DEPOSIT
        )
    }

    /**
     * Opens the sheet on a transfer already recorded.
     *
     * The wording follows the accounts involved rather than defaulting to the generic
     * "move money": a bank to cash correction is still a withdrawal to the person making
     * it, and calling it something else at the point of correction is disorienting.
     */
    fun startEditingTransfer(transfer: AccountTransfer) {
        val byId = state.value.accounts.associateBy { it.id }
        val from = byId[transfer.fromAccountId]?.type
        val to = byId[transfer.toAccountId]?.type

        _transferForm.value = TransferForm(
            isOpen = true,
            editingId = transfer.id,
            fromId = transfer.fromAccountId,
            toId = transfer.toAccountId,
            amountText = transfer.amount.toPlainText(),
            date = transfer.date,
            notes = transfer.notes,
            mode = when {
                from == AccountType.BANK && to == AccountType.CASH -> TransferMode.WITHDRAW
                from == AccountType.CASH && to == AccountType.BANK -> TransferMode.DEPOSIT
                else -> TransferMode.MOVE
            }
        )
    }

    fun dismissTransfer() {
        _transferForm.value = TransferForm()
    }

    fun updateTransferFrom(id: Long) = _transferForm.update { it.copy(fromId = id, error = null) }
    fun updateTransferTo(id: Long) = _transferForm.update { it.copy(toId = id, error = null) }
    fun updateTransferAmount(value: String) =
        _transferForm.update { it.copy(amountText = value, error = null) }

    fun updateTransferDate(value: LocalDate) = _transferForm.update { it.copy(date = value) }
    fun updateTransferNotes(value: String) = _transferForm.update { it.copy(notes = value) }

    fun saveTransfer() {
        val current = _transferForm.value
        val amount = Money.parseOrNull(current.amountText)
        val from = current.fromId
        val to = current.toId

        val error = when {
            from == null || to == null -> "Choose both accounts"
            from == to -> "Choose two different accounts"
            amount == null || !amount.isPositive -> "Enter an amount greater than zero"
            else -> null
        }
        if (error != null) {
            _transferForm.update { it.copy(error = error) }
            return
        }

        viewModelScope.launch {
            val editingId = current.editingId
            if (editingId != null) {
                profileRepository.updateTransfer(
                    AccountTransfer(
                        id = editingId,
                        fromAccountId = from!!,
                        toAccountId = to!!,
                        amount = amount!!,
                        date = current.date,
                        notes = current.notes.trim()
                    )
                )
            } else {
                profileRepository.transfer(
                    fromAccountId = from!!,
                    toAccountId = to!!,
                    amount = amount!!,
                    on = current.date,
                    notes = current.notes.trim()
                )
            }
            _transferForm.value = TransferForm()
        }
    }

    fun deleteTransfer(id: Long) {
        viewModelScope.launch { profileRepository.deleteTransfer(id) }
    }
}

/** Renders an amount as plain editable text, sign included. */
private fun Money.toPlainText(): String {
    val sign = if (paise < 0) "-" else ""
    val absolute = kotlin.math.abs(paise)
    val rupees = absolute / 100
    val remainder = absolute % 100
    return if (remainder == 0L) "$sign$rupees"
    else "$sign$rupees.${remainder.toString().padStart(2, '0')}"
}

data class AccountsState(
    val balances: AccountBalances? = null,
    val transfers: List<AccountTransfer> = emptyList(),
    val accountNames: Map<Long, String> = emptyMap(),
    val isLoading: Boolean = true
) {
    val accounts: List<Account> get() = balances?.rows.orEmpty().map { it.account }
    val hasTwoAccounts: Boolean get() = accounts.size >= 2
}

data class AccountForm(
    val isOpen: Boolean = false,
    val editingId: Long? = null,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val openingBalanceText: String = "",
    val openingDate: LocalDate = LocalDate.now(),
    val nameError: String? = null,
    val amountError: String? = null
) {
    val isEditing: Boolean get() = editingId != null
}

data class TransferForm(
    val isOpen: Boolean = false,
    /** Set when correcting a transfer already recorded, rather than adding one. */
    val editingId: Long? = null,
    val fromId: Long? = null,
    val toId: Long? = null,
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val notes: String = "",
    val error: String? = null,
    val mode: TransferMode = TransferMode.MOVE
) {
    val isEditing: Boolean get() = editingId != null
}

/**
 * What the user thinks they are doing. All three write the same transfer; only the
 * wording and the pre-filled direction differ.
 */
enum class TransferMode(val title: String, val editTitle: String, val action: String) {
    MOVE("Move money", "Edit transfer", "Move"),
    WITHDRAW("Withdraw cash", "Edit withdrawal", "Withdraw"),
    DEPOSIT("Deposit cash", "Edit deposit", "Deposit")
}
