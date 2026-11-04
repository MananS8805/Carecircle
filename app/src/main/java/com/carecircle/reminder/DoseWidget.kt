package com.carecircle.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.carecircle.AppLocale
import com.carecircle.MainActivity
import com.carecircle.R
import com.carecircle.data.AppDb
import com.carecircle.data.DoseEvent
import com.carecircle.domain.DoseSlot
import com.carecircle.domain.NextDose
import com.carecircle.domain.NextDoseCalc
import com.carecircle.domain.NextDoseEvent
import com.carecircle.domain.NextDoseMed
import com.carecircle.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Home-screen widget (RemoteViews, no extra dependency): the dose that needs attention now, or the next one, with a
 * one-tap Taken button that uses the same path as the notification. Not shown on the lock screen.
 */
class DoseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MIDNIGHT) {
            refreshAll(context)
            scheduleMidnightRefresh(context)
        } else super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) = scheduleMidnightRefresh(context)

    companion object {
        private const val ACTION_MIDNIGHT = "com.carecircle.action.WIDGET_MIDNIGHT"
        /** The Taken button also appears this long before a dose, for someone who takes it a little early. */
        private const val EARLY_MS = 30 * 60_000L

        /** Called after any dose changes so the widget never shows a stale Taken button. */
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, DoseWidget::class.java))
            if (ids.isNotEmpty()) CoroutineScope(Dispatchers.IO).launch { render(ctx, mgr, ids) }
        }

        /** One inexact wake-up just after midnight so "all done" and tomorrow's doses roll over. */
        fun scheduleMidnightRefresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            if (mgr.getAppWidgetIds(ComponentName(ctx, DoseWidget::class.java)).isEmpty()) return
            val zone = ZoneId.systemDefault()
            val at = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 60_000
            val pi = PendingIntent.getBroadcast(
                ctx, 9_900_002, Intent(ctx, DoseWidget::class.java).setAction(ACTION_MIDNIGHT), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
        }

        private suspend fun render(base: Context, mgr: AppWidgetManager, ids: IntArray) {
            val ctx = AppLocale.wrap(base)
            val db = AppDb.get(base)
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val meds = db.medicines().all().map { NextDoseMed(it.id, it.name, it.doseText, it.personId, it.timesCsv, it.createdAt, it.archivedAt) }
            val events = db.doses().since(start).map { NextDoseEvent(it.medicineId, it.scheduledAt, it.takenAt, it.skipped) }
            val state = NextDoseCalc.compute(now, zone, meds, events)
            val manyPeople = db.people().count() > 1
            val locale = AppLocale.locale(base)

            val views = RemoteViews(base.packageName, R.layout.widget_dose)
            val openApp = PendingIntent.getActivity(base, 0, Intent(base, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setViewVisibility(R.id.widget_more, View.GONE)
            views.setViewVisibility(R.id.widget_taken, View.GONE)
            views.setViewVisibility(R.id.widget_taken_bad, View.GONE)

            suspend fun describe(label: String, slots: List<DoseSlot>) {
                val first = slots.first()
                val who = if (manyPeople) db.people().get(first.personId)?.name else null
                views.setTextViewText(R.id.widget_label, listOfNotNull(label, who?.uppercase()).joinToString("  -  "))
                views.setTextViewText(R.id.widget_title, first.name)
                views.setTextViewText(R.id.widget_sub, listOf(Fmt.time(first.at, locale), first.doseText).filter { it.isNotBlank() }.joinToString("  -  "))
                if (slots.size > 1) {
                    views.setTextViewText(R.id.widget_more, ctx.getString(R.string.widget_more, slots.size - 1))
                    views.setViewVisibility(R.id.widget_more, View.VISIBLE)
                }
            }

            suspend fun takenButton(slot: DoseSlot, overdue: Boolean) {
                db.doses().insert(DoseEvent(medicineId = slot.medicineId, scheduledAt = slot.at)) // the unique index ignores a repeat
                val ev = db.doses().find(slot.medicineId, slot.at) ?: return
                val taken = PendingIntent.getBroadcast(
                    base, 7_000_000 + ev.id.toInt(), Intent(base, TakenActionReceiver::class.java).putExtra(Notifier.EXTRA_DOSE_ID, ev.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val id = if (overdue) R.id.widget_taken_bad else R.id.widget_taken
                views.setTextViewText(id, ctx.getString(if (overdue) R.string.widget_mark_taken else R.string.action_taken))
                views.setOnClickPendingIntent(id, taken)
                views.setViewVisibility(id, View.VISIBLE)
            }

            when (state) {
                NextDose.Empty -> {
                    views.setTextViewText(R.id.widget_label, ctx.getString(R.string.widget_label_done))
                    views.setTextViewText(R.id.widget_title, ctx.getString(R.string.widget_add))
                    views.setTextViewText(R.id.widget_sub, ctx.getString(R.string.widget_add_hint))
                }
                is NextDose.AllDone -> {
                    views.setTextViewText(R.id.widget_label, ctx.getString(R.string.widget_label_done))
                    views.setTextViewText(R.id.widget_title, ctx.getString(R.string.widget_all_done))
                    val t = state.tomorrowFirst
                    views.setTextViewText(R.id.widget_sub, if (t == null) "" else ctx.getString(R.string.widget_tomorrow, Fmt.time(t.at, locale), t.name))
                }
                is NextDose.Due -> {
                    describe(ctx.getString(if (state.overdue) R.string.widget_label_missed else R.string.status_due).uppercase(), state.slots)
                    takenButton(state.slots.first(), state.overdue)
                }
                is NextDose.Upcoming -> {
                    describe(ctx.getString(R.string.hero_next_dose).uppercase(), state.slots)
                    if (state.slots.first().at - now <= EARLY_MS) takenButton(state.slots.first(), overdue = false)
                }
            }
            ids.forEach { mgr.updateAppWidget(it, views) }
        }
    }
}
