package com.moneyplanner.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moneyplanner.data.prefs.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-registers the background work after a restart.
 *
 * WorkManager restores its own schedule on most devices, but several manufacturer builds
 * drop pending work across a reboot, and a reminder that silently stops arriving is worse
 * than one that was never offered.
 *
 * What gets re-registered comes from the user's saved settings, never from defaults. A
 * reboot is not a decision: someone who turned reminders off should not find them back on,
 * and someone who moved them to 7am should not find them at 9am again. Reading those
 * settings is a suspending call, so the broadcast is held open with goAsync until it
 * finishes rather than being fired off and abandoned.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var autoBackupScheduler: AutoBackupScheduler
    @Inject lateinit var settingsStore: SettingsStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = settingsStore.settings.first()

                if (settings.remindersEnabled) {
                    reminderScheduler.scheduleDailyCheck(settings.reminderHour)
                } else {
                    reminderScheduler.cancel()
                }

                if (settings.canAutoBackup) {
                    autoBackupScheduler.schedule()
                } else {
                    autoBackupScheduler.cancel()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
