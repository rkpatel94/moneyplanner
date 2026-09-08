package com.moneyplanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.data.prefs.ThemeMode
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.work.ReminderScheduler
import com.moneyplanner.widget.BalanceWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application level state: the theme, whether the lock screen is needed, and the one-time
 * setup that has to happen before the first screen is drawn.
 *
 * Seeding runs here rather than in a database callback so it is an ordinary suspending
 * call that can be reasoned about, and so a failure surfaces as an ordinary error rather
 * than as a crash inside Room's initialisation.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val profileRepository: ProfileRepository,
    private val snapshotRepository: SnapshotRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val started = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            profileRepository.seedIfEmpty()

            val settings = settingsStore.settings.first()

            // Cash became an account of its own after the first releases. An install made
            // before that has only a bank account, so it is given one here, once.
            if (!settings.cashAccountAdded) {
                profileRepository.ensureCashAccount()
                settingsStore.markCashAccountAdded()
            }

            if (settings.remindersEnabled) {
                reminderScheduler.scheduleDailyCheck(settings.reminderHour)
            }
            started.value = true
            BalanceWidgetProvider.refresh(context)
        }
        // The widget has no network refresh. Repainting it when the authoritative
        // snapshot changes keeps its balance aligned with an expense, income or transfer.
        viewModelScope.launch {
            snapshotRepository.snapshot.collect { BalanceWidgetProvider.refresh(context) }
        }
    }

    val state: StateFlow<AppState> = combine(
        settingsStore.settings,
        started
    ) { settings, ready ->
        AppState(
            themeMode = settings.themeMode,
            requiresUnlock = settings.appLockEnabled && settings.hasPin,
            needsOnboarding = !settings.onboarded,
            remindersEnabled = settings.remindersEnabled,
            isLoading = !ready
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppState()
    )
}

data class AppState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val requiresUnlock: Boolean = false,
    /** True until first-run setup has been completed or skipped. */
    val needsOnboarding: Boolean = false,
    val remindersEnabled: Boolean = true,
    val isLoading: Boolean = true
)
