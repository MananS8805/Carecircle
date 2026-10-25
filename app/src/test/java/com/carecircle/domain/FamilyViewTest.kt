package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FamilyViewTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 26)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(ist).toInstant().toEpochMilli()

    @Test fun moodFromStatus() {
        assertEquals(Mood.ALL_TAKEN, FamilyView.mood(4, 4, 0, true))
        assertEquals(Mood.ON_TRACK, FamilyView.mood(2, 4, 0, true))
        assertEquals(Mood.MISSED, FamilyView.mood(2, 4, 1, true))
        assertEquals(Mood.NO_DATA, FamilyView.mood(0, 0, 0, true))
        assertEquals(Mood.NO_DATA, FamilyView.mood(4, 4, 0, false)) // yesterday's status is not today's good news
    }

    @Test fun fractionIsClamped() {
        assertEquals(0.75f, FamilyView.fraction(3, 4), 0.001f)
        assertEquals(0f, FamilyView.fraction(0, 0), 0f)
        assertEquals(1f, FamilyView.fraction(5, 4), 0f)
    }

    @Test fun dayBarsCountEventsPerLocalDayOldestFirst() {
        val ev = listOf(
            FamilyEvent(SyncType.TAKEN, at(today, 8), "Metformin"),
            FamilyEvent(SyncType.MISSED, at(today, 20), "Metformin"),
            FamilyEvent(SyncType.TAKEN, at(today.minusDays(1), 8), "Metformin"),
            FamilyEvent(SyncType.SKIPPED, at(today.minusDays(1), 9), "Telma"),
            FamilyEvent(SyncType.SUMMARY, at(today, 21), ""), // ignored
        )
        val days = FamilyView.days(ev, today, 3, ist)
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), days.map { it.date })
        assertEquals(0, days[0].total)
        assertEquals(1, days[1].taken); assertEquals(1, days[1].skipped)
        assertEquals(1, days[2].taken); assertEquals(1, days[2].missed)
    }

    @Test fun justAfterMidnightFollowsTheDeviceZone() {
        val late = FamilyEvent(SyncType.TAKEN, today.atTime(0, 30).atZone(ist).toInstant().toEpochMilli(), "Metformin")
        val d = FamilyView.days(listOf(late), today, 1, ist).single()
        assertEquals(1, d.taken)
        assertEquals(0, FamilyView.days(listOf(late), today, 1, ZoneId.of("America/New_York")).single().taken)
    }

    @Test fun organsFromMedicineNamesMostCommonFirst() {
        val o = FamilyView.organs(listOf("Metformin 500 mg", "Metformin 500 mg", "Telmisartan 40 mg", "Mystery brand"))
        assertEquals(listOf(OrganKind.PANCREAS, OrganKind.HEART), o)
        assertEquals(listOf(OrganKind.BODY), FamilyView.organs(listOf("Mystery brand")))
        assertEquals(listOf("Telmisartan 40 mg"), FamilyView.medicinesFor(OrganKind.HEART, listOf("Metformin 500 mg", "Telmisartan 40 mg", "Telmisartan 40 mg")))
    }
}
