package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderHealthTest {
    private val now = 10 * 24 * 3600_000L
    private fun ok() = ReminderHealth(true, true, true, true, true, 2, now - 1000, now - 3 * 24 * 3600_000L, now, lastRescheduleAt = now - 1000, expectedAlarms = 2, armedAlarms = 2)

    @Test fun healthyWhenEverythingIsOn() {
        assertTrue(ok().issues.isEmpty()); assertFalse(ok().blocking)
    }

    @Test fun blockingIssuesComeFirst() {
        val h = ok().copy(notifications = false, exactAlarms = false, batteryUnrestricted = false)
        assertEquals(listOf(HealthIssue.NOTIFICATIONS_OFF, HealthIssue.EXACT_ALARMS_OFF, HealthIssue.BATTERY_RESTRICTED), h.issues)
        assertTrue(h.blocking)
    }

    @Test fun batteryAndDoNotDisturbAreWarningsOnly() {
        val h = ok().copy(batteryUnrestricted = false, doNotDisturbOff = false)
        assertEquals(listOf(HealthIssue.BATTERY_RESTRICTED, HealthIssue.DO_NOT_DISTURB), h.issues)
        assertFalse(h.blocking)
    }

    @Test fun nothingScheduledIsReported() {
        assertEquals(listOf(HealthIssue.NOTHING_SCHEDULED), ok().copy(scheduledMedicines = 0).issues)
    }

    @Test fun neverFiredOnlyAfterAboutADay() {
        val fresh = ok().copy(lastReminderAt = 0, firstMedicineAddedAt = now - 3600_000L)
        assertTrue(HealthIssue.NEVER_FIRED !in fresh.issues)
        val old = ok().copy(lastReminderAt = 0, firstMedicineAddedAt = now - 3 * 24 * 3600_000L)
        assertTrue(HealthIssue.NEVER_FIRED in old.issues)
    }

    @Test fun channelOffBlocks() {
        assertTrue(ok().copy(channelsOn = false).blocking)
    }

    @Test fun staleRescheduleIsFlaggedOnlyWhenMedicinesExist() {
        val stale = ok().copy(lastRescheduleAt = now - 40 * 3600_000L)
        assertTrue(HealthIssue.NO_RECENT_RESCHEDULE in stale.issues)
        assertTrue(HealthIssue.NO_RECENT_RESCHEDULE !in stale.copy(scheduledMedicines = 0).issues)
        assertTrue(HealthIssue.NO_RECENT_RESCHEDULE !in ok().copy(lastRescheduleAt = now - 3600_000L).issues)
    }

    @Test fun alarmCountMismatchNeedsBothNumbers() {
        val fresh = ok().copy(lastRescheduleAt = now)
        assertTrue(HealthIssue.ALARM_COUNT_MISMATCH in fresh.copy(expectedAlarms = 3, armedAlarms = 2).issues)
        assertTrue(HealthIssue.ALARM_COUNT_MISMATCH !in fresh.copy(expectedAlarms = 3, armedAlarms = 3).issues)
        assertTrue(HealthIssue.ALARM_COUNT_MISMATCH !in fresh.copy(expectedAlarms = 3, armedAlarms = -1).issues)
    }

    @Test fun allGreenWhenEverythingIsFresh() {
        assertTrue(ok().copy(lastRescheduleAt = now, expectedAlarms = 2, armedAlarms = 2).issues.isEmpty())
    }
}
