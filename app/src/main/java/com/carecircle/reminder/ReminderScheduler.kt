package com.carecircle.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.carecircle.data.AppDb
import com.carecircle.domain.Escalation
import com.carecircle.domain.Schedule
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderScheduler {
    private fun pi(ctx: Context, medId: Long, at: Long) = PendingIntent.getBroadcast(
        ctx, medId.toInt(),
        Intent(ctx, DoseAlarmReceiver::class.java).putExtra(Notifier.EXTRA_MED_ID, medId).putExtra(Notifier.EXTRA_AT, at),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun snoozePi(ctx: Context, doseId: Long) = PendingIntent.getBroadcast(
        ctx, doseId.toInt(),
        Intent(ctx, SnoozeReceiver::class.java).setAction(SnoozeReceiver.ACTION_REPOST).putExtra(Notifier.EXTRA_DOSE_ID, doseId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun repeatPi(ctx: Context, doseId: Long, level: Int) = PendingIntent.getBroadcast(
        ctx, doseId.toInt() * 4 + level,
        Intent(ctx, SnoozeReceiver::class.java).setAction(SnoozeReceiver.ACTION_REPEAT)
            .putExtra(Notifier.EXTRA_DOSE_ID, doseId).putExtra(Notifier.EXTRA_LEVEL, level),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun dailyPi(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 9_900_001, Intent(ctx, DailySummaryReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Exact alarms need user-granted access on Android 12+. Fall back to inexact-but-idle-safe if denied. */
    private fun arm(am: AlarmManager, at: Long, p: PendingIntent) {
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    /** One alarm per medicine (the next dose). The receiver re-arms the following one. */
    suspend fun scheduleFor(ctx: Context, medId: Long, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
        val med = AppDb.get(ctx).medicines().get(medId)
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (med == null || !med.active) { cancel(ctx, medId); return }
        val next = Schedule.nextDose(Schedule.parseTimes(med.timesCsv), now) ?: run { cancel(ctx, medId); return }
        val at = next.toInstant().toEpochMilli()
        arm(am, at, pi(ctx, medId, at))
    }

    suspend fun scheduleAll(ctx: Context) {
        Notifier.ensureChannels(ctx)
        scheduleDailySummary(ctx)
        scheduleAppointments(ctx)
        AppDb.get(ctx).medicines().all().forEach { scheduleFor(ctx, it.id) }
    }

    fun cancel(ctx: Context, medId: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pi(ctx, medId, 0))
    }

    /** One-shot alarm that re-posts the same dose notification. Does not touch the missed-dose job. */
    fun scheduleSnooze(ctx: Context, doseId: Long, at: Long) {
        arm(ctx.getSystemService(AlarmManager::class.java), at, snoozePi(ctx, doseId))
    }

    /** Reminders at +10 and +20 minutes while the dose is still open. Cancelled when it is taken or skipped. */
    fun scheduleRepeats(ctx: Context, doseId: Long, scheduledAt: Long, now: Long = System.currentTimeMillis()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        Escalation.repeatTimes(scheduledAt).forEachIndexed { i, at -> if (at > now) arm(am, at, repeatPi(ctx, doseId, i + 1)) }
    }

    fun cancelRepeats(ctx: Context, doseId: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (level in 1..Escalation.REPEAT_AFTER_MINUTES.size) am.cancel(repeatPi(ctx, doseId, level))
    }

    private fun apptPi(ctx: Context, id: Long) = PendingIntent.getBroadcast(
        ctx, 8_000_000 + id.toInt(),
        Intent(ctx, AppointmentReceiver::class.java).putExtra(Notifier.EXTRA_APPT_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun scheduleAppointment(ctx: Context, a: com.carecircle.data.Appointment, now: Long = System.currentTimeMillis()) {
        val lite = com.carecircle.domain.ApptLite(a.id, a.startsAt, a.remindBefore)
        if (com.carecircle.domain.Appointments.shouldArm(lite, now)) arm(ctx.getSystemService(AlarmManager::class.java), com.carecircle.domain.Appointments.remindAt(lite), apptPi(ctx, a.id))
    }

    fun cancelAppointment(ctx: Context, id: Long) { ctx.getSystemService(AlarmManager::class.java).cancel(apptPi(ctx, id)) }

    suspend fun scheduleAppointments(ctx: Context) {
        AppDb.get(ctx).appointments().all().forEach { scheduleAppointment(ctx, it) }
    }

    /** The evening summary notification, once a day at 9 pm. */
    fun scheduleDailySummary(ctx: Context, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
        val at = Escalation.nextDailySummary(now).toInstant().toEpochMilli()
        arm(ctx.getSystemService(AlarmManager::class.java), at, dailyPi(ctx))
    }

    fun cancelSnooze(ctx: Context, doseId: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(snoozePi(ctx, doseId))
    }
}
