package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklySummaryTest {
    private val start = LocalDate.of(2026, 11, 2) // a Monday
    private fun day(i: Int, taken: Int, missed: Int = 0, skipped: Int = 0, pending: Int = 0) = DayStat(start.plusDays(i.toLong()), taken, missed, skipped, pending)
    private fun result(vararg d: DayStat) = AdherenceResult(start, start.plusDays(6), d.toList(), emptyList(), emptyList())
    private fun week(vararg d: DayStat): AdherenceResult {
        val filled = (0..6).map { i -> d.firstOrNull { it.date == start.plusDays(i.toLong()) } ?: day(i, 0) }
        return result(*filled.toTypedArray())
    }

    @Test fun percentCountsAndBestAndWorstDay() {
        val s = WeeklySummaries.compute(week(day(0, 2), day(1, 1, 1), day(2, 0, 2), day(3, 2), day(4, 2), day(5, 2), day(6, 2)), emptyList())
        assertEquals(11, s.taken); assertEquals(3, s.missed); assertEquals(14, s.decided)
        assertEquals(79, s.percent)
        assertEquals(start.plusDays(6), s.bestDay)   // ties go to the latest all-taken day
        assertEquals(start.plusDays(2), s.worstDay)
        assertEquals(7, s.daysWithDoses)
        assertFalse(s.isPartialWeek)
    }

    @Test fun longestStreakOfDaysWithNoMissedDose() {
        val s = WeeklySummaries.compute(week(day(0, 2), day(1, 2), day(2, 1, 1), day(3, 2), day(4, 2), day(5, 2), day(6, 2)), emptyList())
        assertEquals(4, s.longestStreak)
    }

    @Test fun aDayWithoutDosesNeitherBreaksNorExtendsTheStreak() {
        val s = WeeklySummaries.compute(week(day(0, 2), day(1, 0), day(2, 2), day(3, 0), day(4, 0), day(5, 0), day(6, 0)), emptyList())
        assertEquals(2, s.longestStreak)
        assertEquals(2, s.daysWithDoses)
        assertTrue(s.isPartialWeek)
    }

    @Test fun nothingDecidedYieldsNoPercentAndNoBestDay() {
        val s = WeeklySummaries.compute(week(day(0, 0, pending = 2)), emptyList())
        assertNull(s.percent); assertNull(s.bestDay); assertNull(s.worstDay)
    }

    @Test fun allTakenWeekHasNoWorstDay() {
        val s = WeeklySummaries.compute(week(day(0, 2), day(1, 2), day(2, 2), day(3, 2), day(4, 2), day(5, 2), day(6, 2)), emptyList())
        assertNull(s.worstDay)
        assertEquals(100, s.percent)
        assertEquals(7, s.longestStreak)
    }

    @Test fun lowStockUnderSevenDaysFewestFirstAndAsNeededLeftOut() {
        val stock = listOf(
            StockMed("Plenty", "", 100, 1, 2), StockMed("Low", "500 mg", 6, 1, 2), StockMed("Lower", "", 2, 1, 2),
            StockMed("As needed", "", 0, 1, 0), StockMed("Edge", "", 14, 1, 2), // exactly 7 days: not low
        )
        val s = WeeklySummaries.compute(week(day(0, 1)), stock)
        assertEquals(listOf("Lower", "Low 500 mg"), s.lowStock.map { it.label })
        assertEquals(listOf(1, 3), s.lowStock.map { it.daysLeft })
        assertEquals(0, s.lowStockMore)
    }

    @Test fun longListsAreCutToFiveWithACountOfTheRest() {
        val stock = (1..8).map { StockMed("M$it", "", it / 2, 1, 1) }
        val s = WeeklySummaries.compute(week(day(0, 1)), stock)
        assertEquals(WeeklySummaries.MAX_LISTED, s.lowStock.size)
        assertEquals(3, s.lowStockMore)
    }

    @Test fun reminderIsTheNextSundayEveningInLocalTime() {
        val zone = ZoneId.of("Asia/Kolkata")
        val wed = ZonedDateTime.of(2026, 11, 4, 10, 0, 0, 0, zone)
        val n = WeeklyReminder.next(wed)
        assertEquals(DayOfWeek.SUNDAY, n.dayOfWeek); assertEquals(18, n.hour); assertEquals(LocalDate.of(2026, 11, 8), n.toLocalDate())
        // On Sunday before 18:00 it is today, after 18:00 a week later.
        assertEquals(LocalDate.of(2026, 11, 8), WeeklyReminder.next(ZonedDateTime.of(2026, 11, 8, 17, 59, 0, 0, zone)).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 15), WeeklyReminder.next(ZonedDateTime.of(2026, 11, 8, 18, 0, 0, 0, zone)).toLocalDate())
    }

    @Test fun reminderKeepsSixPmAcrossAClockChange() {
        val zone = ZoneId.of("Europe/London") // clocks go back on 2026-10-25, a Sunday
        val n = WeeklyReminder.next(ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, zone))
        assertEquals(LocalDate.of(2026, 10, 25), n.toLocalDate())
        assertEquals(18, n.hour)
    }
}
