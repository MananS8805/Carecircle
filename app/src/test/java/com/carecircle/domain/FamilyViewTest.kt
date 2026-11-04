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

class FamilyMergeTest {
    private fun ev(type: SyncType, at: Long, sched: Long = 100, med: String = "8:00 AM") = FamilyEvent(type, at, med, sched)

    @org.junit.Test fun missedThenTakenCountsAsTaken() {
        val merged = FamilyView.merge(listOf(ev(SyncType.MISSED, 10), ev(SyncType.TAKEN, 20)))
        org.junit.Assert.assertEquals(listOf(SyncType.TAKEN), merged.map { it.type })
    }

    @org.junit.Test fun aStaleMissedArrivingAfterTakenNeverWins() {
        val merged = FamilyView.merge(listOf(ev(SyncType.TAKEN, 20), ev(SyncType.MISSED, 30)))
        org.junit.Assert.assertEquals(listOf(SyncType.TAKEN), merged.map { it.type })
    }

    @org.junit.Test fun skippedBeatsMissedAndDifferentDosesStaySeparate() {
        val merged = FamilyView.merge(listOf(ev(SyncType.MISSED, 10, sched = 100), ev(SyncType.SKIPPED, 12, sched = 100), ev(SyncType.MISSED, 50, sched = 200)))
        org.junit.Assert.assertEquals(setOf(SyncType.SKIPPED, SyncType.MISSED), merged.map { it.type }.toSet())
        org.junit.Assert.assertEquals(2, merged.size)
    }

    @org.junit.Test fun summariesAreDroppedAndTheDayBarsUseTheMergedResult() {
        val zone = java.time.ZoneId.of("UTC")
        val day = java.time.LocalDate.of(2026, 11, 4)
        val at = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val days = FamilyView.days(listOf(ev(SyncType.MISSED, at), ev(SyncType.TAKEN, at + 1000), ev(SyncType.SUMMARY, at)), day, 1, zone)
        org.junit.Assert.assertEquals(1, days.single().taken)
        org.junit.Assert.assertEquals(0, days.single().missed)
    }

    @org.junit.Test fun staleMeansOverTwoHoursButNeverWhenThereIsNoDataYet() {
        val now = 10 * 3600_000L
        org.junit.Assert.assertFalse(FamilyView.isStale(0, now))
        org.junit.Assert.assertFalse(FamilyView.isStale(now - 3600_000L, now))
        org.junit.Assert.assertTrue(FamilyView.isStale(now - 3 * 3600_000L, now))
    }
}
