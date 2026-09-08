package com.moneyplanner.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily reminder check.
 *
 * The work is deferred to the user's chosen hour and carries no network or charging
 * constraints, because everything it needs is already on the device. Using a unique
 * periodic request means repeated calls update the existing schedule rather than
 * stacking up duplicates.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun scheduleDailyCheck(hourOfDay: Int = 9) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofDays(1))
            .setInitialDelay(initialDelayUntil(hourOfDay))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(ReminderWorker.WORK_NAME)
    }

    /** The wait until the next occurrence of [hourOfDay], today or tomorrow. */
    private fun initialDelayUntil(hourOfDay: Int): Duration {
        val now = LocalDateTime.now()
        val target = now.toLocalDate()
            .atTime(LocalTime.of(hourOfDay.coerceIn(0, 23), 0))
            .let { if (it.isAfter(now)) it else it.plusDays(1) }
        return Duration.between(now, target)
    }
}
