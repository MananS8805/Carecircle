package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalsAppointmentsTest {
    @Test fun parsesDecimalsWithCommaOrDot() {
        assertEquals(72.5, Vitals.parse("72,5")!!, 0.0)
        assertEquals(98.0, Vitals.parse(" 98 ")!!, 0.0)
        assertNull(Vitals.parse("abc")); assertNull(Vitals.parse(""))
    }

    @Test fun rejectsReadingsThatCannotBeReal() {
        assertTrue(Vitals.isPlausible(VitalKind.BP, 120.0, 80.0))
        assertFalse(Vitals.isPlausible(VitalKind.BP, 80.0, 120.0)) // swapped
        assertFalse(Vitals.isPlausible(VitalKind.BP, 400.0, 80.0))
        assertFalse(Vitals.isPlausible(VitalKind.BP, 120.0, null))
        assertTrue(Vitals.isPlausible(VitalKind.SUGAR, 110.0)); assertFalse(Vitals.isPlausible(VitalKind.SUGAR, 5.0))
        assertTrue(Vitals.isPlausible(VitalKind.PULSE, 72.0)); assertFalse(Vitals.isPlausible(VitalKind.PULSE, 400.0))
        assertTrue(Vitals.isPlausible(VitalKind.WEIGHT, 68.5)); assertFalse(Vitals.isPlausible(VitalKind.WEIGHT, 0.0))
        assertTrue(Vitals.isPlausible(VitalKind.TEMP, 37.0)); assertFalse(Vitals.isPlausible(VitalKind.TEMP, 98.6)) // Fahrenheit typed in a Celsius field
    }

    @Test fun formatsReadings() {
        assertEquals("120/80", Vitals.format(VitalKind.BP, 120.0, 80.0))
        assertEquals("72.5", Vitals.format(VitalKind.WEIGHT, 72.5, null))
        assertEquals("98", Vitals.format(VitalKind.SUGAR, 98.0, null))
    }

    @Test fun statsOverAReadingSeries() {
        val s = Vitals.stats(listOf(130.0 to 85.0, 120.0 to 80.0, 140.0 to 90.0))!!
        assertEquals(3, s.count); assertEquals(130.0, s.avg1, 0.001); assertEquals(120.0, s.min1, 0.0); assertEquals(140.0, s.max1, 0.0)
        assertEquals(140.0, s.latest1, 0.0); assertEquals(85.0, s.avg2!!, 0.001); assertEquals(90.0, s.latest2!!, 0.0)
        assertNull(Vitals.stats(emptyList()))
    }

    @Test fun reminderTimeAndArming() {
        val a = ApptLite(1, 10_000_000L, 60)
        assertEquals(10_000_000L - 3_600_000L, Appointments.remindAt(a))
        assertTrue(Appointments.shouldArm(a, 1_000_000L))
        assertFalse(Appointments.shouldArm(a, 8_000_000L)) // reminder moment already passed
    }

    @Test fun upcomingFirstThenPastNewestFirst() {
        val now = 100L
        val list = listOf(ApptLite(1, 50, 60), ApptLite(2, 300, 60), ApptLite(3, 200, 60), ApptLite(4, 10, 60))
        assertEquals(listOf(3L, 2L, 1L, 4L), Appointments.order(list, now).map { it.id })
        assertEquals(listOf(3L, 2L), Appointments.order(list, now).take(2).map { it.id })
        assertEquals(listOf(1L, 4L), Appointments.order(list, now).drop(2).map { it.id })
    }
}
