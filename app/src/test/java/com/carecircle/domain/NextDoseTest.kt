package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NextDoseTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 4)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private fun med(id: Long, name: String, times: String, created: Long = 0, archived: Long? = null) = NextDoseMed(id, name, "500 mg", 1, times, created, archived)
    private fun taken(id: Long, h: Int, m: Int = 0) = NextDoseEvent(id, at(h, m), at(h, m) + 60_000, false)

    @Test fun noMedicinesIsEmpty() {
        assertEquals(NextDose.Empty, NextDoseCalc.compute(at(9), zone, emptyList(), emptyList()))
        assertEquals(NextDose.Empty, NextDoseCalc.compute(at(9), zone, listOf(med(1, "A", "")), emptyList()))
    }

    @Test fun upcomingBeforeTheFirstDose() {
        val r = NextDoseCalc.compute(at(7), zone, listOf(med(1, "Metformin", "08:00,20:00")), emptyList())
        assertTrue(r is NextDose.Upcoming)
        assertEquals(at(8), (r as NextDose.Upcoming).slots.single().at)
    }

    @Test fun dueRightAfterTheDoseTime() {
        val r = NextDoseCalc.compute(at(8, 10), zone, listOf(med(1, "Metformin", "08:00")), emptyList()) as NextDose.Due
        assertEquals(false, r.overdue)
    }

    @Test fun overdueAfterThirtyMinutes() {
        val r = NextDoseCalc.compute(at(8, 45), zone, listOf(med(1, "Metformin", "08:00")), emptyList()) as NextDose.Due
        assertTrue(r.overdue)
    }

    @Test fun twoDosesInOneSlotAreGroupedAndSortedByName() {
        val meds = listOf(med(2, "Zinc", "08:00"), med(1, "Aspirin", "08:00"), med(3, "Evening", "20:00"))
        val r = NextDoseCalc.compute(at(8, 5), zone, meds, emptyList()) as NextDose.Due
        assertEquals(listOf("Aspirin", "Zinc"), r.slots.map { it.name })
        // Taking one leaves the other as the next to act on.
        val after = NextDoseCalc.compute(at(8, 6), zone, meds, listOf(taken(1, 8))) as NextDose.Due
        assertEquals(listOf("Zinc"), after.slots.map { it.name })
    }

    @Test fun doseWithinThirtyMinutesJoinsTheGroupButAnHourLaterDoesNot() {
        val meds = listOf(med(1, "A", "08:00"), med(2, "B", "08:20"), med(3, "C", "09:30"))
        val r = NextDoseCalc.compute(at(8, 1), zone, meds, emptyList()) as NextDose.Due
        assertEquals(listOf("A", "B"), r.slots.map { it.name })
    }

    @Test fun allDoneShowsTomorrowsFirstDose() {
        val meds = listOf(med(1, "A", "08:00"), med(2, "B", "07:30,20:00"))
        val ev = listOf(taken(1, 8), taken(2, 7, 30), taken(2, 20))
        val r = NextDoseCalc.compute(at(21), zone, meds, ev) as NextDose.AllDone
        assertEquals(at(7, 30, day.plusDays(1)), r.tomorrowFirst!!.at)
        assertEquals("B", r.tomorrowFirst!!.name)
    }

    @Test fun skippedCountsAsDone() {
        val ev = listOf(NextDoseEvent(1, at(8), null, skipped = true))
        assertTrue(NextDoseCalc.compute(at(9), zone, listOf(med(1, "A", "08:00")), ev) is NextDose.AllDone)
    }

    @Test fun medicineAddedAfterTheSlotDoesNotShowItAsDue() {
        val r = NextDoseCalc.compute(at(12), zone, listOf(med(1, "A", "08:00", created = at(10))), emptyList())
        assertTrue(r is NextDose.AllDone)
    }

    @Test fun deletedMedicineIsIgnoredEvenWithEvents() {
        val gone = med(1, "Old", "08:00", archived = at(7))
        val r = NextDoseCalc.compute(at(9), zone, listOf(gone), listOf(taken(1, 8)))
        assertEquals(NextDose.Empty, r)
        assertNull((NextDoseCalc.compute(at(9), zone, listOf(gone, med(2, "New", "10:00")), emptyList()) as NextDose.Upcoming).slots.firstOrNull { it.name == "Old" })
    }
}
