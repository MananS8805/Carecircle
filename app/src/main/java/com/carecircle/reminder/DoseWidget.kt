package com.carecircle.reminder

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
import com.carecircle.domain.Schedule
import com.carecircle.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Home-screen widget: the dose that needs attention now (or the next one) with a one-tap Taken button. */
class DoseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        /** The Taken button also appears this long before a dose, for someone who takes it a little early. */
        private const val EARLY_MS = 30 * 60_000L

        /** Called after any dose changes so the widget never shows a stale Taken button. */
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, DoseWidget::class.java))
            if (ids.isNotEmpty()) CoroutineScope(Dispatchers.IO).launch { render(ctx, mgr, ids) }
        }

        private suspend fun render(base: Context, mgr: AppWidgetManager, ids: IntArray) {
            val ctx = AppLocale.wrap(base)
            val db = AppDb.get(base)
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val day = LocalDate.now(zone)
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val events = db.doses().since(start)
            val meds = db.medicines().all()
            data class Slot(val medId: Long, val name: String, val dose: String, val at: Long)
            val slots = meds.flatMap { m -> Schedule.parseTimes(m.timesCsv).map { Slot(m.id, m.name, m.doseText, day.atTime(it).atZone(zone).toInstant().toEpochMilli()) } }
                .filter { s -> events.none { it.medicineId == s.medId && it.scheduledAt == s.at && (it.takenAt != null || it.skipped) } }
                .sortedBy { it.at }
            val open = slots.firstOrNull { it.at <= now } ?: slots.firstOrNull()

            val views = RemoteViews(base.packageName, R.layout.widget_dose)
            val openApp = PendingIntent.getActivity(base, 0, Intent(base, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            if (open == null) {
                views.setTextViewText(R.id.widget_label, ctx.getString(R.string.widget_label_done))
                views.setTextViewText(R.id.widget_title, ctx.getString(R.string.widget_all_done))
                views.setTextViewText(R.id.widget_sub, "")
                views.setViewVisibility(R.id.widget_taken, View.GONE)
            } else {
                val due = open.at <= now
                views.setTextViewText(R.id.widget_label, ctx.getString(if (due) R.string.status_due else R.string.hero_next_dose).uppercase())
                views.setTextViewText(R.id.widget_title, open.name)
                views.setTextViewText(R.id.widget_sub, listOf(Fmt.time(open.at, AppLocale.locale(base)), open.dose).filter { it.isNotBlank() }.joinToString("  -  "))
                if (due || open.at - now <= EARLY_MS) {
                    db.doses().insert(DoseEvent(medicineId = open.medId, scheduledAt = open.at)) // the unique index ignores a repeat
                    val ev = db.doses().find(open.medId, open.at)
                    if (ev != null) {
                        val taken = PendingIntent.getBroadcast(
                            base, 7_000_000 + ev.id.toInt(), Intent(base, TakenActionReceiver::class.java).putExtra(Notifier.EXTRA_DOSE_ID, ev.id),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                        views.setTextViewText(R.id.widget_taken, ctx.getString(R.string.action_taken))
                        views.setOnClickPendingIntent(R.id.widget_taken, taken)
                        views.setViewVisibility(R.id.widget_taken, View.VISIBLE)
                    } else views.setViewVisibility(R.id.widget_taken, View.GONE)
                } else views.setViewVisibility(R.id.widget_taken, View.GONE)
            }
            ids.forEach { mgr.updateAppWidget(it, views) }
        }
    }
}
