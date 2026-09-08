package com.moneyplanner.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moneyplanner.MainActivity
import com.moneyplanner.R
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.core.money.sumOfMoney
import com.moneyplanner.core.time.DateUtil
import com.moneyplanner.data.prefs.AppSettings
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.domain.calc.ForecastCalculator
import com.moneyplanner.domain.calc.ForecastItem
import com.moneyplanner.domain.calc.ForecastItemKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The daily reminder check.
 *
 * It runs once a day and looks a short way ahead rather than notifying about everything
 * it can find. Reminders are grouped into a single message per kind, because five
 * separate notifications about five bills is how people learn to swipe an app away.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val snapshotRepository: SnapshotRepository,
    private val settingsStore: SettingsStore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsStore.settings.first()
        if (!settings.remindersEnabled) return Result.success()
        if (!hasNotificationPermission()) return Result.success()

        val snapshot = snapshotRepository.snapshot.first()
        val today = snapshot.today
        val horizon = today.plusDays(settings.remindDaysBefore.toLong())

        val upcoming = ForecastCalculator.forecast(snapshot, 2)
            .flatMap { it.items }
            .filter { !it.date.isBefore(today) && !it.date.isAfter(horizon) }

        ensureChannel()
        var notificationId = BASE_NOTIFICATION_ID

        if (settings.remindAboutEmis) {
            upcoming.filter { it.kind == ForecastItemKind.EMI }
                .takeIf { it.isNotEmpty() }
                ?.let { items -> notify(notificationId++, buildDueMessage("EMI", items, today)) }
        }

        if (settings.remindAboutBills) {
            upcoming.filter { it.kind == ForecastItemKind.BILL || it.kind == ForecastItemKind.ANNUAL }
                .takeIf { it.isNotEmpty() }
                ?.let { items -> notify(notificationId++, buildDueMessage("Bill", items, today)) }
        }

        if (settings.remindAboutCards) {
            upcoming.filter { it.kind == ForecastItemKind.CREDIT_CARD }
                .takeIf { it.isNotEmpty() }
                ?.let { items ->
                    notify(notificationId++, buildDueMessage("Credit card", items, today))
                }
        }

        if (settings.remindAboutPeople) {
            upcoming.filter {
                it.kind == ForecastItemKind.PERSON_INCOMING ||
                    it.kind == ForecastItemKind.PERSON_OUTGOING
            }.takeIf { it.isNotEmpty() }?.let { items ->
                notify(notificationId++, buildPeopleMessage(items, today))
            }
        }

        if (settings.weeklySummary && today.dayOfWeek == java.time.DayOfWeek.MONDAY) {
            // A week of items, not the reminder horizon. `upcoming` stops at the user's
            // "remind me N days before" setting, which defaults to two days, so building
            // the summary from it would promise a week and deliver a couple of days.
            val week = ForecastCalculator.forecast(snapshot, 2)
                .flatMap { it.items }
                .filter { !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(6)) }
            buildWeeklySummary(week)?.let { notify(notificationId, it) }
        }

        return Result.success()
    }

    private fun buildDueMessage(
        label: String,
        items: List<ForecastItem>,
        today: LocalDate
    ): ReminderMessage {
        val total = items.sumOfMoney { it.amount }
        return if (items.size == 1) {
            val item = items.first()
            ReminderMessage(
                title = "${item.title} is due ${DateUtil.relativeDayLabel(item.date, today).lowercase()}",
                body = "${IndianFormat.format(item.amount)} · ${item.subtitle}"
            )
        } else {
            ReminderMessage(
                title = "${items.size} ${label.lowercase()} payments coming up",
                body = "${IndianFormat.format(total)} in total · " +
                    items.take(3).joinToString(", ") { it.title }
            )
        }
    }

    private fun buildPeopleMessage(items: List<ForecastItem>, today: LocalDate): ReminderMessage {
        val incoming = items.filter { it.kind == ForecastItemKind.PERSON_INCOMING }
        val outgoing = items.filter { it.kind == ForecastItemKind.PERSON_OUTGOING }

        return when {
            incoming.isNotEmpty() && outgoing.isEmpty() && incoming.size == 1 -> {
                val item = incoming.first()
                ReminderMessage(
                    title = "${IndianFormat.format(item.amount)} expected from ${item.title}",
                    body = DateUtil.relativeDayLabel(item.date, today)
                )
            }
            outgoing.isNotEmpty() && incoming.isEmpty() && outgoing.size == 1 -> {
                val item = outgoing.first()
                ReminderMessage(
                    title = "You owe ${item.title} ${IndianFormat.format(item.amount)}",
                    body = "Due ${DateUtil.relativeDayLabel(item.date, today).lowercase()}"
                )
            }
            else -> ReminderMessage(
                title = "Money to settle with people",
                body = buildString {
                    if (incoming.isNotEmpty()) {
                        append("${IndianFormat.format(incoming.sumOfMoney { it.amount })} expected")
                    }
                    if (incoming.isNotEmpty() && outgoing.isNotEmpty()) append(" · ")
                    if (outgoing.isNotEmpty()) {
                        append("${IndianFormat.format(outgoing.sumOfMoney { it.amount })} to repay")
                    }
                }
            )
        }
    }

    private fun buildWeeklySummary(weekItems: List<ForecastItem>): ReminderMessage? {
        val outgoing = weekItems.filterNot { it.isInflow }
        if (outgoing.isEmpty()) return null
        val total = outgoing.sumOfMoney { it.amount }
        return ReminderMessage(
            title = "${IndianFormat.format(total)} of payments this week",
            body = "${outgoing.size} scheduled " +
                if (outgoing.size == 1) "payment" else "payments"
        )
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = applicationContext.getString(R.string.reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notify(id: Int, message: ReminderMessage) {
        if (!hasNotificationPermission()) return

        val intent = android.content.Intent(applicationContext, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }

    private fun hasNotificationPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val WORK_NAME = "money-planner-daily-reminders"
        private const val CHANNEL_ID = "payment_reminders"
        private const val BASE_NOTIFICATION_ID = 4200
    }
}

private data class ReminderMessage(val title: String, val body: String)
