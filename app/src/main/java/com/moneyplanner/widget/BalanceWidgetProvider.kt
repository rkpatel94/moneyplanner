package com.moneyplanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.moneyplanner.MainActivity
import com.moneyplanner.R
import com.moneyplanner.core.money.IndianFormat
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.data.prefs.SettingsStore
import com.moneyplanner.domain.calc.BalanceCalculator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A privacy-respecting glanceable balance; no network or widget configuration is needed. */
class BalanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(context, BalanceWidgetEntryPoint::class.java)
                val repository = entryPoint.snapshots()
                val snapshot = repository.snapshot.first()
                val settings = entryPoint.settings().settings.first()
                val views = RemoteViews(context.packageName, R.layout.balance_widget).apply {
                    val private = settings.appLockEnabled && settings.hasPin
                    setTextViewText(R.id.widget_balance, if (private) "Locked" else IndianFormat.format(BalanceCalculator.currentBalance(snapshot)))
                    setTextViewText(R.id.widget_caption, if (private) "Open app to view balance" else "Available now")
                    val intent = PendingIntent.getActivity(
                        context, 0, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    setOnClickPendingIntent(R.id.widget_root, intent)
                }
                ids.forEach { manager.updateAppWidget(it, views) }
            }
            pending.finish()
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BalanceWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) BalanceWidgetProvider().onUpdate(context, manager, ids)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BalanceWidgetEntryPoint {
    fun snapshots(): SnapshotRepository
    fun settings(): SettingsStore
}
