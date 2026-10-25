package com.carecircle.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.carecircle.data.AppDb
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

    fun cancelSnooze(ctx: Context, doseId: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(snoozePi(ctx, doseId))
    }
}
