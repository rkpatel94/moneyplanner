package com.moneyplanner.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily reminder check.
 *
 * Each check is a one-time request for the next time the clock reaches the user's chosen
 * hour, and every run queues the one after it before it finishes. It used to be a periodic
 * request, which cannot be made to keep an hour: WorkManager counts a periodic request's
 * runs from when it was first enqueued, so once the first run had happened a new initial
 * delay was ignored and changing the hour did nothing, while re-enqueueing it at every
 * launch before that first run kept moving it to wherever the launch happened to fall. A
 * one-time request carries its own delay, so the hour picked is the hour it runs.
 *
 * It is all unique work under one name, and the policy says who is asking. A decision by
 * the user, a new hour or reminders being turned on, replaces whatever is queued. App
 * launch and boot are not decisions about the hour, so they keep an existing schedule and
 * only fill a gap if there is none. The work carries no network or charging constraints,
 * because everything it needs is already on the device; it waits for the battery not to
 * be low, as it always has.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * For when the user turns reminders on or picks a new hour: whatever is queued was timed
     * for the old choice, so it is replaced.
     */
    fun scheduleDailyCheck(hourOfDay: Int = 9) {
        enqueue(hourOfDay, ExistingWorkPolicy.REPLACE, minimumGap = Duration.ZERO)
    }

    /**
     * Makes sure a check is queued without moving one that already is. For app launch and
     * boot, which happen at any time of day and must not drag the schedule along with them.
     */
    fun ensureDailyCheck(hourOfDay: Int) {
        enqueue(hourOfDay, ExistingWorkPolicy.KEEP, minimumGap = Duration.ZERO)
    }

    /**
     * Queues tomorrow's check, from inside today's.
     *
     * Appended to the running check rather than replacing it, because replacing would cancel
     * the very run that is asking, and keeping would see that run still in progress and
     * queue nothing. The appended request waits until the current one finishes and then
     * counts its delay from that moment. If the chain has failed or been cancelled in the
     * meantime, it starts a fresh one instead.
     *
     * The next check is always at least an hour away. Without that, a run that started a
     * few seconds before its hour, or one held back by a low battery until shortly before
     * the following day's, would queue another check for minutes later and send the same
     * reminders twice. A run held back overnight still leaves that morning's check in place.
     */
    internal fun scheduleNextCheck(hourOfDay: Int) {
        enqueue(hourOfDay, ExistingWorkPolicy.APPEND_OR_REPLACE, minimumGap = GAP_AFTER_A_RUN)
    }

    fun cancel() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(ReminderWorker.WORK_NAME)
        workManager.cancelUniqueWork(ReminderWorker.LEGACY_PERIODIC_WORK_NAME)
    }

    private fun enqueue(hourOfDay: Int, policy: ExistingWorkPolicy, minimumGap: Duration) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayUntilNextCheck(ZonedDateTime.now(), hourOfDay, minimumGap))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(ReminderWorker.WORK_NAME, policy, request)
        // An install updated from a version that used periodic work still has that request
        // queued under its old name, and left alone it would go on running beside this one,
        // sending every reminder twice. Cancelling a name with nothing under it does nothing.
        workManager.cancelUniqueWork(ReminderWorker.LEGACY_PERIODIC_WORK_NAME)
    }

    private companion object {
        val GAP_AFTER_A_RUN: Duration = Duration.ofHours(1)
    }
}

/**
 * How long from [now] until the clock next reads [hourOfDay] o'clock, at least
 * [minimumGap] away.
 *
 * Worked out on the calendar of the device's own time zone rather than by adding hours, so
 * a day on which the clocks change is still one day: the check stays at the chosen hour
 * instead of drifting an hour off it. The target is rebuilt from each date rather than
 * stepped forward from the last one, so an hour skipped by a change on one day does not
 * carry over to the days after it.
 */
internal fun delayUntilNextCheck(
    now: ZonedDateTime,
    hourOfDay: Int,
    minimumGap: Duration = Duration.ZERO
): Duration {
    val time = LocalTime.of(hourOfDay.coerceIn(0, 23), 0)
    val earliest = now.plus(minimumGap)
    var day = now.toLocalDate()
    var target = day.atTime(time).atZone(now.zone)
    while (!target.isAfter(earliest)) {
        day = day.plusDays(1)
        target = day.atTime(time).atZone(now.zone)
    }
    return Duration.between(now, target)
}
