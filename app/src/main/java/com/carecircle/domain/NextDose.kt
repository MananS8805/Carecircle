package com.carecircle.domain

import java.time.LocalTime
import java.time.ZoneId

data class NextDoseMed(
    val id: Long, val name: String, val doseText: String, val personId: Long, val timesCsv: String, val createdAt: Long, val archivedAt: Long? = null,
)

data class NextDoseEvent(val medicineId: Long, val scheduledAt: Long, val takenAt: Long?, val skipped: Boolean)

data class DoseSlot(val medicineId: Long, val name: String, val doseText: String, val personId: Long, val at: Long)

/** What the home-screen widget shows. [slots] share one 30-minute window; the first is the one the button acts on. */
sealed interface NextDose {
    data object Empty : NextDose
    data class Due(val slots: List<DoseSlot>, val overdue: Boolean) : NextDose
    data class Upcoming(val slots: List<DoseSlot>) : NextDose
    data class AllDone(val tomorrowFirst: DoseSlot?) : NextDose
}

object NextDoseCalc {
    const val WINDOW_MS = 30 * 60_000L
    /** A dose is "overdue" after the same 30 minutes the missed-dose alert waits. */
    const val OVERDUE_MS = 30 * 60_000L

    fun compute(now: Long, zone: ZoneId, meds: List<NextDoseMed>, events: List<NextDoseEvent>): NextDose {
        val live = meds.filter { Schedule.parseTimes(it.timesCsv).isNotEmpty() && (it.archivedAt == null || it.archivedAt > now) }
        if (live.isEmpty()) return NextDose.Empty
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun slotsOn(day: java.time.LocalDate): List<DoseSlot> = live.flatMap { m ->
            Schedule.parseTimes(m.timesCsv).map { t: LocalTime -> DoseSlot(m.id, m.name, m.doseText, m.personId, day.atTime(t).atZone(zone).toInstant().toEpochMilli()) }
                .filter { it.at >= m.createdAt }
        }.sortedWith(compareBy({ it.at }, { it.name }))

        val open = slotsOn(today).filter { s -> events.none { it.medicineId == s.medicineId && it.scheduledAt == s.at && (it.takenAt != null || it.skipped) } }
        if (open.isEmpty()) return NextDose.AllDone(slotsOn(today.plusDays(1)).firstOrNull())
        val first = open.first()
        val group = open.filter { it.at - first.at < WINDOW_MS }
        return if (first.at <= now) NextDose.Due(group, overdue = now - first.at > OVERDUE_MS) else NextDose.Upcoming(group)
    }
}
