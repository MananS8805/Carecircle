package com.carecircle.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.carecircle.domain.PendingNotifies
import com.carecircle.domain.PendingNotify
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/** Parent phone: ask the server to wake the family phone about a missed dose, retrying for up to two hours. */
object PushNotify {
    private const val WORK = "notify-missed"

    /** Called after a missed dose has been published. Holds one pending push per link, then tries to send. */
    suspend fun enqueueMissed(ctx: Context, personId: Long, doseScheduledAt: Long, medicineId: Long, timeLabel: String) {
        if (!CareSync.isConfigured(ctx)) return
        val links = AppDb.get(ctx).sync().linksFor(personId)
        if (links.isEmpty()) return
        val prefs = Prefs(ctx)
        var list = PendingNotifies.prune(PendingNotifies.decode(prefs.pendingNotify), System.currentTimeMillis())
        val now = System.currentTimeMillis()
        for (l in links) list = PendingNotifies.add(list, PendingNotify(l.linkId, "$doseScheduledAt-$medicineId", timeLabel.take(20), now))
        prefs.pendingNotify = PendingNotifies.encode(list)
        if (flush(ctx)) scheduleRetry(ctx)
    }

    /** Tries every pending push. Returns true while some are still waiting (offline, server error, sign-in not ready). */
    suspend fun flush(ctx: Context): Boolean {
        val prefs = Prefs(ctx)
        val active = AppDb.get(ctx).sync().links().map { it.linkId }.toSet() // unlinked links never get a push
        var list = PendingNotifies.forLinks(PendingNotifies.prune(PendingNotifies.decode(prefs.pendingNotify), System.currentTimeMillis()), active)
        val token = CareSync.idToken()
        for (e in list.toList()) {
            val outcome = if (token == null) Outcome.RETRY else send(token, e)
            if (outcome == Outcome.DONE) list = PendingNotifies.remove(list, e)
        }
        prefs.pendingNotify = PendingNotifies.encode(list)
        return list.isNotEmpty()
    }

    private enum class Outcome { DONE, RETRY }

    private suspend fun send(idToken: String, e: PendingNotify): Outcome = try {
        ApiFactory.api.notifyMissed("Bearer $idToken", NotifyMissedRequest(e.linkId, e.slotId, e.slotLabel))
        Outcome.DONE // sent, or the family phone's token was no longer valid and the server dropped it
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: HttpException) {
        when (ex.code()) {
            401, 408, 500, 502, 503, 504 -> Outcome.RETRY
            else -> Outcome.DONE // 403 link gone, 409 nobody to tell, 429 already sent or limited, 400/413 will never succeed
        }
    } catch (ex: Exception) {
        Outcome.RETRY // offline
    }

    fun scheduleRetry(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NotifyRetryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES).build(),
        )
    }
}

class NotifyRetryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = if (PushNotify.flush(applicationContext)) Result.retry() else Result.success()
}

/** Family phone: keeps its push token on the links it follows, only while the "missed-dose alerts" switch is on. */
object PushTokens {
    /** Writes the current token to every followed link, or removes it everywhere when push is off. */
    suspend fun sync(ctx: Context, knownToken: String? = null) {
        if (!CareSync.isConfigured(ctx)) return
        val prefs = Prefs(ctx)
        val links = AppDb.get(ctx).sync().following().map { it.linkId }
        if (links.isEmpty()) return
        if (!prefs.pushEnabled) { links.forEach { CareSync.setCaregiverToken(it, null) }; return }
        val token = knownToken ?: try {
            withTimeoutOrNull(15_000) { FirebaseMessaging.getInstance().token.awaitTask() }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null } ?: return
        links.forEach { CareSync.setCaregiverToken(it, token) }
    }
}
