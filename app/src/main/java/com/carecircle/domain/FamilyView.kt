package com.carecircle.domain

import java.time.LocalDate
import java.time.ZoneId

/** A followed person's event as the family view needs it. */
data class FamilyEvent(val type: SyncType, val at: Long, val medicine: String)

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

    /** [count] days ending at [today], oldest first. Summaries are ignored: only taken, missed and skipped events count. */
    fun days(events: List<FamilyEvent>, today: LocalDate, count: Int, zone: ZoneId): List<FamilyDay> {
        val byDay = events.filter { it.type != SyncType.SUMMARY }
            .groupBy { java.time.Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
        return (count - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val l = byDay[d].orEmpty()
            FamilyDay(d, l.count { it.type == SyncType.TAKEN }, l.count { it.type == SyncType.MISSED }, l.count { it.type == SyncType.SKIPPED })
        }
    }

    /** Organs for the medicines seen, most common first; the generic body only when nothing else matched. */
    fun organs(medicines: List<String>): List<OrganKind> {
        val counts = medicines.mapNotNull { Conditions.infer(it, "")?.organ }.groupingBy { it }.eachCount()
        return counts.entries.sortedByDescending { it.value }.map { it.key }.ifEmpty { listOf(OrganKind.BODY) }
    }

    /** Distinct medicine labels (as sent) that map to [organ], for the tap-to-see list. */
    fun medicinesFor(organ: OrganKind, medicines: List<String>): List<String> =
        medicines.distinct().filter { (Conditions.infer(it, "")?.organ ?: OrganKind.BODY) == organ }
}
