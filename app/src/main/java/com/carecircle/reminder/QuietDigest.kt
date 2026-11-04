package com.carecircle.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.data.Prefs
import com.carecircle.domain.AlertDecision
import com.carecircle.domain.HeldAlerts
import com.carecircle.domain.QuietHours
import com.carecircle.domain.QuietSettings
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Family phone: alerts held during quiet hours or a snooze come out as ONE digest when the quiet ends. */
object QuietDigest {
    private const val WORK = "quiet-digest"
    private const val NOTIF_ID = 6_000_001

    fun settings(ctx: Context): QuietSettings {
        val p = Prefs(ctx)
        fun time(s: String, d: LocalTime) = runCatching { LocalTime.parse(s) }.getOrDefault(d)
        return QuietSettings(p.quietEnabled, time(p.quietStart, LocalTime.of(22, 0)), time(p.quietEnd, LocalTime.of(7, 0)), p.snoozeUntil)
    }

    /** Called when an alert is held: make sure a job will release it. */
    fun scheduleRelease(ctx: Context, now: Long = System.currentTimeMillis()) {
        if (HeldAlerts.decode(Prefs(ctx).heldAlerts).isEmpty()) { WorkManager.getInstance(ctx).cancelUniqueWork(WORK); return }
        val at = QuietHours.releaseAt(now, ZoneId.systemDefault(), settings(ctx)) ?: now
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DigestWorker>().setInitialDelay((at - now).coerceAtLeast(0) + 1_000, TimeUnit.MILLISECONDS).build(),
        )
    }

    /**
     * If something is held and it is no longer quiet or snoozed, posts the digest (when [notify]) and empties the list.
     * Otherwise it re-arms the job. Returns how many alerts were released.
     */
    fun flushIfDue(ctx: Context, notify: Boolean = true, now: Long = System.currentTimeMillis()): Int {
        val prefs = Prefs(ctx)
        val held = HeldAlerts.decode(prefs.heldAlerts)
        if (held.isEmpty()) return 0
        if (QuietHours.shouldShow(now, ZoneId.systemDefault(), settings(ctx)) != AlertDecision.SHOW) { scheduleRelease(ctx, now); return 0 }
        prefs.heldAlerts = ""
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        if (notify) {
            val lc = AppLocale.wrap(ctx)
            val title = if (held.size == 1) lc.getString(R.string.digest_one) else lc.getString(R.string.digest_many, held.size)
            Notifier.caregiverAlert(ctx, NOTIF_ID, title, lc.getString(R.string.digest_text))
        }
        return held.size
    }

    /** Held alerts that are waiting although the quiet period is over, for the banner (for example after a force-stop). */
    fun waitingCount(ctx: Context, now: Long = System.currentTimeMillis()): Int {
        val held = HeldAlerts.decode(Prefs(ctx).heldAlerts)
        return if (held.isNotEmpty() && QuietHours.shouldShow(now, ZoneId.systemDefault(), settings(ctx)) == AlertDecision.SHOW) held.size else 0
    }
}

class DigestWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        QuietDigest.flushIfDue(applicationContext)
        return Result.success()
    }
}
