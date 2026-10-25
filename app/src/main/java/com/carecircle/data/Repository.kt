package com.carecircle.data

import android.content.Context
import com.carecircle.domain.Refill
import com.carecircle.domain.Schedule
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

sealed interface CardResult {
    data class Ok(val response: MedicineInfoResponse, val fromCache: Boolean) : CardResult
    data class Error(val message: String) : CardResult
}

class Repository(ctx: Context) {
    private val db = AppDb.get(ctx)
    val medicines get() = db.medicines()
    val doses get() = db.doses()
    private val cards = db.cards()

    fun daysLeft(m: Medicine): Int = Refill.daysLeft(m.pillCount, m.pillsPerDose, Schedule.parseTimes(m.timesCsv).size)

    /** Cache-first for offline use; refreshes in the background when online. */
    suspend fun medicineCard(salt: String): CardResult {
        val key = salt.trim().lowercase()
        val cached = cards.get(key)
        return try {
            val fresh = ApiFactory.api.medicineInfo(salt)
            cards.put(InfoCardEntity(key, ApiFactory.json.encodeToString(fresh), System.currentTimeMillis()))
            CardResult.Ok(fresh, fromCache = false)
        } catch (e: Exception) {
            if (cached != null) {
                runCatching { ApiFactory.json.decodeFromString<MedicineInfoResponse>(cached.json) }
                    .getOrNull()?.let { return CardResult.Ok(it, fromCache = true) }
            }
            CardResult.Error("Could not load information (${e.javaClass.simpleName}). Check your connection.")
        }
    }
}
