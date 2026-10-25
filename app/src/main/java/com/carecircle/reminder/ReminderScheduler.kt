package com.carecircle.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.carecircle.data.AppDb
import com.carecircle.domain.Schedule
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderScheduler {
    private fun pi(ctx: Context, medId: Long, at: Long) = PendingIntent.getBroadcast(
        ctx, medId.toInt(),
        Intent(ctx, DoseAlarmReceiver::class.java).putExtra(Notifier.EXTRA_MED_ID, medId).putExtra(Notifier.EXTRA_AT, at),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** One alarm per medicine (the next dose). The receiver re-arms the following one. */
    suspend fun scheduleFor(ctx: Context, medId: Long, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())) {
        val med = AppDb.get(ctx).medicines().get(medId)
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (med == null || !med.active) { cancel(ctx, medId); return }
        val next = Schedule.nextDose(Schedule.parseTimes(med.timesCsv), now) ?: run { cancel(ctx, medId); return }
        val at = next.toInstant().toEpochMilli()
        val p = pi(ctx, medId, at)
        // Exact alarms need user-granted access on Android 12+. Fall back to inexact-but-idle-safe if denied.
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    suspend fun scheduleAll(ctx: Context) {
        Notifier.ensureChannels(ctx)
        AppDb.get(ctx).medicines().all().forEach { scheduleFor(ctx, it.id) }
    }

    fun cancel(ctx: Context, medId: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pi(ctx, medId, 0))
    }
}
