package com.moneyplanner.data.repo

import androidx.room.withTransaction
import com.moneyplanner.data.backup.BackupManager
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.prefs.SettingsStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starting over.
 *
 * Two different things get called "reset", and conflating them is how people lose more
 * than they meant to:
 *
 *  - [resetRecords] empties the ledger but leaves the app set up. The PIN still works, the
 *    theme is unchanged, reminders keep their schedule. For someone who was trying the app
 *    out with made-up figures and now wants to start properly.
 *  - [factoryReset] additionally forgets every setting and sends the user back through
 *    onboarding, leaving the install indistinguishable from a fresh one. For handing the
 *    phone on, or genuinely starting again.
 *
 * Both offer a backup first, and both wipe inside a single transaction so a failure
 * half-way through leaves the records as they were rather than partly deleted.
 */
@Singleton
class ResetRepository @Inject constructor(
    private val database: AppDatabase,
    private val backupManager: BackupManager,
    private val profileRepository: ProfileRepository,
    private val settingsStore: SettingsStore,
    private val today: TodayProvider
) {

    /**
     * Writes a backup of everything before it is destroyed.
     *
     * Offered rather than silently skipped because a reset is the one action in the app
     * with nothing behind it: there is no undo, and the records cannot be reconstructed
     * from anywhere else. Returns null when the export fails, which the caller surfaces
     * so the user can decide whether to go ahead without one.
     */
    suspend fun backupBeforeReset(): File? =
        runCatching { backupManager.exportJson(today.today()) }.getOrNull()

    /**
     * Empties the ledger and puts the starting structure back.
     *
     * Re-seeding matters: an install with no categories and no account cannot record an
     * expense at all, so wiping without it would leave the app looking broken rather than
     * empty.
     */
    suspend fun resetRecords() {
        database.withTransaction { database.maintenanceDao().clearEverything() }
        profileRepository.seedIfEmpty()
    }

    /**
     * Everything [resetRecords] does, plus every setting the app remembers.
     *
     * The PIN goes with it. Leaving a lock on a database that no longer holds the data it
     * was protecting only locks the next person out of an empty app.
     */
    suspend fun factoryReset() {
        database.withTransaction { database.maintenanceDao().clearEverything() }
        settingsStore.clearAll()
        profileRepository.seedIfEmpty()
    }
}
