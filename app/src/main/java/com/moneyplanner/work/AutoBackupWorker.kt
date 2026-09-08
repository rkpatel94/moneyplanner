package com.moneyplanner.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneyplanner.data.backup.AutoBackupManager
import com.moneyplanner.data.backup.AutoBackupResult
import com.moneyplanner.data.prefs.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The scheduled backup.
 *
 * A folder that has gone away is a real possibility — the user may have deleted it,
 * moved an SD card, or revoked the grant — so that case is reported as a permanent
 * failure rather than retried forever. A write that merely failed once is worth retrying.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val autoBackupManager: AutoBackupManager,
    private val settingsStore: SettingsStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsStore.settings.first()
        if (!settings.canAutoBackup) return Result.success()

        return when (autoBackupManager.runBackup(LocalDate.now())) {
            is AutoBackupResult.Success -> Result.success()
            AutoBackupResult.NoFolderChosen -> Result.success()
            // Nothing will improve by trying again until the user picks a folder again.
            AutoBackupResult.FolderUnavailable -> Result.failure()
            AutoBackupResult.WriteFailed -> Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "money-planner-auto-backup"
    }
}

/**
 * Schedules the weekly backup. Weekly rather than daily because a personal ledger changes
 * slowly, and a week of lost entries is a far smaller loss than the battery cost of
 * running this every day.
 */
@Singleton
class AutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(Duration.ofDays(7))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(AutoBackupWorker.WORK_NAME)
    }
}
