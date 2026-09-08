package com.moneyplanner.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Application preferences.
 *
 * Only settings live here. Financial records are kept in the database, and the one
 * security-sensitive value stored is the salted hash of the app lock PIN, never the PIN
 * itself, so the stored value cannot be used to unlock the app if the file is read.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val PIN_LOCKED_UNTIL = longPreferencesKey("pin_locked_until")

        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMIND_DAYS_BEFORE = intPreferencesKey("remind_days_before")
        val REMIND_HOUR = intPreferencesKey("remind_hour")
        val REMIND_EMI = booleanPreferencesKey("remind_emi")
        val REMIND_BILLS = booleanPreferencesKey("remind_bills")
        val REMIND_CARDS = booleanPreferencesKey("remind_cards")
        val REMIND_PEOPLE = booleanPreferencesKey("remind_people")
        val REMIND_WEEKLY_SUMMARY = booleanPreferencesKey("remind_weekly_summary")

        val FORECAST_MONTHS = intPreferencesKey("forecast_months")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDED = booleanPreferencesKey("onboarded")

        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val LAST_BACKUP_EPOCH_DAY = intPreferencesKey("last_backup_epoch_day")
        val BACKUP_KEEP_COUNT = intPreferencesKey("backup_keep_count")

        val CASH_ACCOUNT_ADDED = booleanPreferencesKey("cash_account_added")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            hasPin = !prefs[Keys.PIN_HASH].isNullOrBlank(),
            remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: true,
            remindDaysBefore = prefs[Keys.REMIND_DAYS_BEFORE] ?: 2,
            reminderHour = prefs[Keys.REMIND_HOUR] ?: 9,
            remindAboutEmis = prefs[Keys.REMIND_EMI] ?: true,
            remindAboutBills = prefs[Keys.REMIND_BILLS] ?: true,
            remindAboutCards = prefs[Keys.REMIND_CARDS] ?: true,
            remindAboutPeople = prefs[Keys.REMIND_PEOPLE] ?: true,
            weeklySummary = prefs[Keys.REMIND_WEEKLY_SUMMARY] ?: true,
            forecastMonths = prefs[Keys.FORECAST_MONTHS] ?: 12,
            themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE]),
            onboarded = prefs[Keys.ONBOARDED] ?: false,
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
            backupFolderUri = prefs[Keys.BACKUP_FOLDER_URI],
            lastBackupEpochDay = prefs[Keys.LAST_BACKUP_EPOCH_DAY]?.toLong(),
            backupKeepCount = prefs[Keys.BACKUP_KEEP_COUNT] ?: 5,
            cashAccountAdded = prefs[Keys.CASH_ACCOUNT_ADDED] ?: false
        )
    }

    suspend fun setAppLockEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.APP_LOCK_ENABLED] = enabled
        if (!enabled) {
            it.remove(Keys.PIN_HASH)
            it.remove(Keys.PIN_SALT)
            it.remove(Keys.PIN_FAILED_ATTEMPTS)
            it.remove(Keys.PIN_LOCKED_UNTIL)
            it[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.BIOMETRIC_ENABLED] = enabled
    }

    suspend fun setPin(pin: String) {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        context.dataStore.edit {
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hash
            it[Keys.APP_LOCK_ENABLED] = true
            it.remove(Keys.PIN_FAILED_ATTEMPTS)
            it.remove(Keys.PIN_LOCKED_UNTIL)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val salt = prefs[Keys.PIN_SALT] ?: return false
        val stored = prefs[Keys.PIN_HASH] ?: return false
        return PinHasher.verify(pin, salt, stored)
    }

    /**
     * How many wrong PINs in a row, and until when the input is barred.
     *
     * Kept here rather than in the unlock screen's own state because a counter that lives
     * only in memory is no throttle at all: force-stopping the app would reset it, and
     * guessing could carry on at full speed. Stored on disk it survives that, so the wait
     * has to actually be waited out.
     */
    val lockoutState: Flow<PinLockoutState> = context.dataStore.data.map { prefs ->
        PinLockoutState(
            failedAttempts = prefs[Keys.PIN_FAILED_ATTEMPTS] ?: 0,
            lockedUntilEpochMillis = prefs[Keys.PIN_LOCKED_UNTIL] ?: 0L
        )
    }

    suspend fun recordFailedPinAttempt(now: Long, attemptsBeforeLockout: Int, lockoutMillis: Long) =
        context.dataStore.edit { prefs ->
            val attempts = (prefs[Keys.PIN_FAILED_ATTEMPTS] ?: 0) + 1
            prefs[Keys.PIN_FAILED_ATTEMPTS] = attempts
            if (attempts >= attemptsBeforeLockout) {
                prefs[Keys.PIN_LOCKED_UNTIL] = now + lockoutMillis
            }
        }

    suspend fun clearFailedPinAttempts() = context.dataStore.edit { prefs ->
        prefs.remove(Keys.PIN_FAILED_ATTEMPTS)
        prefs.remove(Keys.PIN_LOCKED_UNTIL)
    }

    suspend fun setReminders(
        enabled: Boolean? = null,
        daysBefore: Int? = null,
        hour: Int? = null,
        emis: Boolean? = null,
        bills: Boolean? = null,
        cards: Boolean? = null,
        people: Boolean? = null,
        weeklySummary: Boolean? = null
    ) = context.dataStore.edit { prefs ->
        enabled?.let { prefs[Keys.REMINDERS_ENABLED] = it }
        daysBefore?.let { prefs[Keys.REMIND_DAYS_BEFORE] = it.coerceIn(0, 14) }
        hour?.let { prefs[Keys.REMIND_HOUR] = it.coerceIn(0, 23) }
        emis?.let { prefs[Keys.REMIND_EMI] = it }
        bills?.let { prefs[Keys.REMIND_BILLS] = it }
        cards?.let { prefs[Keys.REMIND_CARDS] = it }
        people?.let { prefs[Keys.REMIND_PEOPLE] = it }
        weeklySummary?.let { prefs[Keys.REMIND_WEEKLY_SUMMARY] = it }
    }

    suspend fun setForecastMonths(months: Int) = context.dataStore.edit {
        it[Keys.FORECAST_MONTHS] = months.coerceIn(3, 24)
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit {
        it[Keys.THEME_MODE] = mode.name
    }

    suspend fun setOnboarded(value: Boolean) = context.dataStore.edit {
        it[Keys.ONBOARDED] = value
    }

    /**
     * Remembers the folder the user picked for automatic backups.
     *
     * Storing the tree URI rather than a path is what lets the app write there later
     * without ever asking for a storage permission: the grant travels with the URI.
     */
    suspend fun setBackupFolder(uri: String?) = context.dataStore.edit {
        if (uri == null) {
            it.remove(Keys.BACKUP_FOLDER_URI)
            it[Keys.AUTO_BACKUP_ENABLED] = false
        } else {
            it[Keys.BACKUP_FOLDER_URI] = uri
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.AUTO_BACKUP_ENABLED] = enabled
    }

    suspend fun setBackupKeepCount(count: Int) = context.dataStore.edit {
        it[Keys.BACKUP_KEEP_COUNT] = count.coerceIn(1, 30)
    }

    suspend fun recordBackupRun(epochDay: Long) = context.dataStore.edit {
        it[Keys.LAST_BACKUP_EPOCH_DAY] = epochDay.toInt()
    }

    /**
     * Forgets every setting, returning the app to how it behaves on a fresh install.
     *
     * Clearing the whole store rather than removing known keys one at a time, so a setting
     * added later cannot be left behind by an update to this method that nobody remembers
     * to make.
     */
    /**
     * Records that the one-time cash account top-up has run, so it never runs again.
     */
    suspend fun markCashAccountAdded() = context.dataStore.edit {
        it[Keys.CASH_ACCOUNT_ADDED] = true
    }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}

/** Wrong-PIN state, persisted so a restart cannot wipe the throttle. */
data class PinLockoutState(
    val failedAttempts: Int = 0,
    val lockedUntilEpochMillis: Long = 0L
) {
    fun isLockedAt(now: Long): Boolean = now < lockedUntilEpochMillis
    fun remainingMillisAt(now: Long): Long = (lockedUntilEpochMillis - now).coerceAtLeast(0L)
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

data class AppSettings(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val hasPin: Boolean = false,
    val remindersEnabled: Boolean = true,
    val remindDaysBefore: Int = 2,
    val reminderHour: Int = 9,
    val remindAboutEmis: Boolean = true,
    val remindAboutBills: Boolean = true,
    val remindAboutCards: Boolean = true,
    val remindAboutPeople: Boolean = true,
    val weeklySummary: Boolean = true,
    val forecastMonths: Int = 12,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboarded: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    /** Tree URI of the folder chosen for backups, or null if none has been picked. */
    val backupFolderUri: String? = null,
    val lastBackupEpochDay: Long? = null,
    /** How many backup files to keep before the oldest is removed. */
    val backupKeepCount: Int = 5,
    /**
     * Whether the install has already been given a cash account. Only ever used to stop
     * the one-time top-up repeating; it says nothing about whether the account still exists.
     */
    val cashAccountAdded: Boolean = false
) {
    val canAutoBackup: Boolean get() = autoBackupEnabled && backupFolderUri != null
}
