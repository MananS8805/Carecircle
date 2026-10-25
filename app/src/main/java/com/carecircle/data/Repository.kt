package com.carecircle.data

import android.content.Context
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
    data class Ok(val response: MedicineInfoResponse, val fromCache: Boolean) : CardResult
    /** The server found nothing official for these names (HTTP 404/422 or an empty answer). */
    data class NotFound(val names: List<String>) : CardResult
    data object RateLimited : CardResult
    data object NoSalt : CardResult
    data object Error : CardResult
}

class Repository(ctx: Context) {
    private val db = AppDb.get(ctx)
    val medicines get() = db.medicines()
    val doses get() = db.doses()
    private val cards = db.cards()
    private val interactionDao = db.interactions()

    private val policyDao = db.policies()
    val savedPolicies get() = policyDao.observeAll()

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

    suspend fun savePolicy(r: PolicyResponse, now: Long = System.currentTimeMillis()) {
        val saved = SavedPolicy(r.summary, r.unverified, r.mode, now)
        policyDao.insert(PolicySummaryEntity(savedAt = now, json = ApiFactory.json.encodeToString(saved)))
    }

    suspend fun deletePolicy(id: Long) = policyDao.delete(id)

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
        return if (decoded != null) CardResult.Ok(decoded, fromCache = true) else CardResult.Error
    }
}
