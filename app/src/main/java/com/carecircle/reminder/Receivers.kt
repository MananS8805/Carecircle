package com.carecircle.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.carecircle.R
import com.carecircle.data.AppDb
import com.carecircle.data.DoseEvent
import com.carecircle.AppLocale
import com.carecircle.data.Medicine
import com.carecircle.ui.Fmt
import com.carecircle.domain.MissedDose
import com.carecircle.domain.Snooze
import com.carecircle.domain.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Fires at each scheduled dose time. */
class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val medId = intent.getLongExtra(Notifier.EXTRA_MED_ID, -1)
        val at = intent.getLongExtra(Notifier.EXTRA_AT, -1)
        if (medId < 0 || at < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDb.get(ctx)
                val med = db.medicines().get(medId)
                if (med != null && med.active) {
                    db.doses().insert(DoseEvent(medicineId = medId, scheduledAt = at)) // unique index ignores duplicates
                    val dose = db.doses().find(medId, at)
                    if (dose != null && dose.takenAt == null) {
                        Notifier.postDose(ctx, dose, med, personLabel(db, med))
                        ReminderScheduler.scheduleRepeats(ctx, dose.id, at)
                        val req = OneTimeWorkRequestBuilder<MissedDoseWorker>()
                            .setInitialDelay(MissedDose.GRACE_MINUTES, TimeUnit.MINUTES)
                            .setInputData(workDataOf(Notifier.EXTRA_DOSE_ID to dose.id)).build()
                        WorkManager.getInstance(ctx).enqueueUniqueWork("missed-${dose.id}", ExistingWorkPolicy.KEEP, req)
                    }
                }
                ReminderScheduler.scheduleFor(ctx, medId)
            } finally { pending.finish() }
        }
    }
}

class TakenActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra(Notifier.EXTRA_DOSE_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { DoseActions.markTaken(ctx, id) } finally { pending.finish() }
        }
    }
}

/** "Snooze 10 min" action, and the one-shot alarm that re-posts the same dose notification. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val doseId = intent.getLongExtra(Notifier.EXTRA_DOSE_ID, -1)
        if (doseId < 0) return
        val level = if (intent.action == ACTION_REPEAT) intent.getIntExtra(Notifier.EXTRA_LEVEL, 1) else 0
        val repost = intent.action == ACTION_REPOST || intent.action == ACTION_REPEAT
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDb.get(ctx)
                val dose = db.doses().get(doseId)
                if (dose == null || dose.skipped || !Snooze.shouldRepost(dose.takenAt)) {
                    Notifier.cancelDose(ctx, doseId)
                    return@launch
                }
                if (repost) {
                    val med = db.medicines().get(dose.medicineId)
                    if (med != null && med.active) Notifier.postDose(ctx, dose, med, personLabel(db, med), level)
                } else {
                    Notifier.cancelDose(ctx, doseId)
                    ReminderScheduler.scheduleSnooze(ctx, doseId, Snooze.fireAt(System.currentTimeMillis()))
                }
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.carecircle.action.SNOOZE"
        const val ACTION_REPOST = "com.carecircle.action.SNOOZE_REPOST"
        const val ACTION_REPEAT = "com.carecircle.action.REPEAT_REMINDER"
    }
}

/** 9 pm: offers to send today's summary to each person's caregiver. Tapping opens the pre-filled message. */
class DailySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lc = AppLocale.wrap(ctx)
                val repo = com.carecircle.data.Repository(ctx)
                for (p in AppDb.get(ctx).people().all()) {
                    val r = repo.adherence(p.id, 1)
                    val total = r.taken + r.missed + r.skipped + r.pending
                    if (total == 0) continue
                    com.carecircle.data.CareSync.publish(ctx, p.id, com.carecircle.domain.SyncEvent(com.carecircle.domain.SyncType.SUMMARY, p.name, at = System.currentTimeMillis(), taken = r.taken, total = total))
                    if (p.caregiverPhone.isBlank()) continue
                    val notTaken = r.lines.filter { it.outcome != com.carecircle.domain.DoseOutcome.TAKEN }.map { it.name }.distinct().take(3)
                    var text = lc.getString(R.string.msg_daily, p.name, r.taken, total)
                    if (notTaken.isNotEmpty()) text += " " + lc.getString(R.string.msg_daily_not_taken, notTaken.joinToString(", "))
                    val url = Messages.whatsAppUrl(Messages.normalizePhone(p.caregiverPhone), text)
                    Notifier.linkAlert(
                        ctx, 4_000_000 + p.id.toInt(),
                        lc.getString(R.string.notif_daily_title, p.name), lc.getString(R.string.notif_daily_text, r.taken, total), url,
                    )
                }
            } finally {
                ReminderScheduler.scheduleDailySummary(ctx)
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { ReminderScheduler.scheduleAll(ctx) } finally { pending.finish() }
        }
    }
}

/** 30 minutes after a dose: if still not taken, offer a pre-filled WhatsApp message to the caregiver. */
class MissedDoseWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(Notifier.EXTRA_DOSE_ID, -1)
        if (id < 0) return Result.success()
        val db = AppDb.get(applicationContext)
        val dose = db.doses().get(id) ?: return Result.success()
        if (dose.missedHandled || dose.skipped || !MissedDose.isMissed(dose.scheduledAt, System.currentTimeMillis(), dose.takenAt)) return Result.success()
        val med = db.medicines().get(dose.medicineId) ?: return Result.success()
        val ctx = AppLocale.wrap(applicationContext)
        val person = db.people().get(med.personId)
        val time = Fmt.time(dose.scheduledAt, AppLocale.locale(applicationContext))
        val who = person?.name ?: ctx.getString(R.string.family_member_fallback)
        val text = ctx.getString(R.string.msg_missed, who, Messages.medicineLabel(med.name, med.doseText), time)
        val url = Messages.whatsAppUrl(Messages.normalizePhone(person?.caregiverPhone.orEmpty()), text)
        Notifier.linkAlert(
            applicationContext, 3_000_000 + (id % 1_000_000).toInt(),
            ctx.getString(R.string.notif_missed_title, med.name, time), ctx.getString(R.string.notif_missed_text), url,
        )
        db.doses().markMissedHandled(id)
        DoseActions.publishMissed(applicationContext, dose, med) // automatic: no tap needed
        return Result.success()
    }
}

/** The person's name for notification titles, only when more than one person is tracked. */
private suspend fun personLabel(db: AppDb, med: Medicine): String? =
    if (db.people().count() > 1) db.people().get(med.personId)?.name else null
