package com.carecircle.data

import android.content.Context
import androidx.room.withTransaction
import com.carecircle.domain.Adherence
import com.carecircle.domain.AdherenceResult
import com.carecircle.domain.DoseRecord
import com.carecircle.domain.MedSchedule
import com.carecircle.domain.Refill
import com.carecircle.domain.SavedPolicy
import com.carecircle.domain.Schedule
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import retrofit2.HttpException

sealed interface InteractionResult {
    data class Ok(val response: InteractionResponse, val names: List<String>, val fromCache: Boolean) : InteractionResult
    /** Fewer than two generic names: no network call is made. */
    data object TooFew : InteractionResult
    data object RateLimited : InteractionResult
    data object Error : InteractionResult
}

sealed interface PolicyResult {
    data class Ok(val response: PolicyResponse) : PolicyResult
    data object TooLarge : PolicyResult
    data object RateLimited : PolicyResult
    data object Offline : PolicyResult
    data object Error : PolicyResult
}

sealed interface CardResult {
    data class Ok(val response: MedicineInfoResponse, val fromCache: Boolean, val fetchedAt: Long = System.currentTimeMillis()) : CardResult
    /** The server found nothing official for these names (HTTP 404/422 or an empty answer). */
    data class NotFound(val names: List<String>) : CardResult
    data object RateLimited : CardResult
    data object NoSalt : CardResult
    data object Error : CardResult
}

class Repository(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val db = AppDb.get(ctx)
    val people get() = db.people()
    val medicines get() = db.medicines()
    val doses get() = db.doses()
    private val cards = db.cards()
    private val interactionDao = db.interactions()

    private val policyDao = db.policies()
    fun savedPolicies(personId: Long) = policyDao.observeForPerson(personId)

    /** Sends OCR text only, only after the consent screen. Nothing is logged or kept by this call. */
    suspend fun policySummary(pages: List<PolicyPageText>): PolicyResult = try {
        PolicyResult.Ok(ApiFactory.api.policySummary(PolicyRequest(pages)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        when (e.code()) {
            413 -> PolicyResult.TooLarge
            429 -> PolicyResult.RateLimited
            else -> PolicyResult.Error
        }
    } catch (e: java.io.IOException) {
        PolicyResult.Offline
    } catch (e: Exception) {
        PolicyResult.Error
    }

    suspend fun savePolicy(personId: Long, r: PolicyResponse, now: Long = System.currentTimeMillis()) {
        val saved = SavedPolicy(r.summary, r.unverified, r.mode, now)
        policyDao.insert(PolicySummaryEntity(savedAt = now, json = ApiFactory.json.encodeToString(saved), personId = personId))
    }

    /** Adherence for one person over the last [days] days, computed from the schedule and stored events. Never touches the network. */
    suspend fun adherence(personId: Long, days: Int, now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): AdherenceResult {
        val (from, to) = Adherence.range(java.time.LocalDate.now(zone), days)
        val meds = db.medicines().forPersonIncludingArchived(personId)
        val ids = meds.map { it.id }.toSet()
        val events = db.doses().since(from.atStartOfDay(zone).toInstant().toEpochMilli()).filter { it.medicineId in ids }
        val schedules = meds.map { m ->
            val created = if (m.createdAt > 0) m.createdAt else events.filter { it.medicineId == m.id }.minOfOrNull { it.scheduledAt } ?: now
            MedSchedule(m.id, m.name, m.doseText, m.timesCsv, created, m.archivedAt ?: if (!m.active) now else null)
        }
        return Adherence.compute(schedules, events.map { DoseRecord(it.medicineId, it.scheduledAt, it.takenAt, it.skipped) }, from, to, now, zone)
    }

    suspend fun latestPolicy(personId: Long): SavedPolicy? =
        policyDao.forPerson(personId).firstOrNull()?.let { runCatching { ApiFactory.json.decodeFromString<SavedPolicy>(it.json) }.getOrNull() }

    // ---- People ----

    /** At least one person must always exist (fresh install, after delete-all, after a restore without people). */
    suspend fun ensureDefaultPerson(): Person {
        db.people().all().firstOrNull()?.let { return it }
        val p = Person(name = Persons.DEFAULT_NAME)
        return p.copy(id = db.people().insert(p))
    }

    suspend fun addPerson(name: String): Long {
        val sort = db.people().maxSort() + 1
        return db.people().insert(Person(name = Persons.cleanName(name).ifEmpty { Persons.DEFAULT_NAME }, sortOrder = sort))
    }

    suspend fun updatePerson(p: Person) = db.people().update(p.copy(name = Persons.cleanName(p.name).ifEmpty { Persons.DEFAULT_NAME }))

    /** Number of active medicines that deleting this person would remove (shown in the confirmation). */
    suspend fun medicineCount(personId: Long): Int = db.medicines().activeCount(personId)

    /**
     * Deletes one person and only their data: cancels their alarms, then removes doses, medicines and summaries.
     * The last remaining person cannot be deleted.
     */
    suspend fun deletePerson(personId: Long): Boolean {
        if (db.people().count() <= 1) return false
        val meds = db.medicines().forPersonIncludingArchived(personId)
        meds.forEach { com.carecircle.reminder.ReminderScheduler.cancel(appCtx, it.id) }
        db.doses().idsForPerson(personId).forEach { com.carecircle.reminder.ReminderScheduler.cancelSnooze(appCtx, it); com.carecircle.reminder.ReminderScheduler.cancelRepeats(appCtx, it) }
        db.withTransaction {
            db.symptoms().deleteForPerson(personId)
            db.doses().deleteForPerson(personId)
            db.policies().deleteForPerson(personId)
            db.medicines().deleteForPerson(personId)
            db.people().delete(personId)
        }
        return true
    }

    suspend fun deletePolicy(id: Long) = policyDao.delete(id)

    // ---- Side-effect diary ----
    fun symptoms(medicineId: Long) = db.symptoms().observeFor(medicineId)
    suspend fun logSymptom(e: SymptomLog) = db.symptoms().insert(e)
    suspend fun deleteSymptom(id: Long) = db.symptoms().delete(id)
    suspend fun symptomsSince(from: Long) = db.symptoms().since(from)

    companion object { const val MAX_INTERACTION_NAMES = 8 }

    fun daysLeft(m: Medicine): Int = Refill.daysLeft(m.pillCount, m.pillsPerDose, Schedule.parseTimes(m.timesCsv).size)

    /**
     * Network first; the saved copy is used only when the call itself fails (offline, timeout, 5xx).
     * An empty name never reaches the API. Names and responses are never logged.
     */
    suspend fun medicineCard(salt: String): CardResult {
        val name = salt.trim()
        if (name.isEmpty()) return CardResult.NoSalt
        val key = name.lowercase()
        return try {
            val fresh = ApiFactory.api.medicineInfo(name)
            if (fresh.cards.isEmpty()) return CardResult.NotFound(fresh.unresolved.ifEmpty { listOf(name) })
            cards.put(InfoCardEntity(key, ApiFactory.json.encodeToString(fresh), System.currentTimeMillis()))
            CardResult.Ok(fresh, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            when (e.code()) {
                404, 422 -> CardResult.NotFound(listOf(name))
                429 -> CardResult.RateLimited
                else -> fromCache(key)
            }
        } catch (e: Exception) {
            fromCache(key)
        }
    }

    /**
     * Splits stored generic names into single ingredients, lowercases, de-duplicates and sorts them.
     * Only these names ever leave the phone for an interaction check.
     */
    fun interactionNames(salts: List<String>): List<String> =
        salts.flatMap { it.split('+', ',', '&', ';') }.map { it.trim().lowercase() }.filter { it.length >= 3 }.distinct().sorted()

    /** Network first; the saved copy for the same name set is used only when the call fails. Names and snippets are never logged. */
    suspend fun interactionCheck(salts: List<String>): InteractionResult {
        val names = interactionNames(salts).take(MAX_INTERACTION_NAMES)
        if (names.size < 2) return InteractionResult.TooFew
        val key = names.joinToString("|")
        return try {
            val fresh = ApiFactory.api.interactions(names.joinToString(","))
            interactionDao.put(InteractionCacheEntity(key, ApiFactory.json.encodeToString(fresh), System.currentTimeMillis()))
            InteractionResult.Ok(fresh, names, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 429) InteractionResult.RateLimited else interactionFromCache(key, names)
        } catch (e: Exception) {
            interactionFromCache(key, names)
        }
    }

    private suspend fun interactionFromCache(key: String, names: List<String>): InteractionResult {
        val cached = interactionDao.get(key) ?: return InteractionResult.Error
        val decoded = runCatching { ApiFactory.json.decodeFromString<InteractionResponse>(cached.json) }.getOrNull()
        return if (decoded != null) InteractionResult.Ok(decoded, names, fromCache = true) else InteractionResult.Error
    }

    private suspend fun fromCache(key: String): CardResult {
        val cached = cards.get(key) ?: return CardResult.Error
        val decoded = runCatching { ApiFactory.json.decodeFromString<MedicineInfoResponse>(cached.json) }.getOrNull()
        return if (decoded != null) CardResult.Ok(decoded, fromCache = true, fetchedAt = cached.fetchedAt) else CardResult.Error
    }
}
