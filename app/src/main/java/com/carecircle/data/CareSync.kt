package com.carecircle.data

import android.content.Context
import com.carecircle.domain.PairingCode
import com.carecircle.domain.SyncEvent
import com.carecircle.domain.SyncType
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

/** One thing that happened to a followed person, as the family sees it. */
data class SyncEventDoc(
    val id: String, val type: SyncType, val personName: String, val medicine: String,
    val scheduledAt: Long, val at: Long, val taken: Int, val total: Int,
)

/** Today at a glance for one followed person, kept up to date by the patient phone. */
data class StatusDoc(
    val day: Long, val taken: Int, val total: Int, val missed: Int, val skipped: Int,
    val nextAt: Long, val nextMedicine: String, val updatedAt: Long,
)

sealed interface PairResult {
    data class Ok(val code: String) : PairResult
    data object NotConfigured : PairResult
    data object Offline : PairResult
    data object Error : PairResult
}

sealed interface FollowResult {
    data class Ok(val linkId: String, val personName: String) : FollowResult
    data object Invalid : FollowResult
    data object NotFound : FollowResult
    data object Expired : FollowResult
    /** The code is already used by someone else. */
    data object Taken : FollowResult
    data object NotConfigured : FollowResult
    data object Offline : FollowResult
    data object Error : FollowResult
}

enum class LinkState { WAITING, LINKED, GONE, UNKNOWN }

/**
 * Caregiver sync on Firebase (anonymous sign-in + Firestore). A patient phone creates a short-lived code; a family
 * member's phone enters it. Only names, medicine names, taken/missed/skipped and times are written, after consent.
 * Nothing here ever throws into the reminder code: every call degrades to "not synced".
 */
object CareSync {
    private const val PAIRINGS = "pairings"
    private const val EVENTS = "events"
    private const val STATUS = "status"
    private const val RETAIN_DAYS = 14L
    private const val NETWORK_MS = 15_000L

    fun isConfigured(ctx: Context): Boolean = runCatching { FirebaseApp.getApps(ctx.applicationContext).isNotEmpty() }.getOrDefault(false)

    private val db get() = FirebaseFirestore.getInstance()

    private suspend fun uid(): String? = try {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.uid ?: withTimeoutOrNull(NETWORK_MS) { auth.signInAnonymously().awaitTask().user?.uid }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    // ---- patient side ----

    /** Creates a code for [personId]. The caller must have shown the consent screen first. */
    suspend fun createPairing(ctx: Context, personId: Long, personName: String): PairResult {
        if (!isConfigured(ctx)) return PairResult.NotConfigured
        val me = uid() ?: return PairResult.Offline
        val now = System.currentTimeMillis()
        repeat(3) {
            val code = PairingCode.generate()
            val data = mapOf(
                "ownerUid" to me, "personName" to personName.take(40),
                "createdAt" to Timestamp(Date(now)),
                "expiresAt" to Timestamp(Date(now + PairingCode.VALID_MINUTES * 60_000L)),
                "caregiverUid" to null,
            )
            try {
                withTimeout(NETWORK_MS) { db.collection(PAIRINGS).document(code).set(data).awaitTask() }
                AppDb.get(ctx).sync().putLink(SyncLink(code, personId, now))
                return PairResult.Ok(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) return PairResult.Error // else: code collision, try another
            } catch (e: Exception) {
                return PairResult.Offline
            }
        }
        return PairResult.Error
    }

    /** Whether the family member has entered the code yet. */
    suspend fun linkState(linkId: String): LinkState = try {
        val snap = withTimeout(NETWORK_MS) { db.collection(PAIRINGS).document(linkId).get().awaitTask() }
        when {
            !snap.exists() -> LinkState.GONE
            snap.getString("caregiverUid") != null -> LinkState.LINKED
            else -> LinkState.WAITING
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LinkState.UNKNOWN
    }

    /**
     * Sends one event to everyone this phone shares [personId] with. Safe to call from receivers and workers; waits a few
     * seconds at most (Firestore keeps the write and sends it when the network returns).
     */
    suspend fun publish(ctx: Context, personId: Long, event: SyncEvent, doseId: Long = 0) {
        if (!isConfigured(ctx)) return
        try {
            val links = AppDb.get(ctx).sync().linksFor(personId)
            if (links.isEmpty()) return
            uid() ?: return
            val body = mapOf(
                "type" to event.type.key, "person" to event.personName.take(40), "medicine" to event.medicine.take(80),
                "scheduledAt" to event.scheduledAt, "at" to event.at, "taken" to event.taken, "total" to event.total,
            )
            for (l in links) {
                val events = db.collection(PAIRINGS).document(l.linkId).collection(EVENTS)
                withTimeoutOrNull(6_000) { events.document(event.id(doseId)).set(body).awaitTask() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never break reminders because sync failed.
        }
    }

    /**
     * Refreshes the "today" summary the family sees: doses taken / scheduled / missed and the next dose. Called after every
     * dose event and when the app opens. Same privacy as events: counts, a time and a medicine name.
     */
    suspend fun publishStatus(ctx: Context, personId: Long, tidy: Boolean = false): Boolean {
        if (!isConfigured(ctx)) return false
        try {
            val links = AppDb.get(ctx).sync().linksFor(personId)
            if (links.isEmpty()) return false
            uid() ?: return false
            val r = Repository(ctx).adherence(personId, 1)
            val now = System.currentTimeMillis()
            val next = r.lines.filter { it.scheduledAt > now && it.outcome == com.carecircle.domain.DoseOutcome.PENDING }.minByOrNull { it.scheduledAt }
            val body = mapOf(
                "day" to java.time.LocalDate.now().toEpochDay(), "taken" to r.taken,
                "total" to (r.taken + r.missed + r.skipped + r.pending), "missed" to r.missed, "skipped" to r.skipped,
                "nextAt" to (next?.scheduledAt ?: 0L), "nextMedicine" to (next?.name.orEmpty().take(80)), "updatedAt" to now,
            )
            var ok = true
            for (l in links) {
                val sent = withTimeoutOrNull(6_000) {
                    db.collection(PAIRINGS).document(l.linkId).collection(STATUS).document("current").set(body).awaitTask()
                    true
                }
                if (sent != true) ok = false
                if (tidy) prune(db.collection(PAIRINGS).document(l.linkId).collection(EVENTS))
            }
            return ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false // Never break reminders because sync failed.
        }
    }

    private suspend fun prune(events: com.google.firebase.firestore.CollectionReference) {
        val cutoff = System.currentTimeMillis() - RETAIN_DAYS * 86_400_000L
        withTimeoutOrNull(4_000) {
            events.whereLessThan("at", cutoff).limit(25).get().awaitTask().documents.forEach { it.reference.delete() }
        }
    }

    /** Stops sharing: removes the stored events and the pairing, then forgets it on this phone. */
    suspend fun unlinkOwner(ctx: Context, linkId: String) {
        try {
            if (isConfigured(ctx) && uid() != null) {
                val ref = db.collection(PAIRINGS).document(linkId)
                withTimeoutOrNull(NETWORK_MS) {
                    ref.collection(EVENTS).limit(400).get().awaitTask().documents.forEach { it.reference.delete() }
                    ref.collection(STATUS).get().awaitTask().documents.forEach { it.reference.delete() }
                    ref.delete().awaitTask()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort; the local record is removed regardless so this phone stops sending.
        }
        AppDb.get(ctx).sync().deleteLink(linkId)
    }

    // ---- family side ----

    suspend fun follow(ctx: Context, input: String): FollowResult {
        if (!isConfigured(ctx)) return FollowResult.NotConfigured
        val code = PairingCode.normalize(input)
        if (!PairingCode.isValid(code)) return FollowResult.Invalid
        val me = uid() ?: return FollowResult.Offline
        val ref = db.collection(PAIRINGS).document(code)
        try {
            val snap = withTimeout(NETWORK_MS) { ref.get().awaitTask() }
            if (!snap.exists()) return FollowResult.NotFound
            val cg = snap.getString("caregiverUid")
            if (cg != null && cg != me) return FollowResult.Taken
            val expires = snap.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
            if (cg == null && expires < System.currentTimeMillis()) return FollowResult.Expired
            if (cg == null) {
                withTimeout(NETWORK_MS) { ref.update(mapOf("caregiverUid" to me, "linkedAt" to Timestamp.now())).awaitTask() }
            }
            val name = snap.getString("personName").orEmpty()
            val now = System.currentTimeMillis()
            AppDb.get(ctx).sync().putFollowing(Following(code, name, now, now))
            return FollowResult.Ok(code, name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            return if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) FollowResult.Taken else FollowResult.Error
        } catch (e: Exception) {
            return FollowResult.Offline
        }
    }

    private fun DocumentSnapshot.toEvent(): SyncEventDoc? {
        val type = SyncType.fromKey(getString("type")) ?: return null
        return SyncEventDoc(
            id = id, type = type, personName = getString("person").orEmpty(), medicine = getString("medicine").orEmpty(),
            scheduledAt = getLong("scheduledAt") ?: 0L, at = getLong("at") ?: return null,
            taken = (getLong("taken") ?: 0L).toInt(), total = (getLong("total") ?: 0L).toInt(),
        )
    }

    /** Live list for the Family screen: newest first. Ends when the collector stops. */
    fun observeEvents(linkId: String): Flow<List<SyncEventDoc>> = callbackFlow {
        val reg = db.collection(PAIRINGS).document(linkId).collection(EVENTS)
            .orderBy("at", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, _ -> if (snap != null) trySend(snap.documents.mapNotNull { it.toEvent() }) }
        awaitClose { reg.remove() }
    }

    /** Live "today" summary of a followed person; null until the patient phone has sent one. */
    fun observeStatus(linkId: String): Flow<StatusDoc?> = callbackFlow {
        val reg = db.collection(PAIRINGS).document(linkId).collection(STATUS).document("current")
            .addSnapshotListener { snap, _ ->
                if (snap != null) trySend(
                    if (!snap.exists()) null else StatusDoc(
                        day = snap.getLong("day") ?: 0L, taken = (snap.getLong("taken") ?: 0L).toInt(), total = (snap.getLong("total") ?: 0L).toInt(),
                        missed = (snap.getLong("missed") ?: 0L).toInt(), skipped = (snap.getLong("skipped") ?: 0L).toInt(),
                        nextAt = snap.getLong("nextAt") ?: 0L, nextMedicine = snap.getString("nextMedicine").orEmpty(), updatedAt = snap.getLong("updatedAt") ?: 0L,
                    ),
                )
            }
        awaitClose { reg.remove() }
    }

    /** For the background check on the family member's phone. Null when it could not be read. */
    suspend fun fetchSince(ctx: Context, linkId: String, since: Long): List<SyncEventDoc>? {
        if (!isConfigured(ctx) || uid() == null) return null
        return try {
            withTimeout(NETWORK_MS) {
                db.collection(PAIRINGS).document(linkId).collection(EVENTS).whereGreaterThan("at", since).orderBy("at").limit(50)
                    .get().awaitTask().documents.mapNotNull { it.toEvent() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Stop following: ends the link for both sides and forgets it here. */
    suspend fun unfollow(ctx: Context, linkId: String) {
        try {
            if (isConfigured(ctx) && uid() != null) withTimeoutOrNull(NETWORK_MS) { db.collection(PAIRINGS).document(linkId).delete().awaitTask() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort.
        }
        AppDb.get(ctx).sync().deleteFollowing(linkId)
    }

    /** Used by "Delete all my data": removes every link in the cloud as far as the network allows. */
    suspend fun unlinkAll(ctx: Context) {
        val dao = AppDb.get(ctx).sync()
        dao.links().forEach { unlinkOwner(ctx, it.linkId) }
        dao.following().forEach { unfollow(ctx, it.linkId) }
    }
}
