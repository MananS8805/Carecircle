package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class QuietHoursTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int = 0, day: Int = 4) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)
    private fun ms(z: ZonedDateTime) = z.toInstant().toEpochMilli()
    private val night = QuietSettings(true, LocalTime.of(22, 0), LocalTime.of(7, 0))

    @Test fun aRangeAcrossMidnightIsQuietLateAndEarly() {
        assertTrue(QuietHours.isQuiet(at(23), night)); assertTrue(QuietHours.isQuiet(at(0, 30), night))
        assertTrue(QuietHours.isQuiet(at(6, 59), night)); assertTrue(QuietHours.isQuiet(at(22, 0), night))
        assertFalse(QuietHours.isQuiet(at(7, 0), night)); assertFalse(QuietHours.isQuiet(at(12), night)); assertFalse(QuietHours.isQuiet(at(21, 59), night))
    }

    @Test fun aSameDayRangeWorksToo() {
        val lunch = QuietSettings(true, LocalTime.of(13, 0), LocalTime.of(14, 0))
        assertTrue(QuietHours.isQuiet(at(13, 30), lunch)); assertFalse(QuietHours.isQuiet(at(14, 0), lunch)); assertFalse(QuietHours.isQuiet(at(12, 59), lunch))
    }

    @Test fun offOrEqualStartAndEndMeansNeverQuiet() {
        assertFalse(QuietHours.isQuiet(at(23), night.copy(enabled = false)))
        assertFalse(QuietHours.isQuiet(at(23), night.copy(end = night.start)))
        assertFalse(QuietHours.validRange(LocalTime.of(8, 0), LocalTime.of(8, 0)))
        assertTrue(QuietHours.validRange(LocalTime.of(22, 0), LocalTime.of(7, 0)))
    }

    @Test fun showHoldOrDrop() {
        assertEquals(AlertDecision.SHOW, QuietHours.shouldShow(ms(at(12)), zone, night))
        assertEquals(AlertDecision.HOLD, QuietHours.shouldShow(ms(at(23)), zone, night))
        assertEquals(AlertDecision.HOLD, QuietHours.shouldShow(ms(at(12)), zone, night.copy(snoozeUntil = ms(at(13)))))
        assertEquals(AlertDecision.SHOW, QuietHours.shouldShow(ms(at(14)), zone, night.copy(snoozeUntil = ms(at(13)))))
        // A push that arrives a day late is dropped, whatever the hour.
        assertEquals(AlertDecision.DROP, QuietHours.shouldShow(ms(at(12)), zone, night, sentAtMs = ms(at(12)) - 13 * 3600_000L))
        assertEquals(AlertDecision.SHOW, QuietHours.shouldShow(ms(at(12)), zone, night, sentAtMs = ms(at(12)) - 3600_000L))
    }

    @Test fun nextQuietEndIsTheComingMorning() {
        assertEquals(at(7, 0, 5), QuietHours.nextQuietEnd(at(23), night))
        assertEquals(at(7, 0, 4), QuietHours.nextQuietEnd(at(2), night))
        assertNull(QuietHours.nextQuietEnd(at(12), night))
    }

    @Test fun releaseWaitsForTheSnoozeAndThenForQuietHoursThatFollow() {
        assertNull(QuietHours.releaseAt(ms(at(12)), zone, night))
        assertEquals(ms(at(7, 0, 5)), QuietHours.releaseAt(ms(at(23)), zone, night))
        val s = night.copy(snoozeUntil = ms(at(22, 30)))
        assertEquals(ms(at(7, 0, 5)), QuietHours.releaseAt(ms(at(20)), zone, s)) // snooze ends inside quiet hours
        assertEquals(ms(at(15)), QuietHours.releaseAt(ms(at(12)), zone, night.copy(enabled = false, snoozeUntil = ms(at(15)))))
    }

    @Test fun snoozeChoices() {
        val now = ms(at(15))
        assertEquals(now + 3600_000L, QuietHours.snoozeUntil(SnoozeChoice.ONE_HOUR, now, zone))
        assertEquals(now + 8 * 3600_000L, QuietHours.snoozeUntil(SnoozeChoice.EIGHT_HOURS, now, zone))
        assertEquals(ms(at(7, 0, 5)), QuietHours.snoozeUntil(SnoozeChoice.TOMORROW_MORNING, now, zone))
        // In the small hours "tomorrow morning" is the morning that is coming.
        assertEquals(ms(at(7, 0, 4)), QuietHours.snoozeUntil(SnoozeChoice.TOMORROW_MORNING, ms(at(2)), zone))
    }

    @Test fun heldAlertsAreDeduplicatedAndCappedAtTwenty() {
        val day = LocalDate.of(2026, 11, 4)
        var held = emptyList<HeldAlert>()
        held = HeldAlerts.add(held, HeldAlert(day, "8:00 AM", "ABCD2345"))
        held = HeldAlerts.add(held, HeldAlert(day, "8:00 AM", "ABCD2345"))
        assertEquals(1, held.size)
        for (i in 1..30) held = HeldAlerts.add(held, HeldAlert(day, "$i:00", "ABCD2345"))
        assertEquals(HeldAlerts.MAX, held.size)
        assertEquals("30:00", held.last().slotLabel)
        assertEquals(held, HeldAlerts.decode(HeldAlerts.encode(held)))
        assertEquals(emptyList<HeldAlert>(), HeldAlerts.decode(""))
    }
}
