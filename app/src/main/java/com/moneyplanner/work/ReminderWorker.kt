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
import com.moneyplanner.domain.calc.MonthForecast
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The daily reminder check.
 *
 * It runs once a day and looks a short way ahead rather than notifying about everything
 * it can find. Reminders are grouped into a single message per kind, because five
 * separate notifications about five bills is how people learn to swipe an app away.
 * What has already gone past its due date unpaid gets one message of its own, because
 * that is the money most likely to cost a late fee.
 *
 * Each run queues the next day's before it finishes; see [ReminderScheduler] for why the
 * check is a chain of one-time requests rather than a periodic one.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val snapshotRepository: SnapshotRepository,
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsStore.settings.first()
        // Turned off: the chain of daily checks ends here, and turning reminders back on
        // starts a new one.
        if (!settings.remindersEnabled) return Result.success()

        try {
            remind(settings)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A check that could not finish today is no reason to lose tomorrow's. Failing
            // the work would also fail the check queued behind it, ending the chain until
            // the app is next opened.
        } finally {
            // Not after a stop. A stop is either a cancel, which means no more checks, or a
            // new hour, which has already queued its own; and one stopped by the system,
            // when the battery ran low mid-run, is run again and queues the next one then.
            if (!isStopped) reminderScheduler.scheduleNextCheck(settings.reminderHour)
        }
        return Result.success()
    }

    private suspend fun remind(settings: AppSettings) {
        // Checked on every run rather than ending the chain: permission granted tomorrow
        // should bring reminders back without the user having to touch the settings.
        if (!hasNotificationPermission()) return

        val snapshot = snapshotRepository.snapshot.first()
        val today = snapshot.today
        val horizon = today.plusDays(settings.remindDaysBefore.toLong())

        val months = ForecastCalculator.forecast(snapshot, 2)
        val upcoming = ReminderSelection.upcoming(months.flatMap { it.items }, today, horizon)
        val overdue = ReminderSelection.overdue(months.first(), settings)

        ensureChannel()

        // A fixed id, so tomorrow's summary replaces today's, and so it can be taken down
        // once nothing is late any more instead of claiming something is still owed.
        if (overdue.isNotEmpty()) {
            notify(OVERDUE_NOTIFICATION_ID, ReminderSelection.overdueMessage(overdue, today))
        } else {
            NotificationManagerCompat.from(applicationContext).cancel(OVERDUE_NOTIFICATION_ID)
        }

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
            val week = months
                .flatMap { it.items }
                .filter { !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(6)) }
            buildWeeklySummary(week)?.let { notify(notificationId, it) }
        }
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
        /** The chain of daily one-time checks. */
        const val WORK_NAME = "money-planner-reminder-check"

        /**
         * The name the check ran under while it was periodic work. Kept only so the
         * scheduler can cancel it on installs updated from then; nothing enqueues it now.
         */
        const val LEGACY_PERIODIC_WORK_NAME = "money-planner-daily-reminders"

        private const val CHANNEL_ID = "payment_reminders"
        private const val BASE_NOTIFICATION_ID = 4200

        /** Below the per-kind ids, which count upwards from [BASE_NOTIFICATION_ID]. */
        private const val OVERDUE_NOTIFICATION_ID = BASE_NOTIFICATION_ID - 1
    }
}

/**
 * Which items in the projection deserve a reminder today, and what the overdue one says.
 *
 * Kept apart from the worker so the choice can be tested without a notification or a
 * running job. Everything it judges by is read straight off the forecast items: the date,
 * the kind and whether the forecast has marked one overdue.
 */
internal object ReminderSelection {

    /**
     * Due between today and [horizon] and not yet late. A late item belongs to the overdue
     * message instead, and showing it in both would remind about the same rupee twice.
     */
    fun upcoming(items: List<ForecastItem>, today: LocalDate, horizon: LocalDate): List<ForecastItem> =
        items.filter { !it.isOverdue && !it.date.isBefore(today) && !it.date.isAfter(horizon) }

    /**
     * Everything this month's projection holds that is past its due date and still unpaid,
     * for the kinds the user wants reminders about, the longest overdue first.
     *
     * Read from [ForecastItem.isOverdue] rather than worked out from the date, because the
     * date cannot be trusted for it either way. A missed EMI, bill or yearly payment keeps
     * the day it was due, which is before today, so a window starting today never contains
     * it and it was never reminded about at all. Money a person owes, once late, is carried
     * forward to today, so judged by its date it would look merely due.
     */
    fun overdue(currentMonth: MonthForecast, settings: AppSettings): List<ForecastItem> =
        currentMonth.items
            .filter { it.isOverdue && wantsReminders(it.kind, settings) }
            .sortedBy { it.dueDate }

    /** The same switches the per-kind reminders use, so turning one off silences both. */
    fun wantsReminders(kind: ForecastItemKind, settings: AppSettings): Boolean = when (kind) {
        ForecastItemKind.EMI -> settings.remindAboutEmis
        ForecastItemKind.BILL, ForecastItemKind.ANNUAL -> settings.remindAboutBills
        ForecastItemKind.CREDIT_CARD -> settings.remindAboutCards
        ForecastItemKind.PERSON_INCOMING, ForecastItemKind.PERSON_OUTGOING ->
            settings.remindAboutPeople
        // Income is never something the user has to act on, late or not. Nor is something
        // the user has already recorded for a later date: it is their own entry, not a due.
        ForecastItemKind.INCOME, ForecastItemKind.PLANNED, ForecastItemKind.PLANNED_IN -> false
    }

    /** One message for everything that is late, however many items that is. */
    fun overdueMessage(items: List<ForecastItem>, today: LocalDate): ReminderMessage {
        require(items.isNotEmpty()) { "Nothing is overdue" }

        if (items.size == 1) {
            val item = items.first()
            val due = sinceDue(item.dueDate, today)
            return if (item.isInflow) {
                ReminderMessage(
                    title = "Overdue: ${IndianFormat.format(item.amount)} from ${item.title}",
                    body = "Expected $due · ${item.subtitle}"
                )
            } else {
                ReminderMessage(
                    title = "Overdue: ${item.title}",
                    body = "${IndianFormat.format(item.amount)} was due $due · ${item.subtitle}"
                )
            }
        }

        val toPay = items.filterNot { it.isInflow }
        val toCollect = items.filter { it.isInflow }
        val names = items.take(3).joinToString(", ") { it.title } +
            if (items.size > 3) " and ${items.size - 3} more" else ""

        return ReminderMessage(
            title = if (toPay.isEmpty()) {
                "Overdue: ${items.size} repayments to you"
            } else {
                "Overdue: ${items.size} payments"
            },
            body = buildList {
                if (toPay.isNotEmpty()) {
                    add("${IndianFormat.format(toPay.sumOfMoney { it.amount })} unpaid")
                }
                if (toCollect.isNotEmpty()) {
                    add("${IndianFormat.format(toCollect.sumOfMoney { it.amount })} owed to you")
                }
                add(names)
            }.joinToString(" · ")
        )
    }

    /**
     * When something fell due, said the way a person would: "yesterday", "3 days ago", or
     * the date once it is more than a week back and a count of days stops meaning much.
     */
    private fun sinceDue(due: LocalDate, today: LocalDate): String =
        when (val days = ChronoUnit.DAYS.between(due, today)) {
            in Long.MIN_VALUE..0L -> "today"
            1L -> "yesterday"
            in 2L..6L -> "$days days ago"
            else -> "on ${DateUtil.formatDate(due)}"
        }
}

internal data class ReminderMessage(val title: String, val body: String)
