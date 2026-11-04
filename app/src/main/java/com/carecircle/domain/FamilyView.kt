package com.carecircle.domain

import java.time.LocalDate
import java.time.ZoneId

/** A followed person's event as the family view needs it. */
data class FamilyEvent(val type: SyncType, val at: Long, val medicine: String, val scheduledAt: Long = 0)

data class FamilyDay(val date: LocalDate, val taken: Int, val missed: Int, val skipped: Int) {
    val total get() = taken + missed + skipped
}

enum class Mood { ALL_TAKEN, ON_TRACK, MISSED, NO_DATA }

/** Pure helpers for the family screens: today's mood, the day bars and the organ shown for a person. */
object FamilyView {
    /** Mood of today from the patient phone's status counts. [fresh] is false when the status is from an earlier day. */
    fun mood(taken: Int, total: Int, missed: Int, fresh: Boolean): Mood = when {
        !fresh || total == 0 -> Mood.NO_DATA
        missed > 0 -> Mood.MISSED
        taken >= total -> Mood.ALL_TAKEN
        else -> Mood.ON_TRACK
    }

    fun fraction(taken: Int, total: Int): Float = if (total <= 0) 0f else (taken.toFloat() / total).coerceIn(0f, 1f)

    const val STALE_MS = 2 * 3600_000L

    /** The phone has not reported for over two hours. [updatedAt] 0 means never, which is not "stale" but "no data". */
    fun isStale(updatedAt: Long, now: Long, limitMs: Long = STALE_MS): Boolean = updatedAt > 0 && now - updatedAt > limitMs

    /**
     * One outcome per dose. A dose that was reported missed and then taken is Taken, and a stale "missed" that arrives after a
     * "taken" never overrides it. Skipped beats missed (a deliberate choice). Summaries are dropped.
     */
    fun merge(events: List<FamilyEvent>): List<FamilyEvent> =
        events.filter { it.type != SyncType.SUMMARY }
            .groupBy { if (it.scheduledAt > 0) "${it.scheduledAt}|${it.medicine}" else "at|${it.at}|${it.medicine}|${it.type.key}" }
            .map { (_, g) ->
                g.firstOrNull { it.type == SyncType.TAKEN } ?: g.firstOrNull { it.type == SyncType.SKIPPED } ?: g.maxByOrNull { it.at }!!
            }

    /** [count] days ending at [today], oldest first. Summaries are ignored: only taken, missed and skipped events count. */
    fun days(events: List<FamilyEvent>, today: LocalDate, count: Int, zone: ZoneId): List<FamilyDay> {
        val byDay = merge(events)
            .groupBy { java.time.Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
        return (count - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val l = byDay[d].orEmpty()
            FamilyDay(d, l.count { it.type == SyncType.TAKEN }, l.count { it.type == SyncType.MISSED }, l.count { it.type == SyncType.SKIPPED })
        }
    }

    /** Organs for the medicines seen, most common first; the generic body only when nothing else matched. */
    fun organs(medicines: List<String>): List<OrganKind> {
        val counts = mutableMapOf<OrganKind, Int>()
        medicines.mapNotNull { Conditions.infer(it, "")?.organ }.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        return counts.entries.sortedByDescending { it.value }.map { it.key }.ifEmpty { listOf(OrganKind.BODY) }
    }

    /** Distinct medicine labels (as sent) that map to [organ], for the tap-to-see list. */
    fun medicinesFor(organ: OrganKind, medicines: List<String>): List<String> =
        medicines.distinct().filter { (Conditions.infer(it, "")?.organ ?: OrganKind.BODY) == organ }
}
