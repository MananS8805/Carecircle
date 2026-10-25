package com.carecircle.reminder

import android.content.Context
import com.carecircle.R
import com.carecircle.data.AppDb
import com.carecircle.data.CareSync
import com.carecircle.domain.SyncEvent
import com.carecircle.domain.SyncType
import com.carecircle.AppLocale
import com.carecircle.domain.Messages
import com.carecircle.domain.Refill
import com.carecircle.domain.Schedule

object DoseActions {
    /** Tells the family (when sync is on) what happened to this dose. Never throws. */
    private suspend fun publish(ctx: Context, type: SyncType, dose: com.carecircle.data.DoseEvent, med: com.carecircle.data.Medicine) {
        val person = AppDb.get(ctx).people().get(med.personId) ?: return
        CareSync.publish(
            ctx, med.personId,
            SyncEvent(type, person.name, Messages.medicineLabel(med.name, med.doseText), dose.scheduledAt, System.currentTimeMillis()),
            dose.id,
        )
        CareSync.publishStatus(ctx, med.personId)
    }

    suspend fun publishMissed(ctx: Context, dose: com.carecircle.data.DoseEvent, med: com.carecircle.data.Medicine) = publish(ctx, SyncType.MISSED, dose, med)

    /** Skipping is a deliberate choice: no pill is used up, reminders and the missed-dose alert stop. */
    suspend fun skip(ctx: Context, doseId: Long) {
        val db = AppDb.get(ctx)
        if (db.doses().markSkipped(doseId) == 0) return
        db.doses().get(doseId)?.let { dose -> db.medicines().get(dose.medicineId)?.let { publish(ctx, SyncType.SKIPPED, dose, it) } }
        DoseWidget.refreshAll(ctx)
        Notifier.cancelDose(ctx, doseId)
        ReminderScheduler.cancelSnooze(ctx, doseId)
        ReminderScheduler.cancelRepeats(ctx, doseId)
    }

    /** Marks a dose taken (idempotent), decrements pills, and raises a reorder alert at <= 5 days left. */
    suspend fun markTaken(ctx: Context, doseId: Long, now: Long = System.currentTimeMillis()) {
        val db = AppDb.get(ctx)
        val dose = db.doses().get(doseId) ?: return
        if (db.doses().markTaken(doseId, now) == 0) return // already taken
        val med = db.medicines().get(dose.medicineId) ?: return
        db.medicines().decrementPills(med.id, med.pillsPerDose)
        Notifier.cancelDose(ctx, doseId)
        ReminderScheduler.cancelSnooze(ctx, doseId)
        ReminderScheduler.cancelRepeats(ctx, doseId)
        publish(ctx, SyncType.TAKEN, dose, med)
        DoseWidget.refreshAll(ctx)

        val updated = db.medicines().get(med.id) ?: return
        val left = Refill.daysLeft(updated.pillCount, updated.pillsPerDose, Schedule.parseTimes(updated.timesCsv).size)
        if (Refill.needsReorder(left)) {
            val person = db.people().get(updated.personId)
            val lc = AppLocale.wrap(ctx)
            val who = person?.name ?: lc.getString(R.string.family_member_fallback)
            val text = lc.getString(R.string.msg_reorder, Messages.medicineLabel(updated.name, updated.doseText), who, left)
            val url = Messages.whatsAppUrl(Messages.normalizePhone(person?.pharmacyPhone.orEmpty()), text)
            Notifier.linkAlert(
                ctx, 2_000_000 + updated.id.toInt(),
                lc.getString(R.string.notif_reorder_title, updated.name, left), lc.getString(R.string.notif_reorder_text), url,
            )
        }
    }
}
