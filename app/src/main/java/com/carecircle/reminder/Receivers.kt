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
        val repost = intent.action == ACTION_REPOST
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
                    if (med != null && med.active) Notifier.postDose(ctx, dose, med, personLabel(db, med))
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
        return Result.success()
    }
}

/** The person's name for notification titles, only when more than one person is tracked. */
private suspend fun personLabel(db: AppDb, med: Medicine): String? =
    if (db.people().count() > 1) db.people().get(med.personId)?.name else null
