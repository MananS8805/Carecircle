package com.carecircle.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.carecircle.data.AppDb
import com.carecircle.domain.MissedSweep
import com.carecircle.domain.Schedule
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Re-arms every alarm from the database and catches doses that passed while the phone was off. Idempotent: alarms use
 * stable request codes, so running it twice never duplicates one. No network is used for scheduling.
 */
class RescheduleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        ReminderScheduler.rescheduleAll(applicationContext, inputData.getString(KEY_REASON) ?: "unknown")
        sweepMissed(applicationContext)
        return Result.success()
    }

    companion object {
        private const val KEY_REASON = "reason"
        private const val ONE_TIME = "reschedule-now"
        private const val PERIODIC = "reschedule-safety-net"

        fun enqueue(ctx: Context, reason: String) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                ONE_TIME, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RescheduleWorker>().setInputData(workDataOf(KEY_REASON to reason)).build(),
            )
        }

        /** Every 12 hours (flex 2 h): the safety net if a broadcast was ever dropped by the system. */
        fun ensurePeriodic(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS, 2, TimeUnit.HOURS)
                    .setInputData(workDataOf(KEY_REASON to "periodic")).build(),
            )
        }

        /** Doses missed while the phone was off become "missed" records and reach the family view. No WhatsApp is opened. */
        suspend fun sweepMissed(ctx: Context, now: Long = System.currentTimeMillis()) {
            val db = AppDb.get(ctx)
            val zone = ZoneId.systemDefault()
            for (med in db.medicines().all()) {
                for (at in MissedSweep.overdueSlots(Schedule.parseTimes(med.timesCsv), now, zone, med.createdAt, med.archivedAt)) {
                    val existing = db.doses().find(med.id, at)
                    if (existing != null && (existing.takenAt != null || existing.skipped || existing.missedHandled)) continue
                    db.doses().insert(com.carecircle.data.DoseEvent(medicineId = med.id, scheduledAt = at))
                    val dose = db.doses().find(med.id, at) ?: continue
                    db.doses().markMissedHandled(dose.id)
                    DoseActions.publishMissed(ctx, dose, med)
                }
            }
            DoseWidget.refreshAll(ctx)
        }
    }
}
