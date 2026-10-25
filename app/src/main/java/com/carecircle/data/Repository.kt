package com.carecircle.data

import android.content.Context
import com.carecircle.domain.Refill
import com.carecircle.domain.Schedule
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import retrofit2.HttpException

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

    private suspend fun fromCache(key: String): CardResult {
        val cached = cards.get(key) ?: return CardResult.Error
        val decoded = runCatching { ApiFactory.json.decodeFromString<MedicineInfoResponse>(cached.json) }.getOrNull()
        return if (decoded != null) CardResult.Ok(decoded, fromCache = true) else CardResult.Error
    }
}
