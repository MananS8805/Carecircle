package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MissedSweepTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private val today = LocalDate.of(2026, 11, 4)

    @Test fun findsOnlyDosesPastTheGraceWindow() {
        val now = at(today, 12, 0)
        val slots = MissedSweep.overdueSlots(listOf(LocalTime.of(8, 0), LocalTime.of(11, 45), LocalTime.of(20, 0)), now, zone, 0, null)
        assertEquals(listOf(at(today.minusDays(1), 8), at(today.minusDays(1), 11, 45), at(today.minusDays(1), 20), at(today, 8)), slots)
    }

    @Test fun ignoresDosesBeforeTheMedicineWasAddedOrAfterItWasArchived() {
        val now = at(today, 12, 0)
        val slots = MissedSweep.overdueSlots(listOf(LocalTime.of(8, 0)), now, zone, createdAt = at(today, 7), archivedAt = null)
        assertEquals(listOf(at(today, 8)), slots)
        assertEquals(emptyList<Long>(), MissedSweep.overdueSlots(listOf(LocalTime.of(8, 0)), now, zone, 0, archivedAt = at(today.minusDays(2), 1)))
    }

    @Test fun keepsWallClockTimeInAnotherZone() {
        val dubai = ZoneId.of("Asia/Dubai")
        val now = today.atTime(12, 0).atZone(dubai).toInstant().toEpochMilli()
        val slot = MissedSweep.overdueSlots(listOf(LocalTime.of(8, 0)), now, dubai, 0, null).last()
        assertEquals(today.atTime(8, 0).atZone(dubai).toInstant().toEpochMilli(), slot)
    }
}
