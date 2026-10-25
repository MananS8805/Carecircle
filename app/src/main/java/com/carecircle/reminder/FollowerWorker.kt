package com.carecircle.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.data.AppDb
import com.carecircle.data.CareSync
import com.carecircle.domain.SyncType
import com.carecircle.ui.describeEvent
import java.util.concurrent.TimeUnit

/**
 * On the family member's phone: about every 15 minutes (the Android minimum) look for new missed doses and daily
 * summaries of the people being followed and raise a notification. While the app is open the Family screen is live.
 */
class FollowerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!CareSync.isConfigured(ctx)) return Result.success()
        val dao = AppDb.get(ctx).sync()
        val lc = AppLocale.wrap(ctx)
        for (f in dao.following()) {
            val events = CareSync.fetchSince(ctx, f.linkId, f.lastSeenAt) ?: continue
            for (e in events.filter { it.type == SyncType.MISSED || it.type == SyncType.SUMMARY }) {
                val title = if (e.type == SyncType.MISSED) lc.getString(R.string.notif_sync_missed_title, e.personName) else lc.getString(R.string.notif_sync_summary_title, e.personName)
                Notifier.plainAlert(ctx, 5_000_000 + (e.id.hashCode() and 0xFFFFF), title, describeEvent(lc, e))
            }
            events.maxOfOrNull { it.at }?.let { dao.markSeen(f.linkId, it) }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "follower-poll"

        /** Starts the periodic check. Safe to call on every launch. */
        fun ensure(ctx: Context) {
            if (!CareSync.isConfigured(ctx)) return
            val req = PeriodicWorkRequestBuilder<FollowerWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
