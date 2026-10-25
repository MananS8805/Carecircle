package com.carecircle.reminder

import android.content.Context
import com.carecircle.R
import com.carecircle.data.AppDb
import com.carecircle.data.Prefs
import com.carecircle.domain.Messages
import com.carecircle.domain.Refill
import com.carecircle.domain.Schedule

object DoseActions {
    /** Marks a dose taken (idempotent), decrements pills, and raises a reorder alert at <= 5 days left. */
    suspend fun markTaken(ctx: Context, doseId: Long, now: Long = System.currentTimeMillis()) {
        val db = AppDb.get(ctx)
        val dose = db.doses().get(doseId) ?: return
        if (db.doses().markTaken(doseId, now) == 0) return // already taken
        val med = db.medicines().get(dose.medicineId) ?: return
        db.medicines().decrementPills(med.id, med.pillsPerDose)
        Notifier.cancelDose(ctx, doseId)
        ReminderScheduler.cancelSnooze(ctx, doseId)

        val updated = db.medicines().get(med.id) ?: return
        val left = Refill.daysLeft(updated.pillCount, updated.pillsPerDose, Schedule.parseTimes(updated.timesCsv).size)
        if (Refill.needsReorder(left)) {
            val prefs = Prefs(ctx)
            val text = Messages.reorder(updated.name, left, updated.patientName.ifBlank { prefs.patientName.ifBlank { "my family member" } })
            val url = Messages.whatsAppUrl(Messages.normalizePhone(prefs.pharmacyPhone), text)
            Notifier.linkAlert(
                ctx, 2_000_000 + updated.id.toInt(),
                ctx.getString(R.string.notif_reorder_title, updated.name, left), ctx.getString(R.string.notif_reorder_text), url,
            )
        }
    }
}
