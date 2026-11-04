package com.carecircle.smoke

import com.carecircle.domain.NextDose
import com.carecircle.domain.NextDoseCalc
import com.carecircle.domain.NextDoseEvent
import com.carecircle.domain.NextDoseMed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The widget's state machine only (no RemoteViews): due, then taken, then all done, then tomorrow. Fast and device-independent. */
class WidgetRefreshTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 4)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private val med = NextDoseMed(1, "Metformin", "500 mg", 1, "08:00,20:00", 0)

    @Test fun theWidgetFollowsTheDayFromDueToAllDone() {
        assertTrue(NextDoseCalc.compute(at(7), zone, listOf(med), emptyList()) is NextDose.Upcoming)
        assertTrue(NextDoseCalc.compute(at(8, 2), zone, listOf(med), emptyList()) is NextDose.Due)
        val morningTaken = listOf(NextDoseEvent(1, at(8), at(8, 3), false))
        assertTrue(NextDoseCalc.compute(at(9), zone, listOf(med), morningTaken) is NextDose.Upcoming)
        val bothTaken = morningTaken + NextDoseEvent(1, at(20), at(20, 1), false)
        val done = NextDoseCalc.compute(at(21), zone, listOf(med), bothTaken) as NextDose.AllDone
        assertEquals(at(8, 0, day.plusDays(1)), done.tomorrowFirst!!.at)
    }

    @Test fun anEmptyListShowsTheAddPrompt() {
        assertEquals(NextDose.Empty, NextDoseCalc.compute(at(9), zone, emptyList(), emptyList()))
    }

    @Test fun anOverdueDoseSwitchesToTheMarkTakenState() {
        val r = NextDoseCalc.compute(at(8, 40), zone, listOf(med), emptyList()) as NextDose.Due
        assertTrue(r.overdue)
    }
}
