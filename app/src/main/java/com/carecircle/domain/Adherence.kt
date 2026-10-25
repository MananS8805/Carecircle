package com.carecircle.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class DoseOutcome { TAKEN, MISSED, SKIPPED, PENDING }

/** A medicine as the schedule sees it. [createdAt] and [archivedAt] bound when its doses count. */
data class MedSchedule(val medicineId: Long, val name: String, val doseText: String, val timesCsv: String, val createdAt: Long, val archivedAt: Long?)

/** What the database knows about one scheduled dose. */
data class DoseRecord(val medicineId: Long, val scheduledAt: Long, val takenAt: Long?, val skipped: Boolean = false)

data class DoseLine(val date: LocalDate, val medicineId: Long, val name: String, val scheduledAt: Long, val outcome: DoseOutcome, val takenAt: Long?)

data class DayStat(val date: LocalDate, val taken: Int, val missed: Int, val skipped: Int, val pending: Int) {
    val total get() = taken + missed + skipped + pending
}

data class MedStat(val medicineId: Long, val name: String, val doseText: String, val taken: Int, val missed: Int, val skipped: Int)

data class AdherenceResult(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DayStat>,
    val lines: List<DoseLine>,
    val perMedicine: List<MedStat>,
) {
    val taken: Int = days.sumOf { it.taken }
    val missed: Int = days.sumOf { it.missed }
    val skipped: Int = days.sumOf { it.skipped }
    val pending: Int = days.sumOf { it.pending }

    /** Doses that are decided (not pending). */
    val decided: Int get() = taken + missed + skipped

    /** Whole-number percentage of decided doses that were taken, or null when there is nothing to judge. */
    val percent: Int? get() = if (decided == 0) null else Math.round(taken * 100.0 / decided).toInt()

    fun linesOn(date: LocalDate) = lines.filter { it.date == date }
}

object Adherence {
    /** A 7- or 30-day range ending today (inclusive). */
    fun range(today: LocalDate, days: Int): Pair<LocalDate, LocalDate> = today.minusDays((days - 1).toLong()) to today

    /**
     * Counts every scheduled dose in [from]..[to] (device time zone). Days before a medicine was added, and times after it
     * was archived, do not count. A dose that is due but inside the grace window is still pending.
     */
    fun compute(
        meds: List<MedSchedule>, events: List<DoseRecord>, from: LocalDate, to: LocalDate, now: Long, zone: ZoneId,
        graceMinutes: Long = MissedDose.GRACE_MINUTES,
    ): AdherenceResult {
        val byKey = events.associateBy { it.medicineId to it.scheduledAt }
        val lines = mutableListOf<DoseLine>()
        var day = from
        while (!day.isAfter(to)) {
            for (m in meds) {
                for (t in Schedule.parseTimes(m.timesCsv)) {
                    val at = day.atTime(t).atZone(zone).toInstant().toEpochMilli()
                    if (at < m.createdAt) continue
                    if (m.archivedAt != null && at >= m.archivedAt) continue
                    val ev = byKey[m.medicineId to at]
                    val outcome = when {
                        ev?.takenAt != null -> DoseOutcome.TAKEN
                        ev?.skipped == true -> DoseOutcome.SKIPPED
                        MissedDose.isMissed(at, now, null, graceMinutes) -> DoseOutcome.MISSED
                        else -> DoseOutcome.PENDING
                    }
                    lines += DoseLine(day, m.medicineId, m.name, at, outcome, ev?.takenAt)
                }
            }
            day = day.plusDays(1)
        }
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
            val l = lines.filter { it.date == d }
            DayStat(d, l.count { it.outcome == DoseOutcome.TAKEN }, l.count { it.outcome == DoseOutcome.MISSED },
                l.count { it.outcome == DoseOutcome.SKIPPED }, l.count { it.outcome == DoseOutcome.PENDING })
        }.toList()
        val perMed = meds.map { m ->
            val l = lines.filter { it.medicineId == m.medicineId }
            MedStat(m.medicineId, m.name, m.doseText, l.count { it.outcome == DoseOutcome.TAKEN },
                l.count { it.outcome == DoseOutcome.MISSED }, l.count { it.outcome == DoseOutcome.SKIPPED })
        }.filter { it.taken + it.missed + it.skipped > 0 }
            .sortedWith(compareByDescending<MedStat> { it.missed }.thenBy { it.name.lowercase() })
        return AdherenceResult(from, to, days, lines.sortedBy { it.scheduledAt }, perMed)
    }

    /** The calendar day an instant falls on in [zone]. Used for the day boundary, which follows the device time zone. */
    fun dayOf(epochMs: Long, zone: ZoneId): LocalDate = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    fun timeOf(epochMs: Long, zone: ZoneId): LocalTime = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
}
