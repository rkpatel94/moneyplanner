package com.moneyplanner.domain.calc

import com.moneyplanner.core.time.DateUtil
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

    /**
     * [autoBackupFailedEpochDay] and [autoBackupFailureReason] are the last backup to the
     * chosen folder that failed with none working since. They are judged apart from the
     * age of the last backup, because that age cannot tell them apart: a backup file made
     * by hand yesterday makes the records look safe while the weekly copy, the one the user
     * is relying on, has stopped.
     */
    fun assess(
        lastBackupEpochDay: Long?,
        autoBackupConfigured: Boolean,
        recordCount: Int,
        today: LocalDate,
        autoBackupFailedEpochDay: Long? = null,
        autoBackupFailureReason: String? = null
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

        // Once automatic backup is turned off, a failure from before is history rather than
        // something to act on, and reporting it would only confuse.
        val failedOn = autoBackupFailedEpochDay
            ?.takeIf { autoBackupConfigured }
            ?.let { LocalDate.ofEpochDay(it) }

        return BackupHealth(
            state = state,
            daysSinceBackup = daysSince,
            lastBackupOn = lastBackupEpochDay?.let { LocalDate.ofEpochDay(it) },
            autoBackupConfigured = autoBackupConfigured,
            recordCount = recordCount,
            autoBackupFailedOn = failedOn,
            autoBackupFailureReason = autoBackupFailureReason
                ?.takeIf { failedOn != null && it.isNotBlank() }
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
    val recordCount: Int,
    /**
     * When the automatic backup last failed, if none has worked since. Null when it has not
     * failed, and whenever automatic backup is not set up.
     */
    val autoBackupFailedOn: LocalDate? = null,
    /** Why it failed, when that is known. */
    val autoBackupFailureReason: String? = null
) {
    /** True while the weekly copy the user set up is not being made. */
    val autoBackupFailing: Boolean get() = autoBackupFailedOn != null

    /**
     * A failing automatic backup always needs attention, however recent the last backup.
     * The user chose a folder so they would not have to remember; if the copy has stopped,
     * they are the only one who can find out why.
     */
    val needsAttention: Boolean
        get() = autoBackupFailing ||
            state == BackupState.NEVER ||
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
     * the advice: the job is then to find out why the weekly copy stopped. A recorded
     * failure comes first, because it says exactly that.
     */
    fun detail(): String? = when {
        // "To your folder" rather than "automatic": Back up now writes to the same folder,
        // and its failure means the weekly copy will fail in exactly the same way.
        autoBackupFailedOn != null -> buildString {
            append("The backup to your folder on ")
            append(DateUtil.formatDate(autoBackupFailedOn))
            append(" failed")
            autoBackupFailureReason?.let { append(": ").append(it) }
            append(". No weekly copy is being made until it works again.")
        }
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
