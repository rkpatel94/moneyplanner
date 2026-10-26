package com.moneyplanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.moneyplanner.MainActivity
import com.moneyplanner.R
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.data.prefs.AppSettings
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.domain.calc.BalanceCalculator
import com.moneyplanner.domain.model.FinancialSnapshot
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * A privacy-respecting glanceable balance; no network or widget configuration is needed.
 *
 * The widget is painted along two routes that build the same views. When the system asks
 * for an update, which can happen with the app not running at all, [onUpdate] loads the
 * snapshot itself. While the app is open it already holds a fresh snapshot after every
 * change, so it hands that to [render] rather than asking the widget to build a second one.
 */
class BalanceWidgetProvider : AppWidgetProvider() {

    // The system tells the provider when the first widget is placed and the last removed,
    // which keeps [placements] current without asking the widget service on every change.
    override fun onEnabled(context: Context) {
        placed.value = true
    }

    override fun onDisabled(context: Context) {
        placed.value = false
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isNotEmpty()) placed.value = true
        // goAsync only has a result to give back while the system is delivering a real
        // broadcast to this receiver; on an instance constructed by hand it is null. The app
        // paints through render instead, and the nullable type means a stray call made by
        // hand still ends cleanly rather than with a crash.
        val pending: BroadcastReceiver.PendingResult? = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // A failure here only means the widget keeps its last picture until the next
                // update. Nothing is listening for errors in this scope, so one that escaped
                // would take the whole process down instead.
                runCatching {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context, BalanceWidgetEntryPoint::class.java
                    )
                    val snapshot = entryPoint.snapshots().snapshot.first()
                    val settings = entryPoint.settings().settings.first()
                    paint(context, manager, ids, snapshot, settings)
                }
            } finally {
                // The system holds the broadcast open, and eventually reports the app as not
                // responding, until it is told the work is over, so this has to run however
                // the work above ended.
                pending?.finish()
            }
        }
    }

    companion object {

        /** Null until first asked; then whether any widget is on a home screen. */
        private val placed = MutableStateFlow<Boolean?>(null)

        /**
         * Whether a widget is placed, now and whenever that changes.
         *
         * The app repaints the widget from every new snapshot while it is open, and every
         * snapshot is a read of the whole ledger. Most people never place the widget, and
         * for them that second read on every change paid for a picture nobody would see;
         * the app listens to this and keeps the repainting running only while there is a
         * widget to paint. The first answer comes from the widget service, which is a
         * binder call, so collect this off the main thread.
         */
        fun placements(context: Context): Flow<Boolean> = placed
            .onStart { placed.compareAndSet(null, hasWidgets(context)) }
            .filterNotNull()
            .distinctUntilChanged()

        private fun hasWidgets(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            return manager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetProvider::class.java)
            ).isNotEmpty()
        }

        /**
         * Paints every placed widget from a snapshot the caller already has.
         *
         * When no widget is placed, or the device has no home screen widgets at all, this
         * returns before working out the balance, so the app can call it on every snapshot
         * without paying for a figure nobody will see. It makes binder calls to the widget
         * service, so it belongs off the main thread.
         */
        fun render(context: Context, snapshot: FinancialSnapshot, settings: AppSettings) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetProvider::class.java))
            if (ids.isNotEmpty()) paint(context, manager, ids, snapshot, settings)
        }

        /**
         * Builds the widget's views and pushes them to [ids].
         *
         * With the app lock on the balance is withheld: a widget is readable by anyone who
         * picks the phone up, and showing the figure there would undo the point of the lock.
         */
        private fun paint(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            snapshot: FinancialSnapshot,
            settings: AppSettings
        ) {
            val locked = settings.appLockEnabled && settings.hasPin
            // The same figure and words the dashboard leads with. Once money is set aside
            // for goals, the headline is what is free to spend, not the bank-and-cash total:
            // a glance at the home screen is exactly when somebody decides whether they can
            // afford something, and the total would invite them to spend their savings.
            val breakdown = BalanceCalculator.breakdown(snapshot)
            val earmarked = breakdown.earmarkedForGoals.isPositive
            val views = RemoteViews(context.packageName, R.layout.balance_widget).apply {
                setTextViewText(
                    R.id.widget_balance,
                    if (locked) "Locked" else IndianFormat.format(breakdown.unallocated)
                )
                setTextViewText(
                    R.id.widget_caption,
                    when {
                        locked -> "Open app to view balance"
                        earmarked -> "Available to spend"
                        else -> "Available now"
                    }
                )
                // The same intent the launcher icon sends. A bare intent for MainActivity does
                // not match the root of the app's task, so with the app already open in the
                // background a tap would stack a second MainActivity on top of it, with its
                // own lock screen and its own start-up work. Matching the launcher's intent
                // brings the existing task forward instead.
                val launch = Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                val intent = PendingIntent.getActivity(
                    context, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_root, intent)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BalanceWidgetEntryPoint {
    fun snapshots(): SnapshotRepository
    fun settings(): SettingsStore
}
