package com.moneyplanner.ui.screens.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Unlock state for the app lock.
 *
 * After several wrong attempts the input is disabled for a short period. The delay is
 * deliberately modest: it makes repeated guessing impractical without punishing somebody
 * who genuinely mistyped their own PIN a few times.
 *
 * The count and the deadline live on disk rather than in this object. Held in memory they
 * would reset every time the view model was recreated, so force-stopping the app between
 * tries would have made the throttle free to skip and left the PIN open to being guessed
 * at full speed.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    @ApplicationContext context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(
        LockState(biometricAvailable = canUseBiometrics(context))
    )
    val state: StateFlow<LockState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _state.update { it.copy(biometricEnabled = settings.biometricEnabled) }
            }
        }
        viewModelScope.launch {
            settingsStore.lockoutState.collect { lockout ->
                _state.update {
                    it.copy(
                        failedAttempts = lockout.failedAttempts,
                        lockedUntilEpochMillis = lockout.lockedUntilEpochMillis
                    )
                }
            }
        }
        // Keeps the countdown honest while the screen is open, so the field re-enables
        // itself the moment the wait is over rather than on the next keystroke.
        viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(nowEpochMillis = System.currentTimeMillis()) }
                delay(500)
            }
        }
    }

    fun updatePin(value: String) {
        val digits = value.filter { it.isDigit() }.take(MAX_PIN_LENGTH)
        _state.update { it.copy(pinInput = digits, error = null) }
    }

    fun verifyPin() {
        val current = _state.value
        if (current.isLockedOut) return

        viewModelScope.launch {
            if (settingsStore.verifyPin(current.pinInput)) {
                settingsStore.clearFailedPinAttempts()
                _state.update { it.copy(isUnlocked = true, error = null, pinInput = "") }
            } else {
                settingsStore.recordFailedPinAttempt(
                    now = System.currentTimeMillis(),
                    attemptsBeforeLockout = ATTEMPTS_BEFORE_LOCKOUT,
                    lockoutMillis = LOCKOUT_MILLIS
                )
                _state.update {
                    it.copy(
                        pinInput = "",
                        nowEpochMillis = System.currentTimeMillis(),
                        error = if (it.failedAttempts + 1 >= ATTEMPTS_BEFORE_LOCKOUT) {
                            "Too many attempts. Wait a moment and try again."
                        } else {
                            "That PIN is not correct."
                        }
                    )
                }
            }
        }
    }

    /** Biometric success is a real authentication, so it clears the throttle too. */
    fun unlock() {
        viewModelScope.launch { settingsStore.clearFailedPinAttempts() }
        _state.update { it.copy(isUnlocked = true, error = null) }
    }

    private fun canUseBiometrics(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    companion object {
        private const val MAX_PIN_LENGTH = 8
        const val ATTEMPTS_BEFORE_LOCKOUT = 5
        const val LOCKOUT_MILLIS = 30_000L
    }
}

data class LockState(
    val pinInput: String = "",
    val error: String? = null,
    val failedAttempts: Int = 0,
    val lockedUntilEpochMillis: Long = 0L,
    val nowEpochMillis: Long = 0L,
    val isUnlocked: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false
) {
    /** Driven by the stored deadline, not by a count this object happens to be holding. */
    val isLockedOut: Boolean get() = nowEpochMillis < lockedUntilEpochMillis

    val lockoutSecondsRemaining: Int
        get() = ((lockedUntilEpochMillis - nowEpochMillis).coerceAtLeast(0L) / 1000L).toInt()
}
