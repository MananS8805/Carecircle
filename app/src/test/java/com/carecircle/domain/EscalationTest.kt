package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class EscalationTest {
    @Test fun repeatsAtTenAndTwentyMinutesAndAlertAtThirty() {
        val at = 1_000_000L
        assertEquals(listOf(at + 600_000L, at + 1_200_000L), Escalation.repeatTimes(at))
        assertEquals(30L, Escalation.ALERT_AFTER_MINUTES)
        assertTrue(Escalation.repeatTimes(at).all { it < at + Escalation.ALERT_AFTER_MINUTES * 60_000L })
    }

    @Test fun repeatsStopOnceTakenOrSkipped() {
        assertTrue(Escalation.shouldRemind(null, false))
        assertFalse(Escalation.shouldRemind(5L, false))
        assertFalse(Escalation.shouldRemind(null, true))
    }

    @Test fun countdownShownOnRepeats() {
        assertEquals(20L, Escalation.minutesUntilAlert(1))
        assertEquals(10L, Escalation.minutesUntilAlert(2))
    }

    @Test fun dailySummaryIsNextNinePm() {
        val ist = ZoneId.of("Asia/Kolkata")
        val morning = ZonedDateTime.of(2026, 10, 26, 8, 0, 0, 0, ist)
        assertEquals(21, Escalation.nextDailySummary(morning).hour)
        assertEquals(26, Escalation.nextDailySummary(morning).dayOfMonth)
        val late = ZonedDateTime.of(2026, 10, 26, 21, 30, 0, 0, ist)
        assertEquals(27, Escalation.nextDailySummary(late).dayOfMonth)
    }
}
