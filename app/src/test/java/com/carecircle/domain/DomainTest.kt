package com.carecircle.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DomainTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 21, h, m, 0, 0, ist)

    @Test fun parseTimesIgnoresBadEntriesAndSorts() {
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)), Schedule.parseTimes("20:30, 08:00, nope, 08:00"))
    }

    @Test fun nextDoseTodayThenTomorrow() {
        val times = Schedule.parseTimes("08:00,20:00")
        assertEquals(at(20, 0), Schedule.nextDose(times, at(9, 0)))
        val tomorrow = Schedule.nextDose(times, at(21, 0))!!
        assertEquals(22, tomorrow.dayOfMonth)
        assertEquals(8, tomorrow.hour)
    }

    @Test fun nextDoseIsStrictlyAfter() {
        assertEquals(22, Schedule.nextDose(listOf(LocalTime.of(8, 0)), at(8, 0))!!.dayOfMonth)
    }

    @Test fun nextDoseEmptyIsNull() = assertNull(Schedule.nextDose(emptyList(), at(8, 0)))

    @Test fun refillDaysLeft() {
        assertEquals(15, Refill.daysLeft(30, 1, 2))
        assertEquals(7, Refill.daysLeft(30, 2, 2))
        assertEquals(0, Refill.daysLeft(0, 1, 1))
        assertEquals(3, Refill.daysLeft(7, 1, 2)) // floor
    }

    @Test fun reorderThreshold() {
        assertTrue(Refill.needsReorder(5)); assertFalse(Refill.needsReorder(6))
    }

    @Test fun missedDoseGraceWindow() {
        val s = 1_000_000L
        assertFalse(MissedDose.isMissed(s, s + 29 * 60_000L, null))
        assertTrue(MissedDose.isMissed(s, s + 30 * 60_000L, null))
        assertFalse(MissedDose.isMissed(s, s + 90 * 60_000L, s + 1000))
    }

    @Test fun phoneNormalization() {
        assertEquals("919876543210", Messages.normalizePhone("98765 43210"))
        assertEquals("919876543210", Messages.normalizePhone("+91 98765-43210"))
        assertNull(Messages.normalizePhone("123"))
    }

    @Test fun whatsappUrlEncodesText() {
        val u = Messages.whatsAppUrl("919876543210", "a b&c")
        assertEquals("https://wa.me/919876543210?text=a+b%26c", u)
    }

    @Test fun saltParserReadsIndianStrip() {
        val ocr = "Each film coated tablet contains:\nParacetamol IP 500 mg\nMfd: 03/2026 Exp: 02/2028\nMRP Rs. 30.00"
        assertEquals(listOf("paracetamol"), SaltParser.parse(ocr))
    }

    @Test fun saltParserCombination() {
        val ocr = "Amoxycillin 500mg\nClavulanic Acid 125 mg"
        assertEquals(listOf("amoxycillin", "clavulanic acid"), SaltParser.parse(ocr))
    }

    @Test fun snoozeFiresTenMinutesLater() {
        assertEquals(1_000_000L + 600_000L, Snooze.fireAt(1_000_000L))
    }

    @Test fun snoozeDoesNothingOnceTaken() {
        assertTrue(Snooze.shouldRepost(null))
        assertFalse(Snooze.shouldRepost(123L))
    }

    @Test fun snoozeDoesNotMoveMissedDeadline() {
        val scheduled = 5_000_000L
        val snoozedUntil = Snooze.fireAt(scheduled + 60_000L)
        // The deadline is still scheduled + grace, regardless of the snooze time.
        assertFalse(MissedDose.isMissed(scheduled, scheduled + 29 * 60_000L, null))
        assertTrue(MissedDose.isMissed(scheduled, scheduled + 30 * 60_000L, null))
        assertTrue(snoozedUntil < scheduled + 30 * 60_000L)
    }

    @Test fun conditionInferredFromGenericName() {
        assertEquals(Condition.DIABETES, Conditions.infer("Glycomet", "metformin"))
        assertEquals(OrganKind.PANCREAS, Conditions.infer("", "Metformin IP 500 mg")?.organ)
        assertEquals(Condition.BLOOD_PRESSURE, Conditions.infer("Telma 40", "telmisartan"))
        assertNull(Conditions.infer("Vitamin C", ""))
    }

    @Test fun conditionFromChipTag() {
        assertEquals(Condition.ASTHMA, Conditions.fromTag(" asthma "))
        assertNull(Conditions.fromTag("my own tag"))
    }
}
