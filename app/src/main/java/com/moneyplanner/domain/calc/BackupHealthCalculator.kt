package com.moneyplanner.domain.calc

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How exposed the user's records currently are.
 *
 * This is the app's single greatest risk and the one it can do least about. Everything is
 * on one device, cloud backup is deliberately off because these are financial records, and
 * the app has no network with which to copy them anywhere. A lost phone is a lost history,
 * and the only defence is a backup the user actually took.
 *
 * So the state is reported plainly rather than left for the user to go and check. The
 * thresholds are stated rather than hidden, and the warning stays quiet while there is
 * nothing to lose: telling somebody with four expenses that their data is at risk teaches
 * them to ignore the warning by the time it matters.
 */
object BackupHealthCalculator {

    /** Past this many days a backup is old enough to mention. */
    const val STALE_AFTER_DAYS = 14L

    /** Past this many days it is worth saying plainly that the records are exposed. */
    const val OVERDUE_AFTER_DAYS = 30L

    /** Below this many records there is little enough at stake to stay quiet. */
    const val WORTH_WARNING_ABOUT = 10

    fun assess(
        lastBackupEpochDay: Long?,
        autoBackupConfigured: Boolean,
        recordCount: Int,
        today: LocalDate
    ): BackupHealth {
        val daysSince = lastBackupEpochDay?.let {
            ChronoUnit.DAYS.between(LocalDate.ofEpochDay(it), today).coerceAtLeast(0)
        }

        val state = when {
            recordCount < WORTH_WARNING_ABOUT && lastBackupEpochDay == null ->
                BackupState.NOTHING_TO_LOSE
            lastBackupEpochDay == null -> BackupState.NEVER
            daysSince != null && daysSince >= OVERDUE_AFTER_DAYS -> BackupState.OVERDUE
            daysSince != null && daysSince >= STALE_AFTER_DAYS -> BackupState.STALE
            else -> BackupState.RECENT
        }

        return BackupHealth(
            state = state,
            daysSinceBackup = daysSince,
            lastBackupOn = lastBackupEpochDay?.let { LocalDate.ofEpochDay(it) },
            autoBackupConfigured = autoBackupConfigured,
            recordCount = recordCount
        )
    }
}

enum class BackupState {
    /** Too little recorded yet for a warning to mean anything. */
    NOTHING_TO_LOSE,
    NEVER,
    RECENT,
    STALE,
    OVERDUE
}

data class BackupHealth(
    val state: BackupState,
    /** Null when no backup has ever been taken. */
    val daysSinceBackup: Long?,
    val lastBackupOn: LocalDate?,
    val autoBackupConfigured: Boolean,
    val recordCount: Int
) {
    val needsAttention: Boolean
        get() = state == BackupState.NEVER ||
            state == BackupState.STALE ||
            state == BackupState.OVERDUE

    val isSerious: Boolean
        get() = state == BackupState.NEVER || state == BackupState.OVERDUE

    /** What to show where the status lives. One line, no hedging. */
    fun headline(): String = when (state) {
        BackupState.NOTHING_TO_LOSE -> "No backup yet"
        BackupState.NEVER -> "You have never backed up"
        BackupState.RECENT -> when (daysSinceBackup) {
            null, 0L -> "Backed up today"
            1L -> "Backed up yesterday"
            else -> "Backed up $daysSinceBackup days ago"
        }
        BackupState.STALE -> "Last backup was $daysSinceBackup days ago"
        BackupState.OVERDUE -> "No backup for $daysSinceBackup days"
    }

    /**
     * Why it matters, said once and only when it does. A folder already chosen changes
     * the advice: the job is then to find out why the weekly copy stopped.
     */
    fun detail(): String? = when {
        state == BackupState.NOTHING_TO_LOSE -> null
        state == BackupState.RECENT && autoBackupConfigured -> null
        state == BackupState.RECENT ->
            "Choose a folder and a copy will be written every week without you asking."
        autoBackupConfigured ->
            "Automatic backup is set up but has not run recently. The folder may have been " +
                "moved or deleted."
        else ->
            "These records exist only on this phone. Losing it loses them."
    }
}
