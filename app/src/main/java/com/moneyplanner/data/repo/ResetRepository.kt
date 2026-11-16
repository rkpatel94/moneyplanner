package com.moneyplanner.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.moneyplanner.data.backup.AutoBackupManager
import com.moneyplanner.data.backup.BackupManager
import com.moneyplanner.data.db.AppDatabase
import com.moneyplanner.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
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
 *
 * Everything after the wipe happens once the records are already gone, so neither throws.
 * Each reports a [ResetOutcome] instead, which keeps "nothing was deleted" for the one case
 * where it is true: the wipe itself did not commit.
 */
@Singleton
class ResetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val backupManager: BackupManager,
    private val autoBackupManager: AutoBackupManager,
    private val profileRepository: ProfileRepository,
    private val settingsStore: SettingsStore,
    private val today: TodayProvider
) {

    private companion object {
        /**
         * Where every export and backup file is written before it is shared: the backup
         * JSON and expenses CSV from [BackupManager], the Excel workbook and the PDF. It is
         * the cache-path named in res/xml/file_paths.xml, and has to stay in step with it.
         */
        const val EXPORTS_DIRECTORY = "exports"
    }

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
     *
     * The files earlier exports left in the cache go too. Each is a copy of the ledger, or
     * of a slice of it, so clearing the records and keeping those would not have cleared
     * the records at all.
     */
    suspend fun resetRecords(): ResetOutcome {
        if (!wipe()) return ResetOutcome.NothingDeleted

        val unfinished = buildList {
            if (!attempt { deleteExportFiles() }) add(ResetStep.EXPORT_FILES)
            if (!attempt { profileRepository.seedIfEmpty() }) add(ResetStep.STARTING_DATA)
        }
        return outcomeOf(unfinished)
    }

    /**
     * Everything [resetRecords] does, plus every setting the app remembers.
     *
     * The PIN goes with it. Leaving a lock on a database that no longer holds the data it
     * was protecting only locks the next person out of an empty app.
     *
     * So does the app's access to the backup folder. Clearing the settings forgets which
     * folder it was, and a grant nobody remembers is still a grant: the app could go on
     * writing into somebody's folder after a reset meant to leave no trace of it.
     */
    suspend fun factoryReset(): ResetOutcome {
        // Read before anything is cleared, because the settings are the only record of
        // which folder the grant belongs to.
        val backupFolder = runCatching { settingsStore.settings.first().backupFolderUri }
            .getOrNull()

        if (!wipe()) return ResetOutcome.NothingDeleted

        val unfinished = buildList {
            if (!attempt { deleteExportFiles() }) add(ResetStep.EXPORT_FILES)
            // Never reported: a grant that cannot be released is one the app no longer holds.
            attempt { autoBackupManager.releaseFolderAccess(backupFolder) }
            if (!attempt { settingsStore.clearAll() }) add(ResetStep.SETTINGS)
            if (!attempt { profileRepository.seedIfEmpty() }) add(ResetStep.STARTING_DATA)
        }
        return outcomeOf(unfinished)
    }

    /** Empties every table in one transaction. False when it did not commit, and so changed nothing. */
    private suspend fun wipe(): Boolean =
        attempt { database.withTransaction { database.maintenanceDao().clearEverything() } }

    private fun deleteExportFiles() {
        val directory = File(context.cacheDir, EXPORTS_DIRECTORY)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IOException("Some exported files could not be deleted")
        }
    }

    /**
     * Runs one step and says whether it worked, so a failure in one step does not stop the
     * ones after it. Cancellation is not a failure of the step and is passed on.
     */
    private inline fun attempt(step: () -> Unit): Boolean =
        try {
            step()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            false
        }

    private fun outcomeOf(unfinished: List<ResetStep>): ResetOutcome =
        if (unfinished.isEmpty()) ResetOutcome.Done else ResetOutcome.Unfinished(unfinished)
}

/** What a reset managed to do, so the message can say exactly that. */
sealed interface ResetOutcome {
    /** Every step went through. */
    data object Done : ResetOutcome

    /** The wipe did not commit, so every record is exactly where it was. */
    data object NothingDeleted : ResetOutcome

    /**
     * The wipe committed, so the records are gone, but [steps] failed after it. Running the
     * reset again finishes them: the wipe has nothing left to remove, and the rest is safe
     * to repeat.
     */
    data class Unfinished(val steps: List<ResetStep>) : ResetOutcome
}

/** The parts of a reset that come after the wipe and can fail on their own. */
enum class ResetStep {
    /** Deleting the copies earlier exports left in the cache. */
    EXPORT_FILES,

    /** Clearing the settings, in a factory reset. */
    SETTINGS,

    /** Putting the starting categories, accounts and profile back. */
    STARTING_DATA
}
