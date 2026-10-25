package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AdherenceTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 25)
    private fun at(d: LocalDate, h: Int, m: Int = 0, zone: ZoneId = ist) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(today, 23, 0) // end of today: every dose today is decided

    private fun med(id: Long = 1, times: String = "08:00,20:00", created: Long = 0, archived: Long? = null, name: String = "Metformin") =
        MedSchedule(id, name, "500 mg", times, created, archived)

    private fun takenFor(m: MedSchedule, from: LocalDate, to: LocalDate, skip: (LocalDate, String) -> Boolean = { _, _ -> false }): List<DoseRecord> {
        val out = mutableListOf<DoseRecord>()
        var d = from
        while (!d.isAfter(to)) {
            Schedule.parseTimes(m.timesCsv).forEach { t ->
                if (!skip(d, t.toString())) out += DoseRecord(m.medicineId, d.atTime(t).atZone(ist).toInstant().toEpochMilli(), d.atTime(t).atZone(ist).toInstant().toEpochMilli() + 60_000)
            }
            d = d.plusDays(1)
        }
        return out
    }

    @Test fun fullyTakenWeekIsOneHundredPercent() {
        val from = today.minusDays(6)
        val m = med()
        val r = Adherence.compute(listOf(m), takenFor(m, from, today), from, today, now, ist)
        assertEquals(14, r.taken); assertEquals(0, r.missed); assertEquals(100, r.percent)
        assertEquals(7, r.days.size)
    }

    @Test fun mixedWeekCountsTakenAndMissedAndRounds() {
        val from = today.minusDays(6)
        val m = med()
        // 24 of 28 style: skip 2 of 14 -> 12 of 14 = 85.7 -> 86
        val ev = takenFor(m, from, today) { d, t -> (d == today.minusDays(2) && t == "20:00") || (d == today.minusDays(4) && t == "08:00") }
        val r = Adherence.compute(listOf(m), ev, from, today, now, ist)
        assertEquals(12, r.taken); assertEquals(2, r.missed); assertEquals(86, r.percent)
        assertEquals(2, r.perMedicine.single().missed)
    }

    @Test fun medicineAddedOnDayThreeDoesNotCountEarlierDays() {
        val from = today.minusDays(6)
        val added = at(today.minusDays(4), 9) // added after the morning dose of day 3
        val m = med(created = added)
        val r = Adherence.compute(listOf(m), emptyList(), from, today, now, ist)
        // from day 3 20:00 to today 20:00: 1 + 4*2 = 9 missed, nothing before that
        assertEquals(9, r.missed)
        assertEquals(0, r.days.first().total)
    }

    @Test fun medicineArchivedMidRangeStopsCounting() {
        val from = today.minusDays(6)
        val m = med(archived = at(today.minusDays(3), 12))
        val r = Adherence.compute(listOf(m), emptyList(), from, today, now, ist)
        // days -6..-3 morning, evening of -6..-4 and morning of -3 => 3 full days (6) + 1 morning = 7
        assertEquals(7, r.missed)
        assertEquals(0, r.days.last().total)
    }

    @Test fun noDataIsNeverZeroPercent() {
        val r = Adherence.compute(emptyList(), emptyList(), today.minusDays(6), today, now, ist)
        assertNull(r.percent)
        assertEquals(0, r.decided)
        // A medicine whose first dose is still ahead is pending, not 0%.
        val future = Adherence.compute(listOf(med(times = "23:30")), emptyList(), today, today, at(today, 10), ist)
        assertNull(future.percent); assertEquals(1, future.pending)
    }

    @Test fun doseInsideTheGraceWindowIsPendingNotMissed() {
        val r = Adherence.compute(listOf(med(times = "08:00")), emptyList(), today, today, at(today, 8, 20), ist)
        assertEquals(1, r.pending); assertEquals(0, r.missed)
        val later = Adherence.compute(listOf(med(times = "08:00")), emptyList(), today, today, at(today, 8, 30), ist)
        assertEquals(1, later.missed)
    }

    @Test fun skippedDosesAreCountedSeparatelyAndLowerThePercentage() {
        val m = med(times = "08:00")
        val ev = listOf(DoseRecord(1, at(today, 8), null, skipped = true))
        val r = Adherence.compute(listOf(m), ev, today, today, now, ist)
        assertEquals(1, r.skipped); assertEquals(0, r.percent)
    }

    @Test fun perMedicineIsSortedByMostMissedFirst() {
        val a = med(1, "08:00", name = "Aspirin"); val b = med(2, "08:00,20:00", name = "Metformin")
        val ev = takenFor(a, today.minusDays(2), today)
        val r = Adherence.compute(listOf(a, b), ev, today.minusDays(2), today, now, ist)
        assertEquals(listOf("Metformin", "Aspirin"), r.perMedicine.map { it.name })
    }

    @Test fun dayBoundaryFollowsTheDeviceTimeZone() {
        // 23:30 IST on the 24th is 18:00 UTC on the 24th; 00:30 IST on the 25th is 19:00 UTC on the 24th.
        val late = at(LocalDate.of(2026, 10, 24), 23, 30)
        val early = at(LocalDate.of(2026, 10, 25), 0, 30)
        assertEquals(LocalDate.of(2026, 10, 24), Adherence.dayOf(late, ist))
        assertEquals(LocalDate.of(2026, 10, 25), Adherence.dayOf(early, ist))
        assertEquals(LocalDate.of(2026, 10, 24), Adherence.dayOf(early, ZoneId.of("UTC")))
        // The same medicine schedule lands on different days when the device zone differs.
        val m = med(times = "00:30")
        val inIst = Adherence.compute(listOf(m), emptyList(), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25), now, ist)
        assertEquals(at(LocalDate.of(2026, 10, 25), 0, 30), inIst.lines.single().scheduledAt)
        val inUtc = Adherence.compute(listOf(m), emptyList(), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25), now, ZoneId.of("UTC"))
        assertTrue(inUtc.lines.single().scheduledAt != inIst.lines.single().scheduledAt)
    }

    @Test fun rangeIsInclusiveOfToday() {
        val (from, to) = Adherence.range(today, 7)
        assertEquals(today.minusDays(6), from); assertEquals(today, to)
        assertEquals(30, Adherence.range(today, 30).let { it.second.toEpochDay() - it.first.toEpochDay() + 1 }.toInt())
    }

    @Test fun dayLinesListStatusAndTimeTaken() {
        val m = med(times = "08:00")
        val ev = listOf(DoseRecord(1, at(today, 8), at(today, 8, 5)))
        val l = Adherence.compute(listOf(m), ev, today, today, now, ist).linesOn(today).single()
        assertEquals(DoseOutcome.TAKEN, l.outcome); assertEquals(at(today, 8, 5), l.takenAt)
    }
}
