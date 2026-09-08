package com.moneyplanner.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.core.money.Money
import com.moneyplanner.data.backup.AutoBackupManager
import com.moneyplanner.data.backup.BackupManager
import com.moneyplanner.data.backup.ExportResult
import com.moneyplanner.data.backup.TransactionExporter
import com.moneyplanner.data.backup.RestoreResult
import com.moneyplanner.data.prefs.AppSettings
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.data.prefs.ThemeMode
import com.moneyplanner.data.repo.CategoryRemoval
import com.moneyplanner.data.repo.CategoryRepository
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.ResetRepository
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.data.repo.TodayProvider
import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.model.Category
import com.moneyplanner.domain.model.CategoryType
import com.moneyplanner.domain.model.UserProfile
import com.moneyplanner.work.AutoBackupScheduler
import com.moneyplanner.work.ReminderScheduler
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import java.time.LocalDate

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val profileRepository: ProfileRepository,
    private val backupManager: BackupManager,
    private val transactionExporter: TransactionExporter,
    private val reminderScheduler: ReminderScheduler,
    private val autoBackupManager: AutoBackupManager,
    private val autoBackupScheduler: AutoBackupScheduler,
    private val resetRepository: ResetRepository,
    private val today: TodayProvider,
    snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        settingsStore.settings,
        snapshotRepository.snapshot
    ) { settings, snapshot ->
        SettingsState(
            settings = settings,
            profile = snapshot.profile,
            currentBalance = BalanceCalculator.currentBalance(snapshot),
            backupFolderName = autoBackupManager.folderDisplayName(settings.backupFolderUri),
            isLoading = false
        )
    }
        .flowOn(computation)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsState()
    )

    private val _events = MutableStateFlow<SettingsEvent?>(null)
    val events: StateFlow<SettingsEvent?> = _events.asStateFlow()

    private val _isResetting = MutableStateFlow(false)
    val isResetting: StateFlow<Boolean> = _isResetting.asStateFlow()

    fun clearEvent() {
        _events.value = null
    }

    fun updateName(name: String) {
        viewModelScope.launch {
            profileRepository.saveProfile(state.value.profile.copy(displayName = name))
        }
    }

    /**
     * Records the balance the user says they actually have.
     *
     * The difference from the computed balance is stored as a visible correction rather
     * than by rewriting history, so the number stays explainable afterwards.
     */
    fun setCurrentBalance(text: String) {
        val stated = Money.parseOrNull(text) ?: return
        viewModelScope.launch {
            profileRepository.reconcileBalance(
                computedBalance = state.value.currentBalance,
                statedBalance = stated
            )
            _events.value = SettingsEvent.Message("Balance updated.")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setForecastMonths(months: Int) {
        viewModelScope.launch { settingsStore.setForecastMonths(months) }
    }

    fun setEmergencyMonths(months: Int) {
        viewModelScope.launch {
            profileRepository.saveProfile(state.value.profile.copy(emergencyFundMonths = months))
        }
    }

    // ---- Notifications ----------------------------------------------------------

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setReminders(enabled = enabled)
            if (enabled) {
                reminderScheduler.scheduleDailyCheck(state.value.settings.reminderHour)
            } else {
                reminderScheduler.cancel()
            }
        }
    }

    fun setReminderOption(
        daysBefore: Int? = null,
        hour: Int? = null,
        emis: Boolean? = null,
        bills: Boolean? = null,
        cards: Boolean? = null,
        people: Boolean? = null,
        weeklySummary: Boolean? = null
    ) {
        viewModelScope.launch {
            settingsStore.setReminders(
                daysBefore = daysBefore,
                hour = hour,
                emis = emis,
                bills = bills,
                cards = cards,
                people = people,
                weeklySummary = weeklySummary
            )
            if (hour != null && state.value.settings.remindersEnabled) {
                reminderScheduler.scheduleDailyCheck(hour)
            }
        }
    }

    // ---- Security ---------------------------------------------------------------

    fun setPin(pin: String) {
        viewModelScope.launch {
            settingsStore.setPin(pin)
            _events.value = SettingsEvent.Message("App lock is on.")
        }
    }

    fun disableAppLock() {
        viewModelScope.launch {
            settingsStore.setAppLockEnabled(false)
            _events.value = SettingsEvent.Message("App lock is off.")
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setBiometricEnabled(enabled) }
    }

    // ---- Backup and export ------------------------------------------------------

    fun exportBackup() {
        viewModelScope.launch {
            runCatching { backupManager.exportJson(today.today()) }
                .onSuccess { _events.value = SettingsEvent.ShareFile(it, "application/json") }
                .onFailure {
                    _events.value = SettingsEvent.Message("The backup could not be created.")
                }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            runCatching { backupManager.exportExpensesCsv(today.today()) }
                .onSuccess { _events.value = SettingsEvent.ShareFile(it, "text/csv") }
                .onFailure {
                    _events.value = SettingsEvent.Message("The export could not be created.")
                }
        }
    }

    // ---- Excel export ------------------------------------------------------------

    private val _excelRange = MutableStateFlow(ExcelExportRange())
    val excelRange: StateFlow<ExcelExportRange> = _excelRange.asStateFlow()

    /** Opens the range picker on the month so far, which is what people usually want. */
    fun startExcelExport() {
        val now = today.today()
        _excelRange.value = ExcelExportRange(
            isOpen = true,
            from = now.withDayOfMonth(1),
            to = now
        )
    }

    fun dismissExcelExport() {
        _excelRange.value = ExcelExportRange()
    }

    fun updateExcelFrom(value: LocalDate) = _excelRange.update { it.copy(from = value) }

    fun updateExcelTo(value: LocalDate) = _excelRange.update { it.copy(to = value) }

    /** Jumps the range to a whole period, so the common cases take one tap. */
    fun useExcelPreset(preset: ExcelRangePreset) {
        val now = today.today()
        _excelRange.update {
            when (preset) {
                ExcelRangePreset.THIS_MONTH -> it.copy(from = now.withDayOfMonth(1), to = now)
                ExcelRangePreset.LAST_MONTH -> {
                    val previous = now.minusMonths(1)
                    it.copy(
                        from = previous.withDayOfMonth(1),
                        to = previous.withDayOfMonth(previous.lengthOfMonth())
                    )
                }
                ExcelRangePreset.THIS_YEAR -> it.copy(from = now.withDayOfYear(1), to = now)
                ExcelRangePreset.EVERYTHING ->
                    // Wide enough to hold any record the user could have entered, without
                    // needing a query for the earliest date.
                    it.copy(from = LocalDate.of(2000, 1, 1), to = now.plusYears(5))
            }
        }
    }

    fun exportExcel() {
        val range = _excelRange.value
        _excelRange.value = ExcelExportRange()
        viewModelScope.launch {
            _events.value = when (val result = transactionExporter.export(range.from, range.to)) {
                is ExportResult.Success -> SettingsEvent.ShareFile(
                    result.file,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
                is ExportResult.Empty ->
                    SettingsEvent.Message("Nothing was recorded between those dates.")
                is ExportResult.Failure -> SettingsEvent.Message(result.message)
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            _events.value = when (val result = backupManager.importJson(uri)) {
                is RestoreResult.Success ->
                    SettingsEvent.Message("Your backup has been restored.")
                is RestoreResult.Failure -> SettingsEvent.Message(result.message)
            }
        }
    }

    // ---- Automatic backup -------------------------------------------------------

    /**
     * Remembers the folder the user picked and starts the weekly job.
     *
     * Taking the persistable grant is what lets a background job write there days later
     * without the app ever holding a storage permission.
     */
    fun setBackupFolder(uri: Uri) {
        viewModelScope.launch {
            autoBackupManager.persistFolderAccess(uri)
            settingsStore.setBackupFolder(uri.toString())
            settingsStore.setAutoBackupEnabled(true)
            autoBackupScheduler.schedule()
            _events.value = SettingsEvent.Message("Automatic backup is on.")
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoBackupEnabled(enabled)
            if (enabled) autoBackupScheduler.schedule() else autoBackupScheduler.cancel()
        }
    }

    /** Runs a backup immediately, so the user can confirm the folder actually works. */
    fun backupNow() {
        viewModelScope.launch {
            val result = autoBackupManager.runBackup(today.today())
            _events.value = SettingsEvent.Message(result.message())
        }
    }

    fun shareableUri(file: File): Uri = backupManager.shareableUri(file)

    // ---- Starting again ---------------------------------------------------------

    /**
     * Wipes the records, and for a complete reset the settings too.
     *
     * The flag is held across the call so the dialog can disable its own buttons: a second
     * tap part-way through would start a second wipe against a database the first is still
     * clearing.
     */
    fun performReset(mode: com.moneyplanner.ui.screens.settings.ResetMode) {
        viewModelScope.launch {
            _isResetting.value = true
            runCatching {
                if (mode == com.moneyplanner.ui.screens.settings.ResetMode.RECORDS) {
                    resetRepository.resetRecords()
                } else {
                    resetRepository.factoryReset()
                }
            }.onSuccess {
                _events.value = SettingsEvent.Message(
                    if (mode == com.moneyplanner.ui.screens.settings.ResetMode.RECORDS) {
                        "Your records have been cleared."
                    } else {
                        "The app has been reset."
                    }
                )
            }.onFailure {
                _events.value = SettingsEvent.Message("Nothing was deleted — the reset failed.")
            }
            _isResetting.value = false
        }
    }
}

data class SettingsState(
    val settings: AppSettings = AppSettings(),
    val profile: UserProfile = UserProfile(),
    val currentBalance: Money = Money.ZERO,
    /** Readable name of the chosen backup folder, or null when none is set. */
    val backupFolderName: String? = null,
    val isLoading: Boolean = true
)

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class ShareFile(val file: File, val mimeType: String) : SettingsEvent
}

/** Managing the category list. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val state: StateFlow<CategoriesState> = categoryRepository.everything
        .map { categories ->
            CategoriesState(
                expense = categories.filter { it.type == CategoryType.EXPENSE },
                income = categories.filter { it.type == CategoryType.INCOME },
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoriesState()
        )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun add(name: String, type: CategoryType, isEssential: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = categoryRepository.add(
                name = name,
                type = type,
                colorHex = pickColour(name),
                iconKey = "other",
                isEssential = isEssential
            )
            if (id <= 0L) _message.value = "A category with that name already exists."
        }
    }

    fun toggleEssential(category: Category) {
        viewModelScope.launch {
            categoryRepository.update(category.copy(isEssential = !category.isEssential))
        }
    }

    fun remove(category: Category) {
        viewModelScope.launch {
            when (val result = categoryRepository.removeOrArchive(category)) {
                is CategoryRemoval.Deleted ->
                    _message.value = "${category.name} was removed."
                is CategoryRemoval.Archived ->
                    _message.value = "${category.name} is used by ${result.expenseCount} " +
                        "expenses, so it was hidden instead of deleted."
            }
        }
    }

    fun restore(category: Category) {
        viewModelScope.launch {
            categoryRepository.update(category.copy(isArchived = false))
        }
    }

    /** A stable colour per name, so the same category always looks the same. */
    private fun pickColour(name: String): String {
        val palette = listOf(
            "#FFE8833A", "#FF3F9E5A", "#FF3B7DD8", "#FFD9534F", "#FFB05FD6",
            "#FF2E7D6B", "#FF4C8DAE", "#FF5C6BC0", "#FFE05252", "#FFEC6FA1",
            "#FF8D6E63", "#FF7A5AF8", "#FF1E88A8", "#FF2FA36B", "#FFD1913C"
        )
        val index = (name.lowercase().hashCode().toLong() and 0xFFFFFFFFL) % palette.size
        return palette[index.toInt()]
    }
}

data class CategoriesState(
    val expense: List<Category> = emptyList(),
    val income: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

/** The date range for an Excel export, and whether the picker is showing. */
data class ExcelExportRange(
    val isOpen: Boolean = false,
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now()
) {
    val isValid: Boolean get() = !to.isBefore(from)
}

enum class ExcelRangePreset(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    THIS_YEAR("This year"),
    EVERYTHING("Everything")
}
