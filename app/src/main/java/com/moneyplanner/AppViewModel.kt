package com.moneyplanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.data.prefs.ThemeMode
import com.moneyplanner.data.repo.ProfileRepository
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.di.IoDispatcher
import com.moneyplanner.work.ReminderScheduler
import com.moneyplanner.widget.BalanceWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Application level state: the theme, whether the lock screen is needed, the one-time
 * setup that has to happen before the first screen is drawn, and keeping the home screen
 * widget in step with the data while the app is open.
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
    private val reminderScheduler: ReminderScheduler,
    @IoDispatcher private val io: CoroutineDispatcher
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
            keepWidgetCurrent()
        }
    }

    /**
     * Repaints the home screen widget from every fresh snapshot for as long as the app is open.
     *
     * The widget has no network refresh, so this is what keeps its balance aligned with an
     * expense, income or transfer. It starts only once seeding has finished, so the first
     * picture is of the user's data rather than of a database not yet set up, and the
     * settings are an input alongside the snapshot so that turning the app lock on hides the
     * balance at once rather than at the next change to the data.
     *
     * It collects until the ViewModel is cleared, which is why it is the last thing the
     * start-up coroutine does. With no widget placed it collects nothing at all: every
     * snapshot is a read of the whole ledger, and paying for one on every change to paint
     * a widget that does not exist was pure waste. Placing one starts it straight away.
     */
    private suspend fun keepWidgetCurrent() {
        BalanceWidgetProvider.placements(context)
            .flowOn(io)
            .collectLatest { placed ->
                if (!placed) return@collectLatest
                combine(snapshotRepository.snapshot, settingsStore.settings, ::Pair)
                    .collect { (snapshot, settings) ->
                        // Painting talks to the widget service over binder and works out
                        // the balance over the whole history, neither of which belongs on
                        // the main thread.
                        withContext(io) {
                            // A widget that cannot be painted keeps its last picture, and
                            // that is never worth taking the app down over.
                            runCatching {
                                BalanceWidgetProvider.render(context, snapshot, settings)
                            }
                        }
                    }
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
